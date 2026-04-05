-- 版本化DDL：销售退货审计日志表
CREATE TABLE IF NOT EXISTS sales_return_audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    return_id BIGINT,
    return_no VARCHAR(64),
    action_type VARCHAR(32) NOT NULL,
    before_status VARCHAR(32),
    after_status VARCHAR(32),
    reason VARCHAR(255),
    detail VARCHAR(500),
    operator VARCHAR(64),
    created_at DATETIME NOT NULL,
    INDEX idx_sral_return_no (return_no),
    INDEX idx_sral_return_id (return_id),
    INDEX idx_sral_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
