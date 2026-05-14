-- DDL: 核心财务模块（AR/AP/Bank/GL/Payments）
-- 生成于 2026-04-30

SET FOREIGN_KEY_CHECKS=0;

-- 总账科目表
CREATE TABLE IF NOT EXISTS gl_account (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(200) NOT NULL,
  type VARCHAR(32) NOT NULL, -- ASSET/LIABILITY/EQUITY/REVENUE/EXPENSE
  parent_id BIGINT DEFAULT NULL,
  normal_balance VARCHAR(8) DEFAULT 'DEBIT',
  is_deleted TINYINT(1) DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_gl_account_code (code),
  INDEX idx_gl_account_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 总账分录（凭证分录）
CREATE TABLE IF NOT EXISTS gl_entry (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  voucher_no VARCHAR(64) NOT NULL,
  entry_date DATE NOT NULL,
  gl_account_id BIGINT NOT NULL,
  debit DECIMAL(18,2) DEFAULT 0,
  credit DECIMAL(18,2) DEFAULT 0,
  currency VARCHAR(8) DEFAULT 'CNY',
  description VARCHAR(400),
  source_type VARCHAR(64), -- sales_invoice, purchase_bill, payment ...
  source_id BIGINT,
  is_deleted TINYINT(1) DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_gl_entry_voucher (voucher_no),
  INDEX idx_gl_entry_account (gl_account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 应收发票
CREATE TABLE IF NOT EXISTS ar_invoice (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  invoice_no VARCHAR(64) NOT NULL,
  customer_code VARCHAR(64) NOT NULL,
  invoice_date DATE NOT NULL,
  due_date DATE DEFAULT NULL,
  total_amount DECIMAL(18,2) NOT NULL,
  paid_amount DECIMAL(18,2) DEFAULT 0,
  currency VARCHAR(8) DEFAULT 'CNY',
  status VARCHAR(32) DEFAULT 'OPEN', -- OPEN/PARTIAL/PAID/CANCELLED
  statement_month VARCHAR(7) DEFAULT NULL,
  is_deleted TINYINT(1) DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ar_invoice_no (invoice_no),
  INDEX idx_ar_customer (customer_code),
  INDEX idx_ar_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ar_invoice_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  invoice_id BIGINT NOT NULL,
  order_no VARCHAR(64),
  material_code VARCHAR(64),
  description VARCHAR(400),
  quantity DECIMAL(18,2) DEFAULT 0,
  unit_price DECIMAL(18,4) DEFAULT 0,
  amount DECIMAL(18,2) DEFAULT 0,
  is_deleted TINYINT(1) DEFAULT 0,
  INDEX idx_ar_invoice_item_invoice (invoice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 应付账款（供应商账单）
CREATE TABLE IF NOT EXISTS ap_bill (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  bill_no VARCHAR(64) NOT NULL,
  supplier_code VARCHAR(64) NOT NULL,
  bill_date DATE NOT NULL,
  due_date DATE DEFAULT NULL,
  total_amount DECIMAL(18,2) NOT NULL,
  paid_amount DECIMAL(18,2) DEFAULT 0,
  currency VARCHAR(8) DEFAULT 'CNY',
  status VARCHAR(32) DEFAULT 'OPEN',
  is_deleted TINYINT(1) DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ap_bill_no (bill_no),
  INDEX idx_ap_supplier (supplier_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ap_bill_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  bill_id BIGINT NOT NULL,
  po_no VARCHAR(64),
  material_code VARCHAR(64),
  description VARCHAR(400),
  quantity DECIMAL(18,2) DEFAULT 0,
  unit_price DECIMAL(18,4) DEFAULT 0,
  amount DECIMAL(18,2) DEFAULT 0,
  is_deleted TINYINT(1) DEFAULT 0,
  INDEX idx_ap_bill_item_bill (bill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 收付款记录（应收/应付关联）
CREATE TABLE IF NOT EXISTS payment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  payment_no VARCHAR(64) NOT NULL,
  payment_date DATE NOT NULL,
  payer VARCHAR(128),
  payee VARCHAR(128),
  amount DECIMAL(18,2) NOT NULL,
  currency VARCHAR(8) DEFAULT 'CNY',
  method VARCHAR(64), -- bank_transfer/cash/check
  bank_account_id BIGINT DEFAULT NULL,
  related_invoice_id BIGINT DEFAULT NULL,
  related_bill_id BIGINT DEFAULT NULL,
  reference VARCHAR(200),
  status VARCHAR(32) DEFAULT 'POSTED',
  is_deleted TINYINT(1) DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_payment_no (payment_no),
  INDEX idx_payment_related (related_invoice_id, related_bill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 银行账户与银行流水/对账
CREATE TABLE IF NOT EXISTS bank_account (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  account_no VARCHAR(128) NOT NULL,
  bank_name VARCHAR(200),
  currency VARCHAR(8) DEFAULT 'CNY',
  opening_balance DECIMAL(18,2) DEFAULT 0,
  current_balance DECIMAL(18,2) DEFAULT 0,
  is_deleted TINYINT(1) DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_bank_account_no (account_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS bank_transaction (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  bank_account_id BIGINT NOT NULL,
  txn_date DATE NOT NULL,
  amount DECIMAL(18,2) NOT NULL,
  balance DECIMAL(18,2) DEFAULT NULL,
  txn_type VARCHAR(32), -- CREDIT/DEBIT
  description VARCHAR(400),
  external_ref VARCHAR(200),
  is_reconciled TINYINT(1) DEFAULT 0,
  reconciliation_id BIGINT DEFAULT NULL,
  is_deleted TINYINT(1) DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_bank_txn_account (bank_account_id),
  INDEX idx_bank_txn_recon (reconciliation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS bank_reconciliation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  bank_account_id BIGINT NOT NULL,
  recon_date DATE NOT NULL,
  start_balance DECIMAL(18,2) DEFAULT 0,
  end_balance DECIMAL(18,2) DEFAULT 0,
  difference DECIMAL(18,2) DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_bank_recon_account (bank_account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS bank_reconciliation_line (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  reconciliation_id BIGINT NOT NULL,
  bank_txn_id BIGINT DEFAULT NULL,
  book_txn_id BIGINT DEFAULT NULL, -- 对应 payment.id 或其他记账 id
  matched_amount DECIMAL(18,2) DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_bank_recon_line_recon (reconciliation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 审计日志
CREATE TABLE IF NOT EXISTS audit_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(128),
  action VARCHAR(64),
  target_table VARCHAR(128),
  target_id BIGINT,
  before_json TEXT,
  after_json TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_audit_target (target_table, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET FOREIGN_KEY_CHECKS=1;

-- 示例：应收未收款查询
-- SELECT * FROM ar_invoice WHERE is_deleted = 0 AND total_amount > paid_amount ORDER BY due_date ASC;

-- 示例：应付未付款查询
-- SELECT * FROM ap_bill WHERE is_deleted = 0 AND total_amount > paid_amount ORDER BY due_date ASC;

-- 示例：银行对账待匹配流水（未对账）
-- SELECT * FROM bank_transaction WHERE bank_account_id = ? AND is_deleted = 0 AND is_reconciled = 0 ORDER BY txn_date ASC;

-- 示例：生成总账凭证（伪 SQL）
-- INSERT INTO gl_entry (voucher_no, entry_date, gl_account_id, debit, credit, source_type, source_id) VALUES (...);
