-- ============================================
-- Phase 1: 修改 tape_stock 表
-- 为库存锁定机制添加必要字段
-- ============================================

-- 添加面积追踪字段
ALTER TABLE tape_stock ADD COLUMN IF NOT EXISTS available_area DECIMAL(10,2) DEFAULT 0 COMMENT '可用面积(m²) = 总面积 - 锁定面积 - 消耗面积';
ALTER TABLE tape_stock ADD COLUMN IF NOT EXISTS reserved_area DECIMAL(10,2) DEFAULT 0 COMMENT '已预留面积(m²)，被锁定的';
ALTER TABLE tape_stock ADD COLUMN IF NOT EXISTS consumed_area DECIMAL(10,2) DEFAULT 0 COMMENT '已消耗面积(m²)，已领料的';

-- 添加物料分类字段
ALTER TABLE tape_stock ADD COLUMN IF NOT EXISTS reel_type VARCHAR(20) DEFAULT '复卷' COMMENT '物料类型：复卷、母卷、支料';

-- 添加乐观锁版本字段
ALTER TABLE tape_stock ADD COLUMN IF NOT EXISTS version INT DEFAULT 1 COMMENT '版本号，用于乐观锁';

-- 添加审计字段
ALTER TABLE tape_stock ADD COLUMN IF NOT EXISTS updated_by BIGINT COMMENT '最后修改人';
ALTER TABLE tape_stock ADD COLUMN IF NOT EXISTS lock_updated_time DATETIME COMMENT '最后一次锁定更新时间';

-- 创建索引优化查询
CREATE INDEX IF NOT EXISTS idx_reel_type_spec_id ON tape_stock(reel_type, spec_id);
CREATE INDEX IF NOT EXISTS idx_available_area ON tape_stock(available_area);

-- 初始化现有数据的 available_area 字段（如果为空）
UPDATE tape_stock 
SET available_area = COALESCE(total_area, 0) - COALESCE(consumed_area, 0)
WHERE available_area = 0 OR available_area IS NULL;

-- 查询验证
SELECT id, code, total_area, available_area, reserved_area, consumed_area, reel_type, version 
FROM tape_stock LIMIT 5;
