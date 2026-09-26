ALTER TABLE mosaic_project
    ADD COLUMN description VARCHAR(1000) NOT NULL DEFAULT '',
    ADD COLUMN configuration_json TEXT NOT NULL DEFAULT '{}',
    ADD COLUMN retention_days INTEGER NOT NULL DEFAULT 30,
    ADD COLUMN max_photo_bytes BIGINT NOT NULL DEFAULT 104857600,
    ADD COLUMN archived_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_mosaic_project_retention_days CHECK (retention_days BETWEEN 1 AND 3650),
    ADD CONSTRAINT ck_mosaic_project_max_photo_bytes CHECK (max_photo_bytes BETWEEN 1024 AND 5368709120),
    ADD CONSTRAINT ck_mosaic_project_configuration_json CHECK (configuration_json::jsonb IS NOT NULL);

CREATE TABLE photo_asset (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES mosaic_project(id) ON DELETE CASCADE,
    uploader_id UUID NOT NULL REFERENCES app_user(id),
    original_filename VARCHAR(255) NOT NULL,
    declared_content_type VARCHAR(80) NOT NULL,
    detected_content_type VARCHAR(80),
    byte_size BIGINT NOT NULL,
    width INTEGER,
    height INTEGER,
    sha256 CHAR(64) NOT NULL,
    authorization_status VARCHAR(24) NOT NULL,
    review_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    status VARCHAR(24) NOT NULL,
    upload_mode VARCHAR(16) NOT NULL,
    multipart_upload_id TEXT,
    part_size BIGINT,
    expected_part_count INTEGER,
    original_key VARCHAR(1024) NOT NULL,
    thumbnail_key VARCHAR(1024) NOT NULL,
    preview_key VARCHAR(1024) NOT NULL,
    duplicate_of UUID REFERENCES photo_asset(id),
    failure_code VARCHAR(64),
    failure_message VARCHAR(500),
    upload_expires_at TIMESTAMPTZ NOT NULL,
    retention_until TIMESTAMPTZ,
    available_at TIMESTAMPTZ,
    withdrawn_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    objects_deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_photo_asset_byte_size CHECK (byte_size > 0),
    CONSTRAINT ck_photo_asset_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_photo_asset_dimensions CHECK (
        (width IS NULL AND height IS NULL) OR (width > 0 AND height > 0)
    ),
    CONSTRAINT ck_photo_asset_authorization CHECK (
        authorization_status IN ('PENDING', 'GRANTED', 'REVOKED')
    ),
    CONSTRAINT ck_photo_asset_review CHECK (review_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_photo_asset_status CHECK (
        status IN ('UPLOADING', 'VERIFYING', 'AVAILABLE', 'DUPLICATE', 'FAILED', 'WITHDRAWN', 'DELETED')
    ),
    CONSTRAINT ck_photo_asset_upload_mode CHECK (upload_mode IN ('SINGLE', 'MULTIPART'))
);

CREATE INDEX idx_photo_asset_project_created ON photo_asset (project_id, created_at DESC);
CREATE INDEX idx_photo_asset_uploader ON photo_asset (uploader_id, created_at DESC);
CREATE INDEX idx_photo_asset_upload_cleanup ON photo_asset (status, upload_expires_at)
    WHERE status IN ('UPLOADING', 'VERIFYING');
CREATE INDEX idx_photo_asset_retention_cleanup ON photo_asset (status, retention_until)
    WHERE status = 'WITHDRAWN';
CREATE UNIQUE INDEX uk_photo_asset_project_sha256_available
    ON photo_asset (project_id, sha256)
    WHERE status = 'AVAILABLE';

INSERT INTO platform_metadata (metadata_key, metadata_value)
VALUES ('project_upload_schema', '3');
