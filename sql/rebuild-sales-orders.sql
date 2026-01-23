-- =====================================================
-- 重建销售订单系统数据库表
-- 执行时间: 2026-01-08
-- 注意：此脚本会删除现有数据！
-- =====================================================

-- 先删除明细表（因为有外键约束）
DROP TABLE IF EXISTS `sales_order_items`;

-- 再删除主表
DROP TABLE IF EXISTS `sales_orders`;

-- =====================================================
-- 1. 创建销售订单主表
-- =====================================================
CREATE TABLE `sales_orders` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` VARCHAR(50) NOT NULL COMMENT '订单号（系统生成，格式：SO-YYYYMMDD-XXX）',
  `customer` VARCHAR(200) NOT NULL COMMENT '客户名称',
  `customer_order_no` VARCHAR(50) DEFAULT NULL COMMENT '客户订单号',
  `total_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '总金额（元）',
  `total_area` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '总面积（平方米）',
  `order_date` DATE NOT NULL COMMENT '下单日期',
  `delivery_date` DATE DEFAULT NULL COMMENT '交货日期',
  `delivery_address` VARCHAR(500) DEFAULT NULL COMMENT '送货地址',
  `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '订单状态：pending-待处理，processing-处理中，completed-已完成，cancelled-已取消',
  `remark` TEXT DEFAULT NULL COMMENT '备注',
  `created_by` VARCHAR(50) DEFAULT NULL COMMENT '创建人',
  `updated_by` VARCHAR(50) DEFAULT NULL COMMENT '更新人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_customer` (`customer`),
  KEY `idx_order_date` (`order_date`),
  KEY `idx_status` (`status`),
  KEY `idx_is_deleted` (`is_deleted`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售订单主表';

-- =====================================================
-- 2. 创建销售订单明细表
-- =====================================================
CREATE TABLE `sales_order_items` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` BIGINT(20) NOT NULL COMMENT '关联的订单ID',
  `material_code` VARCHAR(50) NOT NULL COMMENT '产品编码/物料代码',
  `material_name` VARCHAR(200) NOT NULL COMMENT '产品名称/物料名称',
  `thickness` DECIMAL(10,6) DEFAULT NULL COMMENT '厚度（毫米，存储时已从μm转换）',
  `width` DECIMAL(10,2) NOT NULL COMMENT '宽度（毫米）',
  `length` DECIMAL(12,2) NOT NULL COMMENT '长度（毫米，存储时已从米转换）',
  `rolls` INT(11) NOT NULL COMMENT '卷数',
  `sqm` DECIMAL(12,2) DEFAULT 0.00 COMMENT '平方米数（计算得出）',
  `unit_price` DECIMAL(10,2) DEFAULT 0.00 COMMENT '单价（每平方米）',
  `amount` DECIMAL(12,2) DEFAULT 0.00 COMMENT '金额（计算得出）',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `created_by` VARCHAR(50) DEFAULT NULL COMMENT '创建人',
  `updated_by` VARCHAR(50) DEFAULT NULL COMMENT '更新人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0-未删除，1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_material_code` (`material_code`),
  KEY `idx_is_deleted` (`is_deleted`),
  CONSTRAINT `fk_order_items_order` FOREIGN KEY (`order_id`) REFERENCES `sales_orders` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售订单明细表';

-- =====================================================
-- 3. 插入示例数据（可选）
-- =====================================================

-- 示例订单1：单个明细
INSERT INTO `sales_orders` 
(`order_no`, `customer`, `customer_order_no`, `total_amount`, `total_area`, `order_date`, `delivery_date`, `delivery_address`, `status`, `remark`, `created_by`, `updated_by`) 
VALUES 
('SO-20260108-001', '广州胶带有限公司', 'GZ-2026-001', 12500.00, 500.00, '2026-01-08', '2026-01-18', '广州市天河区XXX路XXX号', 'pending', '测试订单1', 'admin', 'admin');

SET @order_id_1 = LAST_INSERT_ID();

INSERT INTO `sales_order_items` 
(`order_id`, `material_code`, `material_name`, `thickness`, `width`, `length`, `rolls`, `sqm`, `unit_price`, `amount`, `created_by`, `updated_by`) 
VALUES 
(@order_id_1, '201-R015-2525-C03-0500', '50μm茶色PI极耳胶带', 0.050, 500, 100000, 5, 250.00, 50.00, 12500.00, 'admin', 'admin');

-- 示例订单2：多个明细
INSERT INTO `sales_orders` 
(`order_no`, `customer`, `customer_order_no`, `total_amount`, `total_area`, `order_date`, `delivery_date`, `delivery_address`, `status`, `remark`, `created_by`, `updated_by`) 
VALUES 
('SO-20260108-002', '深圳科技有限公司', 'SZ-2026-002', 25000.00, 1000.00, '2026-01-08', '2026-01-20', '深圳市南山区XXX路XXX号', 'pending', '测试订单2-多明细', 'admin', 'admin');

SET @order_id_2 = LAST_INSERT_ID();

INSERT INTO `sales_order_items` 
(`order_id`, `material_code`, `material_name`, `thickness`, `width`, `length`, `rolls`, `sqm`, `unit_price`, `amount`, `created_by`, `updated_by`) 
VALUES 
(@order_id_2, '102-R00-2515-W01-0400', '白色把手胶带', 0.040, 1040, 66000, 10, 686.40, 20.00, 13728.00, 'admin', 'admin'),
(@order_id_2, '1011-R02-2010-G01-0350', '30um翠绿PET终止胶带', 0.030, 10, 300000, 12, 36.00, 25.00, 900.00, 'admin', 'admin'),
(@order_id_2, '1011-R02-2010-G01-0350H', '30um翠绿数字PET终止胶带', 0.030, 20, 500000, 20, 200.00, 30.00, 6000.00, 'admin', 'admin');

-- =====================================================
-- 4. 验证结果
-- =====================================================
SELECT '===== 订单主表 =====' AS info;
SELECT id, order_no, customer, total_amount, total_area, order_date FROM sales_orders WHERE is_deleted = 0;

SELECT '===== 订单明细表 =====' AS info;
SELECT id, order_id, material_code, material_name, thickness, width, length, rolls, sqm, unit_price, amount 
FROM sales_order_items WHERE is_deleted = 0;

SELECT '===== 重建完成 =====' AS info;
