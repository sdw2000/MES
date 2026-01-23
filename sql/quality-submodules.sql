-- Quality module schema
-- Incoming / process / outbound inspections share same head table with inspection_type

CREATE TABLE IF NOT EXISTS quality_inspection (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  inspection_no VARCHAR(64) NOT NULL,
  inspection_type VARCHAR(32) NOT NULL COMMENT 'incoming/process/outbound',
  source_order_no VARCHAR(64) COMMENT '来料单/工序任务/出货单号',
  task_type VARCHAR(32) COMMENT '工序类型，如 COATING/REWINDING/SLITTING',
  task_id BIGINT,
  task_no VARCHAR(64),
  batch_no VARCHAR(64),
  roll_code VARCHAR(64) COMMENT '母卷/复卷/分切卷码',
  material_code VARCHAR(64),
  material_name VARCHAR(128),
  specification VARCHAR(128),
  sample_qty INT,
  pass_qty INT,
  fail_qty INT,
  overall_result VARCHAR(16) COMMENT 'pass/fail/pending',
  inspector_id BIGINT,
  inspector_name VARCHAR(64),
  inspection_time DATETIME,
  defect_type VARCHAR(128),
  defect_desc VARCHAR(512),
  remark VARCHAR(512),
  process_node VARCHAR(64) COMMENT '工序节点/站点',
  process_snapshot JSON NULL COMMENT '工艺参数快照',
  created_at DATETIME,
  updated_at DATETIME,
  is_deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_quality_inspection_no (inspection_no),
  KEY idx_quality_inspection_type (inspection_type),
  KEY idx_quality_inspection_batch (batch_no),
  KEY idx_quality_inspection_roll (roll_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS quality_inspection_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  inspection_id BIGINT NOT NULL,
  item_code VARCHAR(64) COMMENT '检测项编码，如 thickness/tension/appearance',
  item_name VARCHAR(128),
  standard_value VARCHAR(128),
  measured_value VARCHAR(128),
  unit VARCHAR(32),
  result VARCHAR(16) COMMENT 'pass/fail',
  remark VARCHAR(255),
  created_at DATETIME,
  updated_at DATETIME,
  is_deleted TINYINT DEFAULT 0,
  KEY idx_quality_item_inspection (inspection_id),
  CONSTRAINT fk_quality_item_inspection FOREIGN KEY (inspection_id) REFERENCES quality_inspection (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS quality_disposition (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  disposition_no VARCHAR(64) NOT NULL,
  inspection_id BIGINT NOT NULL,
  inspection_no VARCHAR(64) NOT NULL,
  batch_no VARCHAR(64),
  fail_qty INT,
  processed_qty INT DEFAULT 0,
  disposition_method VARCHAR(32) COMMENT 'rework/return/scrap/downgrade/other',
  disposition_description VARCHAR(512),
  status VARCHAR(32) DEFAULT 'pending',
  creator_name VARCHAR(64),
  create_time DATETIME,
  remark VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_quality_disposition_no (disposition_no),
  KEY idx_quality_disposition_inspection (inspection_id),
  CONSTRAINT fk_quality_disposition_inspection FOREIGN KEY (inspection_id) REFERENCES quality_inspection (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS quality_defect_type (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  defect_code VARCHAR(64) NOT NULL,
  defect_name VARCHAR(128) NOT NULL,
  category VARCHAR(64) COMMENT 'incoming/process/outbound/general',
  description VARCHAR(255),
  is_deleted TINYINT DEFAULT 0,
  UNIQUE KEY uk_quality_defect_code (defect_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
