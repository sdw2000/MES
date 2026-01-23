-- =====================================================
-- 修复销售订单数据
-- =====================================================

-- 检查第一个订单是否有明细
SELECT 
    so.id,
    so.order_no,
    so.customer,
    COUNT(soi.id) as item_count
FROM sales_orders so
LEFT JOIN sales_order_items soi ON so.id = soi.order_id AND soi.is_deleted = 0
WHERE so.order_no = 'SO-20250105-001'
GROUP BY so.id, so.order_no, so.customer;

-- 如果第一个订单没有明细，补充插入
INSERT INTO `sales_order_items` 
(`order_id`, `material_code`, `material_name`, `length`, `width`, `thickness`, `rolls`, `sqm`, `unit_price`, `amount`, `created_by`, `updated_by`) 
SELECT 
    so.id,
    'MT-001',
    '聚丙烯胶带',
    1000.00,
    50.00,
    0.080,
    10,
    500.00,
    25.00,
    12500.00,
    'admin',
    'admin'
FROM sales_orders so
WHERE so.order_no = 'SO-20250105-001'
AND NOT EXISTS (
    SELECT 1 FROM sales_order_items soi 
    WHERE soi.order_id = so.id AND soi.is_deleted = 0
);

-- 验证修复结果
SELECT 
    so.order_no,
    so.customer,
    so.total_amount,
    so.total_area,
    soi.material_code,
    soi.material_name,
    soi.sqm,
    soi.unit_price,
    soi.amount
FROM sales_orders so
LEFT JOIN sales_order_items soi ON so.id = soi.order_id AND soi.is_deleted = 0
WHERE so.order_no IN ('SO-20250105-001', 'SO-20250105-002')
AND so.is_deleted = 0
ORDER BY so.order_no, soi.id;
