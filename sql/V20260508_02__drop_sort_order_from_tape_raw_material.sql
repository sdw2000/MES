-- 移除原材料表排序字段（按需执行，幂等）
-- 影响表：tape_raw_material

SET @db_name := DATABASE();

SELECT COUNT(*) INTO @col_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db_name
  AND TABLE_NAME = 'tape_raw_material'
  AND COLUMN_NAME = 'sort_order';

SET @ddl := IF(
  @col_exists > 0,
  'ALTER TABLE tape_raw_material DROP COLUMN sort_order',
  'SELECT ''column sort_order not exists'''
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
