CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    global_role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_app_user_global_role CHECK (global_role IN ('ADMIN', 'USER')),
    CONSTRAINT ck_app_user_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE UNIQUE INDEX uk_app_user_username_lower ON app_user (LOWER(username));

CREATE TABLE mosaic_project (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_mosaic_project_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE TABLE project_member (
    project_id UUID NOT NULL REFERENCES mosaic_project(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL,
    created_by UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id, user_id),
    CONSTRAINT ck_project_member_role CHECK (
        role IN ('PROJECT_ADMIN', 'REVIEWER', 'DESIGNER', 'VIEWER')
    )
);

CREATE INDEX idx_project_member_user_id ON project_member (user_id);

CREATE TABLE audit_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_user_id UUID REFERENCES app_user(id),
    action VARCHAR(80) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id VARCHAR(100),
    success BOOLEAN NOT NULL,
    request_id VARCHAR(128),
    ip_address VARCHAR(64),
    detail_json TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_log_actor_created_at ON audit_log (actor_user_id, created_at DESC);
CREATE INDEX idx_audit_log_action_created_at ON audit_log (action, created_at DESC);

INSERT INTO platform_metadata (metadata_key, metadata_value)
VALUES ('identity_bootstrap', 'pending');
