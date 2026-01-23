-- 添加 color_code 字段到 sales_order_items 表
ALTER TABLE sales_order_items ADD COLUMN color_code VARCHAR(20) COMMENT '颜色代码';
