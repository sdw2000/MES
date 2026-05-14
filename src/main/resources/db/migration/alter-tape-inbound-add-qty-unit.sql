-- 入库申请增加数量单位字段（卷/㎡/箱/个/kg）
-- 兼容不支持 ADD COLUMN IF NOT EXISTS 的 MySQL 版本

SET @has_col := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
        AND TABLE_NAME = 'tape_inbound_request'
        AND COLUMN_NAME = 'qty_unit'
);

SET @ddl := IF(
    @has_col = 0,
    "ALTER TABLE tape_inbound_request ADD COLUMN qty_unit VARCHAR(20) NULL DEFAULT '卷' COMMENT '入库数量单位（卷/㎡/箱/个/kg）' AFTER rolls",
    "SELECT 'qty_unit exists'"
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
