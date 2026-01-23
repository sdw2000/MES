-- ========================================
-- 客户管理功能 - 数据库表结构
-- 创建日期: 2026-01-06
-- 编号规则: 手动前缀 + 自动序号 (例如: ALB001)
-- ========================================

USE erp;

-- ========================================
-- 1. 客户主表 (customers)
-- ========================================
DROP TABLE IF EXISTS `customers`;
CREATE TABLE `customers` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  
  -- 基本信息
  `customer_code` VARCHAR(50) NOT NULL COMMENT '客户编码（唯一）格式：前缀+序号 如ALB001',
  `customer_name` VARCHAR(200) NOT NULL COMMENT '客户名称（公司全称）',
  `short_name` VARCHAR(100) DEFAULT NULL COMMENT '客户简称',
  `customer_type` VARCHAR(20) NOT NULL DEFAULT '企业客户' COMMENT '客户类型：企业客户、个人客户',
  `customer_level` VARCHAR(20) NOT NULL DEFAULT 'C级客户' COMMENT '客户等级：A级客户、B级客户、C级客户、潜在客户',
  `industry` VARCHAR(100) DEFAULT NULL COMMENT '所属行业',
  
  -- 企业信息
  `tax_number` VARCHAR(50) DEFAULT NULL COMMENT '纳税人识别号',
  `legal_person` VARCHAR(50) DEFAULT NULL COMMENT '法人代表',
  `registered_capital` DECIMAL(15,2) DEFAULT NULL COMMENT '注册资本（万元）',
  `registered_address` VARCHAR(500) DEFAULT NULL COMMENT '注册地址',
  `business_address` VARCHAR(500) DEFAULT NULL COMMENT '经营地址',
  `contact_address` VARCHAR(500) DEFAULT NULL COMMENT '联系地址（默认收货地址）',
  `business_scope` TEXT DEFAULT NULL COMMENT '经营范围',
  `credit_code` VARCHAR(50) DEFAULT NULL COMMENT '统一社会信用代码',
  
  -- 联系信息
  `company_phone` VARCHAR(50) DEFAULT NULL COMMENT '公司电话',
  `company_fax` VARCHAR(50) DEFAULT NULL COMMENT '公司传真',
  `company_email` VARCHAR(100) DEFAULT NULL COMMENT '公司邮箱',
  `website` VARCHAR(200) DEFAULT NULL COMMENT '公司网站',
  
  -- 财务信息
  `credit_limit` DECIMAL(15,2) DEFAULT 0.00 COMMENT '信用额度（元）',
  `payment_terms` VARCHAR(50) DEFAULT '现款现货' COMMENT '付款条件：现款现货、货到付款、月结30天、月结60天、预付30%',
  `tax_rate` DECIMAL(5,2) DEFAULT 13.00 COMMENT '税率(%)',
  `bank_name` VARCHAR(100) DEFAULT NULL COMMENT '开户银行',
  `bank_account` VARCHAR(50) DEFAULT NULL COMMENT '银行账号',
  
  -- 销售信息
  `source` VARCHAR(50) DEFAULT NULL COMMENT '客户来源：网络推广、老客户介绍、展会、电话营销、其他',
  `sales_person` VARCHAR(50) DEFAULT NULL COMMENT '销售负责人',
  `sales_department` VARCHAR(50) DEFAULT NULL COMMENT '所属销售部门',
  
  -- 状态管理
  `status` VARCHAR(20) NOT NULL DEFAULT '正常' COMMENT '状态：正常、冻结、黑名单',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  
  -- 系统字段
  `create_by` VARCHAR(50) DEFAULT NULL COMMENT '创建人',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` VARCHAR(50) DEFAULT NULL COMMENT '更新人',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT(1) DEFAULT 0 COMMENT '是否删除：0-否，1-是',
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_code` (`customer_code`),
  KEY `idx_customer_name` (`customer_name`),
  KEY `idx_short_name` (`short_name`),
  KEY `idx_customer_type` (`customer_type`),
  KEY `idx_customer_level` (`customer_level`),
  KEY `idx_status` (`status`),
  KEY `idx_sales_person` (`sales_person`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户主表';

-- ========================================
-- 2. 客户联系人表 (customer_contacts)
-- ========================================
DROP TABLE IF EXISTS `customer_contacts`;
CREATE TABLE `customer_contacts` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `customer_id` BIGINT(20) NOT NULL COMMENT '客户ID（外键）',
  
  -- 基本信息
  `contact_name` VARCHAR(50) NOT NULL COMMENT '联系人姓名',
  `contact_gender` VARCHAR(10) DEFAULT NULL COMMENT '性别：男、女',
  `contact_position` VARCHAR(50) DEFAULT NULL COMMENT '职位',
  `contact_department` VARCHAR(50) DEFAULT NULL COMMENT '所属部门',
  
  -- 联系方式
  `contact_phone` VARCHAR(50) NOT NULL COMMENT '联系电话（固定电话或手机）',
  `contact_mobile` VARCHAR(50) DEFAULT NULL COMMENT '手机号码',
  `contact_email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `contact_wechat` VARCHAR(50) DEFAULT NULL COMMENT '微信号',
  `contact_qq` VARCHAR(20) DEFAULT NULL COMMENT 'QQ号',
  `contact_address` VARCHAR(500) DEFAULT NULL COMMENT '联系地址',
  
  -- 标记信息
  `is_primary` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否主联系人：0-否，1-是',
  `is_decision_maker` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否决策人：0-否，1-是',
  
  -- 个人信息
  `birthday` DATE DEFAULT NULL COMMENT '生日',
  `hobby` VARCHAR(200) DEFAULT NULL COMMENT '爱好',
  
  -- 其他
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `sort_order` INT(11) DEFAULT 0 COMMENT '排序（数字越小越靠前）',
  
  -- 系统字段
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  
  PRIMARY KEY (`id`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_contact_name` (`contact_name`),
  KEY `idx_contact_phone` (`contact_phone`),
  KEY `idx_is_primary` (`is_primary`),
  CONSTRAINT `fk_customer_contacts_customer_id` 
    FOREIGN KEY (`customer_id`) 
    REFERENCES `customers` (`id`) 
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户联系人表';

-- ========================================
-- 3. 客户编号序列表 (用于生成编号)
-- ========================================
DROP TABLE IF EXISTS `customer_code_sequence`;
CREATE TABLE `customer_code_sequence` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `prefix` VARCHAR(10) NOT NULL COMMENT '客户编号前缀（如ALB、TX等）',
  `current_number` INT(11) NOT NULL DEFAULT 0 COMMENT '当前序号',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prefix` (`prefix`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户编号序列表';

-- ========================================
-- 查询验证
-- ========================================
SELECT '✅ 客户管理表创建完成！' AS message;
SELECT TABLE_NAME, TABLE_COMMENT, TABLE_ROWS 
FROM information_schema.TABLES 
WHERE TABLE_SCHEMA = 'erp' 
  AND TABLE_NAME IN ('customers', 'customer_contacts', 'customer_code_sequence')
ORDER BY TABLE_NAME;
