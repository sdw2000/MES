-- 销售退货模块（一期+对账基础）

CREATE TABLE IF NOT EXISTS sales_return_orders (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  return_no VARCHAR(64) NOT NULL UNIQUE COMMENT '退货单号',
  customer VARCHAR(128) NOT NULL COMMENT '客户代码',
  return_date DATE NOT NULL COMMENT '退货日期',
  status VARCHAR(32) DEFAULT 'confirmed' COMMENT 'draft/confirmed/cancelled',
  total_amount DECIMAL(18,2) DEFAULT 0 COMMENT '退货总金额（正值）',
  total_area DECIMAL(18,2) DEFAULT 0 COMMENT '退货总面积',
  statement_amount DECIMAL(18,2) DEFAULT 0 COMMENT '对账金额（负值）',
  statement_month VARCHAR(7) COMMENT '对账月份 yyyy-MM',
  reason VARCHAR(255),
  remark VARCHAR(500),
  created_by VARCHAR(64),
  updated_by VARCHAR(64),
  created_at DATETIME,
  updated_at DATETIME,
  is_deleted TINYINT DEFAULT 0,
  INDEX idx_sro_customer (customer),
  INDEX idx_sro_month (statement_month),
  INDEX idx_sro_date (return_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售退货单表';

CREATE TABLE IF NOT EXISTS sales_return_items (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  return_id BIGINT NOT NULL,
  order_no VARCHAR(64) COMMENT '来源订单号',
  source_order_item_id BIGINT COMMENT '来源订单明细ID',
  material_code VARCHAR(128),
  material_name VARCHAR(255),
  color_code VARCHAR(64),
  thickness DECIMAL(18,2),
  width DECIMAL(18,2) COMMENT 'mm',
  length DECIMAL(18,2) COMMENT 'm',
  rolls INT DEFAULT 0,
  sqm DECIMAL(18,2) DEFAULT 0,
  unit_price DECIMAL(18,4) DEFAULT 0,
  amount DECIMAL(18,2) DEFAULT 0,
  remark VARCHAR(500),
  created_by VARCHAR(64),
  updated_by VARCHAR(64),
  created_at DATETIME,
  updated_at DATETIME,
  is_deleted TINYINT DEFAULT 0,
  INDEX idx_sri_return_id (return_id),
  INDEX idx_sri_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售退货明细表';
