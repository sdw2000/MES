-- 排程批次表 + 明细表
-- 适用库：erp

CREATE TABLE IF NOT EXISTS schedule_batch (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_no VARCHAR(64) COMMENT '排程单号',
  process_type VARCHAR(20) NOT NULL COMMENT 'COATING/REWINDING',
  plan_date DATE COMMENT '计划日期',
  material_code VARCHAR(64) COMMENT '料号',
  material_name VARCHAR(255) COMMENT '品名',
  color_code VARCHAR(64) COMMENT '颜色',
  thickness DECIMAL(10,2) COMMENT '厚度(mm)',
  width_mm DECIMAL(10,2) COMMENT '宽度(mm)',
  length DECIMAL(10,2) COMMENT '长度(m)',
  total_qty INT DEFAULT 0 COMMENT '合计卷数',
  total_area DECIMAL(12,2) DEFAULT 0 COMMENT '合计面积(㎡)',
  status VARCHAR(20) DEFAULT 'UNSCHEDULED' COMMENT 'SCHEDULED/UNSCHEDULED/IN_PROGRESS/COMPLETED/CANCELLED',
  source_batch_id BIGINT COMMENT '来源批次ID(涂布->复卷)',
  remark VARCHAR(500),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_schedule_batch_no (batch_no),
  INDEX idx_schedule_batch_type_date (process_type, plan_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS schedule_batch_order (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id BIGINT NOT NULL COMMENT '批次ID',
  order_id BIGINT COMMENT '订单ID',
  order_item_id BIGINT COMMENT '订单明细ID',
  order_no VARCHAR(64) COMMENT '订单号',
  quantity INT DEFAULT 0 COMMENT '卷数',
  area DECIMAL(12,2) DEFAULT 0 COMMENT '面积(㎡)',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_schedule_batch_order_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- schedule_task 增加批次字段（如已存在可忽略）
SET @has_batch_id := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_task' AND COLUMN_NAME = 'batch_id'
);
SET @has_batch_no := (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_task' AND COLUMN_NAME = 'batch_no'
);
SET @sql1 := IF(@has_batch_id = 0, 'ALTER TABLE schedule_task ADD COLUMN batch_id BIGINT COMMENT ''排程批次ID''', 'SELECT 1');
SET @sql2 := IF(@has_batch_no = 0, 'ALTER TABLE schedule_task ADD COLUMN batch_no VARCHAR(64) COMMENT ''排程单号''', 'SELECT 1');
PREPARE stmt1 FROM @sql1; EXECUTE stmt1; DEALLOCATE PREPARE stmt1;
PREPARE stmt2 FROM @sql2; EXECUTE stmt2; DEALLOCATE PREPARE stmt2;

-- 允许聚合任务不绑定具体订单
ALTER TABLE schedule_task
  MODIFY COLUMN order_id BIGINT NULL,
  MODIFY COLUMN order_item_id BIGINT NULL;
