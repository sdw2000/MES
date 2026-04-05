-- 请假管理/加班管理正式表

CREATE TABLE IF NOT EXISTS `production_leave_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `staff_id` BIGINT NOT NULL COMMENT '人员ID',
    `staff_code` VARCHAR(20) NOT NULL COMMENT '工号',
    `staff_name` VARCHAR(50) NOT NULL COMMENT '姓名',
    `leave_type` VARCHAR(20) NOT NULL COMMENT '请假类型：personal/sick/annual/adjust',
    `start_date` DATE NOT NULL COMMENT '开始日期',
    `end_date` DATE NOT NULL COMMENT '结束日期',
    `days` DECIMAL(6,1) NOT NULL DEFAULT 1.0 COMMENT '请假天数',
    `reason` VARCHAR(500) NOT NULL COMMENT '请假原因',
    `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending/approved/rejected',
    `remark` VARCHAR(200) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` VARCHAR(50) COMMENT '创建人',
    `update_by` VARCHAR(50) COMMENT '更新人',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    INDEX `idx_leave_staff_id` (`staff_id`),
    INDEX `idx_leave_status` (`status`),
    INDEX `idx_leave_date` (`start_date`, `end_date`),
    INDEX `idx_leave_deleted` (`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='请假记录表';

CREATE TABLE IF NOT EXISTS `production_overtime_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `staff_id` BIGINT NOT NULL COMMENT '人员ID',
    `staff_code` VARCHAR(20) NOT NULL COMMENT '工号',
    `staff_name` VARCHAR(50) NOT NULL COMMENT '姓名',
    `overtime_date` DATE NOT NULL COMMENT '加班日期',
    `start_time` VARCHAR(5) NOT NULL COMMENT '开始时间(HH:mm)',
    `end_time` VARCHAR(5) NOT NULL COMMENT '结束时间(HH:mm)',
    `hours` DECIMAL(6,1) NOT NULL DEFAULT 1.0 COMMENT '加班时长(小时)',
    `reason` VARCHAR(500) NOT NULL COMMENT '加班原因',
    `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending/approved/rejected',
    `remark` VARCHAR(200) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` VARCHAR(50) COMMENT '创建人',
    `update_by` VARCHAR(50) COMMENT '更新人',
    `is_deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    INDEX `idx_overtime_staff_id` (`staff_id`),
    INDEX `idx_overtime_status` (`status`),
    INDEX `idx_overtime_date` (`overtime_date`),
    INDEX `idx_overtime_deleted` (`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='加班记录表';
