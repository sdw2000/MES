-- 为排程采购计划增加采购单关联字段（兼容MySQL 5.7/8.0）

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_procurement_plan' AND COLUMN_NAME = 'purchase_order_no') = 0,
  'ALTER TABLE schedule_procurement_plan ADD COLUMN purchase_order_no VARCHAR(64) NULL COMMENT ''关联采购单号'' AFTER status',
  'SELECT ''purchase_order_no exists'''
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_procurement_plan' AND COLUMN_NAME = 'purchase_order_item_id') = 0,
  'ALTER TABLE schedule_procurement_plan ADD COLUMN purchase_order_item_id BIGINT NULL COMMENT ''关联采购明细ID'' AFTER purchase_order_no',
  'SELECT ''purchase_order_item_id exists'''
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_procurement_plan' AND INDEX_NAME = 'idx_spp_po_no') = 0,
  'ALTER TABLE schedule_procurement_plan ADD INDEX idx_spp_po_no(purchase_order_no)',
  'SELECT ''idx_spp_po_no exists'''
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
