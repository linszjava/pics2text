ALTER TABLE mosaic_project
    ADD COLUMN active_layout_id UUID;

ALTER TABLE mosaic_project
    ADD CONSTRAINT fk_project_active_layout
        FOREIGN KEY (active_layout_id) REFERENCES editable_layout(id) ON DELETE SET NULL;

CREATE INDEX idx_project_active_layout ON mosaic_project (active_layout_id);
