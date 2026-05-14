-- 退货金额精度统一到4位小数
ALTER TABLE sales_return_items
    MODIFY COLUMN amount DECIMAL(18,4) DEFAULT 0;

ALTER TABLE sales_return_orders
    MODIFY COLUMN total_amount DECIMAL(18,4) DEFAULT 0,
    MODIFY COLUMN statement_amount DECIMAL(18,4) DEFAULT 0;
