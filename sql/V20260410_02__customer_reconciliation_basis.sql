-- 客户对账依据：按已发货 / 按已收货

SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'customers'
      AND COLUMN_NAME = 'reconciliation_basis'
);

SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE customers ADD COLUMN reconciliation_basis VARCHAR(20) NULL COMMENT ''对账依据：SHIPPED/RECEIVED''',
    'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE customers
SET reconciliation_basis = 'SHIPPED'
WHERE reconciliation_basis IS NULL OR TRIM(reconciliation_basis) = '';
