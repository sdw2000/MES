-- 复卷与分切工艺参数物理独立表

CREATE TABLE IF NOT EXISTS rewinding_process_params (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  material_code VARCHAR(100) NOT NULL COMMENT '产品料号',
  equipment_code VARCHAR(64) NULL COMMENT '设备编码',
  rewinding_speed DECIMAL(10,2) NULL COMMENT '复卷速度(米/分)',
  tension_setting DECIMAL(10,2) NULL COMMENT '张力设定',
  roll_change_time INT NULL COMMENT '换卷时间(分钟)',
  setup_time INT NULL COMMENT '准备时间(分钟)',
  first_check_time INT NULL COMMENT '首检时间(分钟)',
  last_check_time INT NULL COMMENT '末检时间(分钟)',
  remark VARCHAR(500) NULL COMMENT '备注',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态:0停用,1启用',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_rewinding_material_equipment (material_code, equipment_code),
  KEY idx_rewinding_material_code (material_code),
  KEY idx_rewinding_equipment_code (equipment_code),
  KEY idx_rewinding_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='复卷工艺参数表';

CREATE TABLE IF NOT EXISTS slitting_process_params (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  material_code VARCHAR(100) NOT NULL COMMENT '产品料号',
  equipment_code VARCHAR(64) NULL COMMENT '设备编码',
  slitting_speed DECIMAL(10,2) NULL COMMENT '分切速度(卷/分或米/分按业务约定)',
  blade_type VARCHAR(100) NULL COMMENT '刀片类型',
  blade_change_time INT NULL COMMENT '换刀时间(分钟)',
  min_slit_width INT NULL COMMENT '最小分切宽度(mm)',
  max_blades INT NULL COMMENT '最大刀数',
  edge_loss INT NULL COMMENT '首尾损耗(mm)',
  setup_time INT NULL COMMENT '准备时间(分钟)',
  first_check_time INT NULL COMMENT '首检时间(分钟)',
  last_check_time INT NULL COMMENT '末检时间(分钟)',
  remark VARCHAR(500) NULL COMMENT '备注',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '状态:0停用,1启用',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_slitting_material_equipment (material_code, equipment_code),
  KEY idx_slitting_material_code (material_code),
  KEY idx_slitting_equipment_code (equipment_code),
  KEY idx_slitting_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分切工艺参数表';

-- 将原 process_params 中对应工序数据迁移到新表（幂等）
INSERT INTO rewinding_process_params (
  material_code, equipment_code, rewinding_speed, tension_setting, roll_change_time,
  setup_time, first_check_time, last_check_time, remark, status, create_time, update_time
)
SELECT
  p.material_code, p.equipment_code, p.rewinding_speed, p.tension_setting, p.roll_change_time,
  p.setup_time, p.first_check_time, p.last_check_time, p.remark, IFNULL(p.status,1),
  IFNULL(p.create_time, NOW()), IFNULL(p.update_time, NOW())
FROM process_params p
WHERE p.process_type = 'REWINDING' AND IFNULL(p.status,1) = 1
ON DUPLICATE KEY UPDATE
  rewinding_speed = VALUES(rewinding_speed),
  tension_setting = VALUES(tension_setting),
  roll_change_time = VALUES(roll_change_time),
  setup_time = VALUES(setup_time),
  first_check_time = VALUES(first_check_time),
  last_check_time = VALUES(last_check_time),
  remark = VALUES(remark),
  status = VALUES(status),
  update_time = NOW();

INSERT INTO slitting_process_params (
  material_code, equipment_code, slitting_speed, blade_type, blade_change_time,
  min_slit_width, max_blades, edge_loss,
  setup_time, first_check_time, last_check_time, remark, status, create_time, update_time
)
SELECT
  p.material_code, p.equipment_code, p.slitting_speed, p.blade_type, p.blade_change_time,
  p.min_slit_width, p.max_blades, p.edge_loss,
  p.setup_time, p.first_check_time, p.last_check_time, p.remark, IFNULL(p.status,1),
  IFNULL(p.create_time, NOW()), IFNULL(p.update_time, NOW())
FROM process_params p
WHERE p.process_type = 'SLITTING' AND IFNULL(p.status,1) = 1
ON DUPLICATE KEY UPDATE
  slitting_speed = VALUES(slitting_speed),
  blade_type = VALUES(blade_type),
  blade_change_time = VALUES(blade_change_time),
  min_slit_width = VALUES(min_slit_width),
  max_blades = VALUES(max_blades),
  edge_loss = VALUES(edge_loss),
  setup_time = VALUES(setup_time),
  first_check_time = VALUES(first_check_time),
  last_check_time = VALUES(last_check_time),
  remark = VALUES(remark),
  status = VALUES(status),
  update_time = NOW();
