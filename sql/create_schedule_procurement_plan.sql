CREATE TABLE IF NOT EXISTS schedule_procurement_plan (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plan_no VARCHAR(64) NOT NULL,
  schedule_id BIGINT NULL,
  order_no VARCHAR(64) NULL,
  material_code VARCHAR(64) NULL,
  required_area DECIMAL(18,2) NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_spp_plan_no(plan_no),
  KEY idx_spp_schedule(schedule_id),
  KEY idx_spp_order(order_no),
  KEY idx_spp_material(material_code),
  KEY idx_spp_status(status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排程缺料采购计划';
