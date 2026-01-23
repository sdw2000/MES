-- 为 tape_stock 表添加库存锁定机制所需的列
-- 这些列用于实现库存预留、锁定和消耗的跟踪

-- 检查并添加 available_area 列（可用面积）
ALTER TABLE tape_stock ADD COLUMN IF NOT EXISTS available_area DECIMAL(10, 2) DEFAULT 0 COMMENT '可用面积(m²)';

-- 检查并添加 reserved_area 列（已预留/锁定面积）
ALTER TABLE tape_stock ADD COLUMN IF NOT EXISTS reserved_area DECIMAL(10, 2) DEFAULT 0 COMMENT '已预留面积(m²)';

-- 检查并添加 consumed_area 列（已消耗面积）
ALTER TABLE tape_stock ADD COLUMN IF NOT EXISTS consumed_area DECIMAL(10, 2) DEFAULT 0 COMMENT '已消耗面积(m²)';

-- 检查并添加 reel_type 列（物料类型）
ALTER TABLE tape_stock ADD COLUMN IF NOT EXISTS reel_type VARCHAR(50) COMMENT '物料类型：复卷、母卷、支料';

-- 检查并添加 version 列（版本号，用于乐观锁）
ALTER TABLE tape_stock ADD COLUMN IF NOT EXISTS version INT DEFAULT 0 COMMENT '版本号，用于乐观锁';

-- 检查并添加 updated_by 列（最后修改人）
ALTER TABLE tape_stock ADD COLUMN IF NOT EXISTS updated_by BIGINT COMMENT '最后修改人';

-- 检查并添加 lock_updated_time 列（最后一次锁定更新时间）
ALTER TABLE tape_stock ADD COLUMN IF NOT EXISTS lock_updated_time DATETIME COMMENT '最后一次锁定更新时间';

-- 初始化 available_area 为 total_sqm（所有现有库存都可用）
UPDATE tape_stock SET available_area = COALESCE(total_sqm, 0) WHERE available_area = 0;

-- 输出完成信息
SELECT '库存锁定字段已添加到 tape_stock 表' as message;
