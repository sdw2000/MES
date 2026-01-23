-- ================================================
-- 销售订单表结构更新
-- 根据实际业务需求调整字段
-- ================================================

-- 修改订单主表，添加客户代码字段
ALTER TABLE `sales_orders` 
    ADD COLUMN `customer_code` VARCHAR(50) COMMENT '客户代码' AFTER `order_no`,
    MODIFY COLUMN `customer` VARCHAR(200) COMMENT '客户名称（冗余字段，可从客户代码查询）';

-- 修改订单明细表，调整字段
-- 长度改为m单位存储，移除单价和金额（按需保留）
ALTER TABLE `sales_order_items`
    ADD COLUMN `product_name` VARCHAR(200) COMMENT '产品名称' AFTER `order_id`,
    ADD COLUMN `product_code` VARCHAR(100) COMMENT '产品编码' AFTER `product_name`,
    MODIFY COLUMN `length` DECIMAL(15,2) COMMENT '长度/m',
    MODIFY COLUMN `width` DECIMAL(15,2) COMMENT '宽度/mm';

-- 如果需要，可以运行以下语句迁移旧数据
-- UPDATE sales_order_items SET product_name = material_name, product_code = material_code WHERE product_name IS NULL;

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_customer_code ON sales_orders(customer_code);
CREATE INDEX IF NOT EXISTS idx_product_code ON sales_order_items(product_code);
