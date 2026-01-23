-- =====================================================
-- 未排程订单页面 - 数据库表结构修改脚本
-- =====================================================

-- =====================================================
-- 修改1: sales_order 表 - 添加排程跟踪字段
-- =====================================================
ALTER TABLE sales_order ADD COLUMN IF NOT EXISTS (
  `unscheduled_qty` INT DEFAULT 0 COMMENT '未排程数量',
  `shortage_qty` INT DEFAULT 0 COMMENT '缺口总数量',
  `has_shortage` TINYINT(1) DEFAULT 0 COMMENT '是否有缺口',
  `schedule_status` VARCHAR(50) DEFAULT 'PENDING' COMMENT '排程状态: PENDING(待排)/PARTIAL(部分排)/COMPLETED(已排完)/CANCELLED(取消)',
  `coating_entry_status` VARCHAR(50) DEFAULT 'NOT_ENTERED' COMMENT '涂布池入池状态: NOT_ENTERED(未入)/ENTERED(已入)/SCHEDULED(已排)/COMPLETED(已完)',
  `last_status_check` DATETIME COMMENT '最后一次状态检查时间',
  `coating_pool_entry_time` DATETIME COMMENT '进入待排程池的时间'
) COMMENT '销售订单表';

CREATE INDEX IF NOT EXISTS idx_so_schedule_status ON sales_order(schedule_status);
CREATE INDEX IF NOT EXISTS idx_so_coating_entry_status ON sales_order(coating_entry_status);
CREATE INDEX IF NOT EXISTS idx_so_shortage_qty ON sales_order(shortage_qty);
CREATE INDEX IF NOT EXISTS idx_so_plan_date_status ON sales_order(plan_date, schedule_status);

-- =====================================================
-- 修改2: order_item (或sales_order_detail) 表
-- =====================================================
ALTER TABLE sales_order_detail ADD COLUMN IF NOT EXISTS (
  `locked_qty` INT DEFAULT 0 COMMENT '已锁定数量',
  `shortage_qty` INT DEFAULT 0 COMMENT '缺口数量',
  `in_pool_qty` INT DEFAULT 0 COMMENT '已进待排程池的数量',
  `material_status` VARCHAR(50) DEFAULT 'UNLOCKED' COMMENT '物料状态: UNLOCKED(未锁)/PARTIAL(部分)/LOCKED(已锁)/SHORTAGE(缺口)',
  `need_slitting` TINYINT(1) DEFAULT 0 COMMENT '是否需要复卷分切',
  `shortage_id` BIGINT COMMENT '关联的缺口记录ID',
  `last_updated` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间'
) COMMENT '销售订单明细表';

CREATE INDEX IF NOT EXISTS idx_sod_material_status ON sales_order_detail(material_status);
CREATE INDEX IF NOT EXISTS idx_sod_need_slitting ON sales_order_detail(need_slitting);
CREATE INDEX IF NOT EXISTS idx_sod_material_code_status ON sales_order_detail(material_code, material_status);

-- =====================================================
-- 修改3: material_lock_shortage 表 - 添加复卷分切相关字段
-- =====================================================
ALTER TABLE material_lock_shortage ADD COLUMN IF NOT EXISTS (
  `need_slitting` TINYINT(1) DEFAULT 1 COMMENT '是否需要复卷分切(0:无法分切 1:需要分切)',
  `source_material_code` VARCHAR(100) COMMENT '源物料代码(分切来源)',
  `available_source_qty` INT COMMENT '可用源物料数量(查询时的快照)',
  `slitting_equipment_id` BIGINT COMMENT '推荐分切设备ID',
  `urgency_level` VARCHAR(50) DEFAULT 'NORMAL' COMMENT '紧急程度: URGENT/NORMAL/LOW',
  `priority_score` DECIMAL(10,2) COMMENT '综合优先级评分',
  `days_to_deadline` INT COMMENT '距离交期天数',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间'
) COMMENT '物料锁定缺口表';

CREATE INDEX IF NOT EXISTS idx_mls_need_slitting ON material_lock_shortage(need_slitting, status);
CREATE INDEX IF NOT EXISTS idx_mls_priority ON material_lock_shortage(priority_score DESC);
CREATE INDEX IF NOT EXISTS idx_mls_source_material ON material_lock_shortage(source_material_code);
CREATE INDEX IF NOT EXISTS idx_mls_days_to_deadline ON material_lock_shortage(days_to_deadline ASC);
CREATE INDEX IF NOT EXISTS idx_mls_urgency ON material_lock_shortage(urgency_level, priority_score DESC);

-- =====================================================
-- 修改4: pending_coating_pool 表 - 完善相关字段
-- =====================================================
ALTER TABLE pending_coating_pool ADD COLUMN IF NOT EXISTS (
  `shortage_id` BIGINT COMMENT '关联的缺口记录ID(如果来自分切)',
  `is_from_slitting` TINYINT(1) DEFAULT 0 COMMENT '是否来自复卷分切',
  `source_material_code` VARCHAR(100) COMMENT '如果来自分切，源物料代码',
  `slitting_task_id` BIGINT COMMENT '关联的分切任务ID',
  `wait_hours` INT COMMENT '等待小时数',
  `priority_score` DECIMAL(10,2) COMMENT '综合优先级评分',
  `coating_deadline` DATETIME COMMENT '涂布截止时间',
  `coating_start_required` DATETIME COMMENT '必须开始涂布的时间(考虑工期)',
  `entry_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '进入池子的时间'
) COMMENT '待涂布池表';

CREATE INDEX IF NOT EXISTS idx_pcp_priority ON pending_coating_pool(priority_score DESC, coating_deadline ASC);
CREATE INDEX IF NOT EXISTS idx_pcp_status_deadline ON pending_coating_pool(status, coating_deadline);
CREATE INDEX IF NOT EXISTS idx_pcp_is_from_slitting ON pending_coating_pool(is_from_slitting);
CREATE INDEX IF NOT EXISTS idx_pcp_coating_deadline ON pending_coating_pool(coating_deadline);

-- =====================================================
-- 新增视图: v_unscheduled_orders - 未排程订单汇总
-- =====================================================
DROP VIEW IF EXISTS v_unscheduled_orders;
CREATE VIEW v_unscheduled_orders AS
SELECT 
  so.id as order_id,
  so.order_no,
  so.customer_id,
  c.customer_name,
  IFNULL(cp.priority_score, 0) as customer_priority,
  so.plan_date,
  so.delivery_date,
  DATEDIFF(so.delivery_date, CURDATE()) as days_to_delivery,
  COUNT(DISTINCT sod.id) as item_count,
  SUM(sod.qty) as total_qty,
  SUM(IFNULL(sod.locked_qty, 0)) as locked_qty,
  SUM(IFNULL(sod.shortage_qty, 0)) as shortage_qty,
  SUM(sod.qty) - SUM(IFNULL(sod.locked_qty, 0)) as unscheduled_qty,
  SUM(CASE WHEN sod.need_slitting = 1 THEN IFNULL(sod.shortage_qty, 0) ELSE 0 END) as need_slitting_qty,
  COUNT(DISTINCT CASE WHEN mls.status = 'PENDING' THEN mls.id END) as pending_shortage_count,
  COUNT(DISTINCT CASE WHEN mls.need_slitting = 1 AND mls.status = 'PENDING' THEN mls.id END) as slitting_shortage_count,
  COUNT(DISTINCT CASE WHEN pcp.status = 'WAITING' THEN pcp.id END) as in_pool_waiting_count,
  COUNT(DISTINCT CASE WHEN pcp.status = 'SCHEDULED' THEN pcp.id END) as in_pool_scheduled_count,
  so.schedule_status,
  so.coating_entry_status,
  CASE 
    WHEN SUM(IFNULL(sod.locked_qty, 0)) >= SUM(sod.qty) AND COUNT(DISTINCT pcp.id) = 0 THEN '✅可直接排程'
    WHEN SUM(CASE WHEN sod.need_slitting = 1 THEN IFNULL(sod.shortage_qty, 0) ELSE 0 END) > 0 THEN '🟡需要复卷分切'
    WHEN SUM(IFNULL(sod.shortage_qty, 0)) > 0 THEN '🔴需要手动处理'
    WHEN COUNT(DISTINCT pcp.id) > 0 THEN '🔵已入池待排程'
    ELSE '⚪待检查'
  END as status_badge,
  GROUP_CONCAT(DISTINCT sod.material_code) as material_codes,
  GROUP_CONCAT(DISTINCT CASE WHEN sod.need_slitting = 1 THEN sod.material_code END) as slitting_materials,
  so.created_at,
  MAX(so.updated_at) as updated_at
FROM sales_order so
LEFT JOIN customer c ON so.customer_id = c.id
LEFT JOIN order_customer_priority cp ON so.customer_id = cp.customer_id
LEFT JOIN sales_order_detail sod ON so.id = sod.order_id AND sod.is_deleted = 0
LEFT JOIN material_lock_shortage mls ON so.id = mls.order_id
LEFT JOIN pending_coating_pool pcp ON so.id = pcp.order_id
WHERE 
  so.order_status NOT IN ('CANCELLED', 'COMPLETED')
  AND so.schedule_status IN ('PENDING', 'PARTIAL')
  AND (sod.locked_qty > 0 OR mls.shortage_qty > 0 OR pcp.id IS NOT NULL)
GROUP BY so.id, so.order_no, so.customer_id, c.customer_name, cp.priority_score,
         so.plan_date, so.delivery_date, so.schedule_status, so.coating_entry_status,
         so.created_at
ORDER BY IFNULL(cp.priority_score, 0) DESC, so.plan_date ASC;

-- =====================================================
-- 新增视图: v_shortage_details - 物料缺口详情(带优先级)
-- =====================================================
DROP VIEW IF EXISTS v_shortage_details;
CREATE VIEW v_shortage_details AS
SELECT 
  mls.id as shortage_id,
  mls.order_id,
  so.order_no,
  c.customer_name,
  IFNULL(cp.priority_score, 0) as customer_priority,
  mls.material_code as target_material,
  mls.shortage_qty,
  mls.source_material_code,
  ts.available_qty as source_available_qty,
  CASE 
    WHEN ts.available_qty >= mls.shortage_qty THEN 1
    ELSE 0
  END as can_slitting,
  mls.need_slitting,
  mls.urgency_level,
  mls.priority_score,
  so.delivery_date,
  DATEDIFF(so.delivery_date, CURDATE()) as days_to_delivery,
  mls.status,
  mls.slitting_task_id,
  mls.updated_at
FROM material_lock_shortage mls
LEFT JOIN sales_order so ON mls.order_id = so.id
LEFT JOIN customer c ON so.customer_id = c.id
LEFT JOIN order_customer_priority cp ON so.customer_id = cp.customer_id
LEFT JOIN tape_stock ts ON mls.source_material_code = ts.material_code
WHERE mls.status IN ('PENDING', 'IN_SLITTING')
ORDER BY mls.priority_score DESC, so.delivery_date ASC;

-- =====================================================
-- 新增表: order_schedule_progress - 订单排程进度追踪表
-- =====================================================
CREATE TABLE IF NOT EXISTS `order_schedule_progress` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` BIGINT(20) NOT NULL COMMENT '订单ID',
  `order_no` VARCHAR(50) NOT NULL COMMENT '订单号',
  
  -- 阶段1: 物料锁定
  `material_lock_started_at` DATETIME COMMENT '物料锁定开始时间',
  `material_lock_completed_at` DATETIME COMMENT '物料锁定完成时间',
  `lock_status` VARCHAR(50) DEFAULT 'PENDING' COMMENT '锁定状态: PENDING/PARTIAL/COMPLETED',
  
  -- 阶段2: 缺口处理
  `shortage_detected_at` DATETIME COMMENT '缺口检测时间',
  `shortage_count` INT DEFAULT 0 COMMENT '缺口数量',
  `shortage_status` VARCHAR(50) DEFAULT 'NONE' COMMENT '缺口处理状态: NONE/PENDING/IN_SLITTING/COMPLETED',
  `shortage_completed_at` DATETIME COMMENT '缺口处理完成时间',
  
  -- 阶段3: 进入待排程池
  `pool_entry_started_at` DATETIME COMMENT '进入池子开始时间',
  `pool_entry_completed_at` DATETIME COMMENT '进入池子完成时间',
  `pool_entry_count` INT DEFAULT 0 COMMENT '成功进入池子的明细数',
  
  -- 阶段4: 涂布排程
  `schedule_started_at` DATETIME COMMENT '排程开始时间',
  `schedule_completed_at` DATETIME COMMENT '排程完成时间',
  `schedule_count` INT DEFAULT 0 COMMENT '已排程的物料明细数',
  
  -- 总体进度
  `overall_progress` INT DEFAULT 0 COMMENT '整体进度百分比(0-100)',
  `current_stage` VARCHAR(50) COMMENT '当前所在阶段: LOCK/SHORTAGE/POOL/SCHEDULE/COMPLETE',
  `next_action` VARCHAR(100) COMMENT '下一步需要的操作',
  `next_action_by` DATETIME COMMENT '下一步操作的建议时间',
  
  -- 记录信息
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  KEY `idx_current_stage` (`current_stage`),
  KEY `idx_next_action_by` (`next_action_by`),
  KEY `idx_updated_at` (`updated_at`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='订单排程进度追踪表';

-- =====================================================
-- 索引汇总
-- =====================================================
-- 已创建的关键索引总结：
-- 1. sales_order:
--    - idx_so_schedule_status (排程状态快速查询)
--    - idx_so_coating_entry_status (涂布池入池状态)
--    - idx_so_plan_date_status (按计划日期和排程状态)
-- 
-- 2. sales_order_detail:
--    - idx_sod_material_status (物料状态快速查询)
--    - idx_sod_need_slitting (是否需要分切)
--    - idx_sod_material_code_status (复合索引)
--
-- 3. material_lock_shortage:
--    - idx_mls_need_slitting (是否需要分切)
--    - idx_mls_priority (优先级排序)
--    - idx_mls_source_material (源物料查询)
--    - idx_mls_days_to_deadline (交期紧急度)
--    - idx_mls_urgency (紧急程度排序)
--
-- 4. pending_coating_pool:
--    - idx_pcp_priority (优先级排序)
--    - idx_pcp_status_deadline (状态和截止时间)
--    - idx_pcp_is_from_slitting (分切来源)
--    - idx_pcp_coating_deadline (涂布截止)
--
-- 5. order_schedule_progress:
--    - idx_current_stage (当前阶段)
--    - idx_next_action_by (操作建议时间)

-- =====================================================
-- 数据初始化脚本(可选)
-- =====================================================

-- 初始化现有订单的排程进度记录
-- INSERT INTO order_schedule_progress (order_id, order_no, current_stage)
-- SELECT id, order_no, 'LOCK' FROM sales_order 
-- WHERE order_status NOT IN ('CANCELLED', 'COMPLETED')
-- ON DUPLICATE KEY UPDATE updated_at = NOW();

