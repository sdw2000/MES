-- 薄膜明细增加包装口径字段（兼容不支持 ADD COLUMN IF NOT EXISTS 的 MySQL）

SET @has_pack_uom := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'film_stock_detail'
      AND COLUMN_NAME = 'pack_uom'
);

SET @ddl_pack_uom := IF(
    @has_pack_uom = 0,
    "ALTER TABLE film_stock_detail ADD COLUMN pack_uom VARCHAR(20) NULL COMMENT '最小包装单位：卷' AFTER material_code",
    "SELECT 'film_stock_detail.pack_uom exists'"
);

PREPARE stmt FROM @ddl_pack_uom;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_pack_count := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'film_stock_detail'
      AND COLUMN_NAME = 'pack_count'
);

SET @ddl_pack_count := IF(
    @has_pack_count = 0,
    "ALTER TABLE film_stock_detail ADD COLUMN pack_count INT NULL DEFAULT 1 COMMENT '当前包装数量（整数）' AFTER pack_uom",
    "SELECT 'film_stock_detail.pack_count exists'"
);

PREPARE stmt FROM @ddl_pack_count;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_std_uom := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'film_stock_detail'
      AND COLUMN_NAME = 'std_uom'
);

SET @ddl_std_uom := IF(
    @has_std_uom = 0,
    "ALTER TABLE film_stock_detail ADD COLUMN std_uom VARCHAR(20) NULL COMMENT '标准单位：m/㎡' AFTER pack_count",
    "SELECT 'film_stock_detail.std_uom exists'"
);

PREPARE stmt FROM @ddl_std_uom;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_std_qty_per_pack := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'film_stock_detail'
      AND COLUMN_NAME = 'std_qty_per_pack'
);

SET @ddl_std_qty_per_pack := IF(
    @has_std_qty_per_pack = 0,
    "ALTER TABLE film_stock_detail ADD COLUMN std_qty_per_pack DECIMAL(18,6) NULL COMMENT '每包装标准量' AFTER std_uom",
    "SELECT 'film_stock_detail.std_qty_per_pack exists'"
);

PREPARE stmt FROM @ddl_std_qty_per_pack;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
