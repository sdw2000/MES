-- ===================================================================
-- 客户表和销售订单表字段转换为用户ID迁移脚本 (简化版)
-- ===================================================================

-- 先查看当前列结构
DESC customers;
DESC sales_orders;

-- 1. customers表：修改字段类型
ALTER TABLE customers MODIFY COLUMN sales BIGINT COMMENT '销售用户ID';
ALTER TABLE customers MODIFY COLUMN documentation_person BIGINT COMMENT '跟单员用户ID';

-- 2. 删除旧索引
DROP INDEX IF EXISTS idx_customers_sales ON customers;
DROP INDEX IF EXISTS idx_customers_documentation_person ON customers;

-- 3. 创建新索引
CREATE INDEX idx_customers_sales_user_id ON customers(sales);
CREATE INDEX idx_customers_documentation_person_user_id ON customers(documentation_person);

-- 4. sales_orders表：修改字段类型  
ALTER TABLE sales_orders MODIFY COLUMN sales BIGINT COMMENT '销售用户ID';
ALTER TABLE sales_orders MODIFY COLUMN documentation_person BIGINT COMMENT '跟单员用户ID';

-- 5. 删除旧索引
DROP INDEX IF EXISTS idx_sales_orders_sales ON sales_orders;
DROP INDEX IF EXISTS idx_sales_orders_documentation_person ON sales_orders;

-- 6. 创建新索引
CREATE INDEX idx_sales_orders_sales_user_id ON sales_orders(sales);
CREATE INDEX idx_sales_orders_documentation_person_user_id ON sales_orders(documentation_person);

-- 迁移完成
SELECT 'Migration completed successfully' AS result;
