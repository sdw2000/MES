-- 版本化DDL：销售对账历史台账表
CREATE TABLE IF NOT EXISTS sales_statement_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    customer_code VARCHAR(64) NOT NULL,
    statement_month VARCHAR(7) NOT NULL,
    unpaid_amount DECIMAL(18,2) DEFAULT 0,
    invoice_amount DECIMAL(18,2) DEFAULT 0,
    invoice_date DATE NULL,
    remark VARCHAR(500),
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    created_at DATETIME,
    updated_at DATETIME,
    is_deleted TINYINT DEFAULT 0,
    INDEX idx_sales_statement_customer (customer_code),
    INDEX idx_sales_statement_month (statement_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
