-- =====================================================
-- 生产管理系统 - 基础数据表
-- 第一阶段：车间、设备、班次、人员、班组、工艺参数、安全库存
-- =====================================================

-- 1. 车间表
CREATE TABLE IF NOT EXISTS `workshop` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `workshop_code` VARCHAR(20) NOT NULL COMMENT '车间编号',
    `workshop_name` VARCHAR(50) NOT NULL COMMENT '车间名称',
    `manager` VARCHAR(50) COMMENT '负责人',
    `manager_phone` VARCHAR(20) COMMENT '负责人电话',
    `location` VARCHAR(100) COMMENT '位置描述',
    `remark` VARCHAR(500) COMMENT '备注',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-停用，1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` VARCHAR(50) COMMENT '创建人',
    `update_by` VARCHAR(50) COMMENT '更新人',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    UNIQUE KEY `uk_workshop_code` (`workshop_code`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车间表';

-- 2. 设备类型字典
CREATE TABLE IF NOT EXISTS `equipment_type` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `type_code` VARCHAR(20) NOT NULL COMMENT '类型编码',
    `type_name` VARCHAR(50) NOT NULL COMMENT '类型名称',
    `process_order` INT DEFAULT 0 COMMENT '工序顺序（用于排程）',
    `description` VARCHAR(200) COMMENT '描述',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-停用，1-启用',
    UNIQUE KEY `uk_type_code` (`type_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备类型字典';

-- 初始化设备类型
INSERT INTO `equipment_type` (`type_code`, `type_name`, `process_order`, `description`) VALUES
('PRINTING', '印刷机', 1, '印刷工序（可选）'),
('COATING', '涂布机', 2, '涂布工序'),
('REWINDING', '复卷机', 3, '复卷工序（切长度）'),
('SLITTING', '分切机', 4, '分切工序（切宽度）'),
('STRIPPING', '分条机', 5, '分条机（母卷直接切成小卷）');

-- 3. 设备表
CREATE TABLE IF NOT EXISTS `equipment` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `equipment_code` VARCHAR(30) NOT NULL COMMENT '设备编号',
    `equipment_name` VARCHAR(100) NOT NULL COMMENT '设备名称',
    `equipment_type` VARCHAR(20) NOT NULL COMMENT '设备类型编码',
    `workshop_id` BIGINT COMMENT '所属车间ID',
    `brand` VARCHAR(50) COMMENT '品牌',
    `model` VARCHAR(50) COMMENT '型号',
    `max_width` INT COMMENT '最大加工宽度(mm)',
    `max_speed` DECIMAL(10,2) COMMENT '最大速度(米/分钟)',
    `daily_capacity` DECIMAL(12,2) COMMENT '日产能(平方米)',
    `purchase_date` DATE COMMENT '购买日期',
    `status` VARCHAR(20) DEFAULT 'normal' COMMENT '状态：normal-正常，maintenance-维护中，fault-故障',
    `location` VARCHAR(100) COMMENT '设备位置',
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` VARCHAR(50) COMMENT '创建人',
    `update_by` VARCHAR(50) COMMENT '更新人',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    UNIQUE KEY `uk_equipment_code` (`equipment_code`),
    INDEX `idx_equipment_type` (`equipment_type`),
    INDEX `idx_workshop_id` (`workshop_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备表';

-- 4. 班次定义表
CREATE TABLE IF NOT EXISTS `shift_definition` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `shift_code` VARCHAR(20) NOT NULL COMMENT '班次编码',
    `shift_name` VARCHAR(50) NOT NULL COMMENT '班次名称',
    `start_time` TIME NOT NULL COMMENT '开始时间',
    `end_time` TIME NOT NULL COMMENT '结束时间',
    `work_hours` DECIMAL(4,2) COMMENT '有效工作时长(小时，扣除休息)',
    `cross_day` TINYINT DEFAULT 0 COMMENT '是否跨天：0-否，1-是',
    `break_minutes` INT DEFAULT 0 COMMENT '休息时间(分钟)',
    `remark` VARCHAR(200) COMMENT '备注',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-停用，1-启用',
    UNIQUE KEY `uk_shift_code` (`shift_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班次定义表';

-- 初始化班次（两班制）
INSERT INTO `shift_definition` (`shift_code`, `shift_name`, `start_time`, `end_time`, `work_hours`, `cross_day`, `break_minutes`) VALUES
('DAY', '早班', '08:00:00', '20:00:00', 11.0, 0, 60),
('NIGHT', '晚班', '20:00:00', '08:00:00', 11.0, 1, 60);

-- 5. 班组表
CREATE TABLE IF NOT EXISTS `production_team` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `team_code` VARCHAR(20) NOT NULL COMMENT '班组编号',
    `team_name` VARCHAR(50) NOT NULL COMMENT '班组名称',
    `workshop_id` BIGINT COMMENT '所属车间ID',
    `leader_id` BIGINT COMMENT '班组长ID',
    `shift_code` VARCHAR(20) COMMENT '默认班次',
    `remark` VARCHAR(200) COMMENT '备注',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-停用，1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_team_code` (`team_code`),
    INDEX `idx_workshop_id` (`workshop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班组表';

-- 6. 生产人员表
CREATE TABLE IF NOT EXISTS `production_staff` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `staff_code` VARCHAR(20) NOT NULL COMMENT '工号',
    `staff_name` VARCHAR(50) NOT NULL COMMENT '姓名',
    `gender` CHAR(1) COMMENT '性别：M-男，F-女',
    `phone` VARCHAR(20) COMMENT '联系电话',
    `team_id` BIGINT COMMENT '所属班组ID',
    `workshop_id` BIGINT COMMENT '所属车间ID',
    `skill_level` VARCHAR(20) DEFAULT 'junior' COMMENT '技能等级：junior-初级，middle-中级，senior-高级',
    `entry_date` DATE COMMENT '入职日期',
    `status` VARCHAR(20) DEFAULT 'active' COMMENT '状态：active-在职，leave-休假，resigned-离职',
    `remark` VARCHAR(200) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` VARCHAR(50) COMMENT '创建人',
    `update_by` VARCHAR(50) COMMENT '更新人',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    UNIQUE KEY `uk_staff_code` (`staff_code`),
    INDEX `idx_team_id` (`team_id`),
    INDEX `idx_workshop_id` (`workshop_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产人员表';

-- 7. 人员技能表（人员可操作的设备类型）
CREATE TABLE IF NOT EXISTS `staff_skill` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `staff_id` BIGINT NOT NULL COMMENT '人员ID',
    `equipment_type` VARCHAR(20) NOT NULL COMMENT '可操作设备类型',
    `proficiency` VARCHAR(20) DEFAULT 'normal' COMMENT '熟练度：normal-一般，skilled-熟练，expert-精通',
    `max_machines` INT DEFAULT 1 COMMENT '最多同时操作机器数',
    `certificate` VARCHAR(100) COMMENT '资格证书',
    `cert_expire_date` DATE COMMENT '证书有效期',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY `uk_staff_type` (`staff_id`, `equipment_type`),
    INDEX `idx_staff_id` (`staff_id`),
    INDEX `idx_equipment_type` (`equipment_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人员技能表';

-- 8. 人员排班表
CREATE TABLE IF NOT EXISTS `staff_schedule` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `staff_id` BIGINT NOT NULL COMMENT '人员ID',
    `schedule_date` DATE NOT NULL COMMENT '排班日期',
    `shift_code` VARCHAR(20) NOT NULL COMMENT '班次编码',
    `equipment_id` BIGINT COMMENT '分配设备ID',
    `actual_status` VARCHAR(20) DEFAULT 'scheduled' COMMENT '实际状态：scheduled-已排班，present-出勤，absent-缺勤，late-迟到',
    `check_in_time` DATETIME COMMENT '签到时间',
    `check_out_time` DATETIME COMMENT '签退时间',
    `remark` VARCHAR(200) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_staff_date_shift` (`staff_id`, `schedule_date`, `shift_code`),
    INDEX `idx_schedule_date` (`schedule_date`),
    INDEX `idx_equipment_id` (`equipment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人员排班表';

-- 9. 工艺参数表（按产品维护）
CREATE TABLE IF NOT EXISTS `process_params` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `material_code` VARCHAR(50) NOT NULL COMMENT '产品料号',
    `process_type` VARCHAR(20) NOT NULL COMMENT '工序类型：COATING/REWINDING/SLITTING/STRIPPING',
    `equipment_code` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '设备编码',
    
    -- 涂布参数
    `coating_speed` DECIMAL(10,2) COMMENT '涂布速度(米/分钟)',
    `oven_temp` DECIMAL(6,2) COMMENT '烘箱温度(℃)',
    `coating_thickness` DECIMAL(10,3) COMMENT '涂布厚度(μm)',
    `color_change_time` INT COMMENT '换色清洗时间(分钟)',
    `thickness_change_time` INT COMMENT '换厚度调机时间(分钟)',
    
    -- 复卷参数
    `rewinding_speed` DECIMAL(10,2) COMMENT '复卷速度(米/分钟)',
    `tension_setting` DECIMAL(10,2) COMMENT '张力设定',
    `roll_change_time` INT COMMENT '换卷时间(分钟)',
    
    -- 分切参数
    `slitting_speed` DECIMAL(10,2) COMMENT '分切速度(米/分钟)',
    `blade_type` VARCHAR(50) COMMENT '刀片类型',
    `blade_change_time` INT COMMENT '换刀时间(分钟)',
    `min_slit_width` INT COMMENT '最小分切宽度(mm)',
    `max_blades` INT COMMENT '最大刀数',
    `edge_loss` INT DEFAULT 10 COMMENT '首尾损耗(mm)',
    
    -- 分条机参数
    `stripping_speed` DECIMAL(10,2) COMMENT '分条速度(米/分钟)',
    
    -- 通用参数
    `first_check_time` INT DEFAULT 10 COMMENT '首检时间(分钟)',
    `last_check_time` INT DEFAULT 5 COMMENT '末检时间(分钟)',
    `setup_time` INT DEFAULT 15 COMMENT '准备时间(分钟)',
    
    `remark` VARCHAR(500) COMMENT '备注',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-停用，1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` VARCHAR(50) COMMENT '创建人',
    `update_by` VARCHAR(50) COMMENT '更新人',
    
    UNIQUE KEY `uk_material_process_equipment` (`material_code`, `process_type`, `equipment_code`),
    INDEX `idx_material_code` (`material_code`),
    INDEX `idx_process_type` (`process_type`),
    INDEX `idx_equipment_code` (`equipment_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工艺参数表';

-- 10. 安全库存表
CREATE TABLE IF NOT EXISTS `safety_stock` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `material_code` VARCHAR(50) NOT NULL COMMENT '产品料号',
    `product_name` VARCHAR(100) COMMENT '产品名称',
    `safety_qty` INT NOT NULL DEFAULT 0 COMMENT '安全库存数量(卷)',
    `safety_sqm` DECIMAL(12,2) COMMENT '安全库存面积(平方米)',
    `reorder_point` INT COMMENT '补货点(低于此数量触发补货)',
    `max_stock` INT COMMENT '最大库存量',
    `current_stock` INT DEFAULT 0 COMMENT '当前库存(卷)',
    `current_sqm` DECIMAL(12,2) DEFAULT 0 COMMENT '当前库存面积',
    `gap_qty` INT COMMENT '缺口数量(安全库存-当前库存)',
    `last_calc_time` DATETIME COMMENT '最后计算时间',
    `remark` VARCHAR(200) COMMENT '备注',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-停用，1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` VARCHAR(50) COMMENT '创建人',
    `update_by` VARCHAR(50) COMMENT '更新人',
    
    UNIQUE KEY `uk_material_code` (`material_code`),
    INDEX `idx_gap_qty` (`gap_qty`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全库存表';

-- 11. 设备日历表（记录设备可用状态）
CREATE TABLE IF NOT EXISTS `equipment_calendar` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `equipment_id` BIGINT NOT NULL COMMENT '设备ID',
    `calendar_date` DATE NOT NULL COMMENT '日期',
    `shift_code` VARCHAR(20) NOT NULL COMMENT '班次',
    `is_available` TINYINT DEFAULT 1 COMMENT '是否可用：0-不可用，1-可用',
    `unavailable_reason` VARCHAR(100) COMMENT '不可用原因',
    `planned_maintenance` TINYINT DEFAULT 0 COMMENT '是否计划维护',
    `scheduled_hours` DECIMAL(4,2) DEFAULT 0 COMMENT '已排产时长(小时)',
    `available_hours` DECIMAL(4,2) COMMENT '剩余可用时长(小时)',
    `remark` VARCHAR(200) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    UNIQUE KEY `uk_equip_date_shift` (`equipment_id`, `calendar_date`, `shift_code`),
    INDEX `idx_calendar_date` (`calendar_date`),
    INDEX `idx_is_available` (`is_available`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备日历表';

-- =====================================================
-- 初始化测试数据
-- =====================================================

-- 初始化车间
INSERT INTO `workshop` (`workshop_code`, `workshop_name`, `manager`, `location`, `status`) VALUES
('WS001', '涂布车间', '张三', 'A栋1楼', 1),
('WS002', '复卷分切车间', '李四', 'A栋2楼', 1);

-- 初始化设备（涂布机3台、复卷机3台、分切机4台、分条机2台）
INSERT INTO `equipment` (`equipment_code`, `equipment_name`, `equipment_type`, `workshop_id`, `max_width`, `max_speed`, `status`) VALUES
-- 涂布机
('TB-001', '涂布机1号', 'COATING', 1, 1200, 50, 'normal'),
('TB-002', '涂布机2号', 'COATING', 1, 1200, 50, 'normal'),
('TB-003', '涂布机3号', 'COATING', 1, 1050, 40, 'normal'),
-- 复卷机
('FJ-001', '复卷机1号', 'REWINDING', 2, 1200, 80, 'normal'),
('FJ-002', '复卷机2号', 'REWINDING', 2, 1200, 80, 'normal'),
('FJ-003', '复卷机3号', 'REWINDING', 2, 1050, 60, 'normal'),
-- 分切机
('FQ-001', '分切机1号', 'SLITTING', 2, 1200, 100, 'normal'),
('FQ-002', '分切机2号', 'SLITTING', 2, 1200, 100, 'normal'),
('FQ-003', '分切机3号', 'SLITTING', 2, 1050, 80, 'normal'),
('FQ-004', '分切机4号', 'SLITTING', 2, 1050, 80, 'normal'),
-- 分条机
('FT-001', '分条机1号', 'STRIPPING', 2, 1200, 60, 'normal'),
('FT-002', '分条机2号', 'STRIPPING', 2, 1050, 50, 'normal');

-- 初始化班组
INSERT INTO `production_team` (`team_code`, `team_name`, `workshop_id`, `shift_code`, `status`) VALUES
('T-TB-A', '涂布A组', 1, 'DAY', 1),
('T-TB-B', '涂布B组', 1, 'NIGHT', 1),
('T-FJ-A', '复卷A组', 2, 'DAY', 1),
('T-FJ-B', '复卷B组', 2, 'NIGHT', 1),
('T-FQ-A', '分切A组', 2, 'DAY', 1),
('T-FQ-B', '分切B组', 2, 'NIGHT', 1);

-- 初始化一些测试人员
INSERT INTO `production_staff` (`staff_code`, `staff_name`, `gender`, `team_id`, `workshop_id`, `skill_level`, `status`) VALUES
('EMP001', '王师傅', 'M', 1, 1, 'senior', 'active'),
('EMP002', '赵师傅', 'M', 1, 1, 'middle', 'active'),
('EMP003', '钱师傅', 'M', 2, 1, 'senior', 'active'),
('EMP004', '孙师傅', 'F', 3, 2, 'middle', 'active'),
('EMP005', '周师傅', 'M', 3, 2, 'senior', 'active'),
('EMP006', '吴师傅', 'M', 4, 2, 'middle', 'active'),
('EMP007', '郑师傅', 'M', 5, 2, 'senior', 'active'),
('EMP008', '冯师傅', 'F', 5, 2, 'middle', 'active'),
('EMP009', '陈师傅', 'M', 6, 2, 'middle', 'active'),
('EMP010', '卫师傅', 'M', 6, 2, 'junior', 'active');

-- 初始化人员技能
INSERT INTO `staff_skill` (`staff_id`, `equipment_type`, `proficiency`, `max_machines`) VALUES
-- 涂布人员
(1, 'COATING', 'expert', 2),
(2, 'COATING', 'skilled', 1),
(3, 'COATING', 'expert', 2),
-- 复卷人员
(4, 'REWINDING', 'skilled', 2),
(5, 'REWINDING', 'expert', 3),
(6, 'REWINDING', 'skilled', 2),
-- 分切人员
(7, 'SLITTING', 'expert', 2),
(8, 'SLITTING', 'skilled', 2),
(9, 'SLITTING', 'skilled', 2),
(10, 'SLITTING', 'normal', 1),
-- 分条机（部分分切人员也会操作）
(7, 'STRIPPING', 'skilled', 1),
(8, 'STRIPPING', 'normal', 1);

-- 查询验证
SELECT '=== 车间 ===' AS info;
SELECT * FROM workshop;

SELECT '=== 设备类型 ===' AS info;
SELECT * FROM equipment_type;

SELECT '=== 设备列表 ===' AS info;
SELECT e.*, et.type_name, w.workshop_name 
FROM equipment e 
LEFT JOIN equipment_type et ON e.equipment_type = et.type_code
LEFT JOIN workshop w ON e.workshop_id = w.id;

SELECT '=== 班次 ===' AS info;
SELECT * FROM shift_definition;

SELECT '=== 班组 ===' AS info;
SELECT * FROM production_team;

SELECT '=== 人员 ===' AS info;
SELECT s.*, t.team_name 
FROM production_staff s 
LEFT JOIN production_team t ON s.team_id = t.id;

SELECT '=== 人员技能 ===' AS info;
SELECT s.staff_name, sk.equipment_type, et.type_name, sk.proficiency, sk.max_machines
FROM staff_skill sk
JOIN production_staff s ON sk.staff_id = s.id
JOIN equipment_type et ON sk.equipment_type = et.type_code;
