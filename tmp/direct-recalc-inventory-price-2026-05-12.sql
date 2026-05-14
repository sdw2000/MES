SET @biz_date = '2026-05-12';

START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS tmp_latest_kg_quote;
DROP TEMPORARY TABLE IF EXISTS tmp_latest_quote_all;
DROP TEMPORARY TABLE IF EXISTS tmp_latest_any_quote;
DROP TEMPORARY TABLE IF EXISTS tmp_material_has_kg_quote;

CREATE TEMPORARY TABLE tmp_latest_quote_all AS
SELECT t.material_code, t.unit_price, t.unit
FROM (
  SELECT x.material_code, x.unit_price, x.unit,
         ROW_NUMBER() OVER (PARTITION BY x.material_code ORDER BY x.quote_time DESC, x.id DESC) AS rn
  FROM (
    SELECT qi.id, qi.material_code, qi.unit_price, qi.unit,
           COALESCE(qi.updated_at, qi.created_at, q.updated_at, q.created_at, CONCAT(q.quotation_date,' 00:00:00')) AS quote_time
    FROM quotation_items qi
    LEFT JOIN quotations q ON q.id = qi.quotation_id
    WHERE qi.is_deleted=0 AND IFNULL(q.is_deleted,0)=0
      AND qi.material_code IS NOT NULL AND qi.material_code<>''
      AND qi.unit_price IS NOT NULL AND qi.unit_price>0
    UNION ALL
    SELECT pqi.id, pqi.material_code, pqi.unit_price, pqi.unit,
           COALESCE(pqi.updated_at, pqi.created_at) AS quote_time
    FROM purchase_quotation_items pqi
    WHERE pqi.is_deleted=0
      AND pqi.material_code IS NOT NULL AND pqi.material_code<>''
      AND pqi.unit_price IS NOT NULL AND pqi.unit_price>0
  ) x
) t WHERE t.rn=1;

CREATE TEMPORARY TABLE tmp_latest_kg_quote AS
SELECT material_code, unit_price AS kg_price
FROM tmp_latest_quote_all
WHERE UPPER(REPLACE(IFNULL(unit,''),' ','')) IN ('KG','KGS');

UPDATE chemical_stock_detail csd
JOIN tmp_latest_kg_quote q ON q.material_code = csd.material_code
SET csd.unit_price = q.kg_price, csd.update_time = NOW()
WHERE csd.is_deleted=0 AND csd.status IN ('available','locked');

UPDATE film_stock_detail fsd
JOIN tmp_latest_quote_all q ON q.material_code = fsd.material_code
SET fsd.unit_price = q.unit_price, fsd.update_time = NOW()
WHERE fsd.is_deleted=0 AND fsd.status IN ('available','locked')
  AND UPPER(fsd.material_code) NOT LIKE 'PEG-%'
  AND UPPER(fsd.material_code) <> 'NPZ-D105'
  AND UPPER(REPLACE(IFNULL(q.unit,''),' ','')) IN ('M2');

UPDATE film_stock_detail fsd
JOIN tmp_latest_quote_all q ON q.material_code = fsd.material_code
SET fsd.unit_price = q.unit_price, fsd.update_time = NOW()
WHERE fsd.is_deleted=0 AND fsd.status IN ('available','locked')
  AND UPPER(fsd.material_code) NOT LIKE 'PEG-%'
  AND UPPER(fsd.material_code) <> 'NPZ-D105'
  AND (UPPER(fsd.material_code) LIKE '%LXM%' OR UPPER(fsd.material_code) LIKE '%LXZ%');

UPDATE film_stock_detail fsd
JOIN tmp_latest_quote_all q ON q.material_code = fsd.material_code
SET fsd.unit_price = ROUND(q.unit_price * IFNULL(fsd.thickness,0) * (CASE
  WHEN UPPER(fsd.material_code) LIKE 'PET%' THEN 1.38
  WHEN UPPER(fsd.material_code) LIKE 'PI%' OR UPPER(fsd.material_code) LIKE 'PIM%' THEN 1.42
  WHEN UPPER(fsd.material_code) LIKE 'BOPP%' OR UPPER(fsd.material_code) LIKE 'OPP%' OR UPPER(fsd.material_code) LIKE 'CPP%' OR UPPER(fsd.material_code) LIKE 'PP%' THEN 0.90
  WHEN UPPER(fsd.material_code) LIKE 'PE%' THEN 0.92
  WHEN UPPER(fsd.material_code) LIKE 'PVC%' THEN 1.35
  WHEN UPPER(fsd.material_code) LIKE 'OPS%' OR UPPER(fsd.material_code) LIKE 'PS%' THEN 1.05
  ELSE NULL END) / 1000, 4),
fsd.update_time = NOW()
WHERE fsd.is_deleted=0 AND fsd.status IN ('available','locked')
  AND UPPER(fsd.material_code) NOT LIKE 'PEG-%'
  AND UPPER(fsd.material_code) <> 'NPZ-D105'
  AND UPPER(fsd.material_code) NOT LIKE '%LXM%'
  AND UPPER(fsd.material_code) NOT LIKE '%LXZ%'
  AND UPPER(REPLACE(IFNULL(q.unit,''),' ','')) IN ('KG','KGS')
  AND IFNULL(fsd.thickness,0) > 0
  AND (CASE
  WHEN UPPER(fsd.material_code) LIKE 'PET%' THEN 1.38
  WHEN UPPER(fsd.material_code) LIKE 'PI%' OR UPPER(fsd.material_code) LIKE 'PIM%' THEN 1.42
  WHEN UPPER(fsd.material_code) LIKE 'BOPP%' OR UPPER(fsd.material_code) LIKE 'OPP%' OR UPPER(fsd.material_code) LIKE 'CPP%' OR UPPER(fsd.material_code) LIKE 'PP%' THEN 0.90
  WHEN UPPER(fsd.material_code) LIKE 'PE%' THEN 0.92
  WHEN UPPER(fsd.material_code) LIKE 'PVC%' THEN 1.35
  WHEN UPPER(fsd.material_code) LIKE 'OPS%' OR UPPER(fsd.material_code) LIKE 'PS%' THEN 1.05
  ELSE NULL END) IS NOT NULL;

CREATE TEMPORARY TABLE tmp_latest_any_quote AS
SELECT t.material_code, t.unit_price, t.unit
FROM (
  SELECT x.material_code, x.unit_price, x.unit,
         ROW_NUMBER() OVER (PARTITION BY x.material_code ORDER BY x.quote_time DESC, x.id DESC) AS rn
  FROM (
    SELECT qi.id, qi.material_code, qi.unit_price, qi.unit,
           COALESCE(qi.updated_at, qi.created_at, q.updated_at, q.created_at, CONCAT(q.quotation_date,' 00:00:00')) AS quote_time
    FROM quotation_items qi
    LEFT JOIN quotations q ON q.id = qi.quotation_id
    WHERE qi.is_deleted=0 AND IFNULL(q.is_deleted,0)=0
      AND qi.material_code IS NOT NULL AND qi.material_code<>''
      AND qi.unit_price IS NOT NULL AND qi.unit_price>0
    UNION ALL
    SELECT pqi.id, pqi.material_code, pqi.unit_price, pqi.unit,
           COALESCE(pqi.updated_at, pqi.created_at) AS quote_time
    FROM purchase_quotation_items pqi
    WHERE pqi.is_deleted=0
      AND pqi.material_code IS NOT NULL AND pqi.material_code<>''
      AND pqi.unit_price IS NOT NULL AND pqi.unit_price>0
  ) x
) t WHERE t.rn=1;

CREATE TEMPORARY TABLE tmp_material_has_kg_quote AS
SELECT DISTINCT material_code FROM (
  SELECT qi.material_code, qi.unit
  FROM quotation_items qi
  LEFT JOIN quotations q ON q.id = qi.quotation_id
  WHERE qi.is_deleted=0 AND IFNULL(q.is_deleted,0)=0
    AND qi.material_code IS NOT NULL AND qi.material_code<>''
  UNION ALL
  SELECT pqi.material_code, pqi.unit
  FROM purchase_quotation_items pqi
  WHERE pqi.is_deleted=0
    AND pqi.material_code IS NOT NULL AND pqi.material_code<>''
) t
WHERE UPPER(REPLACE(IFNULL(unit,''),' ','')) IN ('KG','KGS');

UPDATE film_stock_detail fsd
JOIN tmp_latest_kg_quote q ON q.material_code = fsd.material_code
SET fsd.unit_price = ROUND(q.kg_price * IFNULL(fsd.thickness,0) * (CASE
  WHEN UPPER(fsd.material_code) LIKE 'PET%' THEN 1.38
  WHEN UPPER(fsd.material_code) LIKE 'PI%' OR UPPER(fsd.material_code) LIKE 'PIM%' THEN 1.42
  WHEN UPPER(fsd.material_code) LIKE 'BOPP%' OR UPPER(fsd.material_code) LIKE 'OPP%' OR UPPER(fsd.material_code) LIKE 'CPP%' OR UPPER(fsd.material_code) LIKE 'PP%' THEN 0.90
  WHEN UPPER(fsd.material_code) LIKE 'PE%' THEN 0.92
  WHEN UPPER(fsd.material_code) LIKE 'PVC%' THEN 1.35
  WHEN UPPER(fsd.material_code) LIKE 'OPS%' OR UPPER(fsd.material_code) LIKE 'PS%' THEN 1.05
  ELSE NULL END) / 1000, 4),
fsd.update_time = NOW()
WHERE fsd.is_deleted=0 AND fsd.status IN ('available','locked')
  AND UPPER(fsd.material_code) NOT LIKE 'PEG-%'
  AND UPPER(fsd.material_code) <> 'NPZ-D105'
  AND IFNULL(fsd.thickness,0) > 0
  AND (CASE
  WHEN UPPER(fsd.material_code) LIKE 'PET%' THEN 1.38
  WHEN UPPER(fsd.material_code) LIKE 'PI%' OR UPPER(fsd.material_code) LIKE 'PIM%' THEN 1.42
  WHEN UPPER(fsd.material_code) LIKE 'BOPP%' OR UPPER(fsd.material_code) LIKE 'OPP%' OR UPPER(fsd.material_code) LIKE 'CPP%' OR UPPER(fsd.material_code) LIKE 'PP%' THEN 0.90
  WHEN UPPER(fsd.material_code) LIKE 'PE%' THEN 0.92
  WHEN UPPER(fsd.material_code) LIKE 'PVC%' THEN 1.35
  WHEN UPPER(fsd.material_code) LIKE 'OPS%' OR UPPER(fsd.material_code) LIKE 'PS%' THEN 1.05
  ELSE NULL END) IS NOT NULL;

UPDATE film_stock_detail fsd
JOIN tmp_latest_any_quote q ON q.material_code = fsd.material_code
JOIN tmp_material_has_kg_quote hk ON hk.material_code = fsd.material_code
SET fsd.unit_price = ROUND(q.unit_price * IFNULL(fsd.thickness,0) * (CASE
  WHEN UPPER(fsd.material_code) LIKE 'PET%' THEN 1.38
  WHEN UPPER(fsd.material_code) LIKE 'PI%' OR UPPER(fsd.material_code) LIKE 'PIM%' THEN 1.42
  WHEN UPPER(fsd.material_code) LIKE 'BOPP%' OR UPPER(fsd.material_code) LIKE 'OPP%' OR UPPER(fsd.material_code) LIKE 'CPP%' OR UPPER(fsd.material_code) LIKE 'PP%' THEN 0.90
  WHEN UPPER(fsd.material_code) LIKE 'PE%' THEN 0.92
  WHEN UPPER(fsd.material_code) LIKE 'PVC%' THEN 1.35
  WHEN UPPER(fsd.material_code) LIKE 'OPS%' OR UPPER(fsd.material_code) LIKE 'PS%' THEN 1.05
  ELSE NULL END) / 1000, 4),
fsd.update_time = NOW()
WHERE fsd.is_deleted=0 AND fsd.status IN ('available','locked')
  AND UPPER(fsd.material_code) NOT LIKE 'PEG-%'
  AND UPPER(fsd.material_code) <> 'NPZ-D105'
  AND IFNULL(fsd.thickness,0) > 0
  AND q.unit_price >= 10
  AND (CASE
  WHEN UPPER(fsd.material_code) LIKE 'PET%' THEN 1.38
  WHEN UPPER(fsd.material_code) LIKE 'PI%' OR UPPER(fsd.material_code) LIKE 'PIM%' THEN 1.42
  WHEN UPPER(fsd.material_code) LIKE 'BOPP%' OR UPPER(fsd.material_code) LIKE 'OPP%' OR UPPER(fsd.material_code) LIKE 'CPP%' OR UPPER(fsd.material_code) LIKE 'PP%' THEN 0.90
  WHEN UPPER(fsd.material_code) LIKE 'PE%' THEN 0.92
  WHEN UPPER(fsd.material_code) LIKE 'PVC%' THEN 1.35
  WHEN UPPER(fsd.material_code) LIKE 'OPS%' OR UPPER(fsd.material_code) LIKE 'PS%' THEN 1.05
  ELSE NULL END) IS NOT NULL;

UPDATE film_stock_detail fsd
JOIN tmp_latest_any_quote q ON q.material_code = fsd.material_code
SET fsd.unit_price = ROUND(q.unit_price * IFNULL(fsd.thickness,0) * 1.05 / 1000, 4),
    fsd.update_time = NOW()
WHERE fsd.is_deleted=0 AND fsd.status IN ('available','locked')
  AND UPPER(fsd.material_code) NOT LIKE 'PEG-%'
  AND UPPER(fsd.material_code) <> 'NPZ-D105'
  AND (UPPER(fsd.material_code) LIKE 'OPS%' OR UPPER(fsd.material_code) LIKE 'PS%')
  AND IFNULL(fsd.thickness,0) > 0
  AND q.unit_price >= 10;

UPDATE film_stock_detail fsd
JOIN tmp_latest_any_quote q ON q.material_code = fsd.material_code
SET fsd.unit_price = ROUND(q.unit_price * IFNULL(fsd.thickness,0) * 1.38 / 1000, 4),
    fsd.update_time = NOW()
WHERE fsd.is_deleted=0 AND fsd.status IN ('available','locked')
  AND UPPER(fsd.material_code) NOT LIKE 'PEG-%'
  AND UPPER(fsd.material_code) <> 'NPZ-D105'
  AND (UPPER(fsd.material_code) LIKE 'PETLXM%' OR UPPER(fsd.material_code) LIKE 'PETLXZ%')
  AND IFNULL(fsd.thickness,0) > 0
  AND q.unit_price >= 10;

DROP TEMPORARY TABLE IF EXISTS tmp_fin_inventory_snapshot_latest_refresh;
CREATE TEMPORARY TABLE tmp_fin_inventory_snapshot_latest_refresh AS
SELECT m.material_code, MAX(m.material_name) AS material_name, MAX(m.uom) AS uom,
       ROUND(SUM(m.qty), 6) AS stock_qty, ROUND(SUM(m.amount), 6) AS stock_amount,
       CASE WHEN SUM(m.qty) > 0 THEN ROUND(SUM(m.amount)/SUM(m.qty), 6) ELSE 0 END AS avg_unit_price
FROM (
    SELECT fsd.material_code, COALESCE(MAX(fs.material_name), fsd.material_code) AS material_name,
      CASE WHEN UPPER(fsd.material_code) LIKE 'PEG-%' THEN 'PCS' ELSE 'M2' END AS uom,
         SUM(CASE WHEN UPPER(fsd.material_code) LIKE 'PEG-%' THEN IFNULL(fsd.pack_count,1) ELSE IFNULL(fsd.area,0) END) AS qty,
         SUM(CASE WHEN UPPER(fsd.material_code) LIKE 'PEG-%' THEN IFNULL(fsd.pack_count,1) * IFNULL(fsd.unit_price,0) ELSE IFNULL(fsd.area,0) * IFNULL(fsd.unit_price,0) END) AS amount
  FROM film_stock_detail fsd LEFT JOIN film_stock fs ON fs.id = fsd.stock_id
  WHERE fsd.is_deleted=0 AND fsd.status IN ('available','locked') GROUP BY fsd.material_code
  UNION ALL
  SELECT csd.material_code, COALESCE(MAX(cs.material_name), csd.material_code) AS material_name, 'kg' AS uom,
         SUM(IFNULL(csd.remaining_weight, IFNULL(csd.weight,0))) AS qty,
         SUM(IFNULL(csd.remaining_weight, IFNULL(csd.weight,0)) * IFNULL(csd.unit_price,0)) AS amount
  FROM chemical_stock_detail csd LEFT JOIN chemical_stock cs ON cs.id = csd.stock_id
  WHERE csd.is_deleted=0 AND csd.status IN ('available','locked') GROUP BY csd.material_code
) m GROUP BY m.material_code;

DELETE FROM finance_inventory_price_latest;
INSERT INTO finance_inventory_price_latest(material_code, material_name, uom, stock_qty, avg_unit_price, stock_amount, version, last_recalc_time, last_inbound_date)
SELECT material_code, material_name, uom, stock_qty, avg_unit_price, stock_amount, 1, NOW(), @biz_date
FROM tmp_fin_inventory_snapshot_latest_refresh;

COMMIT;

SELECT material_code, uom, stock_qty, avg_unit_price, stock_amount
FROM finance_inventory_price_latest
WHERE material_code = 'PETM-T20';
