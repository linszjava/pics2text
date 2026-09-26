CREATE TABLE processing_job (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES mosaic_project(id) ON DELETE CASCADE,
    resource_type VARCHAR(50),
    resource_id UUID,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(40) NOT NULL,
    progress SMALLINT NOT NULL DEFAULT 0,
    current_attempt INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    next_attempt_at TIMESTAMPTZ,
    heartbeat_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    cancellation_requested_at TIMESTAMPTZ,
    cancellable BOOLEAN NOT NULL DEFAULT TRUE,
    worker_id VARCHAR(160),
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    result_json JSONB,
    error_code VARCHAR(100),
    error_message VARCHAR(2000),
    version BIGINT NOT NULL DEFAULT 0,
    created_by UUID REFERENCES app_user(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_processing_job_progress CHECK (progress BETWEEN 0 AND 100),
    CONSTRAINT ck_processing_job_attempts CHECK (current_attempt >= 0 AND max_attempts > 0),
    CONSTRAINT ck_processing_job_status CHECK (status IN (
        'PENDING', 'RUNNING', 'RETRY_WAIT', 'CANCELLATION_REQUESTED',
        'SUCCEEDED', 'FAILED', 'DEAD_LETTERED', 'CANCELLED'
    ))
);

CREATE INDEX idx_processing_job_project_created
    ON processing_job(project_id, created_at DESC);
CREATE INDEX idx_processing_job_recovery
    ON processing_job(status, heartbeat_at)
    WHERE status IN ('RUNNING', 'CANCELLATION_REQUESTED');
CREATE INDEX idx_processing_job_retry
    ON processing_job(status, next_attempt_at)
    WHERE status = 'RETRY_WAIT';

CREATE TABLE job_attempt (
    job_id UUID NOT NULL REFERENCES processing_job(id) ON DELETE CASCADE,
    attempt INTEGER NOT NULL,
    event_id UUID NOT NULL,
    worker_id VARCHAR(160) NOT NULL,
    status VARCHAR(30) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    heartbeat_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    error_message VARCHAR(2000),
    PRIMARY KEY (job_id, attempt),
    UNIQUE (event_id)
);

CREATE TABLE outbox_event (
    event_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(60) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    exchange_name VARCHAR(160) NOT NULL,
    routing_key VARCHAR(160) NOT NULL,
    payload_json JSONB NOT NULL,
    delay_millis BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    publish_attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    locked_until TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    last_error VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHING', 'PUBLISHED', 'DEAD')),
    CONSTRAINT ck_outbox_delay CHECK (delay_millis >= 0)
);

CREATE INDEX idx_outbox_dispatch
    ON outbox_event(status, next_attempt_at, created_at)
    WHERE status IN ('PENDING', 'PUBLISHING');

CREATE TABLE consumed_message (
    consumer_name VARCHAR(160) NOT NULL,
    message_id UUID NOT NULL,
    job_id UUID NOT NULL REFERENCES processing_job(id) ON DELETE CASCADE,
    consumed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (consumer_name, message_id)
);
