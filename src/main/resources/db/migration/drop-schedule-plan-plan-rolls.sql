-- 删除日排程计划量字段（计划卷数）
SET @col_exists := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'schedule_plan'
    AND COLUMN_NAME = 'plan_rolls'
);
SET @sql := IF(
  @col_exists > 0,
  'ALTER TABLE schedule_plan DROP COLUMN plan_rolls',
  'SELECT ''plan_rolls not exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT 'Migration completed: dropped schedule_plan.plan_rolls' AS result;
