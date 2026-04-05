-- 客户物料映射表：用于标签打印按客户显示客户料号/客户品名
CREATE TABLE IF NOT EXISTS `customer_material_mapping` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `customer_code` VARCHAR(64) NOT NULL COMMENT '客户代码',
  `material_code` VARCHAR(128) NOT NULL COMMENT '物料代码',
  `thickness` DECIMAL(18,3) NOT NULL COMMENT '厚度(μm)',
  `width` DECIMAL(18,2) NOT NULL COMMENT '宽度(mm)',
  `length` DECIMAL(18,2) NOT NULL COMMENT '长度(m)',
  `customer_thickness` DECIMAL(18,3) DEFAULT NULL COMMENT '客户厚度(μm)',
  `customer_width` DECIMAL(18,2) DEFAULT NULL COMMENT '客户宽度(mm)',
  `customer_length` DECIMAL(18,2) DEFAULT NULL COMMENT '客户长度(m)',
  `customer_material_code` VARCHAR(128) DEFAULT NULL COMMENT '客户料号',
  `customer_material_name` VARCHAR(255) DEFAULT NULL COMMENT '客户物料名称',
  `is_active` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_by` VARCHAR(64) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_by` VARCHAR(64) DEFAULT NULL,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_material_spec` (`customer_code`,`material_code`,`thickness`,`width`,`length`),
  KEY `idx_customer_material` (`customer_code`,`material_code`),
  KEY `idx_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户物料映射表';
