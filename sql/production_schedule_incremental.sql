-- =====================================================
-- 生产管理系统 - 排程相关表（增量安全版本）
-- 使用存储过程安全添加列，跳过已存在的
-- =====================================================

-- 删除临时存储过程（如果存在）
DROP PROCEDURE IF EXISTS add_column_if_not_exists;

DELIMITER //

-- 创建安全添加列的存储过程
CREATE PROCEDURE add_column_if_not_exists(
    IN table_name VARCHAR(100),
    IN column_name VARCHAR(100),
    IN column_definition VARCHAR(500)
)
BEGIN
    DECLARE column_exists INT DEFAULT 0;
    
    SELECT COUNT(*) INTO column_exists
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = table_name
      AND COLUMN_NAME = column_name;
    
    IF column_exists = 0 THEN
        SET @sql = CONCAT('ALTER TABLE `', table_name, '` ADD COLUMN `', column_name, '` ', column_definition);
        PREPARE stmt FROM @sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
        SELECT CONCAT('✅ 已添加列: ', table_name, '.', column_name) AS result;
    ELSE
        SELECT CONCAT('⏭️ 列已存在，跳过: ', table_name, '.', column_name) AS result;
    END IF;
END //

DELIMITER ;

-- =====================================================
-- 第一部分：安全添加列到现有表
-- =====================================================

-- 1. sales_order_items 表添加排程和交付跟踪字段
CALL add_column_if_not_exists('sales_order_items', 'scheduled_qty', 'INT DEFAULT 0 COMMENT ''已排程数量(卷)''');
CALL add_column_if_not_exists('sales_order_items', 'produced_qty', 'INT DEFAULT 0 COMMENT ''已生产数量(卷)''');
CALL add_column_if_not_exists('sales_order_items', 'delivered_qty', 'INT DEFAULT 0 COMMENT ''已出货数量(卷)''');
CALL add_column_if_not_exists('sales_order_items', 'remaining_qty', 'INT DEFAULT 0 COMMENT ''未交货数量(卷)''');
CALL add_column_if_not_exists('sales_order_items', 'production_status', 'VARCHAR(30) DEFAULT ''pending'' COMMENT ''生产状态：pending-待排程，scheduled-已排程，in_production-生产中，partial_completed-部分完成，completed-已完成''');

-- 更新已有数据的remaining_qty（如果有空值）
UPDATE `sales_order_items` SET `remaining_qty` = `rolls` WHERE (`remaining_qty` = 0 OR `remaining_qty` IS NULL) AND `rolls` IS NOT NULL;

-- 2. tape_stock 表添加库存类型字段
CALL add_column_if_not_exists('tape_stock', 'stock_type', 'VARCHAR(20) DEFAULT ''finished'' COMMENT ''库存类型：jumbo-母卷，slit-支料，finished-成品''');
CALL add_column_if_not_exists('tape_stock', 'parent_batch_no', 'VARCHAR(50) COMMENT ''来源批次号（分切/复卷后记录母卷批次）''');
CALL add_column_if_not_exists('tape_stock', 'source_schedule_id', 'BIGINT COMMENT ''来源排程ID''');

-- =====================================================
-- 第二部分：CREATE TABLE（使用 IF NOT EXISTS，安全执行）
-- =====================================================

-- 4. 排程主表
CREATE TABLE IF NOT EXISTS `production_schedule` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `schedule_no` VARCHAR(50) NOT NULL COMMENT '排程单号（PS-YYYYMMDD-XXX）',
    `schedule_date` DATE NOT NULL COMMENT '排程日期',
    `schedule_type` VARCHAR(20) NOT NULL COMMENT '排程类型：order-订单排程，safety-安全库存补货',
    `total_orders` INT DEFAULT 0 COMMENT '涉及订单数',
    `total_items` INT DEFAULT 0 COMMENT '涉及订单明细数',
    `total_sqm` DECIMAL(12,2) DEFAULT 0 COMMENT '总面积(平方米)',
    `status` VARCHAR(20) DEFAULT 'draft' COMMENT '状态：draft-草稿，confirmed-已确认，in_progress-执行中，completed-已完成，cancelled-已取消',
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

-- 5. 排程订单关联表
CREATE TABLE IF NOT EXISTS `schedule_order_item` (
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

-- 6. 涂布计划表
CREATE TABLE IF NOT EXISTS `schedule_coating` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `schedule_id` BIGINT NOT NULL COMMENT '排程ID',
    `task_no` VARCHAR(50) NOT NULL COMMENT '任务单号（CT-YYYYMMDD-XXX）',
    `equipment_id` BIGINT COMMENT '设备ID',
    `equipment_code` VARCHAR(30) COMMENT '设备编号',
    `staff_id` BIGINT COMMENT '操作人员ID',
    `staff_name` VARCHAR(50) COMMENT '操作人员',
    `shift_code` VARCHAR(20) COMMENT '班次',
    `plan_date` DATE COMMENT '计划日期',
    `material_code` VARCHAR(50) NOT NULL COMMENT '产品料号',
    `material_name` VARCHAR(100) COMMENT '产品名称',
    `color_code` VARCHAR(20) COMMENT '颜色代码',
    `color_name` VARCHAR(50) COMMENT '颜色名称',
    `thickness` DECIMAL(10,3) COMMENT '厚度(mm)',
    `plan_length` DECIMAL(12,2) COMMENT '计划涂布长度(米)',
    `plan_sqm` DECIMAL(12,2) COMMENT '计划面积(平方米)',
    `actual_length` DECIMAL(12,2) COMMENT '实际涂布长度(米)',
    `actual_sqm` DECIMAL(12,2) COMMENT '实际面积(平方米)',
    `jumbo_width` INT COMMENT '母卷宽度(mm)',
    `coating_speed` DECIMAL(10,2) COMMENT '涂布速度(米/分钟)',
    `oven_temp` DECIMAL(6,2) COMMENT '烘箱温度(℃)',
    `plan_start_time` DATETIME COMMENT '计划开始时间',
    `plan_end_time` DATETIME COMMENT '计划结束时间',
    `plan_duration` INT COMMENT '计划时长(分钟)',
    `actual_start_time` DATETIME COMMENT '实际开始时间',
    `actual_end_time` DATETIME COMMENT '实际结束时间',
    `actual_duration` INT COMMENT '实际时长(分钟)',
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
    INDEX `idx_status` (`status`),
    INDEX `idx_color_code` (`color_code`),
    INDEX `idx_thickness` (`thickness`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='涂布计划表';

-- 7. 复卷计划表
CREATE TABLE IF NOT EXISTS `schedule_rewinding` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `schedule_id` BIGINT NOT NULL COMMENT '排程ID',
    `task_no` VARCHAR(50) NOT NULL COMMENT '任务单号（RW-YYYYMMDD-XXX）',
    `equipment_id` BIGINT COMMENT '设备ID',
    `equipment_code` VARCHAR(30) COMMENT '设备编号',
    `staff_id` BIGINT COMMENT '操作人员ID',
    `staff_name` VARCHAR(50) COMMENT '操作人员',
    `shift_code` VARCHAR(20) COMMENT '班次',
    `plan_date` DATE COMMENT '计划日期',
    `source_batch_no` VARCHAR(50) COMMENT '来源母卷批次号',
    `source_stock_id` BIGINT COMMENT '来源库存ID',
    `jumbo_width` INT COMMENT '母卷宽度(mm)',
    `jumbo_length` DECIMAL(12,2) COMMENT '母卷长度(米)',
    `material_code` VARCHAR(50) NOT NULL COMMENT '产品料号',
    `material_name` VARCHAR(100) COMMENT '产品名称',
    `thickness` DECIMAL(10,3) COMMENT '厚度(mm)',
    `slit_length` INT COMMENT '支料长度(米/卷)',
    `plan_rolls` INT COMMENT '计划卷数',
    `actual_rolls` INT COMMENT '实际卷数',
    `rewinding_speed` DECIMAL(10,2) COMMENT '复卷速度(米/分钟)',
    `tension` DECIMAL(10,2) COMMENT '张力设定',
    `plan_start_time` DATETIME COMMENT '计划开始时间',
    `plan_end_time` DATETIME COMMENT '计划结束时间',
    `plan_duration` INT COMMENT '计划时长(分钟)',
    `actual_start_time` DATETIME COMMENT '实际开始时间',
    `actual_end_time` DATETIME COMMENT '实际结束时间',
    `actual_duration` INT COMMENT '实际时长(分钟)',
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
    INDEX `idx_status` (`status`),
    INDEX `idx_source_batch_no` (`source_batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='复卷计划表';

-- 8. 分切计划表
CREATE TABLE IF NOT EXISTS `schedule_slitting` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `schedule_id` BIGINT NOT NULL COMMENT '排程ID',
    `task_no` VARCHAR(50) NOT NULL COMMENT '任务单号（SL-YYYYMMDD-XXX）',
    `equipment_id` BIGINT COMMENT '设备ID',
    `equipment_code` VARCHAR(30) COMMENT '设备编号',
    `staff_id` BIGINT COMMENT '操作人员ID',
    `staff_name` VARCHAR(50) COMMENT '操作人员',
    `shift_code` VARCHAR(20) COMMENT '班次',
    `plan_date` DATE COMMENT '计划日期',
    `source_batch_no` VARCHAR(50) COMMENT '来源支料批次号',
    `source_stock_id` BIGINT COMMENT '来源库存ID',
    `slit_width` INT COMMENT '支料宽度(mm)',
    `slit_length` INT COMMENT '支料长度(米)',
    `material_code` VARCHAR(50) NOT NULL COMMENT '产品料号',
    `material_name` VARCHAR(100) COMMENT '产品名称',
    `thickness` DECIMAL(10,3) COMMENT '厚度(mm)',
    `target_width` INT COMMENT '目标宽度(mm)',
    `cuts_per_slit` INT COMMENT '每支料切几条',
    `plan_rolls` INT COMMENT '计划卷数',
    `actual_rolls` INT COMMENT '实际卷数',
    `edge_loss` INT DEFAULT 10 COMMENT '边料损耗(mm)',
    `slitting_speed` DECIMAL(10,2) COMMENT '分切速度(米/分钟)',
    `plan_start_time` DATETIME COMMENT '计划开始时间',
    `plan_end_time` DATETIME COMMENT '计划结束时间',
    `plan_duration` INT COMMENT '计划时长(分钟)',
    `actual_start_time` DATETIME COMMENT '实际开始时间',
    `actual_end_time` DATETIME COMMENT '实际结束时间',
    `actual_duration` INT COMMENT '实际时长(分钟)',
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
    INDEX `idx_status` (`status`),
    INDEX `idx_source_batch_no` (`source_batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分切计划表';

-- 9. 分条计划表
CREATE TABLE IF NOT EXISTS `schedule_stripping` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `schedule_id` BIGINT NOT NULL COMMENT '排程ID',
    `task_no` VARCHAR(50) NOT NULL COMMENT '任务单号（ST-YYYYMMDD-XXX）',
    `equipment_id` BIGINT COMMENT '设备ID',
    `equipment_code` VARCHAR(30) COMMENT '设备编号',
    `staff_id` BIGINT COMMENT '操作人员ID',
    `staff_name` VARCHAR(50) COMMENT '操作人员',
    `shift_code` VARCHAR(20) COMMENT '班次',
    `plan_date` DATE COMMENT '计划日期',
    `source_batch_no` VARCHAR(50) COMMENT '来源母卷批次号',
    `source_stock_id` BIGINT COMMENT '来源库存ID',
    `jumbo_width` INT COMMENT '母卷宽度(mm)',
    `jumbo_length` DECIMAL(12,2) COMMENT '母卷长度(米)',
    `material_code` VARCHAR(50) NOT NULL COMMENT '产品料号',
    `material_name` VARCHAR(100) COMMENT '产品名称',
    `thickness` DECIMAL(10,3) COMMENT '厚度(mm)',
    `target_width` INT COMMENT '目标宽度(mm)',
    `target_length` INT COMMENT '目标长度(米)',
    `cuts_width` INT COMMENT '宽度方向切几条',
    `cuts_length` INT COMMENT '长度方向切几段',
    `plan_rolls` INT COMMENT '计划卷数',
    `actual_rolls` INT COMMENT '实际卷数',
    `stripping_speed` DECIMAL(10,2) COMMENT '分条速度(米/分钟)',
    `plan_start_time` DATETIME COMMENT '计划开始时间',
    `plan_end_time` DATETIME COMMENT '计划结束时间',
    `plan_duration` INT COMMENT '计划时长(分钟)',
    `actual_start_time` DATETIME COMMENT '实际开始时间',
    `actual_end_time` DATETIME COMMENT '实际结束时间',
    `actual_duration` INT COMMENT '实际时长(分钟)',
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
    INDEX `idx_status` (`status`),
    INDEX `idx_source_batch_no` (`source_batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分条计划表';

-- 10. 生产报工表
CREATE TABLE IF NOT EXISTS `production_report` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `report_no` VARCHAR(50) NOT NULL COMMENT '报工单号',
    `task_type` VARCHAR(20) NOT NULL COMMENT '任务类型：COATING/REWINDING/SLITTING/STRIPPING',
    `task_id` BIGINT NOT NULL COMMENT '任务ID',
    `task_no` VARCHAR(50) COMMENT '任务单号',
    `equipment_id` BIGINT COMMENT '设备ID',
    `staff_id` BIGINT NOT NULL COMMENT '报工人员ID',
    `staff_name` VARCHAR(50) COMMENT '报工人员',
    `shift_code` VARCHAR(20) COMMENT '班次',
    `report_date` DATE COMMENT '报工日期',
    `output_qty` INT COMMENT '产出数量(卷)',
    `output_length` DECIMAL(12,2) COMMENT '产出长度(米)',
    `output_sqm` DECIMAL(12,2) COMMENT '产出面积(平方米)',
    `defect_qty` INT DEFAULT 0 COMMENT '不良数量',
    `defect_reason` VARCHAR(200) COMMENT '不良原因',
    `start_time` DATETIME COMMENT '开始时间',
    `end_time` DATETIME COMMENT '结束时间',
    `work_minutes` INT COMMENT '工作时长(分钟)',
    `pause_minutes` INT DEFAULT 0 COMMENT '暂停时长(分钟)',
    `output_batch_no` VARCHAR(50) COMMENT '产出批次号',
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    UNIQUE KEY `uk_report_no` (`report_no`),
    INDEX `idx_task_id` (`task_id`),
    INDEX `idx_task_type` (`task_type`),
    INDEX `idx_staff_id` (`staff_id`),
    INDEX `idx_report_date` (`report_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产报工表';

-- 11. 订单交付记录表
CREATE TABLE IF NOT EXISTS `order_delivery_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `delivery_no` VARCHAR(50) NOT NULL COMMENT '交付单号',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `order_no` VARCHAR(50) COMMENT '订单号',
    `order_item_id` BIGINT NOT NULL COMMENT '订单明细ID',
    `material_code` VARCHAR(50) COMMENT '产品料号',
    `material_name` VARCHAR(100) COMMENT '产品名称',
    `delivery_qty` INT NOT NULL COMMENT '本次交付数量(卷)',
    `delivery_sqm` DECIMAL(12,2) COMMENT '本次交付面积(平方米)',
    `batch_no` VARCHAR(50) COMMENT '出库批次号',
    `stock_id` BIGINT COMMENT '库存ID',
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

-- 12. 生产异常表
CREATE TABLE IF NOT EXISTS `production_exception` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `exception_no` VARCHAR(50) NOT NULL COMMENT '异常单号',
    `task_type` VARCHAR(20) COMMENT '任务类型',
    `task_id` BIGINT COMMENT '任务ID',
    `task_no` VARCHAR(50) COMMENT '任务单号',
    `equipment_id` BIGINT COMMENT '设备ID',
    `equipment_code` VARCHAR(30) COMMENT '设备编号',
    `exception_type` VARCHAR(50) NOT NULL COMMENT '异常类型：equipment-设备故障，material-物料问题，quality-质量问题，other-其他',
    `exception_level` VARCHAR(20) DEFAULT 'normal' COMMENT '异常级别：minor-轻微，normal-一般，major-严重，critical-紧急',
    `exception_desc` TEXT COMMENT '异常描述',
    `occur_time` DATETIME COMMENT '发生时间',
    `discover_by` VARCHAR(50) COMMENT '发现人',
    `stop_minutes` INT DEFAULT 0 COMMENT '停机时长(分钟)',
    `scrap_qty` INT DEFAULT 0 COMMENT '报废数量',
    `scrap_sqm` DECIMAL(12,2) DEFAULT 0 COMMENT '报废面积(平方米)',
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

-- 13. 分切宽度组合表
CREATE TABLE IF NOT EXISTS `slitting_combination` (
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

-- =====================================================
-- 清理临时存储过程
-- =====================================================
DROP PROCEDURE IF EXISTS add_column_if_not_exists;

-- =====================================================
-- 执行完成提示
-- =====================================================
SELECT '✅ SQL脚本执行完成！所有排程相关表已创建或更新。' AS message;
