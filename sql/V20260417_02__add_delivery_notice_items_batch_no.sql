-- 发货明细表新增批次号字段（用于发货通知编辑与保存）
SET @ddl = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE delivery_notice_items ADD COLUMN batch_no VARCHAR(100) NULL COMMENT ''批次号'' AFTER spec',
        'SELECT 1'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'delivery_notice_items'
      AND COLUMN_NAME = 'batch_no'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
