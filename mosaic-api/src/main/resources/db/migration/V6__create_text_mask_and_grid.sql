CREATE TABLE text_mask_design (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES mosaic_project(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    text_content VARCHAR(400) NOT NULL,
    direction VARCHAR(16) NOT NULL,
    font_id VARCHAR(80) NOT NULL,
    font_version VARCHAR(80) NOT NULL,
    font_sha256 CHAR(64) NOT NULL,
    font_size INTEGER NOT NULL,
    font_weight INTEGER NOT NULL,
    letter_spacing DOUBLE PRECISION NOT NULL,
    line_spacing DOUBLE PRECISION NOT NULL,
    canvas_width INTEGER NOT NULL,
    canvas_height INTEGER NOT NULL,
    padding INTEGER NOT NULL,
    grid_strategy VARCHAR(24) NOT NULL,
    edge_policy VARCHAR(24) NOT NULL,
    cell_width INTEGER NOT NULL,
    cell_height INTEGER NOT NULL,
    gap INTEGER NOT NULL,
    coverage_threshold DOUBLE PRECISION NOT NULL,
    status VARCHAR(24) NOT NULL,
    algorithm_version VARCHAR(64),
    content_hash CHAR(64) NOT NULL,
    mask_key VARCHAR(1024),
    debug_key VARCHAR(1024),
    bounds_x INTEGER,
    bounds_y INTEGER,
    bounds_width INTEGER,
    bounds_height INTEGER,
    estimated_stroke_width DOUBLE PRECISION,
    average_coverage DOUBLE PRECISION,
    slot_count INTEGER NOT NULL DEFAULT 0,
    warnings_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    error_code VARCHAR(64),
    error_message VARCHAR(500),
    created_by UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT ck_text_mask_direction CHECK (direction IN ('HORIZONTAL', 'VERTICAL')),
    CONSTRAINT ck_text_mask_grid_strategy CHECK (grid_strategy IN ('REGULAR', 'STAGGERED')),
    CONSTRAINT ck_text_mask_edge_policy CHECK (edge_policy IN ('STRICT', 'INCLUDE_PARTIAL')),
    CONSTRAINT ck_text_mask_status CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED')),
    CONSTRAINT ck_text_mask_font_size CHECK (font_size BETWEEN 16 AND 2048),
    CONSTRAINT ck_text_mask_font_weight CHECK (font_weight BETWEEN 100 AND 900),
    CONSTRAINT ck_text_mask_canvas CHECK (
        canvas_width BETWEEN 256 AND 4096 AND canvas_height BETWEEN 256 AND 4096
    ),
    CONSTRAINT ck_text_mask_padding CHECK (
        padding BETWEEN 0 AND 2048 AND padding * 2 < LEAST(canvas_width, canvas_height)
    ),
    CONSTRAINT ck_text_mask_cell CHECK (
        cell_width BETWEEN 4 AND 512 AND cell_height BETWEEN 4 AND 512 AND gap BETWEEN 0 AND 128
    ),
    CONSTRAINT ck_text_mask_coverage CHECK (coverage_threshold BETWEEN 0.5 AND 0.98),
    CONSTRAINT ck_text_mask_hash CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_text_mask_font_hash CHECK (font_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_text_mask_project_created ON text_mask_design (project_id, created_at DESC);
CREATE INDEX idx_text_mask_status ON text_mask_design (status, updated_at);

CREATE TABLE text_mask_slot (
    mask_id UUID NOT NULL REFERENCES text_mask_design(id) ON DELETE CASCADE,
    slot_index INTEGER NOT NULL,
    row_index INTEGER NOT NULL,
    column_index INTEGER NOT NULL,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    coverage DOUBLE PRECISION NOT NULL,
    edge BOOLEAN NOT NULL,
    PRIMARY KEY (mask_id, slot_index),
    CONSTRAINT ck_text_mask_slot_position CHECK (
        slot_index >= 0 AND row_index >= 0 AND column_index >= 0 AND x >= 0 AND y >= 0
    ),
    CONSTRAINT ck_text_mask_slot_size CHECK (width > 0 AND height > 0),
    CONSTRAINT ck_text_mask_slot_coverage CHECK (coverage BETWEEN 0 AND 1)
);

CREATE INDEX idx_text_mask_slot_grid ON text_mask_slot (mask_id, row_index, column_index);

INSERT INTO platform_metadata (metadata_key, metadata_value)
VALUES ('text_mask_schema', '6');
