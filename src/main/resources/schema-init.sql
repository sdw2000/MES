-- 确保基础表和数据存在
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

CREATE TABLE IF NOT EXISTS `equipment_type` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `type_code` VARCHAR(20) NOT NULL COMMENT '类型编码',
    `type_name` VARCHAR(50) NOT NULL COMMENT '类型名称',
    `process_order` INT DEFAULT 0 COMMENT '工序顺序（用于排程）',
    `description` VARCHAR(200) COMMENT '描述',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-停用，1-启用',
    UNIQUE KEY `uk_type_code` (`type_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备类型字典';

-- 检查 equipment_type 是否有数据，没有则插入
INSERT IGNORE INTO `equipment_type` (`type_code`, `type_name`, `process_order`, `description`, `status`) VALUES
('PRINTING', '印刷机', 1, '印刷工序（可选）', 1),
('COATING', '涂布机', 2, '涂布工序', 1),
('REWINDING', '复卷机', 3, '复卷工序（切长度）', 1),
('SLITTING', '分切机', 4, '分切工序（切宽度）', 1),
('STRIPPING', '分条机', 5, '分条机（母卷直接切成小卷）', 1);

-- 检查 workshop 是否有数据，没有则插入
INSERT IGNORE INTO `workshop` (`workshop_code`, `workshop_name`, `manager`, `location`, `status`) VALUES
('SHOP001', '第一号车间', '张三', '生产区A', 1),
('SHOP002', '第二号车间', '李四', '生产区B', 1);
