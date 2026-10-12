ALTER TABLE photo_asset
    ADD COLUMN verification_token UUID;

CREATE INDEX idx_photo_asset_verification_token
    ON photo_asset (verification_token)
    WHERE status = 'VERIFYING';
