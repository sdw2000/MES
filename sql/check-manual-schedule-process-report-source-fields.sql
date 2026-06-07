-- 检查 manual_schedule_process_report 是否已包含样板来源字段
SELECT
  c.COLUMN_NAME,
  c.COLUMN_TYPE,
  c.IS_NULLABLE,
  c.COLUMN_DEFAULT,
  c.COLUMN_COMMENT
FROM information_schema.COLUMNS c
WHERE c.TABLE_SCHEMA = DATABASE()
  AND c.TABLE_NAME = 'manual_schedule_process_report'
  AND c.COLUMN_NAME IN ('source_type', 'source_no', 'source_item_id')
ORDER BY FIELD(c.COLUMN_NAME, 'source_type', 'source_no', 'source_item_id');

-- 汇总缺失字段数量（0=都存在）
SELECT
  3 - COUNT(*) AS missing_count
FROM information_schema.COLUMNS c
WHERE c.TABLE_SCHEMA = DATABASE()
  AND c.TABLE_NAME = 'manual_schedule_process_report'
  AND c.COLUMN_NAME IN ('source_type', 'source_no', 'source_item_id');

-- 检查索引是否存在
SELECT
  COUNT(*) AS idx_source_exists
FROM information_schema.STATISTICS s
WHERE s.TABLE_SCHEMA = DATABASE()
  AND s.TABLE_NAME = 'manual_schedule_process_report'
  AND s.INDEX_NAME = 'idx_source';
