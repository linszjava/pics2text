CREATE TABLE print_render (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES mosaic_project(id) ON DELETE CASCADE,
    layout_id UUID NOT NULL REFERENCES editable_layout(id) ON DELETE RESTRICT,
    layout_version_id UUID NOT NULL REFERENCES layout_version(id) ON DELETE RESTRICT,
    surface_binding_id UUID NOT NULL REFERENCES surface_layout_binding(id) ON DELETE RESTRICT,
    job_id UUID REFERENCES processing_job(id) ON DELETE SET NULL,
    status VARCHAR(32) NOT NULL,
    format VARCHAR(24) NOT NULL,
    dpi INTEGER NOT NULL,
    bleed_mm NUMERIC(12, 6) NOT NULL,
    tile_size INTEGER NOT NULL,
    compression VARCHAR(24) NOT NULL,
    color_profile VARCHAR(80) NOT NULL,
    crop_marks BOOLEAN NOT NULL,
    registration_marks BOOLEAN NOT NULL,
    content_width_px INTEGER NOT NULL,
    content_height_px INTEGER NOT NULL,
    output_width_px INTEGER NOT NULL,
    output_height_px INTEGER NOT NULL,
    input_manifest_json JSONB NOT NULL,
    input_manifest_hash CHAR(64) NOT NULL,
    output_object_key VARCHAR(1024),
    output_sha256 CHAR(64),
    output_byte_size BIGINT,
    output_manifest_json JSONB,
    output_manifest_hash CHAR(64),
    location_list_object_key VARCHAR(1024),
    location_list_sha256 CHAR(64),
    error_code VARCHAR(64),
    error_message VARCHAR(500),
    created_by UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uk_print_render_input UNIQUE (project_id, input_manifest_hash),
    CONSTRAINT ck_print_render_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'READY_FOR_APPROVAL', 'APPROVED', 'REJECTED', 'FAILED')
    ),
    CONSTRAINT ck_print_render_format CHECK (format = 'BIGTIFF'),
    CONSTRAINT ck_print_render_compression CHECK (compression IN ('ZSTD', 'LZW', 'DEFLATE')),
    CONSTRAINT ck_print_render_profile CHECK (color_profile IN ('SRGB_IEC61966_2_1')),
    CONSTRAINT ck_print_render_dimensions CHECK (
        dpi BETWEEN 72 AND 2400 AND bleed_mm >= 0 AND tile_size BETWEEN 128 AND 8192
        AND content_width_px > 0 AND content_height_px > 0
        AND output_width_px >= content_width_px AND output_height_px >= content_height_px
        AND input_manifest_hash ~ '^[0-9a-f]{64}$'
    )
);

CREATE INDEX idx_print_render_project_created ON print_render (project_id, created_at DESC);
CREATE INDEX idx_print_render_layout ON print_render (layout_id, created_at DESC);
CREATE INDEX idx_print_render_status ON print_render (status, updated_at);

CREATE TABLE print_render_approval (
    id UUID PRIMARY KEY,
    render_id UUID NOT NULL REFERENCES print_render(id) ON DELETE CASCADE,
    decision VARCHAR(24) NOT NULL,
    note VARCHAR(500) NOT NULL,
    actor_id UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_print_render_decision CHECK (decision IN ('APPROVED', 'REJECTED'))
);

CREATE INDEX idx_print_render_approval_history ON print_render_approval (render_id, created_at DESC);

INSERT INTO platform_metadata (metadata_key, metadata_value)
VALUES ('print_render_schema', '10');
