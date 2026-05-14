-- 修复 quality_inspection 字段缺失导致的插入失败
-- 目标错误：Unknown column 'source_order_no' in 'field list'

SET @db := DATABASE();

-- source_order_no
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'quality_inspection' AND COLUMN_NAME = 'source_order_no'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE quality_inspection ADD COLUMN source_order_no VARCHAR(64) NULL AFTER inspection_type',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- roll_code
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'quality_inspection' AND COLUMN_NAME = 'roll_code'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE quality_inspection ADD COLUMN roll_code VARCHAR(64) NULL AFTER batch_no',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- specification
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'quality_inspection' AND COLUMN_NAME = 'specification'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE quality_inspection ADD COLUMN specification VARCHAR(128) NULL AFTER material_name',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- process_node
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'quality_inspection' AND COLUMN_NAME = 'process_node'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE quality_inspection ADD COLUMN process_node VARCHAR(64) NULL AFTER remark',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- process_snapshot
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'quality_inspection' AND COLUMN_NAME = 'process_snapshot'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE quality_inspection ADD COLUMN process_snapshot JSON NULL AFTER process_node',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- created_at
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'quality_inspection' AND COLUMN_NAME = 'created_at'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE quality_inspection ADD COLUMN created_at DATETIME NULL AFTER process_snapshot',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- updated_at
SET @exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'quality_inspection' AND COLUMN_NAME = 'updated_at'
);
SET @sql := IF(@exists = 0,
  'ALTER TABLE quality_inspection ADD COLUMN updated_at DATETIME NULL AFTER created_at',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
