-- ===================================================================
-- 客户表字段修改迁移脚本
-- 将销售部门改为跟单员，将销售负责人改为销售
-- ===================================================================

-- 1. 备份原有数据
-- 如果表中已有销售部门数据，可以先备份到新字段
ALTER TABLE customers ADD COLUMN temp_sales_person VARCHAR(100) COMMENT '临时备份：原销售负责人' AFTER sales_department;
UPDATE customers SET temp_sales_person = sales_person WHERE sales_person IS NOT NULL;

ALTER TABLE customers ADD COLUMN temp_sales_department VARCHAR(100) COMMENT '临时备份：原所属部门' AFTER temp_sales_person;
UPDATE customers SET temp_sales_department = sales_department WHERE sales_department IS NOT NULL;

-- 2. 删除旧字段
ALTER TABLE customers DROP COLUMN sales_person;
ALTER TABLE customers DROP COLUMN sales_department;

-- 3. 添加新字段
ALTER TABLE customers ADD COLUMN sales VARCHAR(100) COMMENT '销售（原销售负责人）' AFTER source;
ALTER TABLE customers ADD COLUMN documentation_person VARCHAR(100) COMMENT '跟单员（原所属部门）' AFTER sales;

-- 4. 从临时字段复制数据
UPDATE customers SET sales = temp_sales_person WHERE temp_sales_person IS NOT NULL;
UPDATE customers SET documentation_person = temp_sales_department WHERE temp_sales_department IS NOT NULL;

-- 5. 删除临时字段
ALTER TABLE customers DROP COLUMN temp_sales_person;
ALTER TABLE customers DROP COLUMN temp_sales_department;

-- 6. 创建索引以提升查询性能
CREATE INDEX idx_customers_sales ON customers(sales);
CREATE INDEX idx_customers_documentation_person ON customers(documentation_person);

-- 7. 修改sales_orders表，添加销售和跟单员字段
ALTER TABLE sales_orders ADD COLUMN sales VARCHAR(100) COMMENT '销售' AFTER customer_order_no;
ALTER TABLE sales_orders ADD COLUMN documentation_person VARCHAR(100) COMMENT '跟单员' AFTER sales;

-- 8. 从customers表复制销售和跟单员信息到sales_orders表
UPDATE sales_orders so
INNER JOIN customers c ON so.customer = c.customer_name
SET so.sales = c.sales, so.documentation_person = c.documentation_person
WHERE c.sales IS NOT NULL OR c.documentation_person IS NOT NULL;

-- 9. 创建sales_orders表的索引
CREATE INDEX idx_sales_orders_sales ON sales_orders(sales);
CREATE INDEX idx_sales_orders_documentation_person ON sales_orders(documentation_person);

-- 完成：数据库迁移完成
SELECT 'Migration completed: Updated customer and sales_orders tables with new fields' AS result;
