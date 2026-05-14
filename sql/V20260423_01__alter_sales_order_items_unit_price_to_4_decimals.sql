-- 目的：修复销售订单明细单价精度丢失问题
-- 现象：新增订单时按报价带入 4 位小数单价（如 0.0396），保存后被截断为 2 位（0.04）
-- 根因：sales_order_items.unit_price 当前是 DECIMAL(10,2)
-- 方案：调整为 DECIMAL(10,4)，与报价明细精度一致

ALTER TABLE sales_order_items
    MODIFY COLUMN unit_price DECIMAL(10,4) NOT NULL;
