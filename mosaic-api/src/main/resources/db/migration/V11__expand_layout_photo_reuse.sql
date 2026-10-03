ALTER TABLE auto_layout DROP CONSTRAINT ck_auto_layout_reuse;
ALTER TABLE auto_layout
    ADD CONSTRAINT ck_auto_layout_reuse
        CHECK (max_photo_reuse BETWEEN 1 AND 10000 AND (allow_photo_reuse OR max_photo_reuse = 1));

ALTER TABLE editable_layout DROP CONSTRAINT ck_editable_layout_reuse;
ALTER TABLE editable_layout
    ADD CONSTRAINT ck_editable_layout_reuse
        CHECK (max_photo_reuse BETWEEN 1 AND 10000 AND (allow_photo_reuse OR max_photo_reuse = 1));
