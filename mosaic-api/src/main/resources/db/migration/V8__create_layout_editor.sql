CREATE TABLE editable_layout (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES mosaic_project(id) ON DELETE CASCADE,
    source_auto_layout_id UUID NOT NULL REFERENCES auto_layout(id) ON DELETE RESTRICT,
    mask_id UUID NOT NULL REFERENCES text_mask_design(id) ON DELETE RESTRICT,
    name VARCHAR(120) NOT NULL,
    state VARCHAR(24) NOT NULL,
    canvas_width INTEGER NOT NULL,
    canvas_height INTEGER NOT NULL,
    allow_photo_reuse BOOLEAN NOT NULL,
    max_photo_reuse INTEGER NOT NULL,
    current_revision BIGINT NOT NULL DEFAULT 0,
    lock_version BIGINT NOT NULL DEFAULT 0,
    current_version_number INTEGER NOT NULL DEFAULT 0,
    current_version_id UUID,
    published_version_id UUID,
    locked_by UUID REFERENCES app_user(id),
    locked_at TIMESTAMPTZ,
    published_by UUID REFERENCES app_user(id),
    published_at TIMESTAMPTZ,
    created_by UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_editable_layout_source UNIQUE (source_auto_layout_id),
    CONSTRAINT ck_editable_layout_state CHECK (state IN ('DRAFT', 'LOCKED', 'PUBLISHED')),
    CONSTRAINT ck_editable_layout_canvas CHECK (canvas_width > 0 AND canvas_height > 0),
    CONSTRAINT ck_editable_layout_reuse CHECK (
        max_photo_reuse BETWEEN 1 AND 100 AND (allow_photo_reuse OR max_photo_reuse = 1)
    ),
    CONSTRAINT ck_editable_layout_versions CHECK (
        current_revision >= 0 AND lock_version >= 0 AND current_version_number >= 0
    )
);

CREATE INDEX idx_editable_layout_project_updated ON editable_layout (project_id, updated_at DESC);

CREATE TABLE layout_draft_assignment (
    layout_id UUID NOT NULL REFERENCES editable_layout(id) ON DELETE CASCADE,
    slot_index INTEGER NOT NULL,
    photo_id UUID NOT NULL REFERENCES photo_asset(id) ON DELETE RESTRICT,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    crop_x DOUBLE PRECISION NOT NULL,
    crop_y DOUBLE PRECISION NOT NULL,
    crop_width DOUBLE PRECISION NOT NULL,
    crop_height DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (layout_id, slot_index),
    CONSTRAINT ck_layout_draft_position CHECK (slot_index >= 0 AND x >= 0 AND y >= 0),
    CONSTRAINT ck_layout_draft_size CHECK (width > 0 AND height > 0),
    CONSTRAINT ck_layout_draft_crop CHECK (
        crop_x BETWEEN 0 AND 1 AND crop_y BETWEEN 0 AND 1
        AND crop_width > 0 AND crop_width <= 1 AND crop_height > 0 AND crop_height <= 1
        AND crop_x + crop_width <= 1.000001 AND crop_y + crop_height <= 1.000001
    )
);

CREATE INDEX idx_layout_draft_viewport ON layout_draft_assignment (layout_id, x, y);
CREATE INDEX idx_layout_draft_photo ON layout_draft_assignment (layout_id, photo_id);

CREATE TABLE layout_version (
    id UUID PRIMARY KEY,
    layout_id UUID NOT NULL REFERENCES editable_layout(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    parent_version_id UUID REFERENCES layout_version(id) ON DELETE RESTRICT,
    reason VARCHAR(24) NOT NULL,
    client_batch_id UUID,
    source_revision BIGINT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    assignment_count INTEGER NOT NULL,
    note VARCHAR(500),
    created_by UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_layout_version_number UNIQUE (layout_id, version_number),
    CONSTRAINT uk_layout_version_batch UNIQUE (layout_id, client_batch_id),
    CONSTRAINT ck_layout_version_reason CHECK (reason IN ('IMPORT', 'AUTO_SAVE', 'RESTORE', 'LOCK', 'PUBLISH')),
    CONSTRAINT ck_layout_version_values CHECK (
        version_number > 0 AND source_revision >= 0 AND assignment_count > 0
        AND content_hash ~ '^[0-9a-f]{64}$'
    )
);

ALTER TABLE editable_layout
    ADD CONSTRAINT fk_editable_layout_current_version
        FOREIGN KEY (current_version_id) REFERENCES layout_version(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_editable_layout_published_version
        FOREIGN KEY (published_version_id) REFERENCES layout_version(id) ON DELETE RESTRICT;

CREATE INDEX idx_layout_version_layout_created ON layout_version (layout_id, version_number DESC);

CREATE TABLE layout_version_assignment (
    version_id UUID NOT NULL REFERENCES layout_version(id) ON DELETE CASCADE,
    slot_index INTEGER NOT NULL,
    photo_id UUID NOT NULL REFERENCES photo_asset(id) ON DELETE RESTRICT,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    crop_x DOUBLE PRECISION NOT NULL,
    crop_y DOUBLE PRECISION NOT NULL,
    crop_width DOUBLE PRECISION NOT NULL,
    crop_height DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (version_id, slot_index),
    CONSTRAINT ck_layout_version_assignment_position CHECK (slot_index >= 0 AND x >= 0 AND y >= 0),
    CONSTRAINT ck_layout_version_assignment_size CHECK (width > 0 AND height > 0),
    CONSTRAINT ck_layout_version_assignment_crop CHECK (
        crop_x BETWEEN 0 AND 1 AND crop_y BETWEEN 0 AND 1
        AND crop_width > 0 AND crop_width <= 1 AND crop_height > 0 AND crop_height <= 1
        AND crop_x + crop_width <= 1.000001 AND crop_y + crop_height <= 1.000001
    )
);

CREATE INDEX idx_layout_version_assignment_photo
    ON layout_version_assignment (version_id, photo_id);

CREATE TABLE layout_edit_operation (
    id UUID PRIMARY KEY,
    layout_id UUID NOT NULL REFERENCES editable_layout(id) ON DELETE CASCADE,
    revision BIGINT NOT NULL,
    client_batch_id UUID NOT NULL,
    command_index INTEGER NOT NULL,
    command_type VARCHAR(24) NOT NULL,
    slot_index INTEGER,
    secondary_slot_index INTEGER,
    before_json JSONB NOT NULL,
    after_json JSONB NOT NULL,
    base_lock_version BIGINT NOT NULL,
    resulting_lock_version BIGINT NOT NULL,
    actor_id UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_layout_edit_revision UNIQUE (layout_id, revision),
    CONSTRAINT uk_layout_edit_batch_command UNIQUE (layout_id, client_batch_id, command_index),
    CONSTRAINT ck_layout_edit_command CHECK (command_type IN ('SWAP', 'REPLACE', 'CROP', 'RESTORE')),
    CONSTRAINT ck_layout_edit_values CHECK (
        revision > 0 AND command_index >= 0 AND base_lock_version >= 0
        AND resulting_lock_version = base_lock_version + 1
    )
);

CREATE INDEX idx_layout_edit_history ON layout_edit_operation (layout_id, revision DESC);
CREATE INDEX idx_layout_edit_actor ON layout_edit_operation (actor_id, created_at DESC);

CREATE TABLE layout_preview (
    id UUID PRIMARY KEY,
    layout_id UUID NOT NULL REFERENCES editable_layout(id) ON DELETE CASCADE,
    version_id UUID NOT NULL REFERENCES layout_version(id) ON DELETE CASCADE,
    status VARCHAR(24) NOT NULL,
    object_key VARCHAR(1024),
    width INTEGER,
    height INTEGER,
    assignment_count INTEGER NOT NULL DEFAULT 0,
    error_code VARCHAR(64),
    error_message VARCHAR(500),
    created_by UUID NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uk_layout_preview_version UNIQUE (version_id),
    CONSTRAINT ck_layout_preview_status CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED')),
    CONSTRAINT ck_layout_preview_size CHECK (
        (width IS NULL AND height IS NULL) OR (width > 0 AND height > 0)
    )
);

CREATE INDEX idx_layout_preview_layout_created ON layout_preview (layout_id, created_at DESC);

INSERT INTO platform_metadata (metadata_key, metadata_value)
VALUES ('layout_editor_schema', '8');
