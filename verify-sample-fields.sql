-- ==========================================
-- 验证送样表字段是否正确
-- 执行方式：在Navicat中运行此脚本
-- ==========================================

USE erp;

SELECT '========================================' AS '';
SELECT '开始验证送样表字段...' AS 提示;
SELECT '========================================' AS '';

-- 1. 验证主表字段
SELECT '1️⃣ 验证 sample_orders 主表字段' AS 提示;
SELECT 
    COLUMN_NAME AS 字段名,
    COLUMN_TYPE AS 数据类型,
    IS_NULLABLE AS 可为空,
    COLUMN_COMMENT AS 注释
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'erp'
AND TABLE_NAME = 'sample_orders'
ORDER BY ORDINAL_POSITION;

-- 检查关键字段是否存在
SELECT '检查主表关键字段...' AS 提示;
SELECT 
    IF(COUNT(*) > 0, '✅ express_company 字段存在', '❌ express_company 字段缺失') AS 检查结果
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'erp' AND TABLE_NAME = 'sample_orders' AND COLUMN_NAME = 'express_company'
UNION ALL
SELECT 
    IF(COUNT(*) > 0, '✅ total_quantity 字段存在', '❌ total_quantity 字段缺失') AS 检查结果
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'erp' AND TABLE_NAME = 'sample_orders' AND COLUMN_NAME = 'total_quantity'
UNION ALL
SELECT 
    IF(COUNT(*) > 0, '✅ create_by 字段存在', '❌ create_by 字段缺失（不应该是created_by）') AS 检查结果
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'erp' AND TABLE_NAME = 'sample_orders' AND COLUMN_NAME = 'create_by'
UNION ALL
SELECT 
    IF(COUNT(*) = 0, '✅ 没有错误的 courier_company 字段', '❌ 错误！存在 courier_company 字段') AS 检查结果
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'erp' AND TABLE_NAME = 'sample_orders' AND COLUMN_NAME = 'courier_company';

-- 2. 验证明细表字段
SELECT '' AS '';
SELECT '2️⃣ 验证 sample_items 明细表字段' AS 提示;
SELECT 
    COLUMN_NAME AS 字段名,
    COLUMN_TYPE AS 数据类型,
    IS_NULLABLE AS 可为空,
    COLUMN_COMMENT AS 注释
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'erp'
AND TABLE_NAME = 'sample_items'
ORDER BY ORDINAL_POSITION;

-- 检查明细表关键字段
SELECT '检查明细表关键字段...' AS 提示;
SELECT 
    IF(COUNT(*) > 0, '✅ material_code 字段存在', '❌ material_code 字段缺失') AS 检查结果
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'erp' AND TABLE_NAME = 'sample_items' AND COLUMN_NAME = 'material_code'
UNION ALL
SELECT 
    IF(COUNT(*) > 0, '✅ material_name 字段存在', '❌ material_name 字段缺失') AS 检查结果
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'erp' AND TABLE_NAME = 'sample_items' AND COLUMN_NAME = 'material_name'
UNION ALL
SELECT 
    IF(COUNT(*) > 0, '✅ batch_no 字段存在', '❌ batch_no 字段缺失') AS 检查结果
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'erp' AND TABLE_NAME = 'sample_items' AND COLUMN_NAME = 'batch_no'
UNION ALL
SELECT 
    IF(COUNT(*) = 0, '✅ 没有错误的 product_code 字段', '❌ 错误！存在 product_code 字段') AS 检查结果
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'erp' AND TABLE_NAME = 'sample_items' AND COLUMN_NAME = 'product_code';

-- 3. 验证物流记录表字段
SELECT '' AS '';
SELECT '3️⃣ 验证 sample_logistics_records 物流记录表字段' AS 提示;
SELECT 
    COLUMN_NAME AS 字段名,
    COLUMN_TYPE AS 数据类型,
    IS_NULLABLE AS 可为空,
    COLUMN_COMMENT AS 注释
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'erp'
AND TABLE_NAME = 'sample_logistics_records'
ORDER BY ORDINAL_POSITION;

-- 检查物流表关键字段
SELECT '检查物流表关键字段...' AS 提示;
SELECT 
    IF(COUNT(*) > 0, '✅ express_company 字段存在', '❌ express_company 字段缺失') AS 检查结果
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'erp' AND TABLE_NAME = 'sample_logistics_records' AND COLUMN_NAME = 'express_company'
UNION ALL
SELECT 
    IF(COUNT(*) = 0, '✅ 没有错误的 courier_company 字段', '❌ 错误！存在 courier_company 字段') AS 检查结果
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'erp' AND TABLE_NAME = 'sample_logistics_records' AND COLUMN_NAME = 'courier_company';

-- 4. 验证状态历史表
SELECT '' AS '';
SELECT '4️⃣ 验证 sample_status_history 状态历史表字段' AS 提示;
SELECT 
    COLUMN_NAME AS 字段名,
    COLUMN_TYPE AS 数据类型,
    IS_NULLABLE AS 可为空,
    COLUMN_COMMENT AS 注释
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'erp'
AND TABLE_NAME = 'sample_status_history'
ORDER BY ORDINAL_POSITION;

-- 总结
SELECT '' AS '';
SELECT '========================================' AS '';
SELECT '✅ 字段验证完成！' AS 提示;
SELECT '========================================' AS '';
SELECT '请检查上面的输出，确保：' AS '';
SELECT '1. express_company 字段存在（不是 courier_company）' AS '';
SELECT '2. material_code/material_name 字段存在（不是 product_code/product_name）' AS '';
SELECT '3. create_by/update_by 字段存在（不是 created_by/updated_by）' AS '';
SELECT '4. total_quantity 字段存在于主表中' AS '';
SELECT '========================================' AS '';
