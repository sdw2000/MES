-- 创建每卷明细表（tape_stock_rolls），一卷一行
CREATE TABLE IF NOT EXISTS tape_stock_rolls (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  stock_id BIGINT NOT NULL,
  qr_code VARCHAR(128) NOT NULL,
  length INT NULL,
  available_area DECIMAL(18,2) DEFAULT 0,
  reserved_area DECIMAL(18,2) DEFAULT 0,
  consumed_area DECIMAL(18,2) DEFAULT 0,
  version INT DEFAULT 0,
  prod_date DATE NULL,
  fifo_order INT DEFAULT 0,
  UNIQUE KEY uk_qr_code (qr_code),
  KEY idx_stock_id (stock_id),
  CONSTRAINT fk_roll_stock FOREIGN KEY (stock_id) REFERENCES tape_stock(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 初始化：按现有批次拆分为单卷记录
-- 规则：
--  - 每个批次按 total_rolls 生成对应数量的卷
--  - 每卷长度=COALESCE(current_length, length)
--  - 面积按批次的 available/reserved/consumed 等比例拆分到每卷
--  - 卷码生成规则：batch_no-001 ... batch_no-N（如批次已具唯一二维码，可替换 qr_code = s.qr_code || '-' || n）

WITH RECURSIVE nums AS (
  SELECT 1 AS n
  UNION ALL
  SELECT n + 1 FROM nums WHERE n < (
    SELECT COALESCE(MAX(total_rolls), 0) FROM tape_stock WHERE status = 1
  )
)
INSERT INTO tape_stock_rolls (
  stock_id, qr_code, length, available_area, reserved_area, consumed_area, version, prod_date, fifo_order
)
SELECT
  s.id AS stock_id,
  CONCAT(s.batch_no, '-', LPAD(nums.n, 3, '0')) AS qr_code,
  COALESCE(s.current_length, s.length) AS length,
  ROUND(GREATEST(
    COALESCE(s.available_area, (COALESCE(s.total_sqm,0) - COALESCE(s.reserved_area,0) - COALESCE(s.consumed_area,0))),
    0
  ) / NULLIF(s.total_rolls,0), 2) AS available_area,
  ROUND(COALESCE(s.reserved_area,0) / NULLIF(s.total_rolls,0), 2) AS reserved_area,
  ROUND(COALESCE(s.consumed_area,0) / NULLIF(s.total_rolls,0), 2) AS consumed_area,
  0 AS version,
  s.prod_date,
  COALESCE(s.sequence_no, 0) AS fifo_order
FROM tape_stock s
JOIN nums ON nums.n <= COALESCE(s.total_rolls,0)
WHERE s.status = 1 AND COALESCE(s.total_rolls,0) > 0
ON DUPLICATE KEY UPDATE
  length = VALUES(length),
  available_area = VALUES(available_area),
  reserved_area = VALUES(reserved_area),
  consumed_area = VALUES(consumed_area),
  prod_date = VALUES(prod_date),
  fifo_order = VALUES(fifo_order);
