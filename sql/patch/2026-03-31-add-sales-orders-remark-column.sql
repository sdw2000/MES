-- 为销售订单主表补充表头备注字段（如已存在则跳过）
-- 兼容 MySQL 5.7：通过 information_schema + prepared statement 实现条件DDL

SET @db_name = DATABASE();
SET @table_name = 'sales_orders';
SET @column_name = 'remark';

SELECT COUNT(1) INTO @col_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db_name
  AND TABLE_NAME = @table_name
  AND COLUMN_NAME = @column_name;

SET @ddl = IF(
  @col_exists = 0,
  'ALTER TABLE sales_orders ADD COLUMN remark TEXT NULL COMMENT ''订单表头备注'' AFTER delivery_address',
  'SELECT ''sales_orders.remark already exists'' AS msg'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
