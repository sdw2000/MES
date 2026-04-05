-- 二次修复：处理历史规格中的编码与旧格式问题
-- 1) ?m -> μm
-- 2) 旧格式 length*width*thicknessmm -> thickness(μm)*width(mm)*length(m)
-- 3) 可关联订单明细时，以订单厚宽长为准重建规格

START TRANSACTION;

-- A. 修复因字符集导致的 ?m
UPDATE delivery_notice_items
SET spec = REPLACE(spec, '?m', 'μm')
WHERE spec IS NOT NULL
  AND spec LIKE '%?m%';

-- B. 旧格式：length*width*thicknessmm（例：1000*100*0.050mm）
UPDATE delivery_notice_items
SET spec = CONCAT(
    COALESCE(NULLIF(TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST((CAST(REPLACE(SUBSTRING_INDEX(TRIM(spec), '*', -1), 'mm', '') AS DECIMAL(20,6)) * 1000) AS CHAR))), ''), '0'),
    'μm*',
    TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(TRIM(spec), '*', 2), '*', -1)),
    'mm*',
    TRIM(SUBSTRING_INDEX(TRIM(spec), '*', 1)),
    'm'
)
WHERE spec IS NOT NULL
  AND TRIM(spec) REGEXP '^[0-9]+(\\.[0-9]+)?\\*[0-9]+(\\.[0-9]+)?\\*[0-9]+(\\.[0-9]+)?mm$';

-- C. 有订单明细尺寸时，统一以订单厚宽长重建规格（权威来源）
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

COMMIT;

-- 验证：仍不符合目标格式的样本
SELECT id, order_item_id, spec
FROM delivery_notice_items
WHERE spec IS NULL
   OR TRIM(spec) = ''
   OR TRIM(spec) NOT REGEXP '^[0-9]+(\\.[0-9]+)?μm\\*[0-9]+(\\.[0-9]+)?mm\\*[0-9]+(\\.[0-9]+)?m$'
ORDER BY id DESC
LIMIT 30;
