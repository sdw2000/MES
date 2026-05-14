-- 扫码领退料 Phase1
-- 薄膜按长度(m)领退；退料强关联原领料(source_issue_txn_id)

-- 兼容旧版MySQL：通过 information_schema + PREPARE 动态执行
SET @has_original := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'film_stock_detail'
    AND COLUMN_NAME = 'original_length_m'
);

SET @ddl_original := IF(
  @has_original = 0,
  "ALTER TABLE film_stock_detail ADD COLUMN original_length_m DECIMAL(12,3) NULL COMMENT '原始长度(m)' AFTER length",
  "SELECT 'original_length_m exists'"
);

PREPARE stmt_original FROM @ddl_original;
EXECUTE stmt_original;
DEALLOCATE PREPARE stmt_original;

SET @has_current := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'film_stock_detail'
    AND COLUMN_NAME = 'current_length_m'
);

SET @ddl_current := IF(
  @has_current = 0,
  "ALTER TABLE film_stock_detail ADD COLUMN current_length_m DECIMAL(12,3) NULL COMMENT '当前剩余长度(m)' AFTER original_length_m",
  "SELECT 'current_length_m exists'"
);

PREPARE stmt_current FROM @ddl_current;
EXECUTE stmt_current;
DEALLOCATE PREPARE stmt_current;

-- 历史数据回填：若为空则按 area 与 width 反推长度
UPDATE film_stock_detail
SET current_length_m = ROUND((IFNULL(area, 0) * 1000 / NULLIF(width, 0)), 3)
WHERE current_length_m IS NULL
  AND IFNULL(width, 0) > 0
  AND IFNULL(area, 0) > 0;

UPDATE film_stock_detail
SET original_length_m = current_length_m
WHERE original_length_m IS NULL
  AND IFNULL(current_length_m, 0) > 0;

CREATE TABLE IF NOT EXISTS material_scan_txn (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    txn_no VARCHAR(64) NOT NULL COMMENT '事务号',
    txn_type VARCHAR(16) NOT NULL COMMENT 'ISSUE/RETURN',
    stock_type VARCHAR(16) NOT NULL COMMENT 'FILM/CHEMICAL',
    detail_id BIGINT NULL COMMENT '明细ID',
    stock_id BIGINT NULL COMMENT '总库存ID',
    qr_code VARCHAR(128) NULL COMMENT '扫码值',
    batch_no VARCHAR(128) NULL,
    material_code VARCHAR(128) NULL,
    qty DECIMAL(14,3) NOT NULL COMMENT '领退数量',
    unit VARCHAR(16) NOT NULL COMMENT 'm/kg',
    before_qty DECIMAL(14,3) NOT NULL,
    after_qty DECIMAL(14,3) NOT NULL,
    source_issue_txn_id BIGINT NULL COMMENT '退料关联原领料事务ID',
    order_no VARCHAR(64) NULL,
    schedule_id BIGINT NULL,
    process_type VARCHAR(32) NULL,
    operator VARCHAR(64) NULL,
    device_id VARCHAR(64) NULL,
    channel VARCHAR(32) NULL,
    idempotency_key VARCHAR(128) NULL,
    remark VARCHAR(500) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_mstx_qr (qr_code),
    KEY idx_mstx_batch (batch_no),
    KEY idx_mstx_detail (detail_id),
    KEY idx_mstx_source (source_issue_txn_id),
    KEY idx_mstx_order (order_no),
    KEY idx_mstx_schedule (schedule_id),
    UNIQUE KEY uk_mstx_txn_no (txn_no),
    UNIQUE KEY uk_mstx_idempotency (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='扫码领退料事务流水';
