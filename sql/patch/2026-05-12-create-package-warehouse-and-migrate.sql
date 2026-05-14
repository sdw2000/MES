-- 包材仓（管芯/纸箱）建设与迁移脚本
-- 执行前请先备份：film_stock/film_stock_detail/chemical_stock/chemical_stock_detail

START TRANSACTION;

-- 1) 新建包材仓主表
CREATE TABLE IF NOT EXISTS package_stock (
  id BIGINT NOT NULL AUTO_INCREMENT,
  material_code VARCHAR(64) NOT NULL,
  material_name VARCHAR(128) DEFAULT NULL,
  spec_desc VARCHAR(255) DEFAULT NULL,
  unit VARCHAR(16) DEFAULT 'PCS',
  total_quantity INT NOT NULL DEFAULT 0,
  available_quantity INT NOT NULL DEFAULT 0,
  locked_quantity INT NOT NULL DEFAULT 0,
  total_pack_count INT NOT NULL DEFAULT 0,
  available_pack_count INT NOT NULL DEFAULT 0,
  locked_pack_count INT NOT NULL DEFAULT 0,
  safety_stock INT NOT NULL DEFAULT 0,
  status VARCHAR(32) DEFAULT 'active',
  unit_weight DECIMAL(18,3) DEFAULT NULL,
  remark VARCHAR(500) DEFAULT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  create_by VARCHAR(64) DEFAULT NULL,
  update_by VARCHAR(64) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_package_stock_material_code (material_code),
  KEY idx_package_stock_status (status),
  KEY idx_package_stock_material_name (material_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2) 新建包材仓明细表
CREATE TABLE IF NOT EXISTS package_stock_detail (
  id BIGINT NOT NULL AUTO_INCREMENT,
  stock_id BIGINT NOT NULL,
  material_code VARCHAR(64) NOT NULL,
  batch_no VARCHAR(128) DEFAULT NULL,
  container_no VARCHAR(128) DEFAULT NULL,
  pack_uom VARCHAR(16) DEFAULT '个',
  pack_count INT NOT NULL DEFAULT 1,
  std_uom VARCHAR(16) DEFAULT 'PCS',
  std_qty_per_pack DECIMAL(18,3) NOT NULL DEFAULT 1.000,
  quantity DECIMAL(18,3) NOT NULL DEFAULT 1.000,
  warehouse VARCHAR(32) DEFAULT '包材仓',
  location VARCHAR(64) DEFAULT NULL,
  supplier VARCHAR(128) DEFAULT NULL,
  storage_date DATE DEFAULT NULL,
  status VARCHAR(32) DEFAULT 'available',
  remark VARCHAR(500) DEFAULT NULL,
  create_by VARCHAR(64) DEFAULT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_by VARCHAR(64) DEFAULT NULL,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_package_stock_detail_stock_id (stock_id),
  KEY idx_package_stock_detail_material_code (material_code),
  KEY idx_package_stock_detail_status (status),
  KEY idx_package_stock_detail_batch_no (batch_no),
  KEY idx_package_stock_detail_container_no (container_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3) 新建包材仓出库记录表
CREATE TABLE IF NOT EXISTS package_stock_out (
  id BIGINT NOT NULL AUTO_INCREMENT,
  stock_id BIGINT NOT NULL,
  detail_id BIGINT DEFAULT NULL,
  material_code VARCHAR(64) NOT NULL,
  out_no VARCHAR(64) DEFAULT NULL,
  batch_no VARCHAR(128) DEFAULT NULL,
  out_quantity DECIMAL(18,3) NOT NULL DEFAULT 0.000,
  out_type VARCHAR(64) DEFAULT NULL,
  operator VARCHAR(64) DEFAULT NULL,
  out_date DATETIME DEFAULT NULL,
  remark VARCHAR(500) DEFAULT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  create_by VARCHAR(64) DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_package_stock_out_stock_id (stock_id),
  KEY idx_package_stock_out_material_code (material_code),
  KEY idx_package_stock_out_out_date (out_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4) 历史迁移：先把 film/chemical 中管芯纸箱类汇总到 package_stock
-- 识别规则：备注含 inboundCategory=PAPER_BOX_TUBE 或料号/名称/规格含 PEG/纸箱/纸管/PE管/PP管/PVC管/TUBE
INSERT INTO package_stock (
  material_code, material_name, spec_desc, unit,
  total_quantity, available_quantity, locked_quantity,
  total_pack_count, available_pack_count, locked_pack_count,
  safety_stock, status, remark, create_by, update_by
)
SELECT
  s.material_code,
  MAX(s.material_name) AS material_name,
  MAX(s.spec_desc) AS spec_desc,
  'PCS' AS unit,
  SUM(s.qty_total) AS total_quantity,
  SUM(s.qty_available) AS available_quantity,
  SUM(s.qty_locked) AS locked_quantity,
  SUM(s.qty_total) AS total_pack_count,
  SUM(s.qty_available) AS available_pack_count,
  SUM(s.qty_locked) AS locked_pack_count,
  0 AS safety_stock,
  CASE
    WHEN SUM(s.qty_available) <= 0 THEN 'out_of_stock'
    ELSE 'active'
  END AS status,
  'migration:package-warehouse-from-film-chemical' AS remark,
  'migration' AS create_by,
  'migration' AS update_by
FROM (
  SELECT
    fs.material_code,
    fs.material_name,
    fs.spec_desc,
    SUM(CASE WHEN fsd.status <> 'used' THEN COALESCE(fsd.pack_count,1) ELSE 0 END) AS qty_total,
    SUM(CASE WHEN fsd.status = 'available' THEN COALESCE(fsd.pack_count,1) ELSE 0 END) AS qty_available,
    SUM(CASE WHEN fsd.status = 'locked' THEN COALESCE(fsd.pack_count,1) ELSE 0 END) AS qty_locked
  FROM film_stock_detail fsd
  JOIN film_stock fs ON fs.id = fsd.stock_id
  WHERE COALESCE(fsd.is_deleted,0)=0
    AND (
      UPPER(COALESCE(fsd.remark,'')) LIKE '%INBOUNDCATEGORY=PAPER_BOX_TUBE%'
      OR UPPER(COALESCE(fsd.material_code,'')) LIKE 'PEG%'
      OR UPPER(COALESCE(fs.material_code,'')) LIKE 'PEG%'
      OR COALESCE(fsd.roll_no,'') LIKE '%-TUBE-%'
      OR COALESCE(fs.material_name,'') LIKE '%纸箱%'
      OR COALESCE(fs.material_name,'') LIKE '%纸管%'
      OR COALESCE(fs.material_name,'') LIKE '%PE管%'
      OR COALESCE(fs.material_name,'') LIKE '%PP管%'
      OR COALESCE(fs.material_name,'') LIKE '%PVC管%'
      OR UPPER(COALESCE(fs.material_name,'')) LIKE '%TUBE%'
      OR COALESCE(fs.material_name,'') LIKE '%管材%'
      OR COALESCE(fs.spec_desc,'') LIKE '%纸箱%'
      OR COALESCE(fs.spec_desc,'') LIKE '%纸管%'
      OR COALESCE(fs.spec_desc,'') LIKE '%PE管%'
      OR COALESCE(fs.spec_desc,'') LIKE '%PP管%'
      OR COALESCE(fs.spec_desc,'') LIKE '%PVC管%'
      OR UPPER(COALESCE(fs.spec_desc,'')) LIKE '%TUBE%'
      OR COALESCE(fs.spec_desc,'') LIKE '%管材%'
    )
  GROUP BY fs.material_code, fs.material_name, fs.spec_desc

  UNION ALL

  SELECT
    cs.material_code,
    cs.material_name,
    NULL AS spec_desc,
    SUM(CASE WHEN csd.status <> 'used' THEN COALESCE(csd.pack_count,1) ELSE 0 END) AS qty_total,
    SUM(CASE WHEN csd.status = 'available' THEN COALESCE(csd.pack_count,1) ELSE 0 END) AS qty_available,
    SUM(CASE WHEN csd.status = 'locked' THEN COALESCE(csd.pack_count,1) ELSE 0 END) AS qty_locked
  FROM chemical_stock_detail csd
  JOIN chemical_stock cs ON cs.id = csd.stock_id
  WHERE (
      UPPER(COALESCE(csd.remark,'')) LIKE '%INBOUNDCATEGORY=PAPER_BOX_TUBE%'
      OR UPPER(COALESCE(csd.material_code,'')) LIKE 'PEG%'
      OR UPPER(COALESCE(cs.material_code,'')) LIKE 'PEG%'
      OR COALESCE(cs.material_name,'') LIKE '%纸箱%'
      OR COALESCE(cs.material_name,'') LIKE '%纸管%'
      OR COALESCE(cs.material_name,'') LIKE '%PE管%'
      OR COALESCE(cs.material_name,'') LIKE '%PP管%'
      OR COALESCE(cs.material_name,'') LIKE '%PVC管%'
      OR UPPER(COALESCE(cs.material_name,'')) LIKE '%TUBE%'
      OR COALESCE(cs.material_name,'') LIKE '%管材%'
    )
  GROUP BY cs.material_code, cs.material_name
) s
GROUP BY s.material_code
ON DUPLICATE KEY UPDATE
  material_name = VALUES(material_name),
  spec_desc = COALESCE(VALUES(spec_desc), package_stock.spec_desc),
  total_quantity = VALUES(total_quantity),
  available_quantity = VALUES(available_quantity),
  locked_quantity = VALUES(locked_quantity),
  total_pack_count = VALUES(total_pack_count),
  available_pack_count = VALUES(available_pack_count),
  locked_pack_count = VALUES(locked_pack_count),
  status = VALUES(status),
  remark = CONCAT(COALESCE(package_stock.remark,''), ';', VALUES(remark)),
  update_by = 'migration',
  update_time = NOW();

-- 4.1) 迁移明细：先清理旧迁移明细，确保脚本可重复执行
DELETE FROM package_stock_detail
WHERE remark LIKE 'migration:%';

-- 从薄膜明细迁移包材记录
INSERT INTO package_stock_detail (
  stock_id, material_code, batch_no, container_no,
  pack_uom, pack_count, std_uom, std_qty_per_pack, quantity,
  warehouse, location, supplier, storage_date,
  status, remark, create_by, create_time, update_by, update_time, is_deleted
)
SELECT
  ps.id AS stock_id,
  fsd.material_code,
  fsd.batch_no,
  COALESCE(NULLIF(fsd.roll_no,''), CONCAT('FILM-', fsd.id)) AS container_no,
  COALESCE(NULLIF(fsd.pack_uom,''), '个') AS pack_uom,
  GREATEST(COALESCE(fsd.pack_count,1), 1) AS pack_count,
  'PCS' AS std_uom,
  1.000 AS std_qty_per_pack,
  CAST(GREATEST(COALESCE(fsd.pack_count,1), 1) AS DECIMAL(18,3)) AS quantity,
  '包材仓' AS warehouse,
  fsd.location,
  fsd.supplier,
  fsd.storage_date,
  CASE
    WHEN LOWER(COALESCE(fsd.status,'')) = 'locked' THEN 'locked'
    WHEN LOWER(COALESCE(fsd.status,'')) = 'used' THEN 'used'
    ELSE 'available'
  END AS status,
  CONCAT('migration:film_stock_detail:', fsd.id) AS remark,
  'migration' AS create_by,
  NOW() AS create_time,
  'migration' AS update_by,
  NOW() AS update_time,
  0 AS is_deleted
FROM film_stock_detail fsd
JOIN film_stock fs ON fs.id = fsd.stock_id
JOIN package_stock ps ON ps.material_code = fsd.material_code
WHERE COALESCE(fsd.is_deleted,0)=0
  AND (
    UPPER(COALESCE(fsd.remark,'')) LIKE '%INBOUNDCATEGORY=PAPER_BOX_TUBE%'
    OR UPPER(COALESCE(fsd.material_code,'')) LIKE 'PEG%'
    OR UPPER(COALESCE(fs.material_code,'')) LIKE 'PEG%'
    OR COALESCE(fsd.roll_no,'') LIKE '%-TUBE-%'
    OR COALESCE(fs.material_name,'') LIKE '%纸箱%'
    OR COALESCE(fs.material_name,'') LIKE '%纸管%'
    OR COALESCE(fs.material_name,'') LIKE '%PE管%'
    OR COALESCE(fs.material_name,'') LIKE '%PP管%'
    OR COALESCE(fs.material_name,'') LIKE '%PVC管%'
    OR UPPER(COALESCE(fs.material_name,'')) LIKE '%TUBE%'
    OR COALESCE(fs.material_name,'') LIKE '%管材%'
    OR COALESCE(fs.spec_desc,'') LIKE '%纸箱%'
    OR COALESCE(fs.spec_desc,'') LIKE '%纸管%'
    OR COALESCE(fs.spec_desc,'') LIKE '%PE管%'
    OR COALESCE(fs.spec_desc,'') LIKE '%PP管%'
    OR COALESCE(fs.spec_desc,'') LIKE '%PVC管%'
    OR UPPER(COALESCE(fs.spec_desc,'')) LIKE '%TUBE%'
    OR COALESCE(fs.spec_desc,'') LIKE '%管材%'
  );

-- 从化工明细迁移包材记录
INSERT INTO package_stock_detail (
  stock_id, material_code, batch_no, container_no,
  pack_uom, pack_count, std_uom, std_qty_per_pack, quantity,
  warehouse, location, supplier, storage_date,
  status, remark, create_by, create_time, update_by, update_time, is_deleted
)
SELECT
  ps.id AS stock_id,
  csd.material_code,
  csd.batch_no,
  COALESCE(NULLIF(csd.barrel_no,''), CONCAT('CHEM-', csd.id)) AS container_no,
  COALESCE(NULLIF(csd.pack_uom,''), '个') AS pack_uom,
  GREATEST(COALESCE(csd.pack_count,1), 1) AS pack_count,
  'PCS' AS std_uom,
  1.000 AS std_qty_per_pack,
  CAST(GREATEST(COALESCE(csd.pack_count,1), 1) AS DECIMAL(18,3)) AS quantity,
  '包材仓' AS warehouse,
  csd.location,
  csd.supplier,
  csd.storage_date,
  CASE
    WHEN LOWER(COALESCE(csd.status,'')) = 'locked' THEN 'locked'
    WHEN LOWER(COALESCE(csd.status,'')) = 'used' THEN 'used'
    ELSE 'available'
  END AS status,
  CONCAT('migration:chemical_stock_detail:', csd.id) AS remark,
  'migration' AS create_by,
  NOW() AS create_time,
  'migration' AS update_by,
  NOW() AS update_time,
  0 AS is_deleted
FROM chemical_stock_detail csd
JOIN chemical_stock cs ON cs.id = csd.stock_id
JOIN package_stock ps ON ps.material_code = csd.material_code
WHERE (
    UPPER(COALESCE(csd.remark,'')) LIKE '%INBOUNDCATEGORY=PAPER_BOX_TUBE%'
    OR UPPER(COALESCE(csd.material_code,'')) LIKE 'PEG%'
    OR UPPER(COALESCE(cs.material_code,'')) LIKE 'PEG%'
    OR COALESCE(cs.material_name,'') LIKE '%纸箱%'
    OR COALESCE(cs.material_name,'') LIKE '%纸管%'
    OR COALESCE(cs.material_name,'') LIKE '%PE管%'
    OR COALESCE(cs.material_name,'') LIKE '%PP管%'
    OR COALESCE(cs.material_name,'') LIKE '%PVC管%'
    OR UPPER(COALESCE(cs.material_name,'')) LIKE '%TUBE%'
    OR COALESCE(cs.material_name,'') LIKE '%管材%'
  );

-- 4.2) 明细迁移后回写汇总
UPDATE package_stock ps
LEFT JOIN (
  SELECT
    stock_id,
    SUM(CASE WHEN status <> 'used' THEN GREATEST(COALESCE(pack_count,1),1) ELSE 0 END) AS qty_total,
    SUM(CASE WHEN status = 'available' THEN GREATEST(COALESCE(pack_count,1),1) ELSE 0 END) AS qty_available,
    SUM(CASE WHEN status = 'locked' THEN GREATEST(COALESCE(pack_count,1),1) ELSE 0 END) AS qty_locked
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
  ps.update_time = NOW();

COMMIT;

-- 5) 迁移后建议校验
-- SELECT material_code, total_quantity, available_quantity, locked_quantity FROM package_stock ORDER BY material_code;
-- SELECT material_code, SUM(CASE WHEN status='available' THEN pack_count ELSE 0 END) FROM package_stock_detail GROUP BY material_code;
