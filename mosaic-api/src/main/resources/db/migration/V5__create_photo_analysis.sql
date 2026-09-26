ALTER TABLE photo_asset
    ADD COLUMN analysis_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN source_key VARCHAR(1024),
    ADD COLUMN analysis_error_code VARCHAR(64),
    ADD COLUMN analysis_error_message VARCHAR(500),
    ADD COLUMN reviewed_by UUID REFERENCES app_user(id),
    ADD COLUMN reviewed_at TIMESTAMPTZ,
    ADD COLUMN review_note VARCHAR(500),
    ADD CONSTRAINT ck_photo_asset_analysis_status CHECK (
        analysis_status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED')
    );

CREATE INDEX idx_photo_asset_analysis_queue
    ON photo_asset (analysis_status, available_at)
    WHERE status = 'AVAILABLE' AND analysis_status IN ('PENDING', 'PROCESSING');

CREATE TABLE photo_analysis (
    photo_id UUID PRIMARY KEY REFERENCES photo_asset(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES mosaic_project(id) ON DELETE CASCADE,
    algorithm_version VARCHAR(64) NOT NULL,
    sharpness DOUBLE PRECISION NOT NULL,
    brightness DOUBLE PRECISION NOT NULL,
    contrast DOUBLE PRECISION NOT NULL,
    lab_l DOUBLE PRECISION NOT NULL,
    lab_a DOUBLE PRECISION NOT NULL,
    lab_b DOUBLE PRECISION NOT NULL,
    d_hash CHAR(16) NOT NULL,
    p_hash CHAR(16) NOT NULL,
    face_count INTEGER NOT NULL,
    faces_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    subject_x DOUBLE PRECISION NOT NULL,
    subject_y DOUBLE PRECISION NOT NULL,
    subject_width DOUBLE PRECISION NOT NULL,
    subject_height DOUBLE PRECISION NOT NULL,
    crop_x DOUBLE PRECISION NOT NULL,
    crop_y DOUBLE PRECISION NOT NULL,
    crop_width DOUBLE PRECISION NOT NULL,
    crop_height DOUBLE PRECISION NOT NULL,
    duplicate_group_id UUID,
    auto_review_decision VARCHAR(24) NOT NULL,
    review_reasons_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    orientation_applied INTEGER NOT NULL DEFAULT 1,
    analyzed_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_photo_analysis_decision CHECK (
        auto_review_decision IN ('AUTO_APPROVED', 'NEEDS_REVIEW', 'AUTO_REJECTED')
    ),
    CONSTRAINT ck_photo_analysis_face_count CHECK (face_count >= 0),
    CONSTRAINT ck_photo_analysis_subject_bounds CHECK (
        subject_x BETWEEN 0 AND 1 AND subject_y BETWEEN 0 AND 1
        AND subject_width > 0 AND subject_width <= 1
        AND subject_height > 0 AND subject_height <= 1
        AND subject_x + subject_width <= 1.000001
        AND subject_y + subject_height <= 1.000001
    ),
    CONSTRAINT ck_photo_analysis_crop_bounds CHECK (
        crop_x BETWEEN 0 AND 1 AND crop_y BETWEEN 0 AND 1
        AND crop_width > 0 AND crop_width <= 1
        AND crop_height > 0 AND crop_height <= 1
        AND crop_x + crop_width <= 1.000001
        AND crop_y + crop_height <= 1.000001
    )
);

CREATE INDEX idx_photo_analysis_project_hashes ON photo_analysis (project_id, d_hash, p_hash);
CREATE INDEX idx_photo_analysis_duplicate_group ON photo_analysis (duplicate_group_id)
    WHERE duplicate_group_id IS NOT NULL;

CREATE TABLE photo_variant (
    photo_id UUID NOT NULL REFERENCES photo_asset(id) ON DELETE CASCADE,
    variant_type VARCHAR(32) NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    byte_size BIGINT NOT NULL,
    content_type VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (photo_id, variant_type),
    CONSTRAINT ck_photo_variant_type CHECK (
        variant_type IN ('THUMBNAIL_256', 'PREVIEW_1024', 'ANALYSIS_2048')
    ),
    CONSTRAINT ck_photo_variant_dimensions CHECK (width > 0 AND height > 0 AND byte_size > 0)
);

INSERT INTO platform_metadata (metadata_key, metadata_value)
VALUES ('photo_analysis_schema', '5');
