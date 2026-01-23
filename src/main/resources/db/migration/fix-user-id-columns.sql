-- 先查看当前表结构
DESC customers;

-- 1. 第一步：添加新列
ALTER TABLE customers ADD COLUMN sales_user_id BIGINT COMMENT '销售用户ID' AFTER source;
ALTER TABLE customers ADD COLUMN documentation_person_user_id BIGINT COMMENT '跟单员用户ID' AFTER sales_user_id;

-- 2. 第二步：删除旧的索引
DROP INDEX IF EXISTS idx_customers_sales_person ON customers;
DROP INDEX IF EXISTS idx_customers_sales_department ON customers;

-- 3. 第三步：删除旧列
ALTER TABLE customers DROP COLUMN sales_person;
ALTER TABLE customers DROP COLUMN sales_department;

-- 4. 第四步：重命名新列为标准名称（可选）
ALTER TABLE customers CHANGE COLUMN sales_user_id sales BIGINT COMMENT '销售用户ID';
ALTER TABLE customers CHANGE COLUMN documentation_person_user_id documentation_person BIGINT COMMENT '跟单员用户ID';

-- 5. 第五步：创建新索引
CREATE INDEX idx_customers_sales_user_id ON customers(sales);
CREATE INDEX idx_customers_documentation_person_user_id ON customers(documentation_person);

-- 6. 处理sales_orders表
ALTER TABLE sales_orders ADD COLUMN sales_user_id BIGINT COMMENT '销售用户ID' AFTER customer_order_no;
ALTER TABLE sales_orders ADD COLUMN documentation_person_user_id BIGINT COMMENT '跟单员用户ID' AFTER sales_user_id;

DROP INDEX IF EXISTS idx_sales_orders_sales ON sales_orders;
DROP INDEX IF EXISTS idx_sales_orders_documentation_person ON sales_orders;

ALTER TABLE sales_orders DROP COLUMN IF EXISTS sales;
ALTER TABLE sales_orders DROP COLUMN IF EXISTS documentation_person;

ALTER TABLE sales_orders CHANGE COLUMN sales_user_id sales BIGINT COMMENT '销售用户ID';
ALTER TABLE sales_orders CHANGE COLUMN documentation_person_user_id documentation_person BIGINT COMMENT '跟单员用户ID';

CREATE INDEX idx_sales_orders_sales_user_id ON sales_orders(sales);
CREATE INDEX idx_sales_orders_documentation_person_user_id ON sales_orders(documentation_person);

-- 迁移完成
SELECT 'Migration completed successfully!' AS result;
