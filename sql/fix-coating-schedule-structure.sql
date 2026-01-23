-- ================================================================
-- 涂布排程逻辑优化 - 数据库结构修复
-- 目的：让涂布计划关联销售订单，获取完整的产品信息
-- 日期：2026-01-15
-- ================================================================

USE erp;

-- ================== 第一部分：修改涂布计划表 ==================

-- 1. 检查并添加订单关联字段
-- 先检查字段是否存在，如果不存在则添加
SET @dbname = 'erp';
SET @tablename = 'schedule_coating';
SET @columnname1 = 'order_item_id';
SET @columnname2 = 'order_id';

-- 添加order_item_id字段
SET @preparedStatement = (
    SELECT IF(COUNT(*) = 0, 
        CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname1, ' BIGINT COMMENT ''订单明细ID'' AFTER schedule_id'),
        'SELECT ''order_item_id already exists'' AS message'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname
    AND TABLE_NAME = @tablename
    AND COLUMN_NAME = @columnname1
);
PREPARE alterStatement FROM @preparedStatement;
EXECUTE alterStatement;
DEALLOCATE PREPARE alterStatement;

-- 添加order_id字段
SET @preparedStatement = (
    SELECT IF(COUNT(*) = 0, 
        CONCAT('ALTER TABLE ', @tablename, ' ADD COLUMN ', @columnname2, ' BIGINT COMMENT ''订单ID'' AFTER order_item_id'),
        'SELECT ''order_id already exists'' AS message'
    )
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = @dbname
    AND TABLE_NAME = @tablename
    AND COLUMN_NAME = @columnname2
);
PREPARE alterStatement FROM @preparedStatement;
EXECUTE alterStatement;
DEALLOCATE PREPARE alterStatement;

-- 2. 创建索引（如果不存在）
SET @index_sql = (
    SELECT IF(COUNT(*) = 0,
        'CREATE INDEX idx_order_item_id ON schedule_coating(order_item_id)',
        'SELECT ''Index idx_order_item_id already exists'' AS message'
    )
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = 'erp'
    AND TABLE_NAME = 'schedule_coating'
    AND INDEX_NAME = 'idx_order_item_id'
);
PREPARE stmt FROM @index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_sql = (
    SELECT IF(COUNT(*) = 0,
        'CREATE INDEX idx_order_id ON schedule_coating(order_id)',
        'SELECT ''Index idx_order_id already exists'' AS message'
    )
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = 'erp'
    AND TABLE_NAME = 'schedule_coating'
    AND INDEX_NAME = 'idx_order_id'
);
PREPARE stmt FROM @index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 添加外键约束（如果sales_order_item表存在）
-- ALTER TABLE schedule_coating 
-- ADD CONSTRAINT fk_coating_order_item 
-- FOREIGN KEY (order_item_id) REFERENCES sales_order_item(id) ON DELETE SET NULL;

SELECT '✓ 涂布计划表字段添加完成' AS '步骤1';

-- ================== 第二部分：创建物料生产配置表 ==================

-- 4. 创建物料生产配置表（管理MOQ和生产参数）
CREATE TABLE IF NOT EXISTS material_production_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  material_code VARCHAR(50) NOT NULL COMMENT '物料编号',
  material_name VARCHAR(100) COMMENT '物料名称',
  material_type VARCHAR(20) COMMENT '物料类型：coating-涂布,printing-印刷',
  
  -- 生产数量控制
  min_production_qty INT COMMENT '最小生产数量',
  min_production_area DECIMAL(10,2) COMMENT '最小生产面积(㎡)',
  standard_batch_size INT COMMENT '标准批量',
  max_batch_size INT COMMENT '最大批量',
  
  -- 时间参数
  setup_time INT COMMENT '调机时间(分钟)',
  unit_time DECIMAL(10,2) COMMENT '单位时间(分钟/㎡)',
  cleanup_time INT COMMENT '清理时间(分钟)',
  
  -- 质量参数
  loss_rate DECIMAL(5,2) DEFAULT 5.00 COMMENT '损耗率(%)',
  qualified_rate DECIMAL(5,2) DEFAULT 95.00 COMMENT '合格率(%)',
  
  -- 成本参数
  unit_cost DECIMAL(10,4) COMMENT '单位成本(元/㎡)',
  
  -- 推荐参数
  recommended_width INT COMMENT '推荐薄膜宽度(mm)',
  recommended_thickness INT COMMENT '推荐厚度(μm)',
  
  -- 状态
  is_active TINYINT DEFAULT 1 COMMENT '是否启用',
  remark VARCHAR(500) COMMENT '备注',
  
  -- 审计字段
  create_by VARCHAR(50) COMMENT '创建人',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by VARCHAR(50) COMMENT '更新人',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  
  UNIQUE KEY uk_material_code (material_code),
  INDEX idx_material_type (material_type),
  INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物料生产配置表';

SELECT '✓ 物料生产配置表创建完成' AS '步骤2';

-- ================== 第三部分：插入示例配置数据 ==================

-- 5. 插入常见物料的生产配置示例
INSERT INTO material_production_config 
  (material_code, material_name, material_type, min_production_area, setup_time, unit_time, recommended_width, recommended_thickness, loss_rate)
VALUES
  ('1011-R02-1507-B01-0400', '22μm蓝色PET胶带', 'coating', 50.00, 30, 0.5, 1040, 22, 5.00),
  ('1011-R02-1507-B01-0600', '21μm绿色保护膜', 'coating', 60.00, 30, 0.5, 1040, 21, 5.00),
  ('103-WW-55-T01-0015', 'PET保护膜', 'coating', 80.00, 30, 0.6, 1280, 55, 6.00)
ON DUPLICATE KEY UPDATE
  min_production_area = VALUES(min_production_area),
  setup_time = VALUES(setup_time),
  unit_time = VALUES(unit_time),
  update_time = NOW();

SELECT '✓ 示例配置数据插入完成' AS '步骤3';

-- ================== 第四部分：查看待排程订单 ==================

-- 6. 查看当前待排程的订单（用于验证）
SELECT 
    '待排程订单查询' AS '查询类型',
    COUNT(*) AS '待排程订单数'
FROM sales_order_item soi
INNER JOIN sales_order so ON soi.order_id = so.id
WHERE soi.quantity > IFNULL(soi.scheduled_quantity, 0)
  AND so.status != 'cancelled';

-- 7. 查看按料号分组的待排程数量
SELECT 
    soi.material_code AS '料号',
    soi.material_name AS '产品名称',
    COUNT(*) AS '订单数',
    SUM(soi.quantity - IFNULL(soi.scheduled_quantity, 0)) AS '待排程数量',
    SUM((soi.quantity - IFNULL(soi.scheduled_quantity, 0)) * soi.width * soi.length / 1000000) AS '待排程面积(㎡)',
    MIN(so.delivery_date) AS '最早交期',
    GROUP_CONCAT(DISTINCT so.customer_name SEPARATOR ', ') AS '客户列表'
FROM sales_order_item soi
INNER JOIN sales_order so ON soi.order_id = so.id
WHERE soi.quantity > IFNULL(soi.scheduled_quantity, 0)
  AND so.status != 'cancelled'
GROUP BY soi.material_code, soi.material_name
ORDER BY MIN(so.delivery_date), SUM(soi.quantity - IFNULL(soi.scheduled_quantity, 0)) DESC;

SELECT '✓ 待排程订单查询完成' AS '步骤4';

-- ================== 第五部分：创建辅助视图 ==================

-- 8. 创建待排程订单视图（方便查询）
CREATE OR REPLACE VIEW v_pending_schedule_orders AS
SELECT 
    soi.id AS order_item_id,
    so.id AS order_id,
    so.order_no,
    soi.material_code,
    soi.material_name,
    so.customer_name,
    so.customer_level,
    soi.quantity AS order_qty,
    IFNULL(soi.scheduled_quantity, 0) AS scheduled_qty,
    (soi.quantity - IFNULL(soi.scheduled_quantity, 0)) AS pending_qty,
    soi.width,
    soi.length,
    soi.thickness,
    (soi.quantity - IFNULL(soi.scheduled_quantity, 0)) * soi.width * soi.length / 1000000 AS pending_area,
    so.delivery_date,
    DATEDIFF(so.delivery_date, CURDATE()) AS days_until_delivery,
    so.status AS order_status,
    mpc.min_production_area,
    mpc.recommended_width,
    so.create_time AS order_create_time
FROM sales_order_item soi
INNER JOIN sales_order so ON soi.order_id = so.id
LEFT JOIN material_production_config mpc ON soi.material_code = mpc.material_code
WHERE soi.quantity > IFNULL(soi.scheduled_quantity, 0)
  AND so.status NOT IN ('cancelled', 'closed')
ORDER BY 
    CASE so.customer_level 
        WHEN 'VIP' THEN 1 
        WHEN 'A' THEN 2 
        WHEN 'B' THEN 3 
        ELSE 4 
    END,
    so.delivery_date,
    soi.material_code;

SELECT '✓ 待排程订单视图创建完成' AS '步骤5';

-- ================== 第六部分：验证结果 ==================

-- 9. 验证表结构
SELECT 
    TABLE_NAME AS '表名',
    COLUMN_NAME AS '字段名',
    COLUMN_TYPE AS '类型',
    COLUMN_COMMENT AS '注释'
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'erp'
  AND TABLE_NAME = 'schedule_coating'
  AND COLUMN_NAME IN ('order_id', 'order_item_id')
UNION ALL
SELECT 
    'material_production_config' AS '表名',
    '---' AS '字段名',
    '共有字段数' AS '类型',
    CAST(COUNT(*) AS CHAR) AS '注释'
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'erp'
  AND TABLE_NAME = 'material_production_config';

-- 10. 查看物料配置数据
SELECT 
    material_code AS '料号',
    material_name AS '产品名称',
    min_production_area AS '最小生产面积(㎡)',
    setup_time AS '调机时间(分钟)',
    recommended_width AS '推荐宽度(mm)',
    loss_rate AS '损耗率(%)'
FROM material_production_config
WHERE is_active = 1
ORDER BY material_code;

SELECT '✓ 验证完成' AS '步骤6';

-- ================== 执行完成提示 ==================

SELECT 
    '数据库结构修复完成！' AS '提示',
    '下一步：修改后端排程逻辑' AS '下一步操作';
