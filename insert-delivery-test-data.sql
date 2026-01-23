-- =====================================================
-- 发货单功能测试数据
-- 执行前请确保已创建相关表并添加了新字段
-- =====================================================

-- 首先确保 delivery_notices 表有新字段
ALTER TABLE delivery_notices 
    ADD COLUMN IF NOT EXISTS customer_order_no VARCHAR(50) COMMENT '客户订单号',
    ADD COLUMN IF NOT EXISTS carrier_name VARCHAR(100) COMMENT '承运公司',
    ADD COLUMN IF NOT EXISTS carrier_phone VARCHAR(50) COMMENT '运输公司电话';

-- 确保 delivery_notice_items 表有新字段
ALTER TABLE delivery_notice_items 
    ADD COLUMN IF NOT EXISTS area_size DECIMAL(12,2) COMMENT '平方数(m2)',
    ADD COLUMN IF NOT EXISTS box_count INT COMMENT '箱数',
    ADD COLUMN IF NOT EXISTS gross_weight DECIMAL(10,2) COMMENT '每箱毛重(kg)',
    ADD COLUMN IF NOT EXISTS total_weight DECIMAL(10,2) COMMENT '总毛重(kg)',
    ADD COLUMN IF NOT EXISTS remark VARCHAR(255) COMMENT '备注';

-- =====================================================
-- 1. 插入测试销售订单（如果不存在）
-- =====================================================

-- 测试订单1：深圳华为技术有限公司
INSERT INTO sales_orders (order_no, customer, customer_order_no, total_amount, total_area, order_date, delivery_date, delivery_address, status, remark, created_by, is_deleted)
SELECT 'SO-20260110-001', '深圳华为技术有限公司', 'HW-PO-2026-0088', 25800.00, 1200.50, '2026-01-10', '2026-01-20', '广东省深圳市龙岗区坂田街道华为基地F区', 'processing', '优先发货', 'admin', 0
WHERE NOT EXISTS (SELECT 1 FROM sales_orders WHERE order_no = 'SO-20260110-001');

-- 获取订单1的ID用于插入明细
SET @order1_id = (SELECT id FROM sales_orders WHERE order_no = 'SO-20260110-001');

-- 订单1明细
INSERT INTO sales_order_items (order_id, material_code, material_name, length, width, thickness, rolls, sqm, unit_price, amount, remark, is_deleted)
SELECT @order1_id, 'FR-4725-A', '阻燃聚酯薄膜（黑色）', 100, 1000, 0.075, 20, 400.00, 32.00, 12800.00, '常规品', 0
WHERE @order1_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sales_order_items WHERE order_id = @order1_id AND material_code = 'FR-4725-A');

INSERT INTO sales_order_items (order_id, material_code, material_name, length, width, thickness, rolls, sqm, unit_price, amount, remark, is_deleted)
SELECT @order1_id, 'FR-4725-B', '阻燃聚酯薄膜（白色）', 100, 1000, 0.050, 15, 300.00, 28.00, 8400.00, '加急', 0
WHERE @order1_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sales_order_items WHERE order_id = @order1_id AND material_code = 'FR-4725-B');

INSERT INTO sales_order_items (order_id, material_code, material_name, length, width, thickness, rolls, sqm, unit_price, amount, remark, is_deleted)
SELECT @order1_id, 'FR-4730-C', '阻燃PET保护膜', 50, 800, 0.100, 10, 160.00, 35.00, 5600.00, NULL, 0
WHERE @order1_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sales_order_items WHERE order_id = @order1_id AND material_code = 'FR-4730-C');

-- 测试订单2：东莞市比亚迪电子有限公司
INSERT INTO sales_orders (order_no, customer, customer_order_no, total_amount, total_area, order_date, delivery_date, delivery_address, status, remark, created_by, is_deleted)
SELECT 'SO-20260111-002', '东莞市比亚迪电子有限公司', 'BYD-2026-00156', 18500.00, 850.00, '2026-01-11', '2026-01-25', '广东省东莞市大朗镇比亚迪工业园A栋', 'pending', NULL, 'admin', 0
WHERE NOT EXISTS (SELECT 1 FROM sales_orders WHERE order_no = 'SO-20260111-002');

SET @order2_id = (SELECT id FROM sales_orders WHERE order_no = 'SO-20260111-002');

INSERT INTO sales_order_items (order_id, material_code, material_name, length, width, thickness, rolls, sqm, unit_price, amount, remark, is_deleted)
SELECT @order2_id, 'FR-5000-A', '高温阻燃胶带', 50, 500, 0.080, 30, 300.00, 45.00, 13500.00, '新客户', 0
WHERE @order2_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sales_order_items WHERE order_id = @order2_id AND material_code = 'FR-5000-A');

INSERT INTO sales_order_items (order_id, material_code, material_name, length, width, thickness, rolls, sqm, unit_price, amount, remark, is_deleted)
SELECT @order2_id, 'FR-5001-B', '绝缘阻燃薄膜', 100, 1200, 0.125, 8, 384.00, 38.00, 14592.00, NULL, 0
WHERE @order2_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sales_order_items WHERE order_id = @order2_id AND material_code = 'FR-5001-B');

-- 测试订单3：广州富士康科技集团
INSERT INTO sales_orders (order_no, customer, customer_order_no, total_amount, total_area, order_date, delivery_date, delivery_address, status, remark, created_by, is_deleted)
SELECT 'SO-20260112-003', '广州富士康科技集团', 'FX-PO-20260112-A', 32000.00, 1500.00, '2026-01-12', '2026-01-22', '广东省广州市增城区富士康科技园三号门', 'processing', '分批发货', 'admin', 0
WHERE NOT EXISTS (SELECT 1 FROM sales_orders WHERE order_no = 'SO-20260112-003');

SET @order3_id = (SELECT id FROM sales_orders WHERE order_no = 'SO-20260112-003');

INSERT INTO sales_order_items (order_id, material_code, material_name, length, width, thickness, rolls, sqm, unit_price, amount, remark, is_deleted)
SELECT @order3_id, 'FR-6000-X', '防静电阻燃膜', 200, 1000, 0.050, 25, 1000.00, 22.00, 22000.00, '大批量', 0
WHERE @order3_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sales_order_items WHERE order_id = @order3_id AND material_code = 'FR-6000-X');

INSERT INTO sales_order_items (order_id, material_code, material_name, length, width, thickness, rolls, sqm, unit_price, amount, remark, is_deleted)
SELECT @order3_id, 'FR-6001-Y', '双面胶阻燃带', 50, 600, 0.100, 20, 240.00, 55.00, 13200.00, NULL, 0
WHERE @order3_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sales_order_items WHERE order_id = @order3_id AND material_code = 'FR-6001-Y');

-- =====================================================
-- 2. 插入测试发货单
-- =====================================================

-- 发货单1：针对华为的订单（已发货）
INSERT INTO delivery_notices (notice_no, order_id, order_no, customer, customer_order_no, delivery_date, delivery_address, contact_person, contact_phone, carrier_name, carrier_phone, status, remark, created_by, is_deleted)
SELECT 'DN202601100001', @order1_id, 'SO-20260110-001', '深圳华为技术有限公司', 'HW-PO-2026-0088', '2026-01-14', '广东省深圳市龙岗区坂田街道华为基地F区', '张经理', '13800138001', '顺丰速运 SF1234567890', '400-811-1111', 'shipped', '已发货，预计明天到达', 'admin', 0
WHERE @order1_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM delivery_notices WHERE notice_no = 'DN202601100001');

SET @notice1_id = (SELECT id FROM delivery_notices WHERE notice_no = 'DN202601100001');
SET @order1_item1_id = (SELECT id FROM sales_order_items WHERE order_id = @order1_id AND material_code = 'FR-4725-A' LIMIT 1);
SET @order1_item2_id = (SELECT id FROM sales_order_items WHERE order_id = @order1_id AND material_code = 'FR-4725-B' LIMIT 1);

-- 发货单1明细
INSERT INTO delivery_notice_items (notice_id, order_item_id, material_code, material_name, spec, quantity, area_size, box_count, gross_weight, total_weight, remark, is_deleted)
SELECT @notice1_id, @order1_item1_id, 'FR-4725-A', '阻燃聚酯薄膜（黑色）', '0.075*1000*100', 20, 400.00, 4, 12.50, 50.00, '整单发', 0
WHERE @notice1_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM delivery_notice_items WHERE notice_id = @notice1_id AND material_code = 'FR-4725-A');

INSERT INTO delivery_notice_items (notice_id, order_item_id, material_code, material_name, spec, quantity, area_size, box_count, gross_weight, total_weight, remark, is_deleted)
SELECT @notice1_id, @order1_item2_id, 'FR-4725-B', '阻燃聚酯薄膜（白色）', '0.050*1000*100', 15, 300.00, 3, 10.00, 30.00, NULL, 0
WHERE @notice1_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM delivery_notice_items WHERE notice_id = @notice1_id AND material_code = 'FR-4725-B');

-- 发货单2：针对比亚迪的订单（草稿）
INSERT INTO delivery_notices (notice_no, order_id, order_no, customer, customer_order_no, delivery_date, delivery_address, contact_person, contact_phone, carrier_name, carrier_phone, status, remark, created_by, is_deleted)
SELECT 'DN202601110002', @order2_id, 'SO-20260111-002', '东莞市比亚迪电子有限公司', 'BYD-2026-00156', '2026-01-15', '广东省东莞市大朗镇比亚迪工业园A栋', '李小姐', '13900139002', '德邦物流', '95353', 'draft', '等待确认', 'admin', 0
WHERE @order2_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM delivery_notices WHERE notice_no = 'DN202601110002');

SET @notice2_id = (SELECT id FROM delivery_notices WHERE notice_no = 'DN202601110002');
SET @order2_item1_id = (SELECT id FROM sales_order_items WHERE order_id = @order2_id AND material_code = 'FR-5000-A' LIMIT 1);

INSERT INTO delivery_notice_items (notice_id, order_item_id, material_code, material_name, spec, quantity, area_size, box_count, gross_weight, total_weight, remark, is_deleted)
SELECT @notice2_id, @order2_item1_id, 'FR-5000-A', '高温阻燃胶带', '0.080*500*50', 30, 300.00, 6, 8.50, 51.00, '易碎品', 0
WHERE @notice2_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM delivery_notice_items WHERE notice_id = @notice2_id AND material_code = 'FR-5000-A');

-- 发货单3：针对富士康的订单（部分发货，第一批）
INSERT INTO delivery_notices (notice_no, order_id, order_no, customer, customer_order_no, delivery_date, delivery_address, contact_person, contact_phone, carrier_name, carrier_phone, status, remark, created_by, is_deleted)
SELECT 'DN202601120003', @order3_id, 'SO-20260112-003', '广州富士康科技集团', 'FX-PO-20260112-A', '2026-01-13', '广东省广州市增城区富士康科技园三号门', '王工', '13700137003', '京东物流 JD0012345678', '400-656-2211', 'shipped', '第一批发货', 'admin', 0
WHERE @order3_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM delivery_notices WHERE notice_no = 'DN202601120003');

SET @notice3_id = (SELECT id FROM delivery_notices WHERE notice_no = 'DN202601120003');
SET @order3_item1_id = (SELECT id FROM sales_order_items WHERE order_id = @order3_id AND material_code = 'FR-6000-X' LIMIT 1);

INSERT INTO delivery_notice_items (notice_id, order_item_id, material_code, material_name, spec, quantity, area_size, box_count, gross_weight, total_weight, remark, is_deleted)
SELECT @notice3_id, @order3_item1_id, 'FR-6000-X', '防静电阻燃膜', '0.050*1000*200', 15, 600.00, 5, 15.00, 75.00, '第一批15卷', 0
WHERE @notice3_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM delivery_notice_items WHERE notice_id = @notice3_id AND material_code = 'FR-6000-X');

-- 发货单4：针对富士康的订单（部分发货，第二批 - 草稿状态）
INSERT INTO delivery_notices (notice_no, order_id, order_no, customer, customer_order_no, delivery_date, delivery_address, contact_person, contact_phone, carrier_name, carrier_phone, status, remark, created_by, is_deleted)
SELECT 'DN202601140004', @order3_id, 'SO-20260112-003', '广州富士康科技集团', 'FX-PO-20260112-A', '2026-01-16', '广东省广州市增城区富士康科技园三号门', '王工', '13700137003', '', '', 'draft', '第二批待发', 'admin', 0
WHERE @order3_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM delivery_notices WHERE notice_no = 'DN202601140004');

SET @notice4_id = (SELECT id FROM delivery_notices WHERE notice_no = 'DN202601140004');

INSERT INTO delivery_notice_items (notice_id, order_item_id, material_code, material_name, spec, quantity, area_size, box_count, gross_weight, total_weight, remark, is_deleted)
SELECT @notice4_id, @order3_item1_id, 'FR-6000-X', '防静电阻燃膜', '0.050*1000*200', 10, 400.00, 4, 15.00, 60.00, '第二批剩余10卷', 0
WHERE @notice4_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM delivery_notice_items WHERE notice_id = @notice4_id AND material_code = 'FR-6000-X');

SET @order3_item2_id = (SELECT id FROM sales_order_items WHERE order_id = @order3_id AND material_code = 'FR-6001-Y' LIMIT 1);

INSERT INTO delivery_notice_items (notice_id, order_item_id, material_code, material_name, spec, quantity, area_size, box_count, gross_weight, total_weight, remark, is_deleted)
SELECT @notice4_id, @order3_item2_id, 'FR-6001-Y', '双面胶阻燃带', '0.100*600*50', 20, 240.00, 4, 8.00, 32.00, NULL, 0
WHERE @notice4_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM delivery_notice_items WHERE notice_id = @notice4_id AND material_code = 'FR-6001-Y');

-- =====================================================
-- 3. 验证数据
-- =====================================================
SELECT '===== 销售订单 =====' AS '数据类型';
SELECT id, order_no, customer, total_amount, status FROM sales_orders WHERE is_deleted = 0 ORDER BY id;

SELECT '===== 销售订单明细 =====' AS '数据类型';
SELECT soi.id, so.order_no, soi.material_code, soi.material_name, soi.rolls, soi.sqm 
FROM sales_order_items soi 
JOIN sales_orders so ON soi.order_id = so.id 
WHERE so.is_deleted = 0 AND soi.is_deleted = 0;

SELECT '===== 发货通知单 =====' AS '数据类型';
SELECT id, notice_no, order_no, customer, delivery_date, carrier_name, status FROM delivery_notices WHERE is_deleted = 0 ORDER BY id;

SELECT '===== 发货通知单明细 =====' AS '数据类型';
SELECT dni.id, dn.notice_no, dni.material_code, dni.material_name, dni.quantity, dni.area_size, dni.box_count, dni.total_weight
FROM delivery_notice_items dni
JOIN delivery_notices dn ON dni.notice_id = dn.id
WHERE dn.is_deleted = 0;

SELECT '测试数据插入完成!' AS '状态';
