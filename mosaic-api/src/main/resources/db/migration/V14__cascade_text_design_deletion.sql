ALTER TABLE print_render
    DROP CONSTRAINT IF EXISTS print_render_layout_id_fkey,
    ADD CONSTRAINT print_render_layout_id_fkey
        FOREIGN KEY (layout_id) REFERENCES editable_layout(id) ON DELETE CASCADE;

ALTER TABLE editable_layout
    DROP CONSTRAINT IF EXISTS editable_layout_source_auto_layout_id_fkey,
    DROP CONSTRAINT IF EXISTS editable_layout_mask_id_fkey,
    ADD CONSTRAINT editable_layout_source_auto_layout_id_fkey
        FOREIGN KEY (source_auto_layout_id) REFERENCES auto_layout(id) ON DELETE CASCADE,
    ADD CONSTRAINT editable_layout_mask_id_fkey
        FOREIGN KEY (mask_id) REFERENCES text_mask_design(id) ON DELETE CASCADE;

ALTER TABLE auto_layout
    DROP CONSTRAINT IF EXISTS auto_layout_mask_id_fkey,
    ADD CONSTRAINT auto_layout_mask_id_fkey
        FOREIGN KEY (mask_id) REFERENCES text_mask_design(id) ON DELETE CASCADE;
