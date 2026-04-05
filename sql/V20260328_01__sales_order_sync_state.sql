CREATE TABLE IF NOT EXISTS sales_order_sync_state (
    id BIGINT PRIMARY KEY,
    initialized TINYINT(1) NOT NULL DEFAULT 0,
    initialized_at DATETIME NULL,
    initialized_by VARCHAR(64) NULL,
    last_sync_at DATETIME NULL,
    last_sync_by VARCHAR(64) NULL,
    last_import_file VARCHAR(255) NULL,
    total_orders INT NOT NULL DEFAULT 0,
    total_items INT NOT NULL DEFAULT 0,
    remark VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
