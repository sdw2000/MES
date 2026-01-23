-- ==========================================
-- 送样功能完整修复脚本
-- 包含：表结构检查、字段添加、数据清理、测试数据插入
-- 执行方式：在Navicat中一次性运行此脚本
-- ==========================================

USE erp;

SELECT '========================================' AS '';
SELECT '🚀 开始执行送样功能修复...' AS '';
SELECT '========================================' AS '';

-- ==========================================
-- 步骤1: 检查并添加缺失的字段
-- ==========================================

SELECT '步骤1: 检查并添加 total_quantity 字段...' AS 提示;

SET @col_exists = (
    SELECT COUNT(*)
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'erp'
    AND TABLE_NAME = 'sample_orders'
    AND COLUMN_NAME = 'total_quantity'
);

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE sample_orders ADD COLUMN `total_quantity` INT(11) DEFAULT 0 COMMENT ''总数量（统计明细）'' AFTER `last_logistics_query_time`',
    'SELECT ''字段 total_quantity 已存在'' AS 提示'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT '✅ 字段检查完成' AS 状态;

-- ==========================================
-- 步骤2: 清理旧数据
-- ==========================================

SELECT '' AS '';
SELECT '步骤2: 清理旧测试数据...' AS 提示;

SET FOREIGN_KEY_CHECKS=0;

TRUNCATE TABLE sample_items;
TRUNCATE TABLE sample_status_history;
TRUNCATE TABLE sample_logistics_records;
TRUNCATE TABLE sample_orders;

SET FOREIGN_KEY_CHECKS=1;

SELECT '✅ 旧数据已清理' AS 状态;

-- ==========================================
-- 步骤3: 插入测试数据
-- ==========================================

SELECT '' AS '';
SELECT '步骤3: 插入测试数据...' AS 提示;

-- 3.1 插入送样主表数据
INSERT INTO sample_orders (
    sample_no, 
    customer_id, 
    customer_name, 
    contact_name, 
    contact_phone, 
    contact_address,
    send_date,
    express_company,
    tracking_number,
    logistics_status,
    total_quantity,
    remark,
    status,
    create_by,
    create_time,
    update_by,
    update_time
) VALUES
(
    'SP20260105001',
    1,
    '阿里巴巴集团',
    '张经理',
    '13800138001',
    '浙江省杭州市余杭区文一西路969号',
    '2026-01-05',
    '顺丰速运',
    'SF1234567890',
    '运输中',
    150,
    '重要客户，优先处理',
    '已发货',
    'admin',
    NOW(),
    'admin',
    NOW()
),
(
    'SP20260105002',
    2,
    '腾讯科技有限公司',
    '李总监',
    '13900139002',
    '广东省深圳市南山区科技园',
    '2026-01-05',
    '圆通速递',
    NULL,
    '待发货',
    200,
    '新客户样品',
    '待发货',
    'admin',
    NOW(),
    'admin',
    NOW()
);

SELECT '✅ 主表数据插入完成（2条记录）' AS 状态;

-- 3.2 插入送样明细数据
INSERT INTO sample_items (
    sample_no,
    material_code,
    material_name,
    specification,
    batch_no,
    quantity,
    unit,
    remark
) VALUES
(
    'SP20260105001',
    'M001',
    '电路板A型',
    '100x50mm',
    'BATCH20260101',
    50,
    '片',
    '带测试报告'
),
(
    'SP20260105001',
    'M002',
    '电路板B型',
    '150x80mm',
    'BATCH20260102',
    100,
    '片',
    '标准包装'
),
(
    'SP20260105002',
    'M003',
    '芯片C型',
    'QFN-32',
    'BATCH20260103',
    200,
    '颗',
    '防静电包装'
);

SELECT '✅ 明细数据插入完成（3条记录）' AS 状态;

-- 3.3 插入状态历史记录
INSERT INTO sample_status_history (
    sample_no,
    old_status,
    new_status,
    change_reason,
    operator,
    change_time
) VALUES
(
    'SP20260105001',
    '待发货',
    '已发货',
    '已安排快递发货',
    'admin',
    NOW()
),
(
    'SP20260105001',
    '已发货',
    '运输中',
    '快递已揽收，正在运输',
    'system',
    NOW()
);

SELECT '✅ 状态历史插入完成（2条记录）' AS 状态;

-- 3.4 插入物流查询记录
INSERT INTO sample_logistics_records (
    sample_no,
    tracking_number,
    express_company,
    logistics_status,
    logistics_info,
    query_time
) VALUES
(
    'SP20260105001',
    'SF1234567890',
    '顺丰速运',
    '运输中',
    '[{"time":"2026-01-05 10:30:00","status":"已揽收","location":"杭州市余杭区"},{"time":"2026-01-05 14:20:00","status":"运输中","location":"杭州转运中心"},{"time":"2026-01-05 18:45:00","status":"运输中","location":"上海转运中心"}]',
    NOW()
);

SELECT '✅ 物流记录插入完成（1条记录）' AS 状态;

-- ==========================================
-- 步骤4: 验证数据
-- ==========================================

SELECT '' AS '';
SELECT '步骤4: 验证数据...' AS 提示;

-- 4.1 统计各表记录数
SELECT '送样主表' AS 表名, COUNT(*) AS 记录数 FROM sample_orders
UNION ALL
SELECT '送样明细表' AS 表名, COUNT(*) AS 记录数 FROM sample_items
UNION ALL
SELECT '状态历史表' AS 表名, COUNT(*) AS 记录数 FROM sample_status_history
UNION ALL
SELECT '物流记录表' AS 表名, COUNT(*) AS 记录数 FROM sample_logistics_records;

-- 4.2 显示送样单列表
SELECT '' AS '';
SELECT '送样单列表：' AS 提示;
SELECT 
    sample_no AS 送样单号,
    customer_name AS 客户名称,
    contact_name AS 联系人,
    express_company AS 快递公司,
    tracking_number AS 快递单号,
    logistics_status AS 物流状态,
    total_quantity AS 总数量,
    send_date AS 送样日期,
    status AS 状态
FROM sample_orders
ORDER BY sample_no;

-- 4.3 显示明细列表
SELECT '' AS '';
SELECT '送样明细列表：' AS 提示;
SELECT 
    si.sample_no AS 送样单号,
    si.material_code AS 物料编码,
    si.material_name AS 物料名称,
    si.batch_no AS 批次号,
    si.quantity AS 数量,
    si.unit AS 单位,
    si.remark AS 备注
FROM sample_items si
ORDER BY si.sample_no, si.id;

-- ==========================================
-- 步骤5: 验证字段正确性
-- ==========================================

SELECT '' AS '';
SELECT '步骤5: 验证关键字段...' AS 提示;

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
    IF(COUNT(*) > 0, '✅ material_code 字段存在', '❌ material_code 字段缺失') AS 检查结果
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'erp' AND TABLE_NAME = 'sample_items' AND COLUMN_NAME = 'material_code'
UNION ALL
SELECT 
    IF(COUNT(*) = 0, '✅ 没有错误的 courier_company 字段', '❌ 存在错误的 courier_company 字段') AS 检查结果
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'erp' AND TABLE_NAME = 'sample_orders' AND COLUMN_NAME = 'courier_company';

-- ==========================================
-- 完成
-- ==========================================

SELECT '' AS '';
SELECT '========================================' AS '';
SELECT '✅ 送样功能修复完成！' AS '';
SELECT '========================================' AS '';
SELECT '' AS '';
SELECT '已完成：' AS '';
SELECT '  ✓ 添加缺失的 total_quantity 字段' AS '';
SELECT '  ✓ 清理旧的测试数据' AS '';
SELECT '  ✓ 插入新的测试数据（2条订单+3条明细）' AS '';
SELECT '  ✓ 验证所有字段名正确' AS '';
SELECT '' AS '';
SELECT '下一步：' AS '';
SELECT '  1. 检查后端服务是否运行（端口8090）' AS '';
SELECT '  2. 访问前端页面测试功能' AS '';
SELECT '  3. URL: http://localhost:8080/#/sales/samples' AS '';
SELECT '========================================' AS '';
