-- 修复customers表字段名和类型
-- 1. 添加新列
ALTER TABLE customers ADD COLUMN sales BIGINT COMMENT '销售用户ID' AFTER source;
ALTER TABLE customers ADD COLUMN documentation_person BIGINT COMMENT '跟单员用户ID' AFTER sales;

-- 2. 删除旧列
ALTER TABLE customers DROP COLUMN sales_person;
ALTER TABLE customers DROP COLUMN sales_department;

-- 3. 创建索引
CREATE INDEX idx_customers_sales_user_id ON customers(sales);
CREATE INDEX idx_customers_documentation_person_user_id ON customers(documentation_person);

-- 修复sales_orders表
-- 1. 添加新列
ALTER TABLE sales_orders ADD COLUMN sales BIGINT COMMENT '销售用户ID' AFTER customer_order_no;
ALTER TABLE sales_orders ADD COLUMN documentation_person BIGINT COMMENT '跟单员用户ID' AFTER sales;

-- 2. 创建索引
CREATE INDEX idx_sales_orders_sales_user_id ON sales_orders(sales);
CREATE INDEX idx_sales_orders_documentation_person_user_id ON sales_orders(documentation_person);

-- 完成
SELECT 'Migration completed!' AS result;
