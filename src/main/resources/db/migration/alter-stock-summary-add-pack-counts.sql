-- 库存汇总增加包装统计字段（兼容不支持 ADD COLUMN IF NOT EXISTS 的 MySQL）

-- chemical_stock.available_pack_count
SET @has_col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chemical_stock' AND COLUMN_NAME = 'available_pack_count'
);
SET @ddl := IF(
    @has_col = 0,
    "ALTER TABLE chemical_stock ADD COLUMN available_pack_count INT NULL COMMENT '可用包装数' AFTER available_quantity",
    "SELECT 'chemical_stock.available_pack_count exists'"
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- chemical_stock.locked_pack_count
SET @has_col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chemical_stock' AND COLUMN_NAME = 'locked_pack_count'
);
SET @ddl := IF(
    @has_col = 0,
    "ALTER TABLE chemical_stock ADD COLUMN locked_pack_count INT NULL COMMENT '锁定包装数' AFTER locked_quantity",
    "SELECT 'chemical_stock.locked_pack_count exists'"
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- chemical_stock.total_pack_count
SET @has_col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chemical_stock' AND COLUMN_NAME = 'total_pack_count'
);
SET @ddl := IF(
    @has_col = 0,
    "ALTER TABLE chemical_stock ADD COLUMN total_pack_count INT NULL COMMENT '总包装数' AFTER safety_stock",
    "SELECT 'chemical_stock.total_pack_count exists'"
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- film_stock.available_pack_count
SET @has_col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'film_stock' AND COLUMN_NAME = 'available_pack_count'
);
SET @ddl := IF(
    @has_col = 0,
    "ALTER TABLE film_stock ADD COLUMN available_pack_count INT NULL COMMENT '可用包装数' AFTER available_rolls",
    "SELECT 'film_stock.available_pack_count exists'"
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- film_stock.locked_pack_count
SET @has_col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'film_stock' AND COLUMN_NAME = 'locked_pack_count'
);
SET @ddl := IF(
    @has_col = 0,
    "ALTER TABLE film_stock ADD COLUMN locked_pack_count INT NULL COMMENT '锁定包装数' AFTER locked_rolls",
    "SELECT 'film_stock.locked_pack_count exists'"
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- film_stock.total_pack_count
SET @has_col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'film_stock' AND COLUMN_NAME = 'total_pack_count'
);
SET @ddl := IF(
    @has_col = 0,
    "ALTER TABLE film_stock ADD COLUMN total_pack_count INT NULL COMMENT '总包装数' AFTER safety_stock",
    "SELECT 'film_stock.total_pack_count exists'"
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
