-- 客户默认对账日期 + 发货明细对账归属确认

ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS default_reconciliation_day TINYINT NULL COMMENT '默认对账日(1-31)';

UPDATE customers
SET default_reconciliation_day = 25
WHERE default_reconciliation_day IS NULL;

CREATE TABLE IF NOT EXISTS sales_statement_delivery_confirm (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    notice_item_id BIGINT NOT NULL,
    statement_month VARCHAR(7) NOT NULL COMMENT '归属对账月份 yyyy-MM',
    updated_by VARCHAR(64),
    updated_at DATETIME,
    is_deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_notice_item (notice_item_id),
    INDEX idx_statement_month (statement_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发货明细对账归属确认';
