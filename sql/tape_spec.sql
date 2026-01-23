-- ================================================
-- 胶带料号规格管理表（研发管理）
-- 用于维护胶带产品的规格参数及性能指标上下限
-- ================================================

-- 胶带规格主表
CREATE TABLE IF NOT EXISTS `tape_spec` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `material_code` VARCHAR(50) NOT NULL COMMENT '胶带料号',
    `product_name` VARCHAR(100) NOT NULL COMMENT '产品名称',
    `color_code` VARCHAR(20) COMMENT '颜色代码（如G03、B05）',
    `color_name` VARCHAR(50) COMMENT '颜色名称（如翠绿、深蓝）',
    
    -- 基材参数
    `base_thickness` DECIMAL(10,2) COMMENT '基材厚度(μm)',
    `base_material` VARCHAR(50) COMMENT '基材材质（如PET）',
    
    -- 胶水参数
    `glue_material` VARCHAR(50) COMMENT '胶水材质（如亚克力）',
    `glue_thickness` DECIMAL(10,2) COMMENT '胶水厚度(μm)',
    
    -- 初粘性能（范围值）
    `initial_tack_min` DECIMAL(10,2) COMMENT '初粘下限(#)',
    `initial_tack_max` DECIMAL(10,2) COMMENT '初粘上限(#)',
    `initial_tack_type` VARCHAR(10) DEFAULT 'range' COMMENT '初粘类型: range=范围, gte=大于等于, lte=小于等于',
    
    -- 总厚度（范围值）
    `total_thickness` DECIMAL(10,2) COMMENT '总厚度标准值(μm)',
    `total_thickness_min` DECIMAL(10,2) COMMENT '总厚度下限(μm)',
    `total_thickness_max` DECIMAL(10,2) COMMENT '总厚度上限(μm)',
    
    -- 剥离力（范围值）
    `peel_strength_min` DECIMAL(10,2) COMMENT '剥离力下限(N/25mm)',
    `peel_strength_max` DECIMAL(10,2) COMMENT '剥离力上限(N/25mm)',
    `peel_strength_type` VARCHAR(10) DEFAULT 'range' COMMENT '剥离力类型: range=范围, gte=大于等于',
    
    -- 解卷力（范围值）
    `unwind_force_min` DECIMAL(10,2) COMMENT '解卷力下限(N/25mm)',
    `unwind_force_max` DECIMAL(10,2) COMMENT '解卷力上限(N/25mm)',
    `unwind_force_type` VARCHAR(10) DEFAULT 'range' COMMENT '解卷力类型: range=范围, lte=小于等于',
    
    -- 耐温性能
    `heat_resistance` DECIMAL(10,2) COMMENT '耐温标准值(℃/0.5H)',
    `heat_resistance_type` VARCHAR(10) DEFAULT 'gte' COMMENT '耐温类型: gte=大于等于',
    
    -- 其他参数
    `remark` VARCHAR(500) COMMENT '备注',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0=禁用, 1=启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `create_by` VARCHAR(50) COMMENT '创建人',
    `update_by` VARCHAR(50) COMMENT '更新人',
    
    UNIQUE KEY `uk_material_code` (`material_code`),
    INDEX `idx_product_name` (`product_name`),
    INDEX `idx_color_code` (`color_code`),
    INDEX `idx_base_material` (`base_material`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='胶带规格参数表';

-- 颜色代码字典表
CREATE TABLE IF NOT EXISTS `tape_color_dict` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `color_code` VARCHAR(20) NOT NULL COMMENT '颜色代码',
    `color_name` VARCHAR(50) NOT NULL COMMENT '颜色名称',
    `color_hex` VARCHAR(10) COMMENT '颜色十六进制值',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `status` TINYINT DEFAULT 1 COMMENT '状态',
    UNIQUE KEY `uk_color_code` (`color_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='颜色代码字典表';

-- 材质字典表
CREATE TABLE IF NOT EXISTS `tape_material_dict` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `material_type` VARCHAR(20) NOT NULL COMMENT '材质类型: base=基材, glue=胶水',
    `material_code` VARCHAR(20) NOT NULL COMMENT '材质代码',
    `material_name` VARCHAR(50) NOT NULL COMMENT '材质名称',
    `sort_order` INT DEFAULT 0 COMMENT '排序',
    `status` TINYINT DEFAULT 1 COMMENT '状态',
    UNIQUE KEY `uk_type_code` (`material_type`, `material_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='材质字典表';

-- 插入颜色字典数据
INSERT INTO `tape_color_dict` (`color_code`, `color_name`, `color_hex`, `sort_order`) VALUES
('G01', '翠绿', '#00CED1', 1),
('G03', '无机翠绿', '#008B8B', 2),
('B02', '深蓝', '#00008B', 3),
('B05', '蓝色', '#0000FF', 4),
('R01', '红色', '#FF0000', 5),
('Y01', '黄色', '#FFFF00', 6),
('W01', '白色', '#FFFFFF', 7),
('BK01', '黑色', '#000000', 8)
ON DUPLICATE KEY UPDATE `color_name` = VALUES(`color_name`);

-- 插入材质字典数据
INSERT INTO `tape_material_dict` (`material_type`, `material_code`, `material_name`, `sort_order`) VALUES
('base', 'PET', 'PET', 1),
('base', 'PI', 'PI聚酰亚胺', 2),
('base', 'PP', 'PP聚丙烯', 3),
('base', 'PVC', 'PVC', 4),
('glue', 'ACRYLIC', '亚克力', 1),
('glue', 'SILICONE', '硅胶', 2),
('glue', 'RUBBER', '橡胶', 3)
ON DUPLICATE KEY UPDATE `material_name` = VALUES(`material_name`);

-- 插入示例规格数据
INSERT INTO `tape_spec` (
    `material_code`, `product_name`, `color_code`, `color_name`,
    `base_thickness`, `base_material`, `glue_material`, `glue_thickness`,
    `initial_tack_min`, `initial_tack_max`, `initial_tack_type`,
    `total_thickness`, `total_thickness_min`, `total_thickness_max`,
    `peel_strength_min`, `peel_strength_max`, `peel_strength_type`,
    `unwind_force_min`, `unwind_force_max`, `unwind_force_type`,
    `heat_resistance`, `heat_resistance_type`
) VALUES
('1011-R02-0903-G03-0300', '12μ无机翠绿PET胶带', 'G03', '无机翠绿', 9, 'PET', '亚克力', 3, 2, 6, 'range', 12, 10, 14, 2, 4.5, 'range', 0.5, 1.5, 'range', 110, 'gte'),
('1011-R02-0903-B05-0300', '12μm蓝色PET终止胶带', 'B05', '蓝色', 9, 'PET', '亚克力', 3, NULL, 4, 'lte', 12, 11, 13, 3, NULL, 'gte', 0.3, 1.5, 'range', 110, 'gte'),
('303-R02-1204-G03-0300', '16μm无机翠绿橡胶胶带', 'G03', '无机翠绿', 12, 'PET', '亚克力', 4, 2, 5, 'range', 16, 14, 18, 3, NULL, 'gte', 0.5, 2, 'range', 120, 'gte'),
('1011-R02-1204-G03-0300', '16u无机翠绿PET胶带', 'G03', '无机翠绿', 12, 'PET', '亚克力', 4, 2, 6, 'range', 16, 14, 18, 3, NULL, 'gte', 0.3, 1, 'range', 120, 'gte'),
('1011-R02-1204-G01-0300', '16μm翠绿PET胶带', 'G01', '翠绿', 12, 'PET', '亚克力', 4, 2, 6, 'range', 16, 14, 18, 3, NULL, 'gte', 0.3, 1, 'range', 120, 'gte'),
('1011-R02-1204-G03-0200', '16μm无机翠绿PET胶带', 'G03', '无机翠绿', 12, 'PET', '亚克力', 4, 3, NULL, 'gte', 16, 14, 18, 2, 3.5, 'range', 0.3, 1, 'range', 120, 'gte'),
('1011-R02-1502-B02-0300', '17μm深蓝PET终止胶带', 'B02', '深蓝', 15, 'PET', '亚克力', 2, NULL, 3, 'lte', 17, 17, 19, 1.5, 4.5, 'range', 0.2, 1.5, 'range', 120, 'gte'),
('1011-R02-1503-G01-0200H', '18μm翠绿数字PET胶带', 'G01', '翠绿', 15, 'PET', '亚克力', 3, NULL, 3, 'lte', 18, 16, 20, 1, 3.5, 'range', 0.3, 1.2, 'range', 120, 'gte')
ON DUPLICATE KEY UPDATE `product_name` = VALUES(`product_name`);
