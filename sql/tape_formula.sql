-- ================================================
-- 配胶标准单管理表（研发管理）
-- 用于维护胶带产品的胶水配方
-- ================================================

-- 配胶标准单主表（与料号一一对应）
CREATE TABLE IF NOT EXISTS `tape_formula` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `material_code` VARCHAR(50) NOT NULL COMMENT '产品料号（关联tape_spec）',
    `product_name` VARCHAR(100) COMMENT '产品名称',
    `formula_no` VARCHAR(50) COMMENT '文件编号',
    `version` VARCHAR(20) DEFAULT 'A/0' COMMENT '版次',
    `create_date` DATE COMMENT '制定日期',
    
    -- 胶水信息
    `glue_model` VARCHAR(50) COMMENT '胶水型号',
    `color_code` VARCHAR(20) COMMENT '颜色代码',
    `coating_thickness` DECIMAL(10,2) COMMENT '涂胶厚度(μm)',
    `glue_density` DECIMAL(10,3) COMMENT '胶水密度(g/cm³)',
    `solid_content` VARCHAR(20) COMMENT '固含量(%)',
    `coating_area` DECIMAL(15,2) COMMENT '涂布数量(㎡)',
    
    -- 工艺参数（备注）
    `process_remark` TEXT COMMENT '工艺备注（温度、速度等）',
    
    -- 总重量
    `total_weight` DECIMAL(15,4) COMMENT '总重量(kg)',
    
    -- 审批信息
    `prepared_by` VARCHAR(50) COMMENT '编制人',
    `reviewed_by` VARCHAR(50) COMMENT '审核人',
    `approved_by` VARCHAR(50) COMMENT '批准人',
    
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0=禁用, 1=启用',
    `remark` VARCHAR(500) COMMENT '备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` VARCHAR(50) COMMENT '创建人',
    `update_by` VARCHAR(50) COMMENT '更新人',
    
    UNIQUE KEY `uk_material_code` (`material_code`),
    INDEX `idx_glue_model` (`glue_model`),
    INDEX `idx_formula_no` (`formula_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配胶标准单主表';

-- 配胶原料明细表（一个配方对应多个原料）
CREATE TABLE IF NOT EXISTS `tape_formula_item` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `formula_id` BIGINT NOT NULL COMMENT '配方主表ID',
    `material_code` VARCHAR(50) NOT NULL COMMENT '物料代码',
    `material_name` VARCHAR(100) COMMENT '物料名称',
    `weight` DECIMAL(15,4) COMMENT '重量(Kg/桶)',
    `ratio` DECIMAL(10,4) COMMENT '比例(%)',
    `remark` VARCHAR(200) COMMENT '备注（如稀释说明）',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    
    INDEX `idx_formula_id` (`formula_id`),
    CONSTRAINT `fk_formula_item_formula` FOREIGN KEY (`formula_id`) REFERENCES `tape_formula` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配胶原料明细表';

-- 原料字典表（用于下拉选择）
CREATE TABLE IF NOT EXISTS `tape_raw_material` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `material_code` VARCHAR(50) NOT NULL COMMENT '物料代码',
    `material_name` VARCHAR(100) COMMENT '物料名称',
    `material_type` VARCHAR(20) COMMENT '物料类型: resin=树脂, solvent=溶剂, additive=助剂, curing=固化剂',
    `unit` VARCHAR(20) DEFAULT 'Kg' COMMENT '单位',
    `spec` VARCHAR(100) COMMENT '规格说明',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `status` TINYINT DEFAULT 1 COMMENT '状态',
    
    UNIQUE KEY `uk_material_code` (`material_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配胶原料字典表';

-- 插入原料字典示例数据
INSERT INTO `tape_raw_material` (`material_code`, `material_name`, `material_type`, `spec`, `sort_order`) VALUES
('YKLJ0801', '主树脂YKLJ0801', 'resin', '亚克力树脂', 1),
('YSYZ0201', '溶剂YSYZ0201', 'solvent', '溶剂', 2),
('G728-UJ', 'G728-UJ', 'additive', '助剂', 3),
('9002', '固化剂9002', 'curing', '固化剂', 4),
('FY-45', '固化剂FY-45', 'curing', '固化剂', 5),
('AJSZ0101', '助剂AJSZ0101', 'additive', '需用甲苯稀释20%', 6),
('DJBHS0301', '助剂DJBHS0301', 'additive', '需用异丙醇稀释10%', 7)
ON DUPLICATE KEY UPDATE `material_name` = VALUES(`material_name`);

-- 插入示例配方数据
INSERT INTO `tape_formula` (
    `material_code`, `product_name`, `formula_no`, `version`, `create_date`,
    `glue_model`, `color_code`, `coating_thickness`, `glue_density`, `solid_content`, `coating_area`,
    `process_remark`, `total_weight`, `status`
) VALUES (
    '1011-R02-1204-G01-0300', 
    '16μm翠绿PET终止胶带', 
    '107', 
    'A/0', 
    '2025-12-08',
    'YKLJ0801G01040300', 
    'G01', 
    5, 
    1.1, 
    '15±2', 
    24000,
    '3~5N/25mm，温度：70 80 120 120 120 90 80 70，0.2%隔离剂，上5μ干胶，总厚度做到16.5~17.5μ，速度40m',
    132.0880,
    1
) ON DUPLICATE KEY UPDATE `product_name` = VALUES(`product_name`);

-- 获取刚插入配方的ID并插入明细
SET @formula_id = (SELECT id FROM tape_formula WHERE material_code = '1011-R02-1204-G01-0300');

INSERT INTO `tape_formula_item` (`formula_id`, `material_code`, `material_name`, `weight`, `ratio`, `remark`, `sort_order`) VALUES
(@formula_id, 'YKLJ0801', '主树脂', 76.0000, NULL, '/', 1),
(@formula_id, 'YSYZ0201', '溶剂', 49.4000, 65.0000, '/', 2),
(@formula_id, 'G728-UJ', '助剂', 5.3200, 7.0000, NULL, 3),
(@formula_id, '9002', '固化剂', 0.3040, 0.4000, '这两个固化剂要分开加进胶水，不要混在一起加', 4),
(@formula_id, 'FY-45', '固化剂', 0.3040, 0.4000, NULL, 5),
(@formula_id, 'AJSZ0101', '助剂(20%)', 0.6080, 0.8000, '用甲苯稀释20%的浓度的溶液', 6),
(@formula_id, 'DJBHS0301', '助剂(10%)', 0.1520, 0.2000, '用异丙醇稀释10%浓度的溶液', 7);
