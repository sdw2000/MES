-- 将胶带仓纸箱库存迁入包材仓
-- 规则：tape_stock 中 material_code like 'ZX%' 或 product_name 含“纸箱”

START TRANSACTION;

-- 1) 先确保主档存在（按料号）
INSERT INTO package_stock (
  material_code, material_name, spec_desc, unit,
  total_quantity, available_quantity, locked_quantity,
  total_pack_count, available_pack_count, locked_pack_count,
  safety_stock, status, remark, create_by, update_by, create_time, update_time
)
SELECT
  ts.material_code,
  MAX(ts.product_name) AS material_name,
  MAX(ts.spec_desc) AS spec_desc,
  'PCS' AS unit,
  0, 0, 0, 0, 0, 0,
  0,
  'active',
  'migration:carton-from-tape-stock',
  'migration','migration', NOW(), NOW()
FROM tape_stock ts
WHERE ts.status = 1
  AND COALESCE(ts.total_rolls,0) > 0
  AND (ts.material_code LIKE 'ZX%' OR ts.product_name LIKE '%纸箱%')
  AND (ts.remark IS NULL OR ts.remark NOT LIKE '%[MIGRATED_TO_PACKAGE]%')
GROUP BY ts.material_code
ON DUPLICATE KEY UPDATE
  material_name = VALUES(material_name),
  spec_desc = COALESCE(VALUES(spec_desc), package_stock.spec_desc),
  update_by = 'migration',
  update_time = NOW();

-- 2) 明细迁移（每个 tape_stock 行迁 1 条包材明细）
INSERT INTO package_stock_detail (
  stock_id, material_code, batch_no, container_no,
  pack_uom, pack_count, std_uom, std_qty_per_pack, quantity,
  warehouse, location, supplier, storage_date,
  status, remark, create_by, create_time, update_by, update_time, is_deleted
)
SELECT
  ps.id,
  ts.material_code,
  ts.batch_no,
  ts.batch_no,
  '个',
  GREATEST(COALESCE(ts.total_rolls,1), 1),
  'PCS',
  1.000,
  CAST(GREATEST(COALESCE(ts.total_rolls,1), 1) AS DECIMAL(18,3)),
  '包材仓',
  ts.location,
  '胶带仓迁移',
  ts.prod_date,
  'available',
  CONCAT('migration:tape_stock:', ts.id),
  'migration', NOW(), 'migration', NOW(), 0
FROM tape_stock ts
JOIN package_stock ps ON ps.material_code = ts.material_code
WHERE ts.status = 1
  AND COALESCE(ts.total_rolls,0) > 0
  AND (ts.material_code LIKE 'ZX%' OR ts.product_name LIKE '%纸箱%')
  AND (ts.remark IS NULL OR ts.remark NOT LIKE '%[MIGRATED_TO_PACKAGE]%');

-- 3) 回写 package_stock 汇总（仅 ZX/纸箱料号）
UPDATE package_stock ps
LEFT JOIN (
  SELECT
    stock_id,
    SUM(CASE WHEN status <> 'used' THEN GREATEST(COALESCE(pack_count,1),1) ELSE 0 END) AS qty_total,
    SUM(CASE WHEN status='available' THEN GREATEST(COALESCE(pack_count,1),1) ELSE 0 END) AS qty_available,
    SUM(CASE WHEN status='locked' THEN GREATEST(COALESCE(pack_count,1),1) ELSE 0 END) AS qty_locked
  FROM package_stock_detail
  WHERE COALESCE(is_deleted,0)=0
  GROUP BY stock_id
) d ON d.stock_id = ps.id
SET
  ps.total_quantity = COALESCE(d.qty_total,0),
  ps.available_quantity = COALESCE(d.qty_available,0),
  ps.locked_quantity = COALESCE(d.qty_locked,0),
  ps.total_pack_count = COALESCE(d.qty_total,0),
  ps.available_pack_count = COALESCE(d.qty_available,0),
  ps.locked_pack_count = COALESCE(d.qty_locked,0),
  ps.status = CASE
    WHEN COALESCE(d.qty_available,0) <= 0 THEN 'out_of_stock'
    WHEN COALESCE(ps.safety_stock,0) > 0 AND COALESCE(d.qty_available,0) < COALESCE(ps.safety_stock,0) THEN 'low_stock'
    ELSE 'active'
  END,
  ps.update_by = 'migration',
  ps.update_time = NOW()
WHERE ps.material_code LIKE 'ZX%' OR ps.material_name LIKE '%纸箱%';

-- 4) 写流水：PACKAGE IN
INSERT INTO stock_flow_log (
  stock_type, stock_id, batch_no, material_code, product_name,
  type, change_quantity, unit,
  ref_no, operator, remark, create_time, update_time
)
SELECT
  'PACKAGE',
  ps.id,
  ts.batch_no,
  ts.material_code,
  ts.product_name,
  'IN',
  CAST(GREATEST(COALESCE(ts.total_rolls,1),1) AS DECIMAL(15,3)),
  'PCS',
  ts.batch_no,
  'migration',
  '纸箱由胶带仓迁入包材仓',
  NOW(), NOW()
FROM tape_stock ts
JOIN package_stock ps ON ps.material_code = ts.material_code
WHERE ts.status = 1
  AND COALESCE(ts.total_rolls,0) > 0
  AND (ts.material_code LIKE 'ZX%' OR ts.product_name LIKE '%纸箱%')
  AND (ts.remark IS NULL OR ts.remark NOT LIKE '%[MIGRATED_TO_PACKAGE]%');

-- 5) 写流水：TAPE OUT（迁移出）
INSERT INTO stock_flow_log (
  stock_type, stock_id, batch_no, material_code, product_name,
  type, change_quantity, unit,
  ref_no, operator, remark, create_time, update_time
)
SELECT
  'TAPE',
  ts.id,
  ts.batch_no,
  ts.material_code,
  ts.product_name,
  'OUT',
  CAST(-GREATEST(COALESCE(ts.total_rolls,1),1) AS DECIMAL(15,3)),
  'PCS',
  ts.batch_no,
  'migration',
  '纸箱迁移至包材仓',
  NOW(), NOW()
FROM tape_stock ts
WHERE ts.status = 1
  AND COALESCE(ts.total_rolls,0) > 0
  AND (ts.material_code LIKE 'ZX%' OR ts.product_name LIKE '%纸箱%')
  AND (ts.remark IS NULL OR ts.remark NOT LIKE '%[MIGRATED_TO_PACKAGE]%');

-- 6) 清空胶带仓纸箱库存（保留历史行，置为失效）
UPDATE tape_stock ts
SET
  ts.total_rolls = 0,
  ts.total_sqm = 0,
  ts.available_area = 0,
  ts.reserved_area = 0,
  ts.consumed_area = 0,
  ts.status = 0,
  ts.remark = CONCAT(COALESCE(ts.remark,''), ';[MIGRATED_TO_PACKAGE]'),
  ts.update_time = NOW(),
  ts.updated_by = 'migration'
WHERE ts.status = 1
  AND COALESCE(ts.total_rolls,0) > 0
  AND (ts.material_code LIKE 'ZX%' OR ts.product_name LIKE '%纸箱%')
  AND (ts.remark IS NULL OR ts.remark NOT LIKE '%[MIGRATED_TO_PACKAGE]%');

COMMIT;
