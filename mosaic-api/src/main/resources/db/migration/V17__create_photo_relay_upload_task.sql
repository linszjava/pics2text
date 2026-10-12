CREATE TABLE photo_relay_upload_task (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES mosaic_project(id) ON DELETE CASCADE,
    photo_id UUID NOT NULL UNIQUE REFERENCES photo_asset(id) ON DELETE CASCADE,
    uploader_id UUID NOT NULL REFERENCES app_user(id),
    storage_node VARCHAR(128) NOT NULL,
    relative_path VARCHAR(512) NOT NULL,
    byte_size BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    claim_token UUID,
    claimed_at TIMESTAMPTZ,
    failure_code VARCHAR(64),
    failure_message VARCHAR(500),
    temp_deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_photo_relay_status CHECK (status IN ('RECEIVED','QUEUED','BACKEND_UPLOADING','RETRY_WAIT','SUCCEEDED','FAILED')),
    CONSTRAINT ck_photo_relay_attempts CHECK (attempt_count >= 0 AND max_attempts BETWEEN 1 AND 20),
    CONSTRAINT ck_photo_relay_size CHECK (byte_size > 0)
);

CREATE INDEX idx_photo_relay_dispatch
    ON photo_relay_upload_task (storage_node, status, next_attempt_at, created_at)
    WHERE status IN ('QUEUED','RETRY_WAIT','BACKEND_UPLOADING');
