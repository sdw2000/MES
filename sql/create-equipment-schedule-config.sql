CREATE TABLE IF NOT EXISTS equipment_schedule_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  equipment_id BIGINT NOT NULL COMMENT '设备ID',
  equipment_code VARCHAR(64) NOT NULL COMMENT '设备编码',
  initial_schedule_time DATETIME NULL COMMENT '默认排程起点',
  cycle_end_time DATETIME NULL COMMENT '当前周期结束时间',
  next_week_start_time VARCHAR(8) DEFAULT '08:00:00' COMMENT '下周重新开排时间',
  weekend_rest TINYINT(1) DEFAULT 1 COMMENT '周末休息',
  sunday_disabled TINYINT(1) DEFAULT 1 COMMENT '周日不可排',
  enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
  remark VARCHAR(255) NULL COMMENT '备注',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_equipment_schedule_config_equipment_id (equipment_id),
  UNIQUE KEY uk_equipment_schedule_config_equipment_code (equipment_code),
  KEY idx_equipment_schedule_config_cycle_end_time (cycle_end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备排程状态配置';
