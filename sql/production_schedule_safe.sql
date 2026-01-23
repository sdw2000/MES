-- =====================================================
-- 生产管理系统 - 排程相关表（安全可重复执行版本）
-- 文件：production_schedule_safe.sql
-- 用途：在MySQL Workbench中执行，创建生产排程相关表
-- 特点：可重复执行，不会因表或字段已存在而报错
-- =====================================================

-- 设置编码和SQL模式
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- 第一部分：修改现有表（使用存储过程安全添加字段）
-- =====================================================

-- 创建安全添加字段的存储过程
DROP PROCEDURE IF EXISTS `safe_add_column`;
DELIMITER $$
CREATE PROCEDURE `safe_add_column`(
    IN p_table_name VARCHAR(64),
    IN p_column_name VARCHAR(64),
    IN p_column_definition TEXT
)
BEGIN
    DECLARE v_count INT;
    
    SELECT COUNT(*) INTO v_count 
    FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = p_table_name 
      AND COLUMN_NAME = p_column_name;
    
    IF v_count = 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', p_table_name, '` ADD COLUMN `', p_column_name, '` ', p_column_definition);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SELECT CONCAT('✓ 添加字段 ', p_table_name, '.', p_column_name, ' 成功') AS result;
    ELSE
        SELECT CONCAT('→ 字段 ', p_table_name, '.', p_column_name, ' 已存在，跳过') AS result;
    END IF;
END$$
DELIMITER ;

-- 创建安全添加索引的存储过程
DROP PROCEDURE IF EXISTS `safe_add_index`;
DELIMITER $$
CREATE PROCEDURE `safe_add_index`(
    IN p_table_name VARCHAR(64),
    IN p_index_name VARCHAR(64),
    IN p_column_names VARCHAR(255)
)
BEGIN
    DECLARE v_count INT;
    
    SELECT COUNT(*) INTO v_count 
    FROM INFORMATION_SCHEMA.STATISTICS 
    WHERE TABLE_SCHEMA = DATABASE() 
      AND TABLE_NAME = p_table_name 
      AND INDEX_NAME = p_index_name;
    
    IF v_count = 0 THEN
        SET @sql = CONCAT('CREATE INDEX `', p_index_name, '` ON `', p_table_name, '` (', p_column_names, ')');
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SELECT CONCAT('✓ 添加索引 ', p_index_name, ' 成功') AS result;
    ELSE
        SELECT CONCAT('→ 索引 ', p_index_name, ' 已存在，跳过') AS result;
    END IF;
END$$
DELIMITER ;

-- =====================================================
-- 1. 修改订单明细表 sales_order_items
-- =====================================================
SELECT '【1. 修改 sales_order_items 表】' AS step;

CALL safe_add_column('sales_order_items', 'scheduled_qty', 'INT DEFAULT 0 COMMENT \'已排程数量(卷)\'');
CALL safe_add_column('sales_order_items', 'produced_qty', 'INT DEFAULT 0 COMMENT \'已生产数量(卷)\'');
CALL safe_add_column('sales_order_items', 'delivered_qty', 'INT DEFAULT 0 COMMENT \'已出货数量(卷)\'');
CALL safe_add_column('sales_order_items', 'remaining_qty', 'INT DEFAULT 0 COMMENT \'未交货数量(卷)\'');
CALL safe_add_column('sales_order_items', 'production_status', 'VARCHAR(30) DEFAULT \'pending\' COMMENT \'生产状态：pending-待排程，scheduled-已排程，in_production-生产中，partial_completed-部分完成，completed-已完成\'');

-- 更新已有数据的remaining_qty（只更新为0或NULL的记录）
UPDATE `sales_order_items` SET `remaining_qty` = `rolls` WHERE (`remaining_qty` = 0 OR `remaining_qty` IS NULL) AND `rolls` IS NOT NULL;

-- =====================================================
-- 2. 修改库存表 tape_stock
-- =====================================================
SELECT '【2. 修改 tape_stock 表】' AS step;

CALL safe_add_column('tape_stock', 'stock_type', 'VARCHAR(20) DEFAULT \'finished\' COMMENT \'库存类型：jumbo-母卷，slit-支料，finished-成品\'');
CALL safe_add_column('tape_stock', 'parent_batch_no', 'VARCHAR(50) COMMENT \'来源批次号（分切/复卷后记录母卷批次）\'');
CALL safe_add_column('tape_stock', 'source_schedule_id', 'BIGINT COMMENT \'来源排程ID\'');

CALL safe_add_index('tape_stock', 'idx_stock_type', '`stock_type`');

-- =====================================================
-- 第二部分：创建新表（使用 DROP IF EXISTS + CREATE）
-- =====================================================

-- =====================================================
-- 3. 排程主表 production_schedule
-- =====================================================
SELECT '【3. 创建 production_schedule 表】' AS step;

DROP TABLE IF EXISTS `production_schedule`;
CREATE TABLE `production_schedule` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `schedule_no` VARCHAR(50) NOT NULL COMMENT '排程单号（PS-YYYYMMDD-XXX）',
    `schedule_date` DATE NOT NULL COMMENT '排程日期',
    `schedule_type` VARCHAR(20) NOT NULL COMMENT '排程类型：order-订单排程，safety-安全库存补货',
    `total_orders` INT DEFAULT 0 COMMENT '涉及订单数',
    `total_items` INT DEFAULT 0 COMMENT '涉及订单明细数',
    `total_sqm` DECIMAL(12,2) DEFAULT 0 COMMENT '总面积(平方米)',
    `status` VARCHAR(20) DEFAULT 'draft' COMMENT '状态：draft-草稿，pending_approval-待审批，approved-已审批，confirmed-已确认，in_progress-执行中，completed-已完成，cancelled-已取消',
    `approval_by` VARCHAR(50) COMMENT '审批人',
    `approval_time` DATETIME COMMENT '审批时间',
    `approval_remark` VARCHAR(500) COMMENT '审批备注',
    `confirmed_by` VARCHAR(50) COMMENT '确认人',
    `confirmed_time` DATETIME COMMENT '确认时间',
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` VARCHAR(50) COMMENT '创建人',
    `update_by` VARCHAR(50) COMMENT '更新人',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    
    UNIQUE KEY `uk_schedule_no` (`schedule_no`),
    INDEX `idx_schedule_date` (`schedule_date`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排程主表';

SELECT '✓ production_schedule 表创建成功' AS result;

-- =====================================================
-- 4. 排程订单关联表 schedule_order_item
-- =====================================================
SELECT '【4. 创建 schedule_order_item 表】' AS step;

DROP TABLE IF EXISTS `schedule_order_item`;
CREATE TABLE `schedule_order_item` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `schedule_id` BIGINT NOT NULL COMMENT '排程ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `order_item_id` BIGINT NOT NULL COMMENT '订单明细ID',
    `order_no` VARCHAR(50) COMMENT '订单号',
    `customer` VARCHAR(200) COMMENT '客户',
    `customer_level` VARCHAR(20) COMMENT '客户等级',
    `material_code` VARCHAR(50) NOT NULL COMMENT '产品料号',
    `material_name` VARCHAR(100) COMMENT '产品名称',
    `color_code` VARCHAR(20) COMMENT '颜色代码',
    `thickness` DECIMAL(10,3) COMMENT '厚度(mm)',
    `width` DECIMAL(10,2) COMMENT '宽度(mm)',
    `length` DECIMAL(10,2) COMMENT '长度(mm)',
    `order_qty` INT NOT NULL COMMENT '订单数量(卷)',
    `schedule_qty` INT NOT NULL COMMENT '本次排程数量(卷)',
    `delivery_date` DATE COMMENT '交货日期',
    `priority` INT DEFAULT 0 COMMENT '优先级（数值越小越优先）',
    `source_type` VARCHAR(20) COMMENT '来源类型：stock-库存，production-生产',
    `stock_id` BIGINT COMMENT '使用库存ID（如直接出库）',
    `status` VARCHAR(20) DEFAULT 'pending' COMMENT '状态：pending-待处理，processing-处理中，completed-已完成',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    INDEX `idx_schedule_id` (`schedule_id`),
    INDEX `idx_order_item_id` (`order_item_id`),
    INDEX `idx_material_code` (`material_code`),
    INDEX `idx_priority` (`priority`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排程订单关联表';

SELECT '✓ schedule_order_item 表创建成功' AS result;

-- =====================================================
-- 5. 印刷计划表 schedule_printing（涂布前工序）
-- =====================================================
SELECT '【5. 创建 schedule_printing 表】' AS step;

DROP TABLE IF EXISTS `schedule_printing`;
CREATE TABLE `schedule_printing` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `schedule_id` BIGINT NOT NULL COMMENT '排程ID',
    `task_no` VARCHAR(50) NOT NULL COMMENT '任务单号（PR-YYYYMMDD-XXX）',
    `equipment_id` BIGINT COMMENT '设备ID',
    `equipment_code` VARCHAR(30) COMMENT '设备编号',
    `staff_id` BIGINT COMMENT '操作人员ID',
    `staff_name` VARCHAR(50) COMMENT '操作人员',
    `shift_code` VARCHAR(20) COMMENT '班次',
    `plan_date` DATE COMMENT '计划日期',
    
    -- 产品信息
    `material_code` VARCHAR(50) NOT NULL COMMENT '产品料号',
    `material_name` VARCHAR(100) COMMENT '产品名称',
    `color_code` VARCHAR(20) COMMENT '颜色代码',
    `color_name` VARCHAR(50) COMMENT '颜色名称',
    `print_pattern` VARCHAR(100) COMMENT '印刷图案',
    
    -- 生产数量
    `base_width` INT COMMENT '基材宽度(mm)',
    `plan_length` DECIMAL(12,2) COMMENT '计划印刷长度(米)',
    `plan_sqm` DECIMAL(12,2) COMMENT '计划面积(平方米)',
    `actual_length` DECIMAL(12,2) COMMENT '实际印刷长度(米)',
    `actual_sqm` DECIMAL(12,2) COMMENT '实际面积(平方米)',
    
    -- 工艺参数
    `printing_speed` DECIMAL(10,2) COMMENT '印刷速度(米/分钟)',
    `ink_type` VARCHAR(50) COMMENT '油墨类型',
    `drying_temp` DECIMAL(6,2) COMMENT '干燥温度(℃)',
    
    -- 时间
    `plan_start_time` DATETIME COMMENT '计划开始时间',
    `plan_end_time` DATETIME COMMENT '计划结束时间',
    `plan_duration` INT COMMENT '计划时长(分钟)',
    `actual_start_time` DATETIME COMMENT '实际开始时间',
    `actual_end_time` DATETIME COMMENT '实际结束时间',
    `actual_duration` INT COMMENT '实际时长(分钟)',
    
    -- 状态
    `status` VARCHAR(20) DEFAULT 'pending' COMMENT '状态：pending-待生产，in_progress-生产中，completed-已完成，cancelled-已取消',
    `output_batch_no` VARCHAR(50) COMMENT '产出批次号',
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` VARCHAR(50) COMMENT '创建人',
    `update_by` VARCHAR(50) COMMENT '更新人',
    
    UNIQUE KEY `uk_task_no` (`task_no`),
    INDEX `idx_schedule_id` (`schedule_id`),
    INDEX `idx_equipment_id` (`equipment_id`),
    INDEX `idx_plan_date` (`plan_date`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='印刷计划表';

SELECT '✓ schedule_printing 表创建成功' AS result;

-- =====================================================
-- 6. 涂布计划表 schedule_coating
-- =====================================================
SELECT '【6. 创建 schedule_coating 表】' AS step;

DROP TABLE IF EXISTS `schedule_coating`;
CREATE TABLE `schedule_coating` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `schedule_id` BIGINT NOT NULL COMMENT '排程ID',
    `task_no` VARCHAR(50) NOT NULL COMMENT '任务单号（CT-YYYYMMDD-XXX）',
    `printing_task_id` BIGINT COMMENT '印刷任务ID（如需印刷）',
    `equipment_id` BIGINT COMMENT '设备ID',
    `equipment_code` VARCHAR(30) COMMENT '设备编号',
    `staff_id` BIGINT COMMENT '操作人员ID',
    `staff_name` VARCHAR(50) COMMENT '操作人员',
    `shift_code` VARCHAR(20) COMMENT '班次',
    `plan_date` DATE COMMENT '计划日期',
    
    -- 产品信息
    `material_code` VARCHAR(50) NOT NULL COMMENT '产品料号',
    `material_name` VARCHAR(100) COMMENT '产品名称',
    `color_code` VARCHAR(20) COMMENT '颜色代码',
    `color_name` VARCHAR(50) COMMENT '颜色名称',
    `thickness` DECIMAL(10,3) COMMENT '厚度(mm)',
    
    -- 生产数量
    `plan_length` DECIMAL(12,2) COMMENT '计划涂布长度(米)',
    `plan_sqm` DECIMAL(12,2) COMMENT '计划面积(平方米)',
    `actual_length` DECIMAL(12,2) COMMENT '实际涂布长度(米)',
    `actual_sqm` DECIMAL(12,2) COMMENT '实际面积(平方米)',
    `jumbo_width` INT COMMENT '母卷宽度(mm)',
    
    -- 工艺参数
    `coating_speed` DECIMAL(10,2) COMMENT '涂布速度(米/分钟)',
    `oven_temp` DECIMAL(6,2) COMMENT '烘箱温度(℃)',
    
    -- 时间
    `plan_start_time` DATETIME COMMENT '计划开始时间',
    `plan_end_time` DATETIME COMMENT '计划结束时间',
    `plan_duration` INT COMMENT '计划时长(分钟)',
    `actual_start_time` DATETIME COMMENT '实际开始时间',
    `actual_end_time` DATETIME COMMENT '实际结束时间',
    `actual_duration` INT COMMENT '实际时长(分钟)',
    
    -- 状态
    `status` VARCHAR(20) DEFAULT 'pending' COMMENT '状态：pending-待生产，in_progress-生产中，completed-已完成，cancelled-已取消',
    `output_batch_no` VARCHAR(50) COMMENT '产出批次号',
    `warehouse_in` TINYINT DEFAULT 0 COMMENT '是否已入库：0-未入库，1-已入库',
    `warehouse_in_time` DATETIME COMMENT '入库时间',
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` VARCHAR(50) COMMENT '创建人',
    `update_by` VARCHAR(50) COMMENT '更新人',
    
    UNIQUE KEY `uk_task_no` (`task_no`),
    INDEX `idx_schedule_id` (`schedule_id`),
    INDEX `idx_equipment_id` (`equipment_id`),
    INDEX `idx_plan_date` (`plan_date`),
    INDEX `idx_status` (`status`),
    INDEX `idx_color_code` (`color_code`),
    INDEX `idx_thickness` (`thickness`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='涂布计划表';

SELECT '✓ schedule_coating 表创建成功' AS result;

-- =====================================================
-- 7. 复卷计划表 schedule_rewinding
-- =====================================================
SELECT '【7. 创建 schedule_rewinding 表】' AS step;

DROP TABLE IF EXISTS `schedule_rewinding`;
CREATE TABLE `schedule_rewinding` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `schedule_id` BIGINT NOT NULL COMMENT '排程ID',
    `task_no` VARCHAR(50) NOT NULL COMMENT '任务单号（RW-YYYYMMDD-XXX）',
    `coating_task_id` BIGINT COMMENT '涂布任务ID',
    `equipment_id` BIGINT COMMENT '设备ID',
    `equipment_code` VARCHAR(30) COMMENT '设备编号',
    `staff_id` BIGINT COMMENT '操作人员ID',
    `staff_name` VARCHAR(50) COMMENT '操作人员',
    `shift_code` VARCHAR(20) COMMENT '班次',
    `plan_date` DATE COMMENT '计划日期',
    
    -- 来源母卷
    `source_batch_no` VARCHAR(50) COMMENT '来源母卷批次号',
    `source_stock_id` BIGINT COMMENT '来源库存ID',
    `jumbo_width` INT COMMENT '母卷宽度(mm)',
    `jumbo_length` DECIMAL(12,2) COMMENT '母卷长度(米)',
    
    -- 产品信息
    `material_code` VARCHAR(50) NOT NULL COMMENT '产品料号',
    `material_name` VARCHAR(100) COMMENT '产品名称',
    `thickness` DECIMAL(10,3) COMMENT '厚度(mm)',
    
    -- 复卷规格
    `slit_length` INT COMMENT '支料长度(米/卷)',
    `plan_rolls` INT COMMENT '计划卷数',
    `actual_rolls` INT COMMENT '实际卷数',
    
    -- 工艺参数
    `rewinding_speed` DECIMAL(10,2) COMMENT '复卷速度(米/分钟)',
    `tension` DECIMAL(10,2) COMMENT '张力设定',
    
    -- 时间
    `plan_start_time` DATETIME COMMENT '计划开始时间',
    `plan_end_time` DATETIME COMMENT '计划结束时间',
    `plan_duration` INT COMMENT '计划时长(分钟)',
    `actual_start_time` DATETIME COMMENT '实际开始时间',
    `actual_end_time` DATETIME COMMENT '实际结束时间',
    `actual_duration` INT COMMENT '实际时长(分钟)',
    
    -- 状态
    `status` VARCHAR(20) DEFAULT 'pending' COMMENT '状态：pending-待生产，in_progress-生产中，completed-已完成，cancelled-已取消',
    `output_batch_no` VARCHAR(50) COMMENT '产出批次号',
    `warehouse_in` TINYINT DEFAULT 0 COMMENT '是否已入库：0-未入库，1-已入库',
    `warehouse_in_time` DATETIME COMMENT '入库时间',
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` VARCHAR(50) COMMENT '创建人',
    `update_by` VARCHAR(50) COMMENT '更新人',
    
    UNIQUE KEY `uk_task_no` (`task_no`),
    INDEX `idx_schedule_id` (`schedule_id`),
    INDEX `idx_equipment_id` (`equipment_id`),
    INDEX `idx_plan_date` (`plan_date`),
    INDEX `idx_status` (`status`),
    INDEX `idx_source_batch_no` (`source_batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='复卷计划表';

SELECT '✓ schedule_rewinding 表创建成功' AS result;

-- =====================================================
-- 8. 分切计划表 schedule_slitting
-- =====================================================
SELECT '【8. 创建 schedule_slitting 表】' AS step;

DROP TABLE IF EXISTS `schedule_slitting`;
CREATE TABLE `schedule_slitting` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `schedule_id` BIGINT NOT NULL COMMENT '排程ID',
    `task_no` VARCHAR(50) NOT NULL COMMENT '任务单号（SL-YYYYMMDD-XXX）',
    `rewinding_task_id` BIGINT COMMENT '复卷任务ID',
    `equipment_id` BIGINT COMMENT '设备ID',
    `equipment_code` VARCHAR(30) COMMENT '设备编号',
    `staff_id` BIGINT COMMENT '操作人员ID',
    `staff_name` VARCHAR(50) COMMENT '操作人员',
    `shift_code` VARCHAR(20) COMMENT '班次',
    `plan_date` DATE COMMENT '计划日期',
    
    -- 来源支料
    `source_batch_no` VARCHAR(50) COMMENT '来源支料批次号',
    `source_stock_id` BIGINT COMMENT '来源库存ID',
    `slit_width` INT COMMENT '支料宽度(mm)',
    `slit_length` INT COMMENT '支料长度(米)',
    
    -- 产品信息
    `material_code` VARCHAR(50) NOT NULL COMMENT '产品料号',
    `material_name` VARCHAR(100) COMMENT '产品名称',
    `thickness` DECIMAL(10,3) COMMENT '厚度(mm)',
    
    -- 分切规格
    `target_width` INT COMMENT '目标宽度(mm)',
    `cuts_per_slit` INT COMMENT '每支料切几条',
    `plan_rolls` INT COMMENT '计划卷数',
    `actual_rolls` INT COMMENT '实际卷数',
    `edge_loss` INT DEFAULT 10 COMMENT '边料损耗(mm)',
    
    -- 工艺参数
    `slitting_speed` DECIMAL(10,2) COMMENT '分切速度(米/分钟)',
    
    -- 时间
    `plan_start_time` DATETIME COMMENT '计划开始时间',
    `plan_end_time` DATETIME COMMENT '计划结束时间',
    `plan_duration` INT COMMENT '计划时长(分钟)',
    `actual_start_time` DATETIME COMMENT '实际开始时间',
    `actual_end_time` DATETIME COMMENT '实际结束时间',
    `actual_duration` INT COMMENT '实际时长(分钟)',
    
    -- 状态
    `status` VARCHAR(20) DEFAULT 'pending' COMMENT '状态：pending-待生产，in_progress-生产中，completed-已完成，cancelled-已取消',
    `output_batch_no` VARCHAR(50) COMMENT '产出批次号',
    `warehouse_in` TINYINT DEFAULT 0 COMMENT '是否已入库：0-未入库，1-已入库',
    `warehouse_in_time` DATETIME COMMENT '入库时间',
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` VARCHAR(50) COMMENT '创建人',
    `update_by` VARCHAR(50) COMMENT '更新人',
    
    UNIQUE KEY `uk_task_no` (`task_no`),
    INDEX `idx_schedule_id` (`schedule_id`),
    INDEX `idx_equipment_id` (`equipment_id`),
    INDEX `idx_plan_date` (`plan_date`),
    INDEX `idx_status` (`status`),
    INDEX `idx_source_batch_no` (`source_batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分切计划表';

SELECT '✓ schedule_slitting 表创建成功' AS result;

-- =====================================================
-- 9. 分条计划表 schedule_stripping
-- =====================================================
SELECT '【9. 创建 schedule_stripping 表】' AS step;

DROP TABLE IF EXISTS `schedule_stripping`;
CREATE TABLE `schedule_stripping` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `schedule_id` BIGINT NOT NULL COMMENT '排程ID',
    `task_no` VARCHAR(50) NOT NULL COMMENT '任务单号（ST-YYYYMMDD-XXX）',
    `slitting_task_id` BIGINT COMMENT '分切任务ID',
    `equipment_id` BIGINT COMMENT '设备ID',
    `equipment_code` VARCHAR(30) COMMENT '设备编号',
    `staff_id` BIGINT COMMENT '操作人员ID',
    `staff_name` VARCHAR(50) COMMENT '操作人员',
    `shift_code` VARCHAR(20) COMMENT '班次',
    `plan_date` DATE COMMENT '计划日期',
    
    -- 来源母卷/分切料
    `source_batch_no` VARCHAR(50) COMMENT '来源批次号',
    `source_stock_id` BIGINT COMMENT '来源库存ID',
    `source_width` INT COMMENT '来源宽度(mm)',
    `source_length` DECIMAL(12,2) COMMENT '来源长度(米)',
    
    -- 产品信息
    `material_code` VARCHAR(50) NOT NULL COMMENT '产品料号',
    `material_name` VARCHAR(100) COMMENT '产品名称',
    `thickness` DECIMAL(10,3) COMMENT '厚度(mm)',
    
    -- 分条规格（同时切长度和宽度）
    `target_width` INT COMMENT '目标宽度(mm)',
    `target_length` INT COMMENT '目标长度(米)',
    `cuts_width` INT COMMENT '宽度方向切几条',
    `cuts_length` INT COMMENT '长度方向切几段',
    `plan_rolls` INT COMMENT '计划卷数',
    `actual_rolls` INT COMMENT '实际卷数',
    
    -- 工艺参数
    `stripping_speed` DECIMAL(10,2) COMMENT '分条速度(米/分钟)',
    
    -- 时间
    `plan_start_time` DATETIME COMMENT '计划开始时间',
    `plan_end_time` DATETIME COMMENT '计划结束时间',
    `plan_duration` INT COMMENT '计划时长(分钟)',
    `actual_start_time` DATETIME COMMENT '实际开始时间',
    `actual_end_time` DATETIME COMMENT '实际结束时间',
    `actual_duration` INT COMMENT '实际时长(分钟)',
    
    -- 状态
    `status` VARCHAR(20) DEFAULT 'pending' COMMENT '状态：pending-待生产，in_progress-生产中，completed-已完成，cancelled-已取消',
    `output_batch_no` VARCHAR(50) COMMENT '产出批次号',
    `warehouse_in` TINYINT DEFAULT 0 COMMENT '是否已入库：0-未入库，1-已入库',
    `warehouse_in_time` DATETIME COMMENT '入库时间',
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` VARCHAR(50) COMMENT '创建人',
    `update_by` VARCHAR(50) COMMENT '更新人',
    
    UNIQUE KEY `uk_task_no` (`task_no`),
    INDEX `idx_schedule_id` (`schedule_id`),
    INDEX `idx_equipment_id` (`equipment_id`),
    INDEX `idx_plan_date` (`plan_date`),
    INDEX `idx_status` (`status`),
    INDEX `idx_source_batch_no` (`source_batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分条计划表';

SELECT '✓ schedule_stripping 表创建成功' AS result;

-- =====================================================
-- 10. 生产报工表 production_report
-- =====================================================
SELECT '【10. 创建 production_report 表】' AS step;

DROP TABLE IF EXISTS `production_report`;
CREATE TABLE `production_report` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `report_no` VARCHAR(50) NOT NULL COMMENT '报工单号',
    `task_type` VARCHAR(20) NOT NULL COMMENT '任务类型：PRINTING/COATING/REWINDING/SLITTING/STRIPPING',
    `task_id` BIGINT NOT NULL COMMENT '任务ID',
    `task_no` VARCHAR(50) COMMENT '任务单号',
    `equipment_id` BIGINT COMMENT '设备ID',
    `staff_id` BIGINT NOT NULL COMMENT '报工人员ID',
    `staff_name` VARCHAR(50) COMMENT '报工人员',
    `shift_code` VARCHAR(20) COMMENT '班次',
    `report_date` DATE COMMENT '报工日期',
    
    -- 产出信息
    `output_qty` INT COMMENT '产出数量(卷)',
    `output_length` DECIMAL(12,2) COMMENT '产出长度(米)',
    `output_sqm` DECIMAL(12,2) COMMENT '产出面积(平方米)',
    `defect_qty` INT DEFAULT 0 COMMENT '不良数量',
    `defect_reason` VARCHAR(200) COMMENT '不良原因',
    
    -- 时间
    `start_time` DATETIME COMMENT '开始时间',
    `end_time` DATETIME COMMENT '结束时间',
    `work_minutes` INT COMMENT '工作时长(分钟)',
    `pause_minutes` INT DEFAULT 0 COMMENT '暂停时长(分钟)',
    
    -- 批次
    `output_batch_no` VARCHAR(50) COMMENT '产出批次号',
    
    -- 反馈来源
    `report_source` VARCHAR(20) DEFAULT 'workshop' COMMENT '反馈来源：workshop-车间，warehouse-仓库，quality-品质部',
    
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    UNIQUE KEY `uk_report_no` (`report_no`),
    INDEX `idx_task_id` (`task_id`),
    INDEX `idx_task_type` (`task_type`),
    INDEX `idx_staff_id` (`staff_id`),
    INDEX `idx_report_date` (`report_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产报工表';

SELECT '✓ production_report 表创建成功' AS result;

-- =====================================================
-- 11. 质检记录表 quality_inspection
-- =====================================================
SELECT '【11. 创建 quality_inspection 表】' AS step;

DROP TABLE IF EXISTS `quality_inspection`;
CREATE TABLE `quality_inspection` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `inspection_no` VARCHAR(50) NOT NULL COMMENT '检验单号（QC-YYYYMMDD-XXX）',
    `task_type` VARCHAR(20) COMMENT '任务类型：PRINTING/COATING/REWINDING/SLITTING/STRIPPING',
    `task_id` BIGINT COMMENT '任务ID',
    `task_no` VARCHAR(50) COMMENT '任务单号',
    `batch_no` VARCHAR(50) COMMENT '批次号',
    
    -- 产品信息
    `material_code` VARCHAR(50) COMMENT '产品料号',
    `material_name` VARCHAR(100) COMMENT '产品名称',
    
    -- 检验信息
    `inspection_type` VARCHAR(30) NOT NULL COMMENT '检验类型：first_article-首件检验，process-过程检验，final-成品检验',
    `inspector_id` BIGINT COMMENT '检验员ID',
    `inspector_name` VARCHAR(50) COMMENT '检验员',
    `inspection_time` DATETIME COMMENT '检验时间',
    
    -- 检验项目
    `sample_qty` INT COMMENT '抽检数量',
    `pass_qty` INT COMMENT '合格数量',
    `fail_qty` INT COMMENT '不合格数量',
    
    -- 检验结果
    `result` VARCHAR(20) NOT NULL COMMENT '检验结果：pass-合格，fail-不合格，conditional-条件放行',
    `defect_type` VARCHAR(100) COMMENT '缺陷类型',
    `defect_desc` TEXT COMMENT '缺陷描述',
    
    -- 处理意见
    `disposition` VARCHAR(50) COMMENT '处置方式：accept-接收，rework-返工，scrap-报废，concession-让步接收',
    `disposition_remark` VARCHAR(500) COMMENT '处置说明',
    
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` VARCHAR(50) COMMENT '创建人',
    
    UNIQUE KEY `uk_inspection_no` (`inspection_no`),
    INDEX `idx_task_id` (`task_id`),
    INDEX `idx_batch_no` (`batch_no`),
    INDEX `idx_result` (`result`),
    INDEX `idx_inspection_time` (`inspection_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质检记录表';

SELECT '✓ quality_inspection 表创建成功' AS result;

-- =====================================================
-- 12. 订单交付记录表 order_delivery_record
-- =====================================================
SELECT '【12. 创建 order_delivery_record 表】' AS step;

DROP TABLE IF EXISTS `order_delivery_record`;
CREATE TABLE `order_delivery_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `delivery_no` VARCHAR(50) NOT NULL COMMENT '交付单号',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `order_no` VARCHAR(50) COMMENT '订单号',
    `order_item_id` BIGINT NOT NULL COMMENT '订单明细ID',
    `material_code` VARCHAR(50) COMMENT '产品料号',
    `material_name` VARCHAR(100) COMMENT '产品名称',
    
    -- 交付数量
    `delivery_qty` INT NOT NULL COMMENT '本次交付数量(卷)',
    `delivery_sqm` DECIMAL(12,2) COMMENT '本次交付面积(平方米)',
    
    -- 来源
    `batch_no` VARCHAR(50) COMMENT '出库批次号',
    `stock_id` BIGINT COMMENT '库存ID',
    
    -- 交付信息
    `delivery_date` DATE COMMENT '交付日期',
    `delivery_type` VARCHAR(20) DEFAULT 'partial' COMMENT '交付类型：partial-分批，full-全部',
    `logistics_no` VARCHAR(100) COMMENT '物流单号',
    `receiver` VARCHAR(50) COMMENT '收货人',
    `receiver_phone` VARCHAR(20) COMMENT '收货人电话',
    
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_by` VARCHAR(50) COMMENT '创建人',
    
    UNIQUE KEY `uk_delivery_no` (`delivery_no`),
    INDEX `idx_order_id` (`order_id`),
    INDEX `idx_order_item_id` (`order_item_id`),
    INDEX `idx_delivery_date` (`delivery_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单交付记录表';

SELECT '✓ order_delivery_record 表创建成功' AS result;

-- =====================================================
-- 13. 生产异常表 production_exception
-- =====================================================
SELECT '【13. 创建 production_exception 表】' AS step;

DROP TABLE IF EXISTS `production_exception`;
CREATE TABLE `production_exception` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `exception_no` VARCHAR(50) NOT NULL COMMENT '异常单号',
    `task_type` VARCHAR(20) COMMENT '任务类型',
    `task_id` BIGINT COMMENT '任务ID',
    `task_no` VARCHAR(50) COMMENT '任务单号',
    `equipment_id` BIGINT COMMENT '设备ID',
    `equipment_code` VARCHAR(30) COMMENT '设备编号',
    
    -- 异常信息
    `exception_type` VARCHAR(50) NOT NULL COMMENT '异常类型：equipment-设备故障，material-物料问题，quality-质量问题，urgent_insert-紧急插单，other-其他',
    `exception_level` VARCHAR(20) DEFAULT 'normal' COMMENT '异常级别：minor-轻微，normal-一般，major-严重，critical-紧急',
    `exception_desc` TEXT COMMENT '异常描述',
    `occur_time` DATETIME COMMENT '发生时间',
    `discover_by` VARCHAR(50) COMMENT '发现人',
    
    -- 影响评估
    `stop_minutes` INT DEFAULT 0 COMMENT '停机时长(分钟)',
    `scrap_qty` INT DEFAULT 0 COMMENT '报废数量',
    `scrap_sqm` DECIMAL(12,2) DEFAULT 0 COMMENT '报废面积(平方米)',
    `affected_orders` VARCHAR(500) COMMENT '受影响订单号（逗号分隔）',
    
    -- 处理信息
    `handle_method` TEXT COMMENT '处理方案',
    `handle_by` VARCHAR(50) COMMENT '处理人',
    `handle_time` DATETIME COMMENT '处理时间',
    `handle_result` VARCHAR(500) COMMENT '处理结果',
    
    `status` VARCHAR(20) DEFAULT 'pending' COMMENT '状态：pending-待处理，handling-处理中，resolved-已解决，closed-已关闭',
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` VARCHAR(50) COMMENT '创建人',
    
    UNIQUE KEY `uk_exception_no` (`exception_no`),
    INDEX `idx_task_id` (`task_id`),
    INDEX `idx_equipment_id` (`equipment_id`),
    INDEX `idx_exception_type` (`exception_type`),
    INDEX `idx_status` (`status`),
    INDEX `idx_occur_time` (`occur_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产异常表';

SELECT '✓ production_exception 表创建成功' AS result;

-- =====================================================
-- 14. 紧急插单记录表 urgent_insert_order
-- =====================================================
SELECT '【14. 创建 urgent_insert_order 表】' AS step;

DROP TABLE IF EXISTS `urgent_insert_order`;
CREATE TABLE `urgent_insert_order` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `insert_no` VARCHAR(50) NOT NULL COMMENT '插单单号（UI-YYYYMMDD-XXX）',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `order_no` VARCHAR(50) NOT NULL COMMENT '订单号',
    `order_item_id` BIGINT COMMENT '订单明细ID',
    `customer` VARCHAR(200) COMMENT '客户',
    `customer_level` VARCHAR(20) COMMENT '客户等级',
    
    -- 插单信息
    `insert_reason` VARCHAR(500) NOT NULL COMMENT '插单原因',
    `priority_before` INT COMMENT '原优先级',
    `priority_after` INT COMMENT '新优先级',
    `required_date` DATE COMMENT '要求交期',
    
    -- 审批信息
    `apply_by` VARCHAR(50) COMMENT '申请人',
    `apply_time` DATETIME COMMENT '申请时间',
    `approve_by` VARCHAR(50) COMMENT '审批人',
    `approve_time` DATETIME COMMENT '审批时间',
    `approve_result` VARCHAR(20) COMMENT '审批结果：approved-同意，rejected-拒绝',
    `approve_remark` VARCHAR(500) COMMENT '审批意见',
    
    -- 影响评估
    `affected_schedules` TEXT COMMENT '受影响排程ID（JSON数组）',
    `affected_orders` TEXT COMMENT '受影响订单号（JSON数组）',
    `delay_days` INT DEFAULT 0 COMMENT '预计延期天数',
    
    `status` VARCHAR(20) DEFAULT 'pending' COMMENT '状态：pending-待审批，approved-已同意，rejected-已拒绝，executed-已执行',
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    UNIQUE KEY `uk_insert_no` (`insert_no`),
    INDEX `idx_order_id` (`order_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_apply_time` (`apply_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='紧急插单记录表';

SELECT '✓ urgent_insert_order 表创建成功' AS result;

-- =====================================================
-- 15. 分切宽度组合优化记录表 slitting_combination
-- =====================================================
SELECT '【15. 创建 slitting_combination 表】' AS step;

DROP TABLE IF EXISTS `slitting_combination`;
CREATE TABLE `slitting_combination` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `schedule_slitting_id` BIGINT NOT NULL COMMENT '分切计划ID',
    `slit_width` INT NOT NULL COMMENT '支料宽度(mm)',
    `effective_width` INT NOT NULL COMMENT '有效宽度(mm)，扣除损耗后',
    `combination` VARCHAR(200) NOT NULL COMMENT '宽度组合，如：500+300+200',
    `utilization` DECIMAL(5,2) COMMENT '利用率(%)',
    `waste_width` INT COMMENT '余料宽度(mm)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    INDEX `idx_schedule_slitting_id` (`schedule_slitting_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分切宽度组合表';

SELECT '✓ slitting_combination 表创建成功' AS result;

-- =====================================================
-- 16. 排程审批记录表 schedule_approval_log
-- =====================================================
SELECT '【16. 创建 schedule_approval_log 表】' AS step;

DROP TABLE IF EXISTS `schedule_approval_log`;
CREATE TABLE `schedule_approval_log` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `schedule_id` BIGINT NOT NULL COMMENT '排程ID',
    `schedule_no` VARCHAR(50) COMMENT '排程单号',
    `action` VARCHAR(30) NOT NULL COMMENT '操作：submit-提交审批，approve-审批通过，reject-驳回，withdraw-撤回',
    `from_status` VARCHAR(20) COMMENT '原状态',
    `to_status` VARCHAR(20) COMMENT '新状态',
    `operator_id` BIGINT COMMENT '操作人ID',
    `operator_name` VARCHAR(50) COMMENT '操作人',
    `opinion` VARCHAR(500) COMMENT '意见',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    INDEX `idx_schedule_id` (`schedule_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排程审批记录表';

SELECT '✓ schedule_approval_log 表创建成功' AS result;

-- =====================================================
-- 清理：删除临时存储过程
-- =====================================================
DROP PROCEDURE IF EXISTS `safe_add_column`;
DROP PROCEDURE IF EXISTS `safe_add_index`;

SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================
-- 执行完成
-- =====================================================
SELECT '========================================' AS info;
SELECT '✅ 所有表创建/更新完成！' AS result;
SELECT '========================================' AS info;

-- 显示创建的表
SELECT TABLE_NAME AS '已创建的表', TABLE_COMMENT AS '说明'
FROM INFORMATION_SCHEMA.TABLES 
WHERE TABLE_SCHEMA = DATABASE() 
  AND TABLE_NAME IN (
    'production_schedule',
    'schedule_order_item', 
    'schedule_printing',
    'schedule_coating',
    'schedule_rewinding',
    'schedule_slitting',
    'schedule_stripping',
    'production_report',
    'quality_inspection',
    'order_delivery_record',
    'production_exception',
    'urgent_insert_order',
    'slitting_combination',
    'schedule_approval_log'
  )
ORDER BY TABLE_NAME;
