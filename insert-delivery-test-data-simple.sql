-- =====================================================
-- 发货单功能测试数据 (简化版 - 直接执行)
-- 请先确保已执行过表结构的创建和字段添加
-- =====================================================

-- 清理旧测试数据（可选，谨慎执行）
-- DELETE FROM delivery_notice_items WHERE notice_id IN (SELECT id FROM delivery_notices WHERE notice_no LIKE 'DN2026%');
-- DELETE FROM delivery_notices WHERE notice_no LIKE 'DN2026%';

-- =====================================================
-- 插入测试销售订单
-- =====================================================

12-003'), 'FR-6000-X', '防静电阻燃膜', 200, 1000, 0.050, 25, 1000.00, 22.00, 22000.00, '大批量', 0),
((SELECT id FROM sales_orders WHERE order_no = 'SO-20260112-003'), 'FR-6001-Y', '双面胶阻燃带', 50, 600, 0.100, 20, 240.00, 55.00, 13200.00, NULL, 0);

-- =====================================================
-- 插入测试发货通知单
-- =====================================================

INSERT IGNORE INTO delivery_notices (notice_no, order_id, order_no, customer, customer_order_no, delivery_date, delivery_address, contact_person, contact_phone, carrier_name, carrier_phone, status, remark, created_by, is_deleted)
VALUES 
-- 发货单1: 华为订单-已发货-- 如果订单已存在则跳过
INSERT IGNORE INTO sales_orders (order_no, customer, customer_order_no, total_amount, total_area, order_date, delivery_date, delivery_address, status, remark, created_by, is_deleted)
VALUES 
('SO-20260110-001', '深圳华为技术有限公司', 'HW-PO-2026-0088', 25800.00, 1200.50, '2026-01-10', '2026-01-20', '广东省深圳市龙岗区坂田街道华为基地F区', 'processing', '优先发货', 'admin', 0),
('SO-20260111-002', '东莞市比亚迪电子有限公司', 'BYD-2026-00156', 18500.00, 850.00, '2026-01-11', '2026-01-25', '广东省东莞市大朗镇比亚迪工业园A栋', 'pending', NULL, 'admin', 0),
('SO-20260112-003', '广州富士康科技集团', 'FX-PO-20260112-A', 32000.00, 1500.00, '2026-01-12', '2026-01-22', '广东省广州市增城区富士康科技园三号门', 'processing', '分批发货', 'admin', 0);

-- 插入订单明细 (使用子查询获取order_id)
INSERT IGNORE INTO sales_order_items (order_id, material_code, material_name, length, width, thickness, rolls, sqm, unit_price, amount, remark, is_deleted)
VALUES 
-- 华为订单明细
((SELECT id FROM sales_orders WHERE order_no = 'SO-20260110-001'), 'FR-4725-A', '阻燃聚酯薄膜（黑色）', 100, 1000, 0.075, 20, 400.00, 32.00, 12800.00, '常规品', 0),
((SELECT id FROM sales_orders WHERE order_no = 'SO-20260110-001'), 'FR-4725-B', '阻燃聚酯薄膜（白色）', 100, 1000, 0.050, 15, 300.00, 28.00, 8400.00, '加急', 0),
((SELECT id FROM sales_orders WHERE order_no = 'SO-20260110-001'), 'FR-4730-C', '阻燃PET保护膜', 50, 800, 0.100, 10, 160.00, 35.00, 5600.00, NULL, 0),
-- 比亚迪订单明细
((SELECT id FROM sales_orders WHERE order_no = 'SO-20260111-002'), 'FR-5000-A', '高温阻燃胶带', 50, 500, 0.080, 30, 300.00, 45.00, 13500.00, '新客户', 0),
((SELECT id FROM sales_orders WHERE order_no = 'SO-20260111-002'), 'FR-5001-B', '绝缘阻燃薄膜', 100, 1200, 0.125, 8, 384.00, 38.00, 14592.00, NULL, 0),
-- 富士康订单明细
((SELECT id FROM sales_orders WHERE order_no = 'SO-202601
('DN202601100001', (SELECT id FROM sales_orders WHERE order_no = 'SO-20260110-001'), 'SO-20260110-001', '深圳华为技术有限公司', 'HW-PO-2026-0088', '2026-01-14', '广东省深圳市龙岗区坂田街道华为基地F区', '张经理', '13800138001', '顺丰速运 SF1234567890', '400-811-1111', 'shipped', '已发货，预计明天到达', 'admin', 0),
-- 发货单2: 比亚迪订单-草稿
('DN202601110002', (SELECT id FROM sales_orders WHERE order_no = 'SO-20260111-002'), 'SO-20260111-002', '东莞市比亚迪电子有限公司', 'BYD-2026-00156', '2026-01-15', '广东省东莞市大朗镇比亚迪工业园A栋', '李小姐', '13900139002', '德邦物流', '95353', 'draft', '等待确认', 'admin', 0),
-- 发货单3: 富士康订单-已发货(第一批)
('DN202601120003', (SELECT id FROM sales_orders WHERE order_no = 'SO-20260112-003'), 'SO-20260112-003', '广州富士康科技集团', 'FX-PO-20260112-A', '2026-01-13', '广东省广州市增城区富士康科技园三号门', '王工', '13700137003', '京东物流 JD0012345678', '400-656-2211', 'shipped', '第一批发货', 'admin', 0),
-- 发货单4: 富士康订单-草稿(第二批)
('DN202601140004', (SELECT id FROM sales_orders WHERE order_no = 'SO-20260112-003'), 'SO-20260112-003', '广州富士康科技集团', 'FX-PO-20260112-A', '2026-01-16', '广东省广州市增城区富士康科技园三号门', '王工', '13700137003', '', '', 'draft', '第二批待发', 'admin', 0);

-- =====================================================
-- 插入发货通知单明细
-- =====================================================

INSERT IGNORE INTO delivery_notice_items (notice_id, order_item_id, material_code, material_name, spec, quantity, area_size, box_count, gross_weight, total_weight, remark, is_deleted)
VALUES 
-- 发货单1明细 (华为)
((SELECT id FROM delivery_notices WHERE notice_no = 'DN202601100001'), 
 (SELECT id FROM sales_order_items WHERE material_code = 'FR-4725-A' AND order_id = (SELECT id FROM sales_orders WHERE order_no = 'SO-20260110-001') LIMIT 1),
 'FR-4725-A', '阻燃聚酯薄膜（黑色）', '0.075*1000*100', 20, 400.00, 4, 12.50, 50.00, '整单发', 0),

((SELECT id FROM delivery_notices WHERE notice_no = 'DN202601100001'), 
 (SELECT id FROM sales_order_items WHERE material_code = 'FR-4725-B' AND order_id = (SELECT id FROM sales_orders WHERE order_no = 'SO-20260110-001') LIMIT 1),
 'FR-4725-B', '阻燃聚酯薄膜（白色）', '0.050*1000*100', 15, 300.00, 3, 10.00, 30.00, NULL, 0),

-- 发货单2明细 (比亚迪)
((SELECT id FROM delivery_notices WHERE notice_no = 'DN202601110002'), 
 (SELECT id FROM sales_order_items WHERE material_code = 'FR-5000-A' AND order_id = (SELECT id FROM sales_orders WHERE order_no = 'SO-20260111-002') LIMIT 1),
 'FR-5000-A', '高温阻燃胶带', '0.080*500*50', 30, 300.00, 6, 8.50, 51.00, '易碎品', 0),

-- 发货单3明细 (富士康第一批)
((SELECT id FROM delivery_notices WHERE notice_no = 'DN202601120003'), 
 (SELECT id FROM sales_order_items WHERE material_code = 'FR-6000-X' AND order_id = (SELECT id FROM sales_orders WHERE order_no = 'SO-20260112-003') LIMIT 1),
 'FR-6000-X', '防静电阻燃膜', '0.050*1000*200', 15, 600.00, 5, 15.00, 75.00, '第一批15卷', 0),

-- 发货单4明细 (富士康第二批)
((SELECT id FROM delivery_notices WHERE notice_no = 'DN202601140004'), 
 (SELECT id FROM sales_order_items WHERE material_code = 'FR-6000-X' AND order_id = (SELECT id FROM sales_orders WHERE order_no = 'SO-20260112-003') LIMIT 1),
 'FR-6000-X', '防静电阻燃膜', '0.050*1000*200', 10, 400.00, 4, 15.00, 60.00, '第二批剩余10卷', 0),

((SELECT id FROM delivery_notices WHERE notice_no = 'DN202601140004'), 
 (SELECT id FROM sales_order_items WHERE material_code = 'FR-6001-Y' AND order_id = (SELECT id FROM sales_orders WHERE order_no = 'SO-20260112-003') LIMIT 1),
 'FR-6001-Y', '双面胶阻燃带', '0.100*600*50', 20, 240.00, 4, 8.00, 32.00, NULL, 0);

-- =====================================================
-- 查询验证
-- =====================================================

-- 查看销售订单
SELECT '========== 销售订单 ==========' AS info;
SELECT id, order_no, customer, total_amount, status FROM sales_orders WHERE is_deleted = 0 ORDER BY id;

-- 查看发货通知单
SELECT '========== 发货通知单 ==========' AS info;
SELECT id, notice_no, order_no, customer, delivery_date, carrier_name, status FROM delivery_notices WHERE is_deleted = 0 ORDER BY id;

-- 查看发货明细
SELECT '========== 发货明细 ==========' AS info;
SELECT 
    dn.notice_no,
    dni.material_code,
    dni.material_name,
    dni.quantity AS '数量(卷)',
    dni.area_size AS '面积(m²)',
    dni.box_count AS '箱数',
    dni.total_weight AS '总重(kg)',
    dni.remark
FROM delivery_notice_items dni
JOIN delivery_notices dn ON dni.notice_id = dn.id
WHERE dn.is_deleted = 0
ORDER BY dn.notice_no, dni.id;
