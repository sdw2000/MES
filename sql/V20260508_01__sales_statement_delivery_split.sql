CREATE TABLE IF NOT EXISTS sales_statement_delivery_split (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    notice_item_id BIGINT NOT NULL,
    statement_month VARCHAR(7) NOT NULL COMMENT '归属对账月份 yyyy-MM',
    split_quantity DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '拆分卷数',
    split_area DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '拆分面积',
    split_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '拆分金额',
    updated_by VARCHAR(64) DEFAULT NULL,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    UNIQUE KEY uk_notice_month (notice_item_id, statement_month),
    INDEX idx_notice_item (notice_item_id),
    INDEX idx_statement_month (statement_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
