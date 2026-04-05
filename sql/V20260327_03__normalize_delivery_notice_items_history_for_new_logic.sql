-- 历史数据修复：发货明细按新逻辑统一
-- 目标：
-- 1) 明细只保留料号作为关键索引，material_name 清空（展示由 tape_spec.product_name 动态带出）
-- 2) 规格统一为：μm*mm*m（带单位）

START TRANSACTION;

-- A. 回填缺失料号（从销售订单明细带出）
UPDATE delivery_notice_items dni
JOIN sales_order_items soi ON soi.id = dni.order_item_id
SET dni.material_code = soi.material_code
WHERE (dni.material_code IS NULL OR TRIM(dni.material_code) = '')
  AND soi.material_code IS NOT NULL
  AND TRIM(soi.material_code) <> '';

-- B. 有订单明细关联时，用订单厚宽长重建规格（最准确）
UPDATE delivery_notice_items dni
JOIN sales_order_items soi ON soi.id = dni.order_item_id
SET dni.spec = CONCAT(
    COALESCE(NULLIF(TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(soi.thickness AS CHAR))), ''), '0'),
    'μm*',
    COALESCE(NULLIF(TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(soi.width AS CHAR))), ''), '0'),
    'mm*',
    COALESCE(NULLIF(TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(soi.length AS CHAR))), ''), '0'),
    'm'
)
WHERE soi.thickness IS NOT NULL
  AND soi.width IS NOT NULL
  AND soi.length IS NOT NULL;

-- C. 无法关联订单厚宽长时，尽量把旧格式 a*b*c 补齐单位
UPDATE delivery_notice_items
SET spec = CONCAT(
    TRIM(SUBSTRING_INDEX(spec, '*', 1)),
    'μm*',
    TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(spec, '*', 2), '*', -1)),
    'mm*',
    TRIM(SUBSTRING_INDEX(spec, '*', -1)),
    'm'
)
WHERE spec IS NOT NULL
  AND TRIM(spec) <> ''
  AND spec NOT LIKE '%μm%'
  AND spec NOT LIKE '%mm%'
  AND spec REGEXP '^[^*]+\\*[^*]+\\*[^*]+$';

-- D. 清空历史冗余产品名（后续展示由 tape_spec 按料号动态带出）
UPDATE delivery_notice_items
SET material_name = NULL
WHERE material_name IS NOT NULL
  AND TRIM(material_name) <> '';

COMMIT;

-- 验证抽样：
SELECT id, notice_id, order_item_id, material_code, material_name, spec
FROM delivery_notice_items
ORDER BY id DESC
LIMIT 20;
