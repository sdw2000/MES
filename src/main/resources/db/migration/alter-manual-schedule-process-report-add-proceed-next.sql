-- 工序报工记录增加“是否继续下工序”字段
SET @col_exists := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'manual_schedule_process_report'
    AND COLUMN_NAME = 'proceed_next_process'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE manual_schedule_process_report ADD COLUMN proceed_next_process TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''是否继续下工序:1是0否'' AFTER operator_name',
  'SELECT ''proceed_next_process exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT 'Migration completed: manual_schedule_process_report.proceed_next_process' AS result;
