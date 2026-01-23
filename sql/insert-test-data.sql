USE erp;

-- 插入薄膜库存总量数据
INSERT INTO film_stock (material_code, material_name, thickness, width, spec_desc, total_area, total_rolls, available_area, available_rolls) VALUES
('BOPPM-T25', 'BOPP原膜', 25, 1040, '25*1040', 64220.00, 100, 64220.00, 100),
('BOPPM-T37', 'BOPP原膜', 37, 1040, '37*1040', 37284.00, 60, 37284.00, 60),
('BOPPM-T50', 'BOPP原膜,厚度50u', 50, NULL, '50u', 22256.00, 35, 22256.00, 35),
('BOPPM-T60', 'BOPP原膜', 60, NULL, '60u', 8320.00, 15, 8320.00, 15),
('GLXZ-S90Y', '双硅接拉丝纸(1:2)', 90, 1040, '90g*1040', 29211.00, 50, 29211.00, 50),
('NPZ-D105', '单硅牛皮纸', 105, 1040, '105g*1040', 3120.00, 5, 3120.00, 5),
('OPSM-40um', 'OPS收缩膜', 40, NULL, '40um', 46900.00, 75, 46900.00, 75),
('OPSM-50um', 'OPS透明膜', 50, NULL, '50um', 31960.00, 50, 31960.00, 50),
('PELXM-100B', 'PE离型膜', 100, 1070, '100*1070', 1177.00, 2, 1177.00, 2),
('PELXM-60B', 'PE离型膜', 60, 1070, '60*1070', 1926.00, 3, 1926.00, 3);

-- 插入化工库存总量数据
INSERT INTO chemical_stock (material_code, material_name, chemical_type, unit, unit_weight, total_quantity, total_weight, available_quantity, available_weight) VALUES
('FN8558', '胶水', 'adhesive', '桶', 150, 150, 22500, 150, 22500),
('FN8851', '胶水', 'adhesive', '桶', 150, 150, 22500, 150, 22500),
('FN-C102', '胶水', 'adhesive', '桶', NULL, 20, NULL, 20, NULL),
('ZRJ6000F', '阻燃剂-6000F', 'additive', '包', 20, 100, 2000, 100, 2000),
('ZRJ601', '阻燃剂-601', 'additive', '桶', 250, 50, 12500, 50, 12500),
('2089', '绿色色浆2089', 'pigment', '桶', 20, 17, 340, 17, 340),
('6030UJ', '无机蓝色色浆6030UJ', 'pigment', '桶', 25, 25, 625, 25, 625),
('9002', '固化剂9C02', 'additive', '瓶', 1, 20, 20, 20, 20),
('ATSZ0101', '离基树脂(S82-2)', 'additive', '桶', 17, 170, 2890, 170, 2890),
('B6028-U', '群青B6028-U/蓝色茶色色粉', 'pigment', '桶', 20, 20, 400, 20, 400),
('DC', '丙烯酸涂布稀释剂', 'solvent', '桶', 160, 160, 25600, 160, 25600);
