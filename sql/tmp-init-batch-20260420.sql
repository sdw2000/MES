USE erp;

START TRANSACTION;

-- 1) 薄膜：每个 film_stock 初始化 1 条可用批次（若该库存尚无 20260420 可用批次）
INSERT INTO film_stock_detail (
  stock_id, material_code, batch_no, roll_no,
  thickness, width, length, area,
  quality_level, quality_status,
  warehouse, location, supplier,
  storage_date, status, remark,
  create_by, create_time, update_by, update_time, is_deleted
)
SELECT
  fs.id,
  fs.material_code,
  '20260420' AS batch_no,
  CONCAT('INIT-20260420-F-', fs.id) AS roll_no,
  COALESCE(fs.thickness, 0),
  COALESCE(fs.width, 0),
  NULL,
  CASE
    WHEN COALESCE(fs.available_area, 0) > 0 THEN fs.available_area
    WHEN COALESCE(fs.total_area, 0) > 0 THEN fs.total_area
    ELSE 1
  END AS area,
  'A' AS quality_level,
  'qualified' AS quality_status,
  '原料仓' AS warehouse,
  COALESCE(NULLIF(fs.remark, ''), 'F-INIT') AS location,
  NULL AS supplier,
  '2026-04-20' AS storage_date,
  'available' AS status,
  'init batch 20260420' AS remark,
  'system' AS create_by,
  NOW() AS create_time,
  'system' AS update_by,
  NOW() AS update_time,
  0 AS is_deleted
FROM film_stock fs
WHERE COALESCE(fs.is_deleted, 0) = 0
  AND NOT EXISTS (
    SELECT 1
    FROM film_stock_detail d
    WHERE d.stock_id = fs.id
      AND COALESCE(d.is_deleted, 0) = 0
      AND d.batch_no = '20260420'
      AND d.status = 'available'
  );

-- 2) 化工/原材料：每个 chemical_stock 初始化 1 条可用批次（若该库存尚无 20260420 可用批次）
INSERT INTO chemical_stock_detail (
  stock_id, material_code, batch_no, barrel_no,
  unit, weight, net_weight, remaining_weight,
  quality_level, quality_status,
  warehouse, location, supplier,
  storage_date, status, is_opened,
  danger_level, remark,
  create_by, create_time, update_by, update_time, is_deleted
)
SELECT
  cs.id,
  cs.material_code,
  '20260420' AS batch_no,
  CONCAT('INIT-20260420-C-', cs.id) AS barrel_no,
  COALESCE(NULLIF(cs.unit, ''), 'bucket') AS unit,
  CASE
    WHEN COALESCE(cs.unit_weight, 0) > 0 THEN cs.unit_weight
    WHEN COALESCE(cs.available_quantity, 0) > 0 THEN cs.available_quantity
    WHEN COALESCE(cs.total_quantity, 0) > 0 THEN cs.total_quantity
    ELSE 1
  END AS weight,
  NULL AS net_weight,
  NULL AS remaining_weight,
  'A' AS quality_level,
  'qualified' AS quality_status,
  '化工仓' AS warehouse,
  COALESCE(NULLIF(cs.remark, ''), 'C-INIT') AS location,
  NULL AS supplier,
  '2026-04-20' AS storage_date,
  'available' AS status,
  0 AS is_opened,
  NULL AS danger_level,
  'init batch 20260420' AS remark,
  'system' AS create_by,
  NOW() AS create_time,
  'system' AS update_by,
  NOW() AS update_time,
  0 AS is_deleted
FROM chemical_stock cs
WHERE COALESCE(cs.is_deleted, 0) = 0
  AND NOT EXISTS (
    SELECT 1
    FROM chemical_stock_detail d
    WHERE d.stock_id = cs.id
      AND COALESCE(d.is_deleted, 0) = 0
      AND d.batch_no = '20260420'
      AND d.status = 'available'
  );

-- 3) 薄膜汇总回刷（按明细）
UPDATE film_stock fs
JOIN (
  SELECT
    d.stock_id,
    SUM(CASE WHEN d.status <> 'used' THEN COALESCE(d.area, 0) ELSE 0 END) AS total_area,
    SUM(CASE WHEN d.status = 'available' THEN COALESCE(d.area, 0) ELSE 0 END) AS available_area,
    SUM(CASE WHEN d.status = 'locked' THEN COALESCE(d.area, 0) ELSE 0 END) AS locked_area,
    SUM(CASE WHEN d.status <> 'used' THEN 1 ELSE 0 END) AS total_rolls,
    SUM(CASE WHEN d.status = 'available' THEN 1 ELSE 0 END) AS available_rolls,
    SUM(CASE WHEN d.status = 'locked' THEN 1 ELSE 0 END) AS locked_rolls
  FROM film_stock_detail d
  WHERE COALESCE(d.is_deleted, 0) = 0
  GROUP BY d.stock_id
) x ON x.stock_id = fs.id
SET
  fs.total_area = x.total_area,
  fs.available_area = x.available_area,
  fs.locked_area = x.locked_area,
  fs.total_rolls = x.total_rolls,
  fs.available_rolls = x.available_rolls,
  fs.locked_rolls = x.locked_rolls,
  fs.status = CASE
    WHEN x.available_area <= 0 THEN 'out_of_stock'
    WHEN fs.safety_stock IS NOT NULL AND x.available_area < fs.safety_stock THEN 'low_stock'
    ELSE 'active'
  END,
  fs.update_time = NOW();

-- 4) 化工汇总回刷（按明细）
UPDATE chemical_stock cs
JOIN (
  SELECT
    d.stock_id,
    ROUND(SUM(CASE WHEN d.status = 'available' THEN COALESCE(d.weight, 0) ELSE 0 END), 0) AS available_qty,
    ROUND(SUM(CASE WHEN d.status = 'locked' THEN COALESCE(d.weight, 0) ELSE 0 END), 0) AS locked_qty,
    SUM(CASE WHEN d.status <> 'used' THEN 1 ELSE 0 END) AS bucket_count
  FROM chemical_stock_detail d
  WHERE COALESCE(d.is_deleted, 0) = 0
  GROUP BY d.stock_id
) x ON x.stock_id = cs.id
SET
  cs.available_quantity = x.available_qty,
  cs.locked_quantity = x.locked_qty,
  cs.total_quantity = x.available_qty + x.locked_qty,
  cs.bucket_count = x.bucket_count,
  cs.status = CASE
    WHEN x.available_qty <= 0 THEN 'out_of_stock'
    WHEN cs.safety_stock IS NOT NULL AND cs.safety_stock > 0 AND x.available_qty < cs.safety_stock THEN 'low_stock'
    ELSE 'active'
  END,
  cs.update_time = NOW();

COMMIT;

-- 校验结果
SELECT COUNT(*) AS film_with_batch_20260420
FROM film_stock_detail
WHERE COALESCE(is_deleted, 0) = 0
  AND batch_no = '20260420'
  AND status = 'available';

SELECT COUNT(*) AS chemical_with_batch_20260420
FROM chemical_stock_detail
WHERE COALESCE(is_deleted, 0) = 0
  AND batch_no = '20260420'
  AND status = 'available';
