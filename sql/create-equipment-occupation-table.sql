-- 设备占用时间轴（用于计算机台最早可开工时间）
CREATE TABLE IF NOT EXISTS equipment_occupation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  schedule_id BIGINT NOT NULL COMMENT '手动排程ID',
  process_type VARCHAR(20) NOT NULL COMMENT '工序类型',
  equipment_id BIGINT NULL COMMENT '设备ID',
  equipment_code VARCHAR(50) NOT NULL COMMENT '设备编码',
  start_time DATETIME NOT NULL COMMENT '开始时间',
  end_time DATETIME NOT NULL COMMENT '结束时间',
  duration_minutes INT NOT NULL COMMENT '占用分钟数',
  status VARCHAR(20) NOT NULL DEFAULT 'PLANNED' COMMENT 'PLANNED/RUNNING/FINISHED/CANCELLED',
  remark VARCHAR(200) NULL COMMENT '备注',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_schedule_process (schedule_id, process_type),
  KEY idx_equipment_time (equipment_code, process_type, start_time, end_time),
  KEY idx_equipment_status (equipment_code, process_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备占用时间轴';
