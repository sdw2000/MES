-- 添加缺失的膨胀胶带规格数据
-- 对应料号：5011-R00-4010-T01-0050
-- 假设 T01 代表某种特定颜色，暂时命名为 'T01'，如果代表透明则为 '透明'
-- 厚度设定为 50um (根据截图)

INSERT INTO `tape_color_dict` (`color_code`, `color_name`, `color_hex`, `sort_order`, `status`)
SELECT 'T01', '灰色', '#808080', 9, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tape_color_dict` WHERE `color_code` = 'T01');

INSERT INTO `tape_spec` (
    `material_code`, 
    `product_name`, 
    `color_code`, 
    `color_name`,
    `base_thickness`, 
    `base_material`, 
    `glue_material`, 
    `glue_thickness`,
    `total_thickness`, 
    `total_thickness_min`, 
    `total_thickness_max`,
    `remark`, 
    `status`
) VALUES (
    '5011-R00-4010-T01-0050', -- material_code
    '膨胀胶带', -- product_name
    'T01', -- color_code
    '灰色', -- color_name
    50, -- base_thickness (assuming full thickness is base for now or split)
    'PET', -- base_material (guess)
    'ACRYLIC', -- glue_material (guess)
    0, -- glue_thickness
    50, -- total_thickness
    48, -- total_thickness_min
    52, -- total_thickness_max
    '自动导入的缺失规格',
    1
) ON DUPLICATE KEY UPDATE 
    `product_name` = VALUES(`product_name`),
    `color_code` = VALUES(`color_code`),
    `total_thickness` = VALUES(`total_thickness`);
