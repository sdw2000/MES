-- 清理重复的列
ALTER TABLE customers DROP COLUMN sales_user_id;
ALTER TABLE customers DROP COLUMN documentation_person_user_id;

ALTER TABLE sales_orders DROP COLUMN IF EXISTS sales_user_id;
ALTER TABLE sales_orders DROP COLUMN IF EXISTS documentation_person_user_id;

-- 验证
DESC customers;
DESC sales_orders;
