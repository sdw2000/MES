-- ============================================
-- Phase 1: 创建 schedule_material_allocation 表
-- 排程物料分配汇总表
-- ============================================

CREATE TABLE IF NOT EXISTS schedule_material_allocation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '分配记录ID',
  
  -- 关键外键
  schedule_id BIGINT NOT NULL COMMENT '批排程ID',
  order_id BIGINT NOT NULL COMMENT '订单ID',
  
  -- 物料需求与分配情况
  required_area DECIMAL(10,2) NOT NULL COMMENT '订单需求的总面积(m²)',
  allocated_area DECIMAL(10,2) DEFAULT 0 COMMENT '实际分配的面积(m²)',
  shortage_area DECIMAL(10,2) DEFAULT 0 COMMENT '不足面积(m²) = required - allocated',
  
  -- 分配状态
  allocation_status VARCHAR(50) DEFAULT '未满足' COMMENT '分配状态：完全满足、部分满足、未满足',
  
  -- 涂布排程相关
  need_coating TINYINT DEFAULT 0 COMMENT '是否需要触发涂布排程(0/1)',
  coating_schedule_id BIGINT COMMENT '关联的涂布排程ID',
  
  -- 时间戳
  allocated_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '分配时间',
  
  -- 审计
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  
  -- 约束
  UNIQUE KEY uk_schedule_order (schedule_id, order_id),
  KEY idx_schedule_id (schedule_id),
  KEY idx_order_id (order_id),
  KEY idx_allocation_status (allocation_status),
  
  FOREIGN KEY fk_schedule (schedule_id) REFERENCES batch_schedule(id),
  FOREIGN KEY fk_order (order_id) REFERENCES sales_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='排程物料分配表';

-- 创建额外索引
CREATE INDEX IF NOT EXISTS idx_need_coating ON schedule_material_allocation(need_coating);

-- 查询验证
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT 
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_NAME = 'schedule_material_allocation' 
ORDER BY ORDINAL_POSITION;
