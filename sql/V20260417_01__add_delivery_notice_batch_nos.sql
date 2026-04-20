-- 发货单主表新增“批次号集合”字段（打印标签时累计追加，逗号分隔唯一值）
SET @ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE delivery_notices ADD COLUMN batch_nos VARCHAR(1000) NULL COMMENT ''标签打印累计批次号（逗号分隔唯一值）'' AFTER carrier_phone',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'delivery_notices'
      AND COLUMN_NAME = 'batch_nos'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
