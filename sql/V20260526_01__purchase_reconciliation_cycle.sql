-- 采购对账：收货明细按月份归属与确认

CREATE TABLE IF NOT EXISTS purchase_statement_receipt_confirm (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    receipt_item_id BIGINT NOT NULL COMMENT 'purchase_receipt_items.id',
    statement_month VARCHAR(7) NOT NULL COMMENT '归属对账月份 yyyy-MM',
    confirmed TINYINT NOT NULL DEFAULT 0 COMMENT '1已对账 0未对账',
    confirmed_by VARCHAR(64) NULL,
    confirmed_at DATETIME NULL,
    updated_by VARCHAR(64) NULL,
    updated_at DATETIME NULL,
    is_deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_purchase_statement_receipt_item (receipt_item_id),
    KEY idx_purchase_statement_month (statement_month),
    KEY idx_purchase_statement_confirmed (confirmed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购收货明细对账归属与确认';

SET @idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'purchase_receipts'
      AND index_name = 'idx_purchase_receipts_supplier_date'
);
SET @sql := IF(@idx_exists = 0,
               'ALTER TABLE purchase_receipts ADD INDEX idx_purchase_receipts_supplier_date (supplier, received_date, expected_date)',
               'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'purchase_receipt_items'
      AND index_name = 'idx_purchase_receipt_items_receipt_material'
);
SET @sql := IF(@idx_exists = 0,
               'ALTER TABLE purchase_receipt_items ADD INDEX idx_purchase_receipt_items_receipt_material (receipt_id, material_code)',
               'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
