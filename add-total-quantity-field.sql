-- ==========================================
-- 为送样主表添加 total_quantity 字段
-- 执行时间: 2026-01-05
-- ==========================================

USE erp;

-- 检查字段是否已存在
SET @col_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'erp'
    AND TABLE_NAME = 'sample_orders'
    AND COLUMN_NAME = 'total_quantity'
);

-- 如果字段不存在，则添加
SELECT IF(@col_exists = 0, 
    'ALTER TABLE sample_orders ADD COLUMN `total_quantity` INT(11) DEFAULT 0 COMMENT \'总数量（统计明细）\' AFTER `last_logistics_query_time`;',
    'SELECT \'字段 total_quantity 已存在，无需添加\' AS 提示;'
) INTO @sql;

-- 执行SQL
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 验证字段
SELECT 
    COLUMN_NAME AS 字段名,
    COLUMN_TYPE AS 类型,
    IS_NULLABLE AS 可为空,
    COLUMN_DEFAULT AS 默认值,
    COLUMN_COMMENT AS 注释
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'erp'
AND TABLE_NAME = 'sample_orders'
AND COLUMN_NAME = 'total_quantity';

SELECT '✅ 字段添加完成！' AS 状态;
