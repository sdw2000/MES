-- 胶带仓库管理表结构
-- 适用于胶带行业：按批次管理、整卷出库、FIFO、审批流程

-- 1. 库存主表（每个批次一条记录）
CREATE TABLE IF NOT EXISTS tape_stock (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  material_code VARCHAR(50) NOT NULL COMMENT '料号',
  product_name VARCHAR(100) COMMENT '产品名称',
  batch_no VARCHAR(50) NOT NULL COMMENT '生产批次号（唯一）',
  thickness INT COMMENT '厚度μm',
  width INT COMMENT '宽度mm',
  length INT COMMENT '长度M（每卷）',
  total_rolls INT DEFAULT 0 COMMENT '当前库存卷数',
  total_sqm DECIMAL(12,2) COMMENT '总平米数（宽度/1000*长度*卷数）',
  location VARCHAR(50) COMMENT '卡板位/库位',
  spec_desc VARCHAR(100) COMMENT '规格描述（如30μm*500mm*6000m）',
  prod_year INT COMMENT '生产年份',
  prod_month INT COMMENT '生产月份',
  prod_day INT COMMENT '生产日期',
  prod_date DATE COMMENT '完整生产日期（用于FIFO排序）',
  remark VARCHAR(200) COMMENT '备注',
  status TINYINT DEFAULT 1 COMMENT '状态：1正常 0已清空',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_batch_no (batch_no),
  INDEX idx_material_code (material_code),
  INDEX idx_prod_date (prod_date),
  INDEX idx_location (location)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='胶带库存表';

-- 2. 入库申请表
CREATE TABLE IF NOT EXISTS tape_inbound_request (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_no VARCHAR(50) NOT NULL COMMENT '入库申请单号',
  material_code VARCHAR(50) NOT NULL COMMENT '料号',
  product_name VARCHAR(100) COMMENT '产品名称',
  batch_no VARCHAR(50) NOT NULL COMMENT '生产批次号',
  thickness INT COMMENT '厚度μm',
  width INT COMMENT '宽度mm',
  length INT COMMENT '长度M（每卷）',
  rolls INT NOT NULL COMMENT '入库卷数',
  location VARCHAR(50) COMMENT '卡板位',
  spec_desc VARCHAR(100) COMMENT '规格描述',
  prod_year INT COMMENT '生产年份',
  prod_month INT COMMENT '生产月份',
  prod_day INT COMMENT '生产日期',
  prod_date DATE COMMENT '完整生产日期',
  applicant VARCHAR(50) COMMENT '申请人',
  apply_dept VARCHAR(50) COMMENT '申请部门',
  apply_time DATETIME COMMENT '申请时间',
  status TINYINT DEFAULT 0 COMMENT '状态：0待审批 1已通过 2已拒绝 3已取消',
  auditor VARCHAR(50) COMMENT '审批人',
  audit_time DATETIME COMMENT '审批时间',
  audit_remark VARCHAR(200) COMMENT '审批备注',
  remark VARCHAR(200) COMMENT '申请备注',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_request_no (request_no),
  INDEX idx_status (status),
  INDEX idx_apply_time (apply_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='入库申请表';

-- 3. 出库申请表
CREATE TABLE IF NOT EXISTS tape_outbound_request (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_no VARCHAR(50) NOT NULL COMMENT '出库申请单号',
  stock_id BIGINT COMMENT '关联库存ID',
  material_code VARCHAR(50) NOT NULL COMMENT '料号',
  product_name VARCHAR(100) COMMENT '产品名称',
  batch_no VARCHAR(50) NOT NULL COMMENT '生产批次号',
  spec_desc VARCHAR(100) COMMENT '规格描述',
  rolls INT NOT NULL COMMENT '出库卷数',
  available_rolls INT COMMENT '申请时可用卷数',
  applicant VARCHAR(50) COMMENT '申请人',
  apply_dept VARCHAR(50) COMMENT '申请部门',
  apply_time DATETIME COMMENT '申请时间',
  status TINYINT DEFAULT 0 COMMENT '状态：0待审批 1已通过 2已拒绝 3已取消',
  auditor VARCHAR(50) COMMENT '审批人',
  audit_time DATETIME COMMENT '审批时间',
  audit_remark VARCHAR(200) COMMENT '审批备注',
  remark VARCHAR(200) COMMENT '申请备注（如用途、去向）',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_request_no (request_no),
  INDEX idx_stock_id (stock_id),
  INDEX idx_status (status),
  INDEX idx_apply_time (apply_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出库申请表';

-- 4. 库存流水表（记录所有变动）
CREATE TABLE IF NOT EXISTS tape_stock_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  stock_id BIGINT COMMENT '关联库存ID',
  batch_no VARCHAR(50) COMMENT '生产批次号',
  material_code VARCHAR(50) COMMENT '料号',
  product_name VARCHAR(100) COMMENT '产品名称',
  type VARCHAR(10) NOT NULL COMMENT 'IN入库/OUT出库/ADJUST调整',
  change_rolls INT NOT NULL COMMENT '变动卷数（入库正数，出库负数）',
  before_rolls INT COMMENT '变动前卷数',
  after_rolls INT COMMENT '变动后卷数',
  ref_no VARCHAR(50) COMMENT '关联单号（入库/出库申请单号）',
  operator VARCHAR(50) COMMENT '操作人',
  remark VARCHAR(200) COMMENT '备注',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_stock_id (stock_id),
  INDEX idx_batch_no (batch_no),
  INDEX idx_material_code (material_code),
  INDEX idx_type (type),
  INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存流水表';

-- 5. 单号序列表（用于生成单号）
CREATE TABLE IF NOT EXISTS tape_sequence (
  seq_name VARCHAR(50) PRIMARY KEY COMMENT '序列名称',
  current_value BIGINT DEFAULT 0 COMMENT '当前值',
  prefix VARCHAR(10) COMMENT '前缀',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单号序列表';

-- 初始化序列
INSERT INTO tape_sequence (seq_name, current_value, prefix) VALUES 
('INBOUND', 0, 'IN'),
('OUTBOUND', 0, 'OUT')
ON DUPLICATE KEY UPDATE seq_name=seq_name;
