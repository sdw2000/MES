-- 采购明细新增薄膜规格原文存储字段
ALTER TABLE purchase_order_items
    ADD COLUMN film_spec_raw VARCHAR(255) NULL COMMENT '薄膜规格原文（前端输入原样）' AFTER raw_spec;
