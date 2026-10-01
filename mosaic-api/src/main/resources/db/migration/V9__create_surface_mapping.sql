CREATE TABLE surface_template (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES mosaic_project(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    surface_type VARCHAR(24) NOT NULL,
    unit VARCHAR(24) NOT NULL,
    diameter_mm NUMERIC(14, 6) NOT NULL,
    circumference_mm NUMERIC(14, 6) NOT NULL,
    axial_length_mm NUMERIC(14, 6) NOT NULL,
    seam_x_mm NUMERIC(14, 6) NOT NULL,
    seam_safety_mm NUMERIC(14, 6) NOT NULL,
    safe_top_mm NUMERIC(14, 6) NOT NULL,
    safe_right_mm NUMERIC(14, 6) NOT NULL,
    safe_bottom_mm NUMERIC(14, 6) NOT NULL,
    safe_left_mm NUMERIC(14, 6) NOT NULL,
    template_version INTEGER NOT NULL,
    status VARCHAR(24) NOT NULL,
    geometry_hash CHAR(64) NOT NULL,
    created_by UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_surface_template_name UNIQUE (project_id, name),
    CONSTRAINT ck_surface_template_type CHECK (surface_type IN ('CYLINDER')),
    CONSTRAINT ck_surface_template_unit CHECK (unit = 'MILLIMETER'),
    CONSTRAINT ck_surface_template_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_surface_template_dimensions CHECK (
        diameter_mm > 0 AND circumference_mm > 0 AND axial_length_mm > 0
        AND seam_x_mm >= 0 AND seam_x_mm < circumference_mm
        AND seam_safety_mm >= 0 AND safe_top_mm >= 0 AND safe_right_mm >= 0
        AND safe_bottom_mm >= 0 AND safe_left_mm >= 0 AND template_version > 0
        AND geometry_hash ~ '^[0-9a-f]{64}$'
    )
);

CREATE INDEX idx_surface_template_project_created ON surface_template (project_id, created_at DESC);

CREATE TABLE surface_region (
    template_id UUID NOT NULL REFERENCES surface_template(id) ON DELETE CASCADE,
    region_index INTEGER NOT NULL,
    region_type VARCHAR(24) NOT NULL,
    name VARCHAR(120) NOT NULL,
    x_mm NUMERIC(14, 6) NOT NULL,
    y_mm NUMERIC(14, 6) NOT NULL,
    width_mm NUMERIC(14, 6) NOT NULL,
    height_mm NUMERIC(14, 6) NOT NULL,
    blocking BOOLEAN NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    PRIMARY KEY (template_id, region_index),
    CONSTRAINT ck_surface_region_type CHECK (region_type IN ('NO_PRINT', 'COMPARTMENT', 'STRUCTURE', 'REFERENCE')),
    CONSTRAINT ck_surface_region_bounds CHECK (
        region_index >= 0 AND x_mm >= 0 AND y_mm >= 0 AND width_mm > 0 AND height_mm > 0
    )
);

CREATE TABLE surface_layout_binding (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES mosaic_project(id) ON DELETE CASCADE,
    layout_id UUID NOT NULL REFERENCES editable_layout(id) ON DELETE CASCADE,
    layout_version_id UUID NOT NULL REFERENCES layout_version(id) ON DELETE RESTRICT,
    template_id UUID NOT NULL REFERENCES surface_template(id) ON DELETE RESTRICT,
    origin_x_mm NUMERIC(14, 6) NOT NULL,
    origin_y_mm NUMERIC(14, 6) NOT NULL,
    print_width_mm NUMERIC(14, 6) NOT NULL,
    print_height_mm NUMERIC(14, 6) NOT NULL,
    scale_x_mm_per_px NUMERIC(18, 9) NOT NULL,
    scale_y_mm_per_px NUMERIC(18, 9) NOT NULL,
    validation_status VARCHAR(24) NOT NULL,
    violation_count INTEGER NOT NULL,
    violations_json JSONB NOT NULL,
    coordinate_manifest_json JSONB NOT NULL,
    manifest_hash CHAR(64) NOT NULL,
    validated_by UUID NOT NULL REFERENCES app_user(id),
    validated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_surface_layout_binding UNIQUE (layout_id),
    CONSTRAINT ck_surface_binding_dimensions CHECK (
        origin_x_mm >= 0 AND origin_y_mm >= 0 AND print_width_mm > 0 AND print_height_mm > 0
        AND scale_x_mm_per_px > 0 AND scale_y_mm_per_px > 0 AND violation_count >= 0
        AND manifest_hash ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_surface_binding_status CHECK (validation_status IN ('VALID', 'INVALID'))
);

CREATE INDEX idx_surface_binding_template ON surface_layout_binding (template_id, validated_at DESC);
CREATE INDEX idx_surface_binding_version ON surface_layout_binding (layout_version_id);

INSERT INTO platform_metadata (metadata_key, metadata_value)
VALUES ('surface_mapping_schema', '9');
