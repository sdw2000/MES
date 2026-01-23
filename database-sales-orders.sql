-- =====================================================
-- 销售订单系统数据库表结构
-- =====================================================

-- 先删除明细表（因为有外键约束指向主表）
DROP TABLE IF EXISTS `sales_order_items`;

-- 再删除主表
DROP TABLE IF EXISTS `sales_orders`;

-- 1. 销售订单主表
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
  KEY `idx_is_deleted` (`is_deleted`),  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售订单主表';

-- 2. 销售订单明细表
CREATE TABLE `sales_order_items` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` BIGINT(20) NOT NULL COMMENT '关联的订单ID',
  `material_code` VARCHAR(50) NOT NULL COMMENT '物料代码',
  `material_name` VARCHAR(200) NOT NULL COMMENT '物料名称',
  `length` DECIMAL(10,2) NOT NULL COMMENT '长度（毫米）',
  `width` DECIMAL(10,2) NOT NULL COMMENT '宽度（毫米）',
  `thickness` DECIMAL(10,3) DEFAULT NULL COMMENT '厚度（毫米）',
  `rolls` INT(11) NOT NULL COMMENT '卷数',
  `sqm` DECIMAL(12,2) NOT NULL COMMENT '平方米数（计算得出）',
  `unit_price` DECIMAL(10,2) NOT NULL COMMENT '单价（每平方米）',
  `amount` DECIMAL(12,2) NOT NULL COMMENT '金额（计算得出）',
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
