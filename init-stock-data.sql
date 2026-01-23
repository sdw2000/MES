-- =====================================================
-- 初始化库存和安全库存测试数据
-- 与tape_spec规格表的物料代码对应
-- =====================================================

USE erp;

-- 1. 清空并初始化 safety_stock 安全库存表
DELETE FROM safety_stock;

-- 安全库存数据 - 使用tape_spec表中的实际物料代码
INSERT INTO safety_stock (material_code, product_name, safety_qty, safety_sqm, reorder_point, max_stock, current_stock, current_sqm, status, create_time) VALUES
('1011-R02-0903-G03-0300', '12μ无机翠绿PET胶带', 100, 1250.00, 50, 500, 0, 0, 1, NOW()),
('1011-R02-0903-B05-0300', '12μm蓝色PET终止胶带', 150, 2000.00, 80, 600, 0, 0, 1, NOW()),
('303-R02-1204-G03-0300', '16μm无机翠绿橡胶胶带', 120, 1800.00, 60, 400, 0, 0, 1, NOW()),
('1011-R02-1204-G03-0300', '16u无机翠绿PET胶带', 200, 3000.00, 100, 800, 0, 0, 1, NOW()),
('1011-R02-1204-G01-0300', '16μm翠绿PET胶带', 80, 960.00, 40, 300, 0, 0, 1, NOW()),
('1011-R02-1204-G03-0200', '16μm无机翠绿PET胶带', 90, 1080.00, 45, 350, 0, 0, 1, NOW()),
('1011-R02-1502-B02-0300', '17μm深蓝PET终止胶带', 60, 720.00, 30, 200, 0, 0, 1, NOW()),
('1011-R02-1503-G01-0200H', '18μm翠绿数字PET胶带', 70, 840.00, 35, 250, 0, 0, 1, NOW());

-- 2. 初始化 tape_stock 仓库库存表（母卷）- 使用相同的物料代码
DELETE FROM tape_stock;

-- 12μ无机翠绿PET胶带 - 有较多库存
-- 12μm蓝色PET终止胶带 - 库存正常
-- 16μm无机翠绿橡胶胶带 - 库存不足（低于补货点）
-- 16u无机翠绿PET胶带 - 有充足库存
-- 16μm翠绿PET胶带 - 库存偏低
-- 16μm无机翠绿PET胶带(另一种) - 库存较少
-- 17μm深蓝PET终止胶带 - 库存不足（低于补货点）
-- 18μm翠绿数字PET胶带 - 无库存（测试补货场景）- 不插入记录

INSERT INTO tape_stock (material_code, product_name, stock_type, batch_no, thickness, width, length, total_rolls, total_sqm, location, spec_desc, prod_year, prod_month, prod_day, prod_date, status, create_time) VALUES
('1011-R02-0903-G03-0300', '12μ无机翠绿PET胶带', 'jumbo', 'PB-20260105-001', 12, 1200, 6000, 40, 288.00, 'A-01-01', '12μm*1200mm*6000m', 2026, 1, 5, '2026-01-05', 1, NOW()),
('1011-R02-0903-G03-0300', '12μ无机翠绿PET胶带', 'jumbo', 'PB-20260106-001', 12, 1200, 6000, 35, 252.00, 'A-01-02', '12μm*1200mm*6000m', 2026, 1, 6, '2026-01-06', 1, NOW()),
('1011-R02-0903-B05-0300', '12μm蓝色PET终止胶带', 'jumbo', 'PB-20260104-001', 12, 1000, 5000, 60, 300.00, 'A-02-01', '12μm*1000mm*5000m', 2026, 1, 4, '2026-01-04', 1, NOW()),
('1011-R02-0903-B05-0300', '12μm蓝色PET终止胶带', 'jumbo', 'PB-20260107-001', 12, 1000, 5000, 50, 250.00, 'A-02-02', '12μm*1000mm*5000m', 2026, 1, 7, '2026-01-07', 1, NOW()),
('303-R02-1204-G03-0300', '16μm无机翠绿橡胶胶带', 'jumbo', 'PB-20260103-001', 16, 1200, 4000, 30, 144.00, 'B-01-01', '16μm*1200mm*4000m', 2026, 1, 3, '2026-01-03', 1, NOW()),
('1011-R02-1204-G03-0300', '16u无机翠绿PET胶带', 'jumbo', 'PB-20260102-001', 16, 1200, 5000, 80, 480.00, 'B-02-01', '16μm*1200mm*5000m', 2026, 1, 2, '2026-01-02', 1, NOW()),
('1011-R02-1204-G03-0300', '16u无机翠绿PET胶带', 'jumbo', 'PB-20260105-002', 16, 1200, 5000, 70, 420.00, 'B-02-02', '16μm*1200mm*5000m', 2026, 1, 5, '2026-01-05', 1, NOW()),
('1011-R02-1204-G01-0300', '16μm翠绿PET胶带', 'jumbo', 'PB-20260101-001', 16, 800, 3000, 35, 84.00, 'C-01-01', '16μm*800mm*3000m', 2026, 1, 1, '2026-01-01', 1, NOW()),
('1011-R02-1204-G03-0200', '16μm无机翠绿PET胶带', 'jumbo', 'PB-20260106-002', 16, 1500, 4500, 20, 135.00, 'C-02-01', '16μm*1500mm*4500m', 2026, 1, 6, '2026-01-06', 1, NOW()),
('1011-R02-1502-B02-0300', '17μm深蓝PET终止胶带', 'jumbo', 'PB-20260107-002', 17, 600, 2000, 20, 24.00, 'D-01-01', '17μm*600mm*2000m', 2026, 1, 7, '2026-01-07', 1, NOW());

-- 查询结果验证
SELECT '安全库存记录数:' as info, COUNT(*) as cnt FROM safety_stock;
SELECT '仓库库存记录数:' as info, COUNT(*) as cnt FROM tape_stock;

-- 查询安全库存与实际库存对比
SELECT 
    ss.material_code as '物料代码',
    ss.product_name as '产品名称',
    ss.safety_qty as '安全库存',
    ss.reorder_point as '补货点',
    IFNULL(SUM(ts.total_rolls), 0) as '当前库存',
    ss.max_stock as '最大库存',
    CASE 
        WHEN IFNULL(SUM(ts.total_rolls), 0) = 0 THEN '零库存'
        WHEN IFNULL(SUM(ts.total_rolls), 0) <= ss.reorder_point THEN '需补货'
        WHEN IFNULL(SUM(ts.total_rolls), 0) < ss.safety_qty THEN '库存偏低'
        ELSE '正常'
    END as '状态'
FROM safety_stock ss
LEFT JOIN tape_stock ts ON ss.material_code = ts.material_code AND ts.status = 1
WHERE ss.status = 1
GROUP BY ss.material_code, ss.product_name, ss.safety_qty, ss.reorder_point, ss.max_stock
ORDER BY ss.material_code;
