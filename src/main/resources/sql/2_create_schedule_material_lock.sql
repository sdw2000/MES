-- ============================================
-- Phase 1: 创建 schedule_material_lock 表
-- 排程物料锁定表
-- ============================================

CREATE TABLE IF NOT EXISTS schedule_material_lock (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '锁定记录ID',
  
  -- 关键外键
  schedule_id BIGINT NOT NULL COMMENT '批排程ID',
  order_id BIGINT NOT NULL COMMENT '订单ID',
  tape_stock_id BIGINT NOT NULL COMMENT '被锁定的物料卷ID',
  
  -- 锁定数据
  locked_area DECIMAL(10,2) NOT NULL COMMENT '锁定的面积(m²)',
  required_area DECIMAL(10,2) NOT NULL COMMENT '订单需求面积(m²)',
  
  -- 状态管理
  lock_status VARCHAR(50) DEFAULT '锁定中' COMMENT '锁定状态：锁定中、已领料、已消耗、已释放、已取消',
  
  -- 时间戳
  locked_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '锁定时间',
  allocated_time DATETIME COMMENT '分配时间(生产领料时)',
  consumed_time DATETIME COMMENT '消耗时间(生产反馈时)',
  released_time DATETIME COMMENT '释放时间(异常取消时)',
  
  -- 操作人员
  locked_by_user_id BIGINT COMMENT '操作者ID(排程人)',
  allocated_by_user_id BIGINT COMMENT '领料人ID',
  
  -- 乐观锁
  version INT DEFAULT 1 COMMENT '版本号',
  
  -- 备注
  remark VARCHAR(500) COMMENT '备注信息',
  
  -- 审计
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  
  -- 约束
  UNIQUE KEY uk_schedule_order_tape (schedule_id, order_id, tape_stock_id),
  KEY idx_schedule_id (schedule_id),
  KEY idx_order_id (order_id),
  KEY idx_tape_stock_id (tape_stock_id),
  KEY idx_lock_status (lock_status),
  
  FOREIGN KEY fk_schedule (schedule_id) REFERENCES batch_schedule(id),
  FOREIGN KEY fk_order (order_id) REFERENCES sales_order(id),
  FOREIGN KEY fk_tape_stock (tape_stock_id) REFERENCES tape_stock(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='排程物料锁定表';

-- 创建额外索引
CREATE INDEX IF NOT EXISTS idx_lock_status_time ON schedule_material_lock(lock_status, locked_time);

-- 查询验证
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT 
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_NAME = 'schedule_material_lock' 
ORDER BY ORDINAL_POSITION;
