-- 移除冗余字段：订单明细/发货明细不再持久化 material_name / product_name
-- 统一通过 material_code 关联 tape_spec 等基础数据动态查询。

SET @db := DATABASE();

-- sales_order_items.material_name
SET @exists_col := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db
    AND TABLE_NAME = 'sales_order_items'
    AND COLUMN_NAME = 'material_name'
);
SET @sql := IF(@exists_col > 0,
  'ALTER TABLE sales_order_items DROP COLUMN material_name',
  'SELECT ''skip drop sales_order_items.material_name'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- sales_order_items.product_name
SET @exists_col := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db
    AND TABLE_NAME = 'sales_order_items'
    AND COLUMN_NAME = 'product_name'
);
SET @sql := IF(@exists_col > 0,
  'ALTER TABLE sales_order_items DROP COLUMN product_name',
  'SELECT ''skip drop sales_order_items.product_name'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- delivery_notice_items.material_name
SET @exists_col := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db
    AND TABLE_NAME = 'delivery_notice_items'
    AND COLUMN_NAME = 'material_name'
);
SET @sql := IF(@exists_col > 0,
  'ALTER TABLE delivery_notice_items DROP COLUMN material_name',
  'SELECT ''skip drop delivery_notice_items.material_name'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- delivery_notice_items.product_name
SET @exists_col := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db
    AND TABLE_NAME = 'delivery_notice_items'
    AND COLUMN_NAME = 'product_name'
);
SET @sql := IF(@exists_col > 0,
  'ALTER TABLE delivery_notice_items DROP COLUMN product_name',
  'SELECT ''skip drop delivery_notice_items.product_name'' AS info');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 校验结果
SELECT TABLE_NAME, COLUMN_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = @db
  AND TABLE_NAME IN ('sales_order_items','delivery_notice_items')
  AND COLUMN_NAME IN ('material_name','product_name');
