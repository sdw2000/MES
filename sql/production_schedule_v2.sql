-- =====================================================
-- 生产管理系统 - 排程扩展表（第二阶段）
-- 包含：印刷计划、审批流程、质检反馈、紧急插单
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
-- 1. 扩展排程主表 - 添加审批流程字段
-- =====================================================

-- 排程状态扩展: draft → pending_approval → approved → in_progress → completed
CALL add_column_if_not_exists('production_schedule', 'approval_status', 'VARCHAR(20) DEFAULT ''none'' COMMENT ''审批状态：none-无需审批，pending-待审批，approved-已批准，rejected-已驳回''');
CALL add_column_if_not_exists('production_schedule', 'submitted_by', 'VARCHAR(50) COMMENT ''提交人''');
CALL add_column_if_not_exists('production_schedule', 'submitted_time', 'DATETIME COMMENT ''提交审批时间''');
CALL add_column_if_not_exists('production_schedule', 'approved_by', 'VARCHAR(50) COMMENT ''审批人''');
CALL add_column_if_not_exists('production_schedule', 'approved_time', 'DATETIME COMMENT ''审批时间''');
CALL add_column_if_not_exists('production_schedule', 'approval_remark', 'VARCHAR(500) COMMENT ''审批意见''');
CALL add_column_if_not_exists('production_schedule', 'is_urgent', 'TINYINT DEFAULT 0 COMMENT ''是否紧急插单：0-否，1-是''');
CALL add_column_if_not_exists('production_schedule', 'urgent_reason', 'VARCHAR(500) COMMENT ''紧急原因''');
CALL add_column_if_not_exists('production_schedule', 'priority', 'INT DEFAULT 100 COMMENT ''排程优先级（数值越小越优先）''');

-- =====================================================
-- 2. 印刷计划表（涂布前的可选工序）
-- =====================================================
CREATE TABLE IF NOT EXISTS `schedule_printing` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `schedule_id` BIGINT NOT NULL COMMENT '排程ID',
    `task_no` VARCHAR(50) NOT NULL COMMENT '任务单号（PR-YYYYMMDD-XXX）',
    `equipment_id` BIGINT COMMENT '印刷机ID',
    `equipment_code` VARCHAR(30) COMMENT '设备编号',
    `staff_id` BIGINT COMMENT '操作人员ID',
    `staff_name` VARCHAR(50) COMMENT '操作人员',
    `shift_code` VARCHAR(20) COMMENT '班次',
    `plan_date` DATE COMMENT '计划日期',
    `material_code` VARCHAR(50) NOT NULL COMMENT '产品料号',
    `material_name` VARCHAR(100) COMMENT '产品名称',
    `print_type` VARCHAR(30) COMMENT '印刷类型：logo-商标，text-文字，pattern-图案，full-满版印刷',
    `print_color` VARCHAR(50) COMMENT '印刷颜色',
    `print_content` VARCHAR(200) COMMENT '印刷内容',
    `base_width` INT COMMENT '底材宽度(mm)',
    `base_length` DECIMAL(12,2) COMMENT '底材长度(米)',
    `plan_length` DECIMAL(12,2) COMMENT '计划印刷长度(米)',
    `plan_sqm` DECIMAL(12,2) COMMENT '计划面积(平方米)',
    `actual_length` DECIMAL(12,2) COMMENT '实际印刷长度(米)',
    `actual_sqm` DECIMAL(12,2) COMMENT '实际面积(平方米)',
    `print_speed` DECIMAL(10,2) COMMENT '印刷速度(米/分钟)',
    `dry_temp` DECIMAL(6,2) COMMENT '干燥温度(℃)',
    `plan_start_time` DATETIME COMMENT '计划开始时间',
    `plan_end_time` DATETIME COMMENT '计划结束时间',
    `plan_duration` INT COMMENT '计划时长(分钟)',
    `actual_start_time` DATETIME COMMENT '实际开始时间',
    `actual_end_time` DATETIME COMMENT '实际结束时间',
    `actual_duration` INT COMMENT '实际时长(分钟)',
    `status` VARCHAR(20) DEFAULT 'pending' COMMENT '状态：pending-待生产，in_progress-生产中，completed-已完成，cancelled-已取消',
    `output_batch_no` VARCHAR(50) COMMENT '产出批次号',
    `next_process` VARCHAR(20) DEFAULT 'coating' COMMENT '下一工序：coating-涂布',
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
    INDEX `idx_material_code` (`material_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='印刷计划表';

-- =====================================================
-- 3. 质检记录表（品质部质检反馈）
-- =====================================================
CREATE TABLE IF NOT EXISTS `quality_inspection` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `inspection_no` VARCHAR(50) NOT NULL COMMENT '质检单号（QC-YYYYMMDD-XXX）',
    `task_type` VARCHAR(20) NOT NULL COMMENT '任务类型：PRINTING/COATING/REWINDING/SLITTING/STRIPPING',
    `task_id` BIGINT NOT NULL COMMENT '任务ID',
    `task_no` VARCHAR(50) COMMENT '任务单号',
    `batch_no` VARCHAR(50) COMMENT '批次号',
    `material_code` VARCHAR(50) COMMENT '产品料号',
    `material_name` VARCHAR(100) COMMENT '产品名称',
    `inspection_type` VARCHAR(30) NOT NULL COMMENT '质检类型：first-首检，process-过程检，final-完工检，sampling-抽检',
    `sample_qty` INT DEFAULT 1 COMMENT '抽样数量',
    `inspector_id` BIGINT COMMENT '质检员ID',
    `inspector_name` VARCHAR(50) COMMENT '质检员',
    `inspection_time` DATETIME COMMENT '质检时间',
    
    -- 外观检验项
    `appearance_result` VARCHAR(20) COMMENT '外观结果：pass-合格，fail-不合格，conditional-有条件放行',
    `appearance_desc` VARCHAR(500) COMMENT '外观描述',
    
    -- 尺寸检验项
    `thickness_actual` DECIMAL(10,3) COMMENT '实测厚度(mm)',
    `thickness_result` VARCHAR(20) COMMENT '厚度结果：pass/fail',
    `width_actual` DECIMAL(10,2) COMMENT '实测宽度(mm)',
    `width_result` VARCHAR(20) COMMENT '宽度结果：pass/fail',
    `length_actual` DECIMAL(10,2) COMMENT '实测长度(mm)',
    `length_result` VARCHAR(20) COMMENT '长度结果：pass/fail',
    
    -- 性能检验项
    `adhesion_result` VARCHAR(20) COMMENT '粘性测试结果：pass/fail',
    `adhesion_value` DECIMAL(10,2) COMMENT '粘性值',
    `tensile_result` VARCHAR(20) COMMENT '拉伸测试结果：pass/fail',
    `tensile_value` DECIMAL(10,2) COMMENT '拉伸值',
    `elongation_result` VARCHAR(20) COMMENT '伸长率测试结果：pass/fail',
    `elongation_value` DECIMAL(5,2) COMMENT '伸长率(%)',
    
    -- 综合结论
    `overall_result` VARCHAR(20) NOT NULL COMMENT '综合结论：pass-合格，fail-不合格，conditional-有条件放行',
    `defect_type` VARCHAR(100) COMMENT '缺陷类型',
    `defect_desc` TEXT COMMENT '缺陷描述',
    `defect_qty` INT DEFAULT 0 COMMENT '不良数量',
    `pass_qty` INT DEFAULT 0 COMMENT '合格数量',
    
    -- 处理措施
    `handle_method` VARCHAR(50) COMMENT '处理方式：rework-返工，scrap-报废，downgrade-降级，accept-特采',
    `handle_desc` VARCHAR(500) COMMENT '处理说明',
    `handle_by` VARCHAR(50) COMMENT '处理人',
    `handle_time` DATETIME COMMENT '处理时间',
    
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` VARCHAR(50) COMMENT '创建人',
    
    UNIQUE KEY `uk_inspection_no` (`inspection_no`),
    INDEX `idx_task_id` (`task_id`),
    INDEX `idx_task_type` (`task_type`),
    INDEX `idx_batch_no` (`batch_no`),
    INDEX `idx_inspection_type` (`inspection_type`),
    INDEX `idx_overall_result` (`overall_result`),
    INDEX `idx_inspection_time` (`inspection_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='质检记录表';

-- =====================================================
-- 4. 排程审批记录表
-- =====================================================
CREATE TABLE IF NOT EXISTS `schedule_approval_log` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `schedule_id` BIGINT NOT NULL COMMENT '排程ID',
    `schedule_no` VARCHAR(50) COMMENT '排程单号',
    `action` VARCHAR(20) NOT NULL COMMENT '操作：submit-提交，approve-批准，reject-驳回，withdraw-撤回',
    `from_status` VARCHAR(20) COMMENT '原状态',
    `to_status` VARCHAR(20) COMMENT '新状态',
    `operator_id` BIGINT COMMENT '操作人ID',
    `operator_name` VARCHAR(50) COMMENT '操作人',
    `remark` VARCHAR(500) COMMENT '备注/意见',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    
    INDEX `idx_schedule_id` (`schedule_id`),
    INDEX `idx_action` (`action`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排程审批记录表';

-- =====================================================
-- 5. 紧急插单记录表
-- =====================================================
CREATE TABLE IF NOT EXISTS `urgent_order_log` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `schedule_id` BIGINT NOT NULL COMMENT '紧急排程ID',
    `schedule_no` VARCHAR(50) COMMENT '排程单号',
    `order_id` BIGINT COMMENT '关联订单ID',
    `order_no` VARCHAR(50) COMMENT '订单号',
    `customer` VARCHAR(200) COMMENT '客户',
    `urgent_level` VARCHAR(20) DEFAULT 'high' COMMENT '紧急程度：normal-一般，high-紧急，critical-特急',
    `urgent_reason` VARCHAR(500) COMMENT '紧急原因',
    `original_delivery_date` DATE COMMENT '原交货日期',
    `new_delivery_date` DATE COMMENT '新交货日期',
    `affected_schedules` TEXT COMMENT '受影响的排程ID列表（JSON）',
    `affected_orders` TEXT COMMENT '受影响的订单ID列表（JSON）',
    `adjustment_desc` TEXT COMMENT '调整说明',
    `applicant_id` BIGINT COMMENT '申请人ID',
    `applicant_name` VARCHAR(50) COMMENT '申请人',
    `apply_time` DATETIME COMMENT '申请时间',
    `approver_id` BIGINT COMMENT '审批人ID',
    `approver_name` VARCHAR(50) COMMENT '审批人',
    `approve_time` DATETIME COMMENT '审批时间',
    `status` VARCHAR(20) DEFAULT 'pending' COMMENT '状态：pending-待审批，approved-已批准，rejected-已驳回，executed-已执行',
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    INDEX `idx_schedule_id` (`schedule_id`),
    INDEX `idx_order_id` (`order_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_urgent_level` (`urgent_level`),
    INDEX `idx_apply_time` (`apply_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='紧急插单记录表';

-- =====================================================
-- 6. 涂布计划表 - 添加印刷关联字段
-- =====================================================
CALL add_column_if_not_exists('schedule_coating', 'printing_task_id', 'BIGINT COMMENT ''关联印刷任务ID（如果需要印刷）''');
CALL add_column_if_not_exists('schedule_coating', 'need_printing', 'TINYINT DEFAULT 0 COMMENT ''是否需要印刷：0-否，1-是''');

-- =====================================================
-- 7. 生产报工表 - 添加质检关联字段
-- =====================================================
CALL add_column_if_not_exists('production_report', 'inspection_id', 'BIGINT COMMENT ''关联质检记录ID''');
CALL add_column_if_not_exists('production_report', 'inspection_status', 'VARCHAR(20) DEFAULT ''pending'' COMMENT ''质检状态：pending-待检，passed-已通过，failed-不合格''');

-- =====================================================
-- 清理临时存储过程
-- =====================================================
DROP PROCEDURE IF EXISTS add_column_if_not_exists;

-- =====================================================
-- 执行完成提示
-- =====================================================
SELECT '✅ SQL脚本执行完成！印刷计划、质检反馈、审批流程、紧急插单相关表已创建。' AS message;
