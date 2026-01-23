-- 此SQL文件已不再需要
-- 实体类已移除 customerCode、productName、productCode 字段
-- 数据库表结构无需修改

-- 如果之前执行过此SQL添加了字段，可以执行以下语句删除：
-- ALTER TABLE `sales_orders` DROP COLUMN `customer_code`;
-- ALTER TABLE `sales_orders` DROP INDEX `idx_customer_code`;
-- ALTER TABLE `sales_order_items` DROP COLUMN `product_name`;
-- ALTER TABLE `sales_order_items` DROP COLUMN `product_code`;
