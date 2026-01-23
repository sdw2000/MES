-- ========================================
-- 送样管理功能 - 数据库表结构
-- 创建日期: 2026-01-05
-- ========================================

USE erp;

-- 1. 送样主表
DROP TABLE IF EXISTS `sample_orders`;
CREATE TABLE `sample_orders` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `sample_no` VARCHAR(50) NOT NULL COMMENT '送样编号 格式：SP20260105001',
  
  -- 客户信息（关联客户表）
  `customer_id` BIGINT(20) NOT NULL COMMENT '客户ID',
  `customer_name` VARCHAR(200) NOT NULL COMMENT '客户名称',
  
  -- 联系人信息
  `contact_name` VARCHAR(100) NOT NULL COMMENT '联系人姓名',
  `contact_phone` VARCHAR(50) NOT NULL COMMENT '联系电话',
  `contact_address` VARCHAR(500) NOT NULL COMMENT '收货地址',
  
  -- 送样信息
  `send_date` DATE NOT NULL COMMENT '送样日期',
  `expected_feedback_date` DATE DEFAULT NULL COMMENT '期望反馈日期',
  
  -- 物流信息
  `express_company` VARCHAR(100) DEFAULT NULL COMMENT '快递公司：顺丰、圆通、中通、申通、韵达、邮政EMS、其他',
  `tracking_number` VARCHAR(100) DEFAULT NULL COMMENT '快递单号',
  `ship_date` DATE DEFAULT NULL COMMENT '发货日期',
  `delivery_date` DATE DEFAULT NULL COMMENT '送达日期',
    -- 状态管理（物流状态自动查询更新）
  `status` VARCHAR(20) NOT NULL DEFAULT '待发货' COMMENT '状态：待发货、已发货、运输中、已签收、已拒收、已取消',
  `logistics_status` VARCHAR(50) DEFAULT NULL COMMENT '物流状态（快递公司返回的状态）',
  `last_logistics_query_time` DATETIME DEFAULT NULL COMMENT '最后一次物流查询时间',
  
  -- 统计信息
  `total_quantity` INT(11) DEFAULT 0 COMMENT '总数量（统计明细）',
  
  -- 反馈信息
  `customer_feedback` TEXT DEFAULT NULL COMMENT '客户反馈',
  `feedback_date` DATE DEFAULT NULL COMMENT '反馈日期',
  `is_satisfied` TINYINT(1) DEFAULT NULL COMMENT '是否满意：0-否，1-是',
  
  -- 转订单
  `converted_to_order` TINYINT(1) DEFAULT 0 COMMENT '是否已转订单：0-否，1-是',
  `order_no` VARCHAR(50) DEFAULT NULL COMMENT '关联订单号',
  
  -- 备注
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `internal_note` VARCHAR(500) DEFAULT NULL COMMENT '内部备注（客户不可见）',
  
  -- 系统字段
  `create_by` VARCHAR(50) DEFAULT NULL COMMENT '创建人',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` VARCHAR(50) DEFAULT NULL COMMENT '更新人',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT(1) DEFAULT 0 COMMENT '是否删除：0-否，1-是',
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sample_no` (`sample_no`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_customer_name` (`customer_name`),
  KEY `idx_status` (`status`),
  KEY `idx_send_date` (`send_date`),
  KEY `idx_tracking_number` (`tracking_number`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='送样订单主表';

-- 2. 送样明细表
DROP TABLE IF EXISTS `sample_items`;
CREATE TABLE `sample_items` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `sample_no` VARCHAR(50) NOT NULL COMMENT '送样编号',
  
  -- 产品信息
  `material_code` VARCHAR(50) DEFAULT NULL COMMENT '物料代码',
  `material_name` VARCHAR(200) NOT NULL COMMENT '物料名称/产品名称',
  `specification` VARCHAR(200) DEFAULT NULL COMMENT '规格',
  `model` VARCHAR(100) DEFAULT NULL COMMENT '型号',
  `batch_no` VARCHAR(100) DEFAULT NULL COMMENT '批次号',
  
  -- 尺寸信息（可选，从产品库带出）
  `length` DECIMAL(10,2) DEFAULT NULL COMMENT '长度(mm)',
  `width` DECIMAL(10,2) DEFAULT NULL COMMENT '宽度(mm)',
  `thickness` DECIMAL(10,3) DEFAULT NULL COMMENT '厚度(mm)',
  
  -- 数量信息（不需要单价、平米数、金额）
  `quantity` INT(11) NOT NULL DEFAULT 1 COMMENT '数量/卷数',
  `unit` VARCHAR(20) DEFAULT '卷' COMMENT '单位：卷、个、片、米等',
  
  -- 备注（在明细行中）
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  
  -- 系统字段
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  
  PRIMARY KEY (`id`),
  KEY `idx_sample_no` (`sample_no`),
  KEY `idx_material_code` (`material_code`),
  CONSTRAINT `fk_sample_items_sample_no` FOREIGN KEY (`sample_no`) 
    REFERENCES `sample_orders` (`sample_no`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='送样明细表';

-- 3. 送样状态历史表
DROP TABLE IF EXISTS `sample_status_history`;
CREATE TABLE `sample_status_history` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `sample_no` VARCHAR(50) NOT NULL COMMENT '送样编号',
  `old_status` VARCHAR(20) DEFAULT NULL COMMENT '原状态',
  `new_status` VARCHAR(20) NOT NULL COMMENT '新状态',
  `change_reason` VARCHAR(500) DEFAULT NULL COMMENT '变更原因',
  `change_source` VARCHAR(20) DEFAULT 'MANUAL' COMMENT '变更来源：MANUAL-手动，AUTO-自动（物流查询）',
  `operator` VARCHAR(50) DEFAULT NULL COMMENT '操作人',
  `change_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '变更时间',
  
  PRIMARY KEY (`id`),
  KEY `idx_sample_no` (`sample_no`),
  KEY `idx_change_time` (`change_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='送样状态历史表';

-- 4. 物流查询记录表（用于缓存物流信息，减少API调用）
DROP TABLE IF EXISTS `sample_logistics_records`;
CREATE TABLE `sample_logistics_records` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `sample_no` VARCHAR(50) NOT NULL COMMENT '送样编号',
  `tracking_number` VARCHAR(100) NOT NULL COMMENT '快递单号',
  `express_company` VARCHAR(100) NOT NULL COMMENT '快递公司',
  
  -- 物流信息
  `logistics_status` VARCHAR(50) DEFAULT NULL COMMENT '物流状态',
  `logistics_info` TEXT DEFAULT NULL COMMENT '物流详细信息（JSON格式）',
  `is_signed` TINYINT(1) DEFAULT 0 COMMENT '是否已签收：0-否，1-是',
  
  -- 查询信息
  `query_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '查询时间',
  `query_success` TINYINT(1) DEFAULT 1 COMMENT '查询是否成功：0-失败，1-成功',
  `error_message` VARCHAR(500) DEFAULT NULL COMMENT '错误信息',
  
  PRIMARY KEY (`id`),
  KEY `idx_sample_no` (`sample_no`),
  KEY `idx_tracking_number` (`tracking_number`),
  KEY `idx_query_time` (`query_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物流查询记录表';

-- 插入初始数据（测试用）
-- 注意：需要先有客户数据，这里假设客户ID为1的客户已存在
-- INSERT INTO `sample_orders` (`sample_no`, `customer_id`, `customer_name`, `contact_name`, `contact_phone`, `contact_address`, `send_date`, `status`)
-- VALUES ('SP20260105001', 1, '测试客户', '张三', '13800138000', '广东省深圳市南山区科技园', '2026-01-05', '待发货');

-- 创建编号生成函数（可选，也可以在Java代码中生成）
DELIMITER $$
DROP FUNCTION IF EXISTS generate_sample_no$$
CREATE FUNCTION generate_sample_no()
RETURNS VARCHAR(50)
DETERMINISTIC
BEGIN
  DECLARE new_no VARCHAR(50);
  DECLARE date_prefix VARCHAR(8);
  DECLARE seq INT;
  
  SET date_prefix = DATE_FORMAT(NOW(), 'SP%Y%m%d');
  
  SELECT IFNULL(MAX(CAST(SUBSTRING(sample_no, 11) AS UNSIGNED)), 0) + 1 INTO seq
  FROM sample_orders
  WHERE sample_no LIKE CONCAT(date_prefix, '%')
    AND is_deleted = 0;
  
  SET new_no = CONCAT(date_prefix, LPAD(seq, 3, '0'));
  
  RETURN new_no;
END$$
DELIMITER ;

-- 测试编号生成
-- SELECT generate_sample_no() AS new_sample_no;

-- 授权（根据实际情况调整）
-- GRANT SELECT, INSERT, UPDATE, DELETE ON erp_system.sample_orders TO 'your_user'@'localhost';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON erp_system.sample_items TO 'your_user'@'localhost';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON erp_system.sample_status_history TO 'your_user'@'localhost';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON erp_system.sample_logistics_records TO 'your_user'@'localhost';

-- 查看创建的表
SHOW TABLES LIKE 'sample%';

-- 查看表结构
DESC sample_orders;
DESC sample_items;
DESC sample_status_history;
DESC sample_logistics_records;

COMMIT;
