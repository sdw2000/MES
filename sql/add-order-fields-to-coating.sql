-- 为 schedule_coating 表添加订单相关字段
USE erp;

-- 添加订单号字段
ALTER TABLE schedule_coating ADD COLUMN IF NOT EXISTS order_no VARCHAR(50) COMMENT '订单号';

-- 添加订单ID字段
ALTER TABLE schedule_coating ADD COLUMN IF NOT EXISTS order_id BIGINT COMMENT '订单ID';

-- 添加订单明细ID字段
ALTER TABLE schedule_coating ADD COLUMN IF NOT EXISTS order_item_id BIGINT COMMENT '订单明细ID';

-- 添加索引
ALTER TABLE schedule_coating ADD INDEX IF NOT EXISTS idx_order_no (order_no);
ALTER TABLE schedule_coating ADD INDEX IF NOT EXISTS idx_order_item_id (order_item_id);

-- 验证字段是否添加成功
SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT 
FROM information_schema.COLUMNS 
WHERE TABLE_SCHEMA = 'erp' 
  AND TABLE_NAME = 'schedule_coating' 
  AND COLUMN_NAME IN ('order_no', 'order_id', 'order_item_id');
