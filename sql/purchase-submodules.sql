-- Purchase supplier master data
CREATE TABLE IF NOT EXISTS purchase_suppliers (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  supplier_code VARCHAR(64) NOT NULL,
  supplier_name VARCHAR(128) NOT NULL,
  short_name VARCHAR(64),
  primary_contact_name VARCHAR(64),
  primary_contact_mobile VARCHAR(32),
  contact_email VARCHAR(128),
  contact_address VARCHAR(255),
  tax_no VARCHAR(64),
  bank_name VARCHAR(128),
  bank_account VARCHAR(64),
  status VARCHAR(32) DEFAULT 'active',
  remark VARCHAR(255),
  created_by VARCHAR(64),
  updated_by VARCHAR(64),
  created_at DATETIME,
  updated_at DATETIME,
  is_deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_supplier_code (supplier_code),
  KEY idx_supplier_name (supplier_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Supplier priority scoring
CREATE TABLE IF NOT EXISTS purchase_supplier_priority (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  supplier_code VARCHAR(64),
  supplier_name VARCHAR(128),
  score DECIMAL(12,2) DEFAULT 0,
  level VARCHAR(16) DEFAULT 'MEDIUM',
  remark VARCHAR(255),
  created_at DATETIME,
  updated_at DATETIME,
  created_by VARCHAR(64),
  updated_by VARCHAR(64),
  is_deleted TINYINT DEFAULT 0,
  KEY idx_priority_supplier (supplier_code),
  KEY idx_priority_level (level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Purchase quotation header
CREATE TABLE IF NOT EXISTS purchase_quotations (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  quotation_no VARCHAR(64) NOT NULL,
  supplier VARCHAR(128) NOT NULL,
  contact_person VARCHAR(64),
  contact_phone VARCHAR(32),
  quotation_date DATE,
  valid_until DATE,
  total_amount DECIMAL(18,2) DEFAULT 0,
  total_area DECIMAL(18,2) DEFAULT 0,
  status VARCHAR(32) DEFAULT 'draft',
  remark VARCHAR(255),
  created_by VARCHAR(64),
  updated_by VARCHAR(64),
  created_at DATETIME,
  updated_at DATETIME,
  is_deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_purchase_quotation_no (quotation_no),
  KEY idx_purchase_quotation_supplier (supplier),
  KEY idx_purchase_quotation_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Purchase quotation items
CREATE TABLE IF NOT EXISTS purchase_quotation_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  quotation_id BIGINT NOT NULL,
  material_code VARCHAR(64),
  material_name VARCHAR(128),
  specifications VARCHAR(128),
  length DECIMAL(18,2),
  width DECIMAL(18,6),
  thickness DECIMAL(18,2),
  quantity INT,
  unit VARCHAR(32),
  sqm DECIMAL(18,2),
  unit_price DECIMAL(18,4),
  amount DECIMAL(18,2),
  remark VARCHAR(255),
  created_by VARCHAR(64),
  updated_by VARCHAR(64),
  created_at DATETIME,
  updated_at DATETIME,
  is_deleted TINYINT DEFAULT 0,
  KEY idx_purchase_quotation_item_qid (quotation_id),
  CONSTRAINT fk_purchase_quotation_item_qid FOREIGN KEY (quotation_id) REFERENCES purchase_quotations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Purchase sample header
CREATE TABLE IF NOT EXISTS purchase_samples (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  sample_no VARCHAR(64) NOT NULL,
  supplier VARCHAR(128) NOT NULL,
  contact_name VARCHAR(64),
  contact_phone VARCHAR(32),
  contact_address VARCHAR(255),
  send_date DATE,
  expected_feedback_date DATE,
  express_company VARCHAR(128),
  tracking_number VARCHAR(64),
  ship_date DATE,
  delivery_date DATE,
  status VARCHAR(64),
  logistics_status VARCHAR(64),
  last_logistics_query_time DATETIME,
  total_quantity INT,
  remark VARCHAR(255),
  internal_note VARCHAR(255),
  created_by VARCHAR(64),
  updated_by VARCHAR(64),
  created_at DATETIME,
  updated_at DATETIME,
  is_deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_purchase_sample_no (sample_no),
  KEY idx_purchase_sample_supplier (supplier)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Purchase sample items
CREATE TABLE IF NOT EXISTS purchase_sample_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  sample_no VARCHAR(64) NOT NULL,
  material_code VARCHAR(64),
  material_name VARCHAR(128),
  specification VARCHAR(128),
  model VARCHAR(64),
  batch_no VARCHAR(64),
  length DECIMAL(18,2),
  width DECIMAL(18,6),
  thickness DECIMAL(18,2),
  quantity INT,
  unit VARCHAR(32),
  remark VARCHAR(255),
  created_at DATETIME,
  updated_at DATETIME,
  is_deleted TINYINT DEFAULT 0,
  KEY idx_purchase_sample_item_no (sample_no),
  CONSTRAINT fk_purchase_sample_item_no FOREIGN KEY (sample_no) REFERENCES purchase_samples (sample_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Purchase receipt header
CREATE TABLE IF NOT EXISTS purchase_receipts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  receipt_no VARCHAR(64),
  supplier VARCHAR(128) NOT NULL,
  contact_name VARCHAR(64),
  contact_phone VARCHAR(32),
  receive_address VARCHAR(255),
  expected_date DATE,
  received_date DATE,
  status VARCHAR(32) DEFAULT 'planned',
  remark VARCHAR(255),
  created_by VARCHAR(64),
  updated_by VARCHAR(64),
  created_at DATETIME,
  updated_at DATETIME,
  is_deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_purchase_receipt_no (receipt_no),
  KEY idx_purchase_receipt_supplier (supplier)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Purchase receipt items
CREATE TABLE IF NOT EXISTS purchase_receipt_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  receipt_id BIGINT NOT NULL,
  material_code VARCHAR(64),
  material_name VARCHAR(128),
  specification VARCHAR(128),
  expected_qty INT,
  received_qty INT,
  unit VARCHAR(32),
  unit_price DECIMAL(18,4),
  amount DECIMAL(18,2),
  remark VARCHAR(255),
  created_at DATETIME,
  updated_at DATETIME,
  is_deleted TINYINT DEFAULT 0,
  KEY idx_purchase_receipt_item_rid (receipt_id),
  CONSTRAINT fk_purchase_receipt_item_rid FOREIGN KEY (receipt_id) REFERENCES purchase_receipts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
