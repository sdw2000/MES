-- 扫码领退料流水增加包装口径字段（兼容不支持 ADD COLUMN IF NOT EXISTS 的 MySQL）

SET @has_pack_qty := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'material_scan_txn'
      AND COLUMN_NAME = 'pack_qty'
);

SET @ddl_pack_qty := IF(
    @has_pack_qty = 0,
    "ALTER TABLE material_scan_txn ADD COLUMN pack_qty INT NULL COMMENT '领退包装数量（整数）' AFTER qty",
    "SELECT 'material_scan_txn.pack_qty exists'"
);

PREPARE stmt FROM @ddl_pack_qty;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_pack_uom := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'material_scan_txn'
      AND COLUMN_NAME = 'pack_uom'
);

SET @ddl_pack_uom := IF(
    @has_pack_uom = 0,
    "ALTER TABLE material_scan_txn ADD COLUMN pack_uom VARCHAR(20) NULL COMMENT '包装单位' AFTER pack_qty",
    "SELECT 'material_scan_txn.pack_uom exists'"
);

PREPARE stmt FROM @ddl_pack_uom;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_std_qty := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'material_scan_txn'
      AND COLUMN_NAME = 'std_qty'
);

SET @ddl_std_qty := IF(
    @has_std_qty = 0,
    "ALTER TABLE material_scan_txn ADD COLUMN std_qty DECIMAL(18,6) NULL COMMENT '折算标准量' AFTER pack_uom",
    "SELECT 'material_scan_txn.std_qty exists'"
);

PREPARE stmt FROM @ddl_std_qty;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_std_uom := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'material_scan_txn'
      AND COLUMN_NAME = 'std_uom'
);

SET @ddl_std_uom := IF(
    @has_std_uom = 0,
    "ALTER TABLE material_scan_txn ADD COLUMN std_uom VARCHAR(20) NULL COMMENT '标准单位' AFTER std_qty",
    "SELECT 'material_scan_txn.std_uom exists'"
);

PREPARE stmt FROM @ddl_std_uom;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
