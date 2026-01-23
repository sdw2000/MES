-- Ensure quality tables have logical delete flag used by MyBatis Plus
ALTER TABLE quality_inspection
  ADD COLUMN IF NOT EXISTS is_deleted TINYINT DEFAULT 0;

ALTER TABLE quality_inspection_item
  ADD COLUMN IF NOT EXISTS is_deleted TINYINT DEFAULT 0;

ALTER TABLE quality_disposition
  ADD COLUMN IF NOT EXISTS is_deleted TINYINT DEFAULT 0;

ALTER TABLE quality_defect_type
  ADD COLUMN IF NOT EXISTS is_deleted TINYINT DEFAULT 0;
