-- 为 tape_stock 表添加缺失的列
USE erp;

ALTER TABLE tape_stock ADD COLUMN IF NOT EXISTS available_area DECIMAL(12,2) COMMENT '可用面积(m²)';
ALTER TABLE tape_stock ADD COLUMN IF NOT EXISTS reserved_area DECIMAL(12,2) DEFAULT 0 COMMENT '预留面积(m²)';
ALTER TABLE tape_stock ADD COLUMN IF NOT EXISTS consumed_area DECIMAL(12,2) DEFAULT 0 COMMENT '消耗面积(m²)';
ALTER TABLE tape_stock ADD COLUMN IF NOT EXISTS reel_type VARCHAR(50) COMMENT '卷筒类型';
ALTER TABLE tape_stock ADD COLUMN IF NOT EXISTS version INT DEFAULT 0 COMMENT '版本号';
ALTER TABLE tape_stock ADD COLUMN IF NOT EXISTS updated_by VARCHAR(50) COMMENT '更新人';
ALTER TABLE tape_stock ADD COLUMN IF NOT EXISTS lock_updated_time DATETIME COMMENT '锁定更新时间';

-- 检查新增列
DESC tape_stock;
