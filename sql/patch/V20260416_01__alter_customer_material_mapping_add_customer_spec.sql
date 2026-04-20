-- 客户物料映射：增加客户规格文本字段（用于标签打印按客户规格显示）
SET @db = DATABASE();

SET @sql = IF(
  EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'customer_material_mapping' AND COLUMN_NAME = 'customer_spec'
  ),
  'SELECT 1',
  'ALTER TABLE `customer_material_mapping` ADD COLUMN `customer_spec` VARCHAR(255) NULL COMMENT ''客户规格（文本）'' AFTER `customer_material_name`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE customer_material_mapping
SET customer_spec = CONCAT(
  TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(customer_thickness AS CHAR))),
  'μm*',
  TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(customer_width AS CHAR))),
  'mm*',
  TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(customer_length AS CHAR))),
  'm'
)
WHERE (customer_spec IS NULL OR TRIM(customer_spec) = '')
  AND customer_thickness IS NOT NULL
  AND customer_width IS NOT NULL
  AND customer_length IS NOT NULL;
