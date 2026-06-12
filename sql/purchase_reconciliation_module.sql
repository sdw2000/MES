-- 采购对账历史台账表
CREATE TABLE IF NOT EXISTS `purchase_statement_history` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `supplier_code` VARCHAR(64) NOT NULL,
  `statement_month` VARCHAR(7) NOT NULL COMMENT '对账月份 yyyy-MM',
  `unpaid_amount` DECIMAL(18,2) DEFAULT 0.00 COMMENT '期末未付金额',
  `invoice_amount` DECIMAL(18,2) DEFAULT 0.00 COMMENT '本月发票金额',
  `invoice_date` DATE DEFAULT NULL COMMENT '发票日期',
  `remark` VARCHAR(500) DEFAULT NULL,
  `created_by` VARCHAR(64) DEFAULT NULL,
  `updated_by` VARCHAR(64) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` TINYINT DEFAULT 0,
  INDEX idx_supplier (supplier_code),
  INDEX idx_month (statement_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 采购结算入库确认明细表
CREATE TABLE IF NOT EXISTS `purchase_receipt_confirm` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `receipt_item_id` BIGINT NOT NULL,
  `statement_month` VARCHAR(7) NOT NULL,
  `confirmed_amount` DECIMAL(18,2) DEFAULT 0.00,
  `is_confirmed` TINYINT DEFAULT 1,
  `confirmed_by` VARCHAR(64) DEFAULT NULL,
  `confirmed_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_receipt_item (receipt_item_id),
  INDEX idx_month (statement_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 采购月度结算状态确认表
CREATE TABLE IF NOT EXISTS `purchase_statement_month_confirm` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `supplier_code` VARCHAR(64) NOT NULL,
  `statement_month` VARCHAR(7) NOT NULL,
  `purchase_confirmed_by` VARCHAR(64) DEFAULT NULL,
  `purchase_confirmed_at` DATETIME DEFAULT NULL,
  `finance_confirmed_by` VARCHAR(64) DEFAULT NULL,
  `finance_confirmed_at` DATETIME DEFAULT NULL,
  `status` VARCHAR(20) DEFAULT 'PENDING',
  UNIQUE KEY uk_supplier_month (supplier_code, statement_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
