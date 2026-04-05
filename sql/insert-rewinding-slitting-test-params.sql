-- 复卷/分切工艺参数测试数据（幂等，可重复执行）
-- 使用方式（PowerShell）：
-- Get-Content sql/insert-rewinding-slitting-test-params.sql -Encoding UTF8 | mysql -h <host> -u <user> -p<password> erp

START TRANSACTION;

-- 1) 复卷参数（rewinding_process_params）
INSERT INTO rewinding_process_params (
  material_code, equipment_code, rewinding_speed, tension_setting, roll_change_time,
  setup_time, first_check_time, last_check_time, remark, status, create_time, update_time
) VALUES
  ('1011-R02-2307', 'RW-01', 65.00, 9.50, 6, 15, 10, 6, '测试数据-复卷-RW01', 1, NOW(), NOW()),
  ('1011-R02-2307', 'RW-02', 62.00, 9.00, 7, 16, 10, 6, '测试数据-复卷-RW02', 1, NOW(), NOW()),
  ('1011-R03-2310', 'RW-01', 58.00, 10.20, 8, 18, 12, 8, '测试数据-复卷-RW01-高张力', 1, NOW(), NOW()),
  ('1011-R03-2310', 'RW-03', 60.00, 10.00, 7, 16, 10, 7, '测试数据-复卷-RW03', 1, NOW(), NOW()),
  ('2012-B01-2401', 'RW-01', 55.00, 8.80, 9, 20, 12, 8, '测试数据-复卷-蓝系', 1, NOW(), NOW()),
  ('2012-B01-2401', 'RW-02', 57.00, 8.60, 8, 18, 12, 8, '测试数据-复卷-蓝系-RW02', 1, NOW(), NOW()),
  ('3015-G05-2402', 'RW-03', 52.00, 11.20, 10, 22, 15, 10, '测试数据-复卷-厚料', 1, NOW(), NOW()),
  ('4010-W02-2403', 'RW-01', 68.00, 7.80, 5, 14, 8, 5, '测试数据-复卷-高速', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  rewinding_speed = VALUES(rewinding_speed),
  tension_setting = VALUES(tension_setting),
  roll_change_time = VALUES(roll_change_time),
  setup_time = VALUES(setup_time),
  first_check_time = VALUES(first_check_time),
  last_check_time = VALUES(last_check_time),
  remark = VALUES(remark),
  status = VALUES(status),
  update_time = NOW();

-- 2) 分切参数（slitting_process_params）
INSERT INTO slitting_process_params (
  material_code, equipment_code, total_thickness, process_length, process_width, production_speed,
  slitting_speed, blade_type, blade_change_time,
  min_slit_width, max_blades, edge_loss,
  setup_time, first_check_time, last_check_time, remark, status, create_time, update_time
) VALUES
  ('1011-R02-2307', 'SL-01', 42.000, 1200.00, 35.00, 1.60, 95.00, '圆刀-A', 12, 15, 18, 8, 14, 10, 6, '测试数据-分切-SL01', 1, NOW(), NOW()),
  ('1011-R02-2307', 'SL-02', 42.000, 1180.00, 38.00, 1.50, 90.00, '圆刀-B', 14, 18, 16, 10, 15, 10, 6, '测试数据-分切-SL02', 1, NOW(), NOW()),
  ('1011-R03-2310', 'SL-01', 55.000, 1000.00, 40.00, 1.30, 88.00, '圆刀-A', 15, 20, 14, 12, 16, 12, 8, '测试数据-分切-厚料', 1, NOW(), NOW()),
  ('2012-B01-2401', 'SL-02', 30.000, 1500.00, 25.00, 1.80, 92.00, '直刀-C', 13, 12, 20, 6, 13, 9, 6, '测试数据-分切-蓝系', 1, NOW(), NOW()),
  ('2012-B01-2401', 'SL-03', 30.000, 1450.00, 20.00, 1.40, 85.00, '直刀-D', 16, 10, 22, 5, 15, 10, 7, '测试数据-分切-窄幅', 1, NOW(), NOW()),
  ('3015-G05-2402', 'SL-01', 68.000, 900.00, 50.00, 1.10, 80.00, '圆刀-B', 18, 25, 12, 15, 18, 12, 10, '测试数据-分切-宽幅', 1, NOW(), NOW()),
  ('4010-W02-2403', 'SL-03', 28.000, 1600.00, 30.00, 2.00, 98.00, '圆刀-A', 10, 14, 20, 7, 12, 8, 5, '测试数据-分切-高速', 1, NOW(), NOW()),
  ('5018-Y01-2404', 'SL-02', 72.000, 800.00, 60.00, 0.90, 78.00, '锯齿刀-E', 20, 30, 10, 18, 20, 15, 10, '测试数据-分切-难切材质', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  total_thickness = VALUES(total_thickness),
  process_length = VALUES(process_length),
  process_width = VALUES(process_width),
  production_speed = VALUES(production_speed),
  slitting_speed = VALUES(slitting_speed),
  blade_type = VALUES(blade_type),
  blade_change_time = VALUES(blade_change_time),
  min_slit_width = VALUES(min_slit_width),
  max_blades = VALUES(max_blades),
  edge_loss = VALUES(edge_loss),
  setup_time = VALUES(setup_time),
  first_check_time = VALUES(first_check_time),
  last_check_time = VALUES(last_check_time),
  remark = VALUES(remark),
  status = VALUES(status),
  update_time = NOW();

COMMIT;

-- 可选验证
SELECT COUNT(1) AS rewinding_count FROM rewinding_process_params WHERE status = 1;
SELECT COUNT(1) AS slitting_count FROM slitting_process_params WHERE status = 1;
