-- 退货明细对账月份覆盖表（用于将单条退货明细顺延到下月）
CREATE TABLE IF NOT EXISTS sales_statement_return_confirm (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    return_item_id BIGINT NOT NULL,
    statement_month VARCHAR(7) NOT NULL,
    updated_by VARCHAR(64),
    updated_at DATETIME,
    is_deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_return_item (return_item_id),
    INDEX idx_statement_month (statement_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
