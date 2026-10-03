UPDATE photo_asset
SET review_status = 'APPROVED',
    reviewed_by = NULL,
    reviewed_at = NULL,
    review_note = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'AVAILABLE'
  AND analysis_status = 'SUCCEEDED'
  AND review_status <> 'APPROVED';

DROP TABLE IF EXISTS project_member;
