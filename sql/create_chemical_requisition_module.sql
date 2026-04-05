-- 化工请购模块（配方分解自动锁定/请购）

CREATE TABLE IF NOT EXISTS chemical_material_lock (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plan_date DATE NOT NULL,
  schedule_id BIGINT NULL,
  order_no VARCHAR(64) NULL,
  finished_material_code VARCHAR(64) NULL,
  raw_material_code VARCHAR(64) NOT NULL,
  raw_material_name VARCHAR(128) NULL,
  chemical_stock_id BIGINT NULL,
  required_kg DECIMAL(18,3) NOT NULL DEFAULT 0,
  required_qty INT NOT NULL DEFAULT 0,
  locked_qty INT NOT NULL DEFAULT 0,
  lock_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  source_ref VARCHAR(255) NULL,
  remark VARCHAR(255) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_cml_plan(plan_date),
  KEY idx_cml_order(order_no),
  KEY idx_cml_raw(raw_material_code),
  KEY idx_cml_status(lock_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='化工原料锁定记录';

CREATE TABLE IF NOT EXISTS chemical_purchase_request (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_no VARCHAR(64) NOT NULL,
  plan_date DATE NOT NULL,
  schedule_id BIGINT NULL,
  order_no VARCHAR(64) NULL,
  finished_material_code VARCHAR(64) NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
  source VARCHAR(64) NULL,
  remark VARCHAR(255) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by VARCHAR(64) NULL,
  update_by VARCHAR(64) NULL,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_cpr_no(request_no),
  KEY idx_cpr_plan(plan_date),
  KEY idx_cpr_order(order_no),
  KEY idx_cpr_status(status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='化工请购单主表';

CREATE TABLE IF NOT EXISTS chemical_purchase_request_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_id BIGINT NOT NULL,
  schedule_id BIGINT NULL,
  order_no VARCHAR(64) NULL,
  finished_material_code VARCHAR(64) NULL,
  raw_material_code VARCHAR(64) NOT NULL,
  raw_material_name VARCHAR(128) NULL,
  required_kg DECIMAL(18,3) NOT NULL DEFAULT 0,
  suggested_qty INT NOT NULL DEFAULT 0,
  requested_qty INT NOT NULL DEFAULT 0,
  unit VARCHAR(16) NULL,
  remark VARCHAR(255) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_cpri_req(request_id),
  KEY idx_cpri_raw(raw_material_code),
  CONSTRAINT fk_cpri_req FOREIGN KEY (request_id) REFERENCES chemical_purchase_request(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='化工请购单明细';
