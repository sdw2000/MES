-- DDL: 固定资产管理模块
-- 生成于 2026-05-01

SET FOREIGN_KEY_CHECKS=0;

CREATE TABLE IF NOT EXISTS fixed_asset (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  asset_code VARCHAR(64) NOT NULL,
  asset_name VARCHAR(200) NOT NULL,
  category VARCHAR(64) DEFAULT NULL,
  purchase_date DATE NOT NULL,
  original_value DECIMAL(18,2) NOT NULL,
  salvage_value DECIMAL(18,2) DEFAULT 0,
  useful_life_months INT NOT NULL,
  depreciation_method VARCHAR(32) DEFAULT 'STRAIGHT_LINE',
  accumulated_depreciation DECIMAL(18,2) DEFAULT 0,
  net_value DECIMAL(18,2) NOT NULL,
  status VARCHAR(32) DEFAULT 'ACTIVE', -- ACTIVE/DISPOSED
  location VARCHAR(200) DEFAULT NULL,
  responsible_person VARCHAR(128) DEFAULT NULL,
  dispose_date DATE DEFAULT NULL,
  dispose_amount DECIMAL(18,2) DEFAULT NULL,
  remark VARCHAR(500) DEFAULT NULL,
  is_deleted TINYINT(1) DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_fixed_asset_code (asset_code),
  INDEX idx_fixed_asset_status (status),
  INDEX idx_fixed_asset_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fixed_asset_depreciation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  asset_id BIGINT NOT NULL,
  period_month VARCHAR(7) NOT NULL, -- yyyy-MM
  depreciation_amount DECIMAL(18,2) NOT NULL,
  accumulated_after DECIMAL(18,2) NOT NULL,
  net_value_after DECIMAL(18,2) NOT NULL,
  voucher_no VARCHAR(64) DEFAULT NULL,
  note VARCHAR(500) DEFAULT NULL,
  is_deleted TINYINT(1) DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_asset_period (asset_id, period_month),
  INDEX idx_fad_asset (asset_id),
  INDEX idx_fad_period (period_month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET FOREIGN_KEY_CHECKS=1;
