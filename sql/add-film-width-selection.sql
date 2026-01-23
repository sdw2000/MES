-- ================================================================
-- 涂布排程薄膜宽度选择和物料锁定功能
-- 功能：为涂布排程添加膜宽字段，支持从仓库选择宽度并锁定物料
-- 日期：2026-01-15
-- ================================================================

USE mes_db;

-- 1. 在涂布排程表中添加膜宽字段（如果不存在）
ALTER TABLE schedule_coating 
ADD COLUMN IF NOT EXISTS film_width INT COMMENT '膜宽(mm)' AFTER jumbo_width,
ADD COLUMN IF NOT EXISTS film_stock_id BIGINT COMMENT '薄膜库存ID' AFTER film_width,
ADD COLUMN IF NOT EXISTS film_locked_time DATETIME COMMENT '物料锁定时间' AFTER film_stock_id,
ADD COLUMN IF NOT EXISTS material_lock_status VARCHAR(20) DEFAULT 'unlocked' COMMENT '物料锁定状态: unlocked-未锁定, locked-已锁定, allocated-已领料' AFTER film_locked_time;

-- 2. 添加索引以提高查询性能
CREATE INDEX IF NOT EXISTS idx_film_stock_id ON schedule_coating(film_stock_id);
CREATE INDEX IF NOT EXISTS idx_material_lock_status ON schedule_coating(material_lock_status);

-- 3. 确保薄膜库存表有正确的索引
CREATE INDEX IF NOT EXISTS idx_width_thickness ON film_stock(width, thickness);
CREATE INDEX IF NOT EXISTS idx_available_area ON film_stock(available_area);

-- 4. 添加薄膜库存明细状态索引
CREATE INDEX IF NOT EXISTS idx_detail_status ON film_stock_detail(status);

-- 5. 验证表结构
SELECT 
    COLUMN_NAME AS '字段名',
    COLUMN_TYPE AS '类型',
    IS_NULLABLE AS '可空',
    COLUMN_DEFAULT AS '默认值',
    COLUMN_COMMENT AS '注释'
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = 'mes_db' 
  AND TABLE_NAME = 'schedule_coating'
  AND COLUMN_NAME IN ('film_width', 'film_stock_id', 'film_locked_time', 'material_lock_status')
ORDER BY ORDINAL_POSITION;

-- 6. 查看现有排程的统计
SELECT 
    COUNT(*) AS '总排程数',
    SUM(CASE WHEN film_width IS NOT NULL THEN 1 ELSE 0 END) AS '已选择宽度数',
    SUM(CASE WHEN material_lock_status = 'locked' THEN 1 ELSE 0 END) AS '已锁定物料数'
FROM schedule_coating;

-- 7. 查看可用薄膜宽度统计
SELECT 
    width AS '宽度(mm)',
    COUNT(DISTINCT thickness) AS '厚度种类数',
    SUM(available_area) AS '总可用面积(㎡)',
    SUM(available_rolls) AS '总可用卷数'
FROM film_stock
WHERE available_area > 0 AND available_rolls > 0
GROUP BY width
ORDER BY width;

-- 8. 创建视图：排程物料锁定情况
CREATE OR REPLACE VIEW v_schedule_material_lock AS
SELECT 
    sc.id AS schedule_coating_id,
    sc.task_no,
    sc.order_no,
    sc.material_code,
    sc.film_width,
    sc.film_stock_id,
    sc.material_lock_status,
    sc.film_locked_time,
    fs.material_name AS film_material_name,
    fs.thickness AS film_thickness,
    fs.available_area AS film_available_area,
    fs.available_rolls AS film_available_rolls,
    sml.locked_area,
    sml.lock_status,
    sml.locked_time AS lock_detail_time
FROM schedule_coating sc
LEFT JOIN film_stock fs ON sc.film_stock_id = fs.id
LEFT JOIN schedule_material_lock sml ON sc.schedule_id = sml.schedule_id
WHERE sc.material_lock_status != 'unlocked';

SELECT '✓ 薄膜宽度选择和物料锁定功能字段已添加完成' AS '执行结果';
