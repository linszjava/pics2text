ALTER TABLE mosaic_project
    ADD COLUMN active_text_mask_id UUID;

ALTER TABLE mosaic_project
    ADD CONSTRAINT fk_project_active_text_mask
        FOREIGN KEY (active_text_mask_id) REFERENCES text_mask_design(id) ON DELETE SET NULL;

CREATE INDEX idx_project_active_text_mask ON mosaic_project (active_text_mask_id);
