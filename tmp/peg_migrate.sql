START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS tmp_peg_move;
CREATE TEMPORARY TABLE tmp_peg_move AS
SELECT ts.*
FROM tape_stock ts
WHERE ts.status = 1
  AND ts.material_code LIKE 'PEG-%'
  AND ts.remark LIKE '%[PURCHASE_RECEIPT]%'
  AND IFNULL(ts.total_rolls,0) > 0
  AND IFNULL(ts.reserved_area,0) = 0
  AND IFNULL(ts.consumed_area,0) = 0
  AND ts.remark NOT LIKE '%MIGRATED_TO_RAW_WAREHOUSE%'
  AND NOT EXISTS (
    SELECT 1 FROM tape_stock_log l
    WHERE l.stock_id = ts.id AND l.type = 'OUT'
  );

SELECT 'candidate_rows' AS metric, COUNT(*) AS val FROM tmp_peg_move;
SELECT 'candidate_rolls' AS metric, IFNULL(SUM(total_rolls),0) AS val FROM tmp_peg_move;
SELECT 'candidate_area' AS metric, IFNULL(SUM(total_sqm),0) AS val FROM tmp_peg_move;

INSERT INTO film_stock (
  material_code, material_name, thickness, width, spec_desc,
  total_area, total_rolls, available_area, available_rolls,
  locked_area, locked_rolls, safety_stock, status, remark,
  create_by, update_by, create_time, update_time, is_deleted
)
SELECT
  t.material_code,
  COALESCE(NULLIF(MAX(t.product_name), ''), t.material_code) AS material_name,
  NULLIF(MAX(t.thickness), 0) AS thickness,
  NULLIF(MAX(t.width), 0) AS width,
  NULLIF(MAX(t.spec_desc), '') AS spec_desc,
  ROUND(SUM(IFNULL(t.total_sqm,0)), 2) AS total_area,
  SUM(IFNULL(t.total_rolls,0)) AS total_rolls,
  ROUND(SUM(IFNULL(t.available_area, IFNULL(t.total_sqm,0))), 2) AS available_area,
  SUM(IFNULL(t.total_rolls,0)) AS available_rolls,
  0, 0, 0, 'active', 'db-migration:PEG from tape_stock',
  'db-migration', 'db-migration', NOW(), NOW(), 0
FROM tmp_peg_move t
GROUP BY t.material_code
ON DUPLICATE KEY UPDATE
  material_name = COALESCE(NULLIF(VALUES(material_name), ''), material_name),
  thickness = COALESCE(VALUES(thickness), thickness),
  width = COALESCE(VALUES(width), width),
  spec_desc = COALESCE(NULLIF(VALUES(spec_desc), ''), spec_desc),
  total_area = IFNULL(total_area,0) + VALUES(total_area),
  total_rolls = IFNULL(total_rolls,0) + VALUES(total_rolls),
  available_area = IFNULL(available_area,0) + VALUES(available_area),
  available_rolls = IFNULL(available_rolls,0) + VALUES(available_rolls),
  update_by = 'db-migration',
  update_time = NOW();

SELECT 'upsert_film_stock_rows' AS metric, ROW_COUNT() AS val;

INSERT INTO film_stock_detail (
  stock_id, material_code,
  pack_uom, pack_count, std_uom, std_qty_per_pack,
  batch_no, roll_no, thickness, width, length,
  original_length_m, current_length_m, area,
  quality_status, warehouse, location, supplier,
  status, remark, create_by, create_time, update_by, update_time, is_deleted
)
SELECT
  fs.id,
  t.material_code,
  '卷',
  IFNULL(t.total_rolls,1),
  '㎡',
  CASE WHEN IFNULL(t.total_rolls,0) > 0
       THEN ROUND(IFNULL(t.total_sqm,0) / t.total_rolls, 6)
       ELSE ROUND(IFNULL(t.total_sqm,0), 6)
  END,
  t.batch_no,
  t.batch_no,
  IFNULL(NULLIF(t.thickness,0), 1),
  IFNULL(NULLIF(t.width,0), 1),
  t.length,
  t.length,
  t.length,
  ROUND(IFNULL(t.total_sqm,0), 2),
  'qualified',
  '原料仓',
  t.location,
  '采购来料',
  'available',
  CONCAT(IFNULL(t.remark,''), ';[DB_MIGRATE_FROM_TAPE]'),
  'db-migration', NOW(), 'db-migration', NOW(), 0
FROM tmp_peg_move t
JOIN film_stock fs ON fs.material_code = t.material_code
LEFT JOIN film_stock_detail d ON d.roll_no = t.batch_no
WHERE d.id IS NULL;

SELECT 'insert_film_stock_detail_rows' AS metric, ROW_COUNT() AS val;

UPDATE tape_stock ts
JOIN tmp_peg_move t ON t.id = ts.id
SET ts.total_rolls = 0,
    ts.total_sqm = 0,
    ts.available_area = 0,
    ts.reserved_area = 0,
    ts.consumed_area = 0,
    ts.status = 0,
    ts.remark = CONCAT(IFNULL(ts.remark,''), ';MIGRATED_TO_RAW_WAREHOUSE@', DATE_FORMAT(NOW(), '%Y%m%d%H%i%s')),
    ts.update_time = NOW();

SELECT 'deactivate_tape_stock_rows' AS metric, ROW_COUNT() AS val;

COMMIT;

SELECT material_code, total_rolls, available_rolls, total_area, available_area, spec_desc
FROM film_stock
WHERE material_code LIKE 'PEG-%'
ORDER BY material_code;
