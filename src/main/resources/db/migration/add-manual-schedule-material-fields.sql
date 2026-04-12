-- 为手动排程表补充产品编码/产品名称字段
ALTER TABLE manual_schedule
  ADD COLUMN material_code VARCHAR(255) NULL COMMENT '产品编码/料号（手工排程录入）' AFTER order_no,
  ADD COLUMN material_name VARCHAR(255) NULL COMMENT '产品名称（手工排程录入）' AFTER material_code;

-- 回填已有数据：优先使用订单明细物料信息
UPDATE manual_schedule ms
LEFT JOIN sales_order_items soi ON ms.order_detail_id = soi.id
SET ms.material_code = COALESCE(NULLIF(ms.material_code, ''), soi.material_code),
    ms.material_name = COALESCE(NULLIF(ms.material_name, ''), soi.material_name)
WHERE (ms.material_code IS NULL OR ms.material_code = '')
   OR (ms.material_name IS NULL OR ms.material_name = '');

SELECT 'Migration completed: added manual_schedule.material_code/material_name' AS result;