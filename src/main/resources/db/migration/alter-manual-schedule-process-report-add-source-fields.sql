-- 工序报工记录增加来源字段，用于区分样板报工与常规报工

-- 1) source_type
SET @col_exists := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'manual_schedule_process_report'
    AND COLUMN_NAME = 'source_type'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE manual_schedule_process_report ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT ''NORMAL'' COMMENT ''来源类型:NORMAL/SAMPLE'' AFTER proceed_next_process',
  'SELECT ''source_type exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) source_no
SET @col_exists := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'manual_schedule_process_report'
    AND COLUMN_NAME = 'source_no'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE manual_schedule_process_report ADD COLUMN source_no VARCHAR(64) DEFAULT NULL COMMENT ''来源单号(样板=sample_no)'' AFTER source_type',
  'SELECT ''source_no exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) source_item_id
SET @col_exists := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'manual_schedule_process_report'
    AND COLUMN_NAME = 'source_item_id'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE manual_schedule_process_report ADD COLUMN source_item_id BIGINT DEFAULT NULL COMMENT ''来源明细ID(样板=sample_item_id)'' AFTER source_no',
  'SELECT ''source_item_id exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4) 索引 idx_source
SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'manual_schedule_process_report'
    AND INDEX_NAME = 'idx_source'
);
SET @sql := IF(
  @idx_exists = 0,
  'ALTER TABLE manual_schedule_process_report ADD INDEX idx_source (source_type, source_no, source_item_id)',
  'SELECT ''idx_source exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 5) 存量数据回填（根据 manual_schedule.remark 的样板标记）
UPDATE manual_schedule_process_report r
JOIN manual_schedule ms ON ms.id = r.schedule_id
SET r.source_type = 'SAMPLE',
    r.source_no = NULLIF(TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(ms.remark, 'sampleNo=', -1), ',sampleItemId=', 1)), ''),
    r.source_item_id = CAST(NULLIF(TRIM(SUBSTRING_INDEX(ms.remark, ',sampleItemId=', -1)), '') AS UNSIGNED)
WHERE r.is_deleted = 0
  AND ms.remark LIKE '%[SAMPLE_TASK]%';

-- 6) 兜底：空来源统一标记 NORMAL
UPDATE manual_schedule_process_report
SET source_type = 'NORMAL'
WHERE source_type IS NULL OR TRIM(source_type) = '';

SELECT 'Migration completed: manual_schedule_process_report source fields' AS result;
