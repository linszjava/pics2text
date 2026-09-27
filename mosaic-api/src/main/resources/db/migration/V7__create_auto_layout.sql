CREATE TABLE auto_layout (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES mosaic_project(id) ON DELETE CASCADE,
    mask_id UUID NOT NULL REFERENCES text_mask_design(id) ON DELETE RESTRICT,
    name VARCHAR(120) NOT NULL,
    status VARCHAR(24) NOT NULL,
    algorithm_version VARCHAR(64),
    scoring_version VARCHAR(64) NOT NULL,
    configuration_hash CHAR(64) NOT NULL,
    mask_content_hash CHAR(64) NOT NULL,
    font_sha256 CHAR(64) NOT NULL,
    photo_set_hash CHAR(64),
    random_seed BIGINT NOT NULL,
    allow_photo_reuse BOOLEAN NOT NULL,
    max_photo_reuse INTEGER NOT NULL,
    target_lab_l DOUBLE PRECISION NOT NULL,
    target_lab_a DOUBLE PRECISION NOT NULL,
    target_lab_b DOUBLE PRECISION NOT NULL,
    candidate_window INTEGER NOT NULL,
    optimization_iterations INTEGER NOT NULL,
    weight_quality DOUBLE PRECISION NOT NULL,
    weight_brightness DOUBLE PRECISION NOT NULL,
    weight_color DOUBLE PRECISION NOT NULL,
    weight_crop DOUBLE PRECISION NOT NULL,
    weight_duplicate DOUBLE PRECISION NOT NULL,
    weight_adjacent DOUBLE PRECISION NOT NULL,
    candidate_count INTEGER NOT NULL DEFAULT 0,
    slot_count INTEGER NOT NULL,
    assigned_count INTEGER NOT NULL DEFAULT 0,
    initial_average_score DOUBLE PRECISION,
    final_average_score DOUBLE PRECISION,
    average_brightness_score DOUBLE PRECISION,
    average_color_score DOUBLE PRECISION,
    average_crop_score DOUBLE PRECISION,
    average_adjacent_score DOUBLE PRECISION,
    unique_photo_count INTEGER,
    duplicate_group_reuse_count INTEGER,
    candidate_evaluations BIGINT,
    estimated_memory_bytes BIGINT,
    runtime_millis BIGINT,
    error_code VARCHAR(64),
    error_message VARCHAR(500),
    created_by UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT ck_auto_layout_status CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED')),
    CONSTRAINT ck_auto_layout_hashes CHECK (
        configuration_hash ~ '^[0-9a-f]{64}$'
        AND mask_content_hash ~ '^[0-9a-f]{64}$'
        AND font_sha256 ~ '^[0-9a-f]{64}$'
        AND (photo_set_hash IS NULL OR photo_set_hash ~ '^[0-9a-f]{64}$')
    ),
    CONSTRAINT ck_auto_layout_reuse CHECK (
        max_photo_reuse BETWEEN 1 AND 100
        AND (allow_photo_reuse OR max_photo_reuse = 1)
    ),
    CONSTRAINT ck_auto_layout_lab CHECK (
        target_lab_l BETWEEN 0 AND 255 AND target_lab_a BETWEEN 0 AND 255
        AND target_lab_b BETWEEN 0 AND 255
    ),
    CONSTRAINT ck_auto_layout_limits CHECK (
        candidate_window BETWEEN 8 AND 512
        AND optimization_iterations BETWEEN 0 AND 200000
        AND slot_count > 0 AND candidate_count >= 0 AND assigned_count >= 0
    ),
    CONSTRAINT ck_auto_layout_weights CHECK (
        weight_quality BETWEEN 0 AND 10 AND weight_brightness BETWEEN 0 AND 10
        AND weight_color BETWEEN 0 AND 10 AND weight_crop BETWEEN 0 AND 10
        AND weight_duplicate BETWEEN 0 AND 10 AND weight_adjacent BETWEEN 0 AND 10
        AND weight_quality + weight_brightness + weight_color + weight_crop
            + weight_duplicate + weight_adjacent > 0
    )
);

CREATE INDEX idx_auto_layout_project_created ON auto_layout (project_id, created_at DESC);
CREATE INDEX idx_auto_layout_mask_created ON auto_layout (mask_id, created_at DESC);
CREATE INDEX idx_auto_layout_status ON auto_layout (status, updated_at);

CREATE TABLE auto_layout_assignment (
    layout_id UUID NOT NULL REFERENCES auto_layout(id) ON DELETE CASCADE,
    slot_index INTEGER NOT NULL,
    photo_id UUID NOT NULL REFERENCES photo_asset(id) ON DELETE RESTRICT,
    total_score DOUBLE PRECISION NOT NULL,
    quality_score DOUBLE PRECISION NOT NULL,
    brightness_score DOUBLE PRECISION NOT NULL,
    color_score DOUBLE PRECISION NOT NULL,
    crop_score DOUBLE PRECISION NOT NULL,
    duplicate_score DOUBLE PRECISION NOT NULL,
    adjacent_score DOUBLE PRECISION NOT NULL,
    crop_x DOUBLE PRECISION NOT NULL,
    crop_y DOUBLE PRECISION NOT NULL,
    crop_width DOUBLE PRECISION NOT NULL,
    crop_height DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (layout_id, slot_index),
    CONSTRAINT ck_auto_layout_assignment_scores CHECK (
        total_score BETWEEN 0 AND 1 AND quality_score BETWEEN 0 AND 1
        AND brightness_score BETWEEN 0 AND 1 AND color_score BETWEEN 0 AND 1
        AND crop_score BETWEEN 0 AND 1 AND duplicate_score BETWEEN 0 AND 1
        AND adjacent_score BETWEEN 0 AND 1
    ),
    CONSTRAINT ck_auto_layout_assignment_crop CHECK (
        crop_x BETWEEN 0 AND 1 AND crop_y BETWEEN 0 AND 1
        AND crop_width > 0 AND crop_width <= 1 AND crop_height > 0 AND crop_height <= 1
        AND crop_x + crop_width <= 1.000001 AND crop_y + crop_height <= 1.000001
    )
);

CREATE INDEX idx_auto_layout_assignment_photo ON auto_layout_assignment (layout_id, photo_id);

INSERT INTO platform_metadata (metadata_key, metadata_value)
VALUES ('auto_layout_schema', '7');
