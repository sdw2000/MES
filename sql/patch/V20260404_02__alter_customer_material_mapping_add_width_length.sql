-- 客户物料映射：增加宽度/长度与客户宽度/客户长度
-- 并将唯一键从(customer_code, material_code, thickness)
-- 升级为(customer_code, material_code, thickness, width, length)

SET @db = DATABASE();

SET @sql = IF(
  EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'customer_material_mapping' AND COLUMN_NAME = 'width'
  ),
  'SELECT 1',
  'ALTER TABLE `customer_material_mapping` ADD COLUMN `width` DECIMAL(18,2) NULL COMMENT ''宽度(mm)'' AFTER `thickness`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'customer_material_mapping' AND COLUMN_NAME = 'length'
  ),
  'SELECT 1',
  'ALTER TABLE `customer_material_mapping` ADD COLUMN `length` DECIMAL(18,2) NULL COMMENT ''长度(m)'' AFTER `width`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'customer_material_mapping' AND COLUMN_NAME = 'customer_width'
  ),
  'SELECT 1',
  'ALTER TABLE `customer_material_mapping` ADD COLUMN `customer_width` DECIMAL(18,2) NULL COMMENT ''客户宽度(mm)'' AFTER `customer_thickness`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'customer_material_mapping' AND COLUMN_NAME = 'customer_length'
  ),
  'SELECT 1',
  'ALTER TABLE `customer_material_mapping` ADD COLUMN `customer_length` DECIMAL(18,2) NULL COMMENT ''客户长度(m)'' AFTER `customer_width`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 为历史数据回填（通用兜底，避免环境中规格表名不一致导致脚本中断）
UPDATE customer_material_mapping
SET width = COALESCE(width, 0.01),
    length = COALESCE(length, 0.01),
    customer_width = COALESCE(customer_width, width, 0.01),
    customer_length = COALESCE(customer_length, length, 0.01)
WHERE width IS NULL OR length IS NULL OR customer_width IS NULL OR customer_length IS NULL;

-- 收紧非空约束
ALTER TABLE `customer_material_mapping`
  MODIFY COLUMN `width` DECIMAL(18,2) NOT NULL COMMENT '宽度(mm)',
  MODIFY COLUMN `length` DECIMAL(18,2) NOT NULL COMMENT '长度(m)';

-- 重建唯一索引（兼容已有/不存在场景）
SET @sql = IF(
  EXISTS(
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'customer_material_mapping' AND INDEX_NAME = 'uk_customer_material_spec'
  ),
  'DROP INDEX `uk_customer_material_spec` ON `customer_material_mapping`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE UNIQUE INDEX `uk_customer_material_spec`
ON `customer_material_mapping` (`customer_code`, `material_code`, `thickness`, `width`, `length`);
