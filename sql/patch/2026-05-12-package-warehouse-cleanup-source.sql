-- 包材仓迁移后：清理来源仓（薄膜/化工）中的包材库存，避免双仓重复
-- 策略：软清理（明细置 used；薄膜明细同时 is_deleted=1），并回写汇总

START TRANSACTION;

-- 1) 薄膜仓包材明细软清理
UPDATE film_stock_detail fsd
JOIN package_stock ps ON ps.material_code = fsd.material_code
SET fsd.status = 'used',
    fsd.is_deleted = 1,
    fsd.remark = CONCAT(COALESCE(fsd.remark,''), ';[MIGRATED_TO_PACKAGE]'),
    fsd.update_time = NOW(),
    fsd.update_by = 'migration'
WHERE COALESCE(fsd.is_deleted,0)=0
  AND LOWER(COALESCE(fsd.status,'')) <> 'used';

-- 2) 化工仓包材明细软清理
UPDATE chemical_stock_detail csd
JOIN package_stock ps ON ps.material_code = csd.material_code
SET csd.status = 'used',
    csd.remark = CONCAT(COALESCE(csd.remark,''), ';[MIGRATED_TO_PACKAGE]'),
    csd.update_time = NOW()
WHERE LOWER(COALESCE(csd.status,'')) <> 'used';

-- 3) 回写薄膜仓汇总
UPDATE film_stock fs
LEFT JOIN (
  SELECT
    stock_id,
    SUM(CASE WHEN status <> 'used' AND COALESCE(is_deleted,0)=0 THEN COALESCE(area,0) ELSE 0 END) AS total_area,
    SUM(CASE WHEN status='available' AND COALESCE(is_deleted,0)=0 THEN COALESCE(area,0) ELSE 0 END) AS avail_area,
    SUM(CASE WHEN status='locked' AND COALESCE(is_deleted,0)=0 THEN COALESCE(area,0) ELSE 0 END) AS locked_area,
    SUM(CASE WHEN status <> 'used' AND COALESCE(is_deleted,0)=0 THEN GREATEST(COALESCE(pack_count,1),1) ELSE 0 END) AS total_rolls,
    SUM(CASE WHEN status='available' AND COALESCE(is_deleted,0)=0 THEN GREATEST(COALESCE(pack_count,1),1) ELSE 0 END) AS avail_rolls,
    SUM(CASE WHEN status='locked' AND COALESCE(is_deleted,0)=0 THEN GREATEST(COALESCE(pack_count,1),1) ELSE 0 END) AS locked_rolls
  FROM film_stock_detail
  GROUP BY stock_id
) d ON d.stock_id = fs.id
SET fs.total_area = COALESCE(d.total_area,0),
    fs.available_area = COALESCE(d.avail_area,0),
    fs.locked_area = COALESCE(d.locked_area,0),
    fs.total_rolls = COALESCE(d.total_rolls,0),
    fs.available_rolls = COALESCE(d.avail_rolls,0),
    fs.locked_rolls = COALESCE(d.locked_rolls,0),
    fs.total_pack_count = COALESCE(d.total_rolls,0),
    fs.available_pack_count = COALESCE(d.avail_rolls,0),
    fs.locked_pack_count = COALESCE(d.locked_rolls,0),
    fs.status = CASE
      WHEN COALESCE(d.avail_area,0) <= 0 THEN 'out_of_stock'
      WHEN COALESCE(fs.safety_stock,0) > 0 AND COALESCE(d.avail_area,0) < COALESCE(fs.safety_stock,0) THEN 'low_stock'
      ELSE 'active'
    END,
    fs.update_time = NOW(),
    fs.update_by = 'migration';

-- 4) 回写化工仓汇总
UPDATE chemical_stock cs
LEFT JOIN (
  SELECT
    stock_id,
    SUM(CASE WHEN status <> 'used' THEN GREATEST(COALESCE(pack_count,1),1) ELSE 0 END) AS total_qty,
    SUM(CASE WHEN status='available' THEN GREATEST(COALESCE(pack_count,1),1) ELSE 0 END) AS avail_qty,
    SUM(CASE WHEN status='locked' THEN GREATEST(COALESCE(pack_count,1),1) ELSE 0 END) AS locked_qty
  FROM chemical_stock_detail
  GROUP BY stock_id
) d ON d.stock_id = cs.id
SET cs.total_quantity = COALESCE(d.total_qty,0),
    cs.available_quantity = COALESCE(d.avail_qty,0),
    cs.locked_quantity = COALESCE(d.locked_qty,0),
    cs.total_pack_count = COALESCE(d.total_qty,0),
    cs.available_pack_count = COALESCE(d.avail_qty,0),
    cs.locked_pack_count = COALESCE(d.locked_qty,0),
    cs.status = CASE
      WHEN COALESCE(d.avail_qty,0) <= 0 THEN 'out_of_stock'
      WHEN COALESCE(cs.safety_stock,0) > 0 AND COALESCE(d.avail_qty,0) < COALESCE(cs.safety_stock,0) THEN 'low_stock'
      ELSE 'active'
    END,
    cs.update_time = NOW(),
    cs.update_by = 'migration';

COMMIT;

-- 验证：
-- SELECT material_code, available_quantity FROM package_stock ORDER BY material_code;
-- SELECT material_code, available_rolls FROM film_stock WHERE material_code IN (SELECT material_code FROM package_stock);
-- SELECT material_code, available_quantity FROM chemical_stock WHERE material_code IN (SELECT material_code FROM package_stock);
