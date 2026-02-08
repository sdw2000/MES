-- 单表排程：schedule_task
-- 适用库：erp

CREATE TABLE IF NOT EXISTS schedule_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  batch_id BIGINT COMMENT '排程批次ID',
  batch_no VARCHAR(64) COMMENT '排程单号',
  order_id BIGINT NULL COMMENT '订单ID',
  order_item_id BIGINT NULL COMMENT '订单明细ID',
  order_no VARCHAR(64) COMMENT '订单号',
  material_code VARCHAR(64) COMMENT '料号',
  material_name VARCHAR(255) COMMENT '品名',
  width_mm DECIMAL(10,2) COMMENT '宽度(mm)',
  length DECIMAL(10,2) COMMENT '长度(m)',
  quantity INT DEFAULT 0 COMMENT '排程卷数',
  area DECIMAL(12,2) DEFAULT 0 COMMENT '排程面积(㎡)',
  process_type VARCHAR(20) NOT NULL COMMENT 'COATING/REWINDING/SLITTING',
  equipment_id BIGINT COMMENT '设备ID',
  plan_start_time DATETIME COMMENT '计划开始时间',
  plan_end_time DATETIME COMMENT '计划结束时间',
  plan_duration_min INT COMMENT '工时(分钟)',
  status VARCHAR(20) DEFAULT 'UNSCHEDULED' COMMENT 'SCHEDULED/UNSCHEDULED/IN_PROGRESS/COMPLETED/CANCELLED',
  can_ship_by_48h TINYINT DEFAULT 0 COMMENT '48h可出货',
  priority_score DECIMAL(10,2) DEFAULT 0 COMMENT '优先级',
  delivery_date DATETIME COMMENT '交期',
  lock_stock TINYINT DEFAULT 0 COMMENT '是否锁定库存(预留)',
  remark VARCHAR(500),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_schedule_task_order (order_id, order_item_id),
  INDEX idx_schedule_task_status (status),
  INDEX idx_schedule_task_process (process_type),
  INDEX idx_schedule_task_equipment (equipment_id),
  INDEX idx_schedule_task_plan_start (plan_start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 已建表场景：字段更名为长度(m)
-- ALTER TABLE schedule_task CHANGE length_mm length DECIMAL(10,2) COMMENT '长度(m)';
