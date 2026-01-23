-- ===================================================================
-- 客户表和销售订单表字段转换为用户ID迁移脚本
-- 将sales和documentation_person字段从VARCHAR改为BIGINT，存储users表的ID
-- ===================================================================

-- 1. 备份原有数据
ALTER TABLE customers ADD COLUMN backup_sales_name VARCHAR(100) COMMENT '备份：原销售名称' AFTER documentation_person;
UPDATE customers SET backup_sales_name = sales WHERE sales IS NOT NULL;

ALTER TABLE customers ADD COLUMN backup_doc_person_name VARCHAR(100) COMMENT '备份：原跟单员名称' AFTER backup_sales_name;
UPDATE customers SET backup_doc_person_name = documentation_person WHERE documentation_person IS NOT NULL;

-- 2. 删除旧字段的索引
DROP INDEX IF EXISTS idx_customers_sales ON customers;
DROP INDEX IF EXISTS idx_customers_documentation_person ON customers;

-- 3. 修改字段类型：从VARCHAR改为BIGINT
ALTER TABLE customers MODIFY COLUMN sales BIGINT COMMENT '销售用户ID' AFTER source;
ALTER TABLE customers MODIFY COLUMN documentation_person BIGINT COMMENT '跟单员用户ID' AFTER sales;

-- 4. 尝试根据用户名匹配用户ID（如果有用户真实姓名数据）
-- 这是一个可选的数据迁移步骤，如果users表中存储了用户名，可以尝试匹配
-- UPDATE customers c
-- INNER JOIN users u ON c.backup_sales_name = u.real_name
-- SET c.sales = u.id
-- WHERE c.backup_sales_name IS NOT NULL AND c.sales IS NULL;

-- UPDATE customers c
-- INNER JOIN users u ON c.backup_doc_person_name = u.real_name
-- SET c.documentation_person = u.id
-- WHERE c.backup_doc_person_name IS NOT NULL AND c.documentation_person IS NULL;

-- 5. 创建新的索引
CREATE INDEX idx_customers_sales_user_id ON customers(sales);
CREATE INDEX idx_customers_documentation_person_user_id ON customers(documentation_person);

-- 6. 添加外键约束（可选，确保数据完整性）
ALTER TABLE customers ADD CONSTRAINT fk_customers_sales_user 
  FOREIGN KEY (sales) REFERENCES users(id);
ALTER TABLE customers ADD CONSTRAINT fk_customers_documentation_user 
  FOREIGN KEY (documentation_person) REFERENCES users(id);

-- 7. 处理sales_orders表
-- 备份原有数据
ALTER TABLE sales_orders ADD COLUMN backup_sales_name VARCHAR(100) COMMENT '备份：原销售名称' AFTER documentation_person;
UPDATE sales_orders SET backup_sales_name = sales WHERE sales IS NOT NULL;

ALTER TABLE sales_orders ADD COLUMN backup_doc_person_name VARCHAR(100) COMMENT '备份：原跟单员名称' AFTER backup_sales_name;
UPDATE sales_orders SET backup_doc_person_name = documentation_person WHERE documentation_person IS NOT NULL;

-- 删除旧索引
DROP INDEX IF EXISTS idx_sales_orders_sales ON sales_orders;
DROP INDEX IF EXISTS idx_sales_orders_documentation_person ON sales_orders;

-- 修改字段类型
ALTER TABLE sales_orders MODIFY COLUMN sales BIGINT COMMENT '销售用户ID' AFTER customer_order_no;
ALTER TABLE sales_orders MODIFY COLUMN documentation_person BIGINT COMMENT '跟单员用户ID' AFTER sales;

-- 创建新的索引
CREATE INDEX idx_sales_orders_sales_user_id ON sales_orders(sales);
CREATE INDEX idx_sales_orders_documentation_person_user_id ON sales_orders(documentation_person);

-- 添加外键约束
ALTER TABLE sales_orders ADD CONSTRAINT fk_sales_orders_sales_user 
  FOREIGN KEY (sales) REFERENCES users(id);
ALTER TABLE sales_orders ADD CONSTRAINT fk_sales_orders_documentation_user 
  FOREIGN KEY (documentation_person) REFERENCES users(id);

-- 完成：数据库迁移完成
SELECT 'Migration completed: Converted sales and documentation_person fields to user IDs' AS result;
