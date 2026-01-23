-- =====================================================
-- 发货单功能测试数据（修正版）
-- =====================================================

-- 1. 插入测试销售订单
INSERT IGNORE INTO sales_orders (order_no, customer, customer_order_no, total_amount, total_area, order_date, delivery_date, delivery_address, status, remark, created_by, is_deleted)
VALUES 
('SO-20260110-001', '深圳华为技术有限公司', 'HW-PO-2026-0088', 25800.00, 1200.50, '2026-01-10', '2026-01-20', '广东省深圳市龙岗区坂田街道华为基地F区', 'processing', '优先发货', 'admin', 0),
('SO-20260111-002', '东莞市比亚迪电子有限公司', 'BYD-2026-00156', 18500.00, 850.00, '2026-01-11', '2026-01-25', '广东省东莞市大朗镇比亚迪工业园A栋', 'pending', NULL, 'admin', 0),
('SO-20260112-003', '广州小鹏汽车科技有限公司', 'XP-2026-0099', 32000.00, 1500.00, '2026-01-12', '2026-01-28', '广东省广州市番禺区化龙镇小鹏汽车生产基地', 'completed', '已完成发货', 'admin', 0);

-- 获取订单ID
SET @order1_id = (SELECT id FROM sales_orders WHERE order_no = 'SO-20260110-001');
SET @order2_id = (SELECT id FROM sales_orders WHERE order_no = 'SO-20260111-002');
SET @order3_id = (SELECT id FROM sales_orders WHERE order_no = 'SO-20260112-003');

-- 2. 插入订单明细
INSERT IGNORE INTO sales_order_items (order_id, material_code, material_name, length, width, thickness, rolls, sqm, unit_price, amount, remark, is_deleted)
VALUES
-- 订单1明细
(@order1_id, 'FR-4725-A', '阻燃聚酯薄膜（黑色）', 100, 1000, 0.075, 20, 400.00, 32.00, 12800.00, '常规品', 0),
(@order1_id, 'FR-4725-B', '阻燃聚酯薄膜（白色）', 100, 1000, 0.050, 15, 300.00, 28.00, 8400.00, '加急', 0),
(@order1_id, 'FR-4730-C', '阻燃PET保护膜', 50, 800, 0.100, 10, 160.00, 35.00, 5600.00, NULL, 0),
-- 订单2明细
(@order2_id, 'PET-5020-A', '透明PET薄膜', 80, 900, 0.080, 12, 345.60, 26.50, 9158.40, '耐高温', 0),
(@order2_id, 'PET-5020-B', '磨砂PET薄膜', 80, 900, 0.100, 8, 230.40, 29.00, 6681.60, NULL, 0),
-- 订单3明细
(@order3_id, 'AL-3015-A', '铝箔复合膜', 120, 1200, 0.090, 18, 777.60, 38.00, 29548.80, '防静电', 0),
(@order3_id, 'AL-3015-B', '铝箔屏蔽膜', 100, 1100, 0.085, 12, 529.20, 42.00, 22226.40, NULL, 0);

-- 获取订单明细ID
SET @item1_1 = (SELECT id FROM sales_order_items WHERE order_id = @order1_id AND material_code = 'FR-4725-A' LIMIT 1);
SET @item1_2 = (SELECT id FROM sales_order_items WHERE order_id = @order1_id AND material_code = 'FR-4725-B' LIMIT 1);
SET @item1_3 = (SELECT id FROM sales_order_items WHERE order_id = @order1_id AND material_code = 'FR-4730-C' LIMIT 1);
SET @item2_1 = (SELECT id FROM sales_order_items WHERE order_id = @order2_id AND material_code = 'PET-5020-A' LIMIT 1);
SET @item2_2 = (SELECT id FROM sales_order_items WHERE order_id = @order2_id AND material_code = 'PET-5020-B' LIMIT 1);
SET @item3_1 = (SELECT id FROM sales_order_items WHERE order_id = @order3_id AND material_code = 'AL-3015-A' LIMIT 1);
SET @item3_2 = (SELECT id FROM sales_order_items WHERE order_id = @order3_id AND material_code = 'AL-3015-B' LIMIT 1);

-- 3. 插入发货通知单主表
INSERT IGNORE INTO delivery_notices (
    notice_no, order_id, order_no, customer, customer_order_no, delivery_date,
    delivery_address, contact_person, contact_phone,
    carrier_name, carrier_phone, 
    status, remarks, created_by, created_at
) VALUES
('DN-2026-0001', @order1_id, 'SO-20260110-001', '深圳华为技术有限公司', 'HW-PO-2026-0088', '2026-01-15',
 '广东省深圳市龙岗区坂田街道华为基地F区', '张工', '13800138001',
 '顺丰速运', '400-811-1111',
 'pending', '优先配送，请注意防潮', 'admin', NOW()),

('DN-2026-0002', @order2_id, 'SO-20260111-002', '东莞市比亚迪电子有限公司', 'BYD-2026-00156', '2026-01-16',
 '广东省东莞市大朗镇比亚迪工业园A栋', '李主管', '13900139002',
 '德邦物流', '95353',
 'shipped', '已发货，运单号：DB2026011600123', 'admin', NOW()),

('DN-2026-0003', @order3_id, 'SO-20260112-003', '广州小鹏汽车科技有限公司', 'XP-2026-0099', '2026-01-13',
 '广东省广州市番禺区化龙镇小鹏汽车生产基地', '王经理', '13700137003',
 '中通快运', '95311',
 'shipped', '客户已签收，满意度高', 'admin', NOW());

-- 获取发货单ID
SET @notice1_id = (SELECT id FROM delivery_notices WHERE notice_no = 'DN-2026-0001' LIMIT 1);
SET @notice2_id = (SELECT id FROM delivery_notices WHERE notice_no = 'DN-2026-0002' LIMIT 1);
SET @notice3_id = (SELECT id FROM delivery_notices WHERE notice_no = 'DN-2026-0003' LIMIT 1);

-- 4. 插入发货通知单明细
INSERT IGNORE INTO delivery_notice_items (
    notice_id, order_item_id, material_code, material_name, spec,
    quantity, area_size, box_count, gross_weight, total_weight, remark, created_by
) VALUES
-- DN-2026-0001 的明细
(@notice1_id, @item1_1, 'FR-4725-A', '阻燃聚酯薄膜（黑色）', '1000*100*0.075mm',
 20, 400.00, 4, 55.0, 220.0, '第一批次', 'admin'),
(@notice1_id, @item1_2, 'FR-4725-B', '阻燃聚酯薄膜（白色）', '1000*100*0.050mm',
 15, 300.00, 3, 48.0, 144.0, '加急发货', 'admin'),
(@notice1_id, @item1_3, 'FR-4730-C', '阻燃PET保护膜', '800*50*0.100mm',
 10, 160.00, 2, 42.0, 84.0, NULL, 'admin'),

-- DN-2026-0002 的明细
(@notice2_id, @item2_1, 'PET-5020-A', '透明PET薄膜', '900*80*0.080mm',
 12, 345.60, 3, 62.5, 187.5, '耐高温材料', 'admin'),
(@notice2_id, @item2_2, 'PET-5020-B', '磨砂PET薄膜', '900*80*0.100mm',
 8, 230.40, 2, 58.0, 116.0, NULL, 'admin'),

-- DN-2026-0003 的明细
(@notice3_id, @item3_1, 'AL-3015-A', '铝箔复合膜', '1200*120*0.090mm',
 18, 777.60, 5, 88.0, 440.0, '防静电包装', 'admin'),
(@notice3_id, @item3_2, 'AL-3015-B', '铝箔屏蔽膜', '1100*100*0.085mm',
 12, 529.20, 4, 75.0, 300.0, NULL, 'admin');

-- 5. 查询验证结果
SELECT '========== 销售订单 ==========' AS '';
SELECT id, order_no, customer, order_date, delivery_date, total_amount, status 
FROM sales_orders 
WHERE order_no LIKE 'SO-202601%' 
ORDER BY order_no;

SELECT '========== 订单明细 ==========' AS '';
SELECT soi.id, so.order_no, soi.material_code, soi.material_name, 
       CONCAT(soi.width, '*', soi.length, '*', soi.thickness, 'mm') AS spec,
       soi.rolls, soi.amount
FROM sales_order_items soi
JOIN sales_orders so ON soi.order_id = so.id
WHERE so.order_no LIKE 'SO-202601%'
ORDER BY so.order_no, soi.id;

SELECT '========== 发货通知单 ==========' AS '';
SELECT id, notice_no, order_no, customer, delivery_date, carrier_name, status
FROM delivery_notices 
WHERE notice_no LIKE 'DN-2026-%'
ORDER BY notice_no;

SELECT '========== 发货明细 ==========' AS '';
SELECT dni.id, dn.notice_no, dni.material_code, dni.material_name, 
       dni.quantity, dni.area_size, dni.box_count, dni.total_weight
FROM delivery_notice_items dni
JOIN delivery_notices dn ON dni.notice_id = dn.id
WHERE dn.notice_no LIKE 'DN-2026-%'
ORDER BY dn.notice_no, dni.id;

SELECT CONCAT('✅ 测试数据插入完成！共插入 ', 
       (SELECT COUNT(*) FROM delivery_notices WHERE notice_no LIKE 'DN-2026-%'),
       ' 条发货单，',
       (SELECT COUNT(*) FROM delivery_notice_items dni JOIN delivery_notices dn ON dni.notice_id = dn.id WHERE dn.notice_no LIKE 'DN-2026-%'),
       ' 条明细') AS result;
