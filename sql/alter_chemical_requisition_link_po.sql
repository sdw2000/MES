-- 化工请购模块：补充采购关联字段

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chemical_purchase_request' AND COLUMN_NAME = 'purchase_order_no') = 0,
  'ALTER TABLE chemical_purchase_request ADD COLUMN purchase_order_no VARCHAR(64) NULL COMMENT ''关联采购单号'' AFTER status',
  'SELECT ''purchase_order_no exists'''
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chemical_purchase_request_item' AND COLUMN_NAME = 'purchase_order_item_id') = 0,
  'ALTER TABLE chemical_purchase_request_item ADD COLUMN purchase_order_item_id BIGINT NULL COMMENT ''关联采购明细ID'' AFTER unit',
  'SELECT ''purchase_order_item_id exists'''
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chemical_purchase_request' AND INDEX_NAME = 'idx_cpr_po_no') = 0,
  'ALTER TABLE chemical_purchase_request ADD INDEX idx_cpr_po_no(purchase_order_no)',
  'SELECT ''idx_cpr_po_no exists'''
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
