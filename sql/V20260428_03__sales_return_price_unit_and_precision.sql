-- 退货明细：补充计价单位，并统一单价精度为4位
ALTER TABLE sales_return_items
    ADD COLUMN IF NOT EXISTS price_unit VARCHAR(16) NULL COMMENT '计价单位: 卷/m/㎡' AFTER sqm;

ALTER TABLE sales_return_items
    MODIFY COLUMN unit_price DECIMAL(18,4) DEFAULT 0;
