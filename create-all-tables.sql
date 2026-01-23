-- =====================================================
-- 物料锁定缺口表
-- =====================================================
CREATE TABLE IF NOT EXISTS `material_lock_shortage` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint(20) NOT NULL COMMENT '订单ID',
  `order_no` varchar(50) NOT NULL COMMENT '订单号',
  `material_code` varchar(100) NOT NULL COMMENT '物料代码',
  `shortage_qty` int(11) NOT NULL COMMENT '缺口数量',
  `customer_priority` decimal(10,2) COMMENT '客户优先级',
  `status` varchar(50) DEFAULT 'PENDING' COMMENT '状态：PENDING(待处理)/IN_SLITTING(分切中)/COMPLETED(已完成)/FAILED(失败)',
  `slitting_task_id` bigint(20) COMMENT '分切任务ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `remark` varchar(500) COMMENT '备注',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_material_code` (`material_code`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='物料锁定缺口表';

-- =====================================================
-- 待涂布池表
-- =====================================================
CREATE TABLE IF NOT EXISTS `pending_coating_pool` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint(20) NOT NULL COMMENT '订单ID',
  `order_no` varchar(50) NOT NULL COMMENT '订单号',
  `material_code` varchar(100) NOT NULL COMMENT '物料代码',
  `tape_spec_id` bigint(20) COMMENT '胶带规格ID',
  `qty` int(11) NOT NULL COMMENT '数量',
  `film_width` decimal(10,2) COMMENT '膜宽',
  `customer_priority` decimal(10,2) COMMENT '客户优先级',
  `status` varchar(50) DEFAULT 'WAITING' COMMENT '状态：WAITING(等待)/SCHEDULED(已排程)/IN_PROGRESS(进行中)/COMPLETED(已完成)',
  `coating_schedule_id` bigint(20) COMMENT '涂布排程ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_status` (`status`),
  KEY `idx_material_code` (`material_code`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='待涂布池表';

-- =====================================================
-- 涂布排程表
-- =====================================================
CREATE TABLE IF NOT EXISTS `coating_schedule` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `schedule_code` varchar(50) NOT NULL UNIQUE COMMENT '排程代码',
  `pool_id` bigint(20) NOT NULL COMMENT '待涂布池ID',
  `order_id` bigint(20) NOT NULL COMMENT '订单ID',
  `order_no` varchar(50) NOT NULL COMMENT '订单号',
  `equipment_id` bigint(20) NOT NULL COMMENT '设备ID',
  `equipment_name` varchar(100) COMMENT '设备名称',
  `film_width` decimal(10,2) COMMENT '膜宽',
  `qty` int(11) NOT NULL COMMENT '数量',
  `scheduled_start` datetime COMMENT '计划开始时间',
  `scheduled_end` datetime COMMENT '计划结束时间',
  `actual_start` datetime COMMENT '实际开始时间',
  `actual_end` datetime COMMENT '实际结束时间',
  `estimated_time_minutes` int(11) COMMENT '预计时间(分钟)',
  `status` varchar(50) DEFAULT 'PENDING' COMMENT '状态：PENDING(待执行)/RUNNING(运行中)/COMPLETED(已完成)/CANCELLED(已取消)',
  `conflict_status` varchar(50) DEFAULT 'CLEAN' COMMENT '冲突状态：CLEAN(无冲突)/WARNING(警告)/ERROR(错误)',
  `customer_priority` decimal(10,2) COMMENT '客户优先级',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_schedule_code` (`schedule_code`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_equipment_id` (`equipment_id`),
  KEY `idx_status` (`status`),
  KEY `idx_scheduled_start` (`scheduled_start`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='涂布排程表';

-- =====================================================
-- 复卷分切任务表
-- =====================================================
CREATE TABLE IF NOT EXISTS `slitting_task` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_code` varchar(50) NOT NULL UNIQUE COMMENT '任务代码',
  `shortage_id` bigint(20) NOT NULL COMMENT '缺口ID',
  `order_id` bigint(20) NOT NULL COMMENT '订单ID',
  `order_no` varchar(50) NOT NULL COMMENT '订单号',
  `source_material_code` varchar(100) NOT NULL COMMENT '源物料代码',
  `target_material_code` varchar(100) NOT NULL COMMENT '目标物料代码',
  `film_width` decimal(10,2) COMMENT '膜宽',
  `qty` int(11) NOT NULL COMMENT '需求数量',
  `slitting_equipment_id` bigint(20) COMMENT '分切设备ID',
  `status` varchar(50) DEFAULT 'PENDING' COMMENT '状态：PENDING(待执行)/RUNNING(运行中)/COMPLETED(已完成)/FAILED(失败)',
  `scheduled_date` date COMMENT '计划日期',
  `actual_start` datetime COMMENT '实际开始时间',
  `actual_end` datetime COMMENT '实际结束时间',
  `completed_qty` int(11) COMMENT '完成数量',
  `waste_qty` int(11) COMMENT '废料数量',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_code` (`task_code`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_shortage_id` (`shortage_id`),
  KEY `idx_status` (`status`),
  KEY `idx_scheduled_date` (`scheduled_date`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='复卷分切任务表';

-- =====================================================
-- 分切结果表
-- =====================================================
CREATE TABLE IF NOT EXISTS `slitting_result` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `task_id` bigint(20) NOT NULL COMMENT '任务ID',
  `task_code` varchar(50) NOT NULL COMMENT '任务代码',
  `target_material_code` varchar(100) NOT NULL COMMENT '目标物料代码',
  `qty` int(11) NOT NULL COMMENT '完成数量',
  `waste_qty` int(11) COMMENT '废料数量',
  `batch_no` varchar(50) COMMENT '批号',
  `produced_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '生产时间',
  `warehouse_status` varchar(50) DEFAULT 'PENDING' COMMENT '仓库状态：PENDING(待入库)/IN_WAREHOUSE(已入库)/ALLOCATED(已分配)',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_target_material_code` (`target_material_code`),
  KEY `idx_warehouse_status` (`warehouse_status`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='分切结果表';

-- =====================================================
-- 成本追溯表
-- =====================================================
CREATE TABLE IF NOT EXISTS `cost_tracking` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint(20) NOT NULL COMMENT '订单ID',
  `order_no` varchar(50) NOT NULL COMMENT '订单号',
  `material_cost` decimal(15,4) DEFAULT 0 COMMENT '物料成本',
  `slitting_cost` decimal(15,4) DEFAULT 0 COMMENT '分切成本',
  `coating_cost` decimal(15,4) DEFAULT 0 COMMENT '涂布成本',
  `labor_cost` decimal(15,4) DEFAULT 0 COMMENT '人工成本',
  `equipment_cost` decimal(15,4) DEFAULT 0 COMMENT '设备成本',
  `other_cost` decimal(15,4) DEFAULT 0 COMMENT '其他成本',
  `total_cost` decimal(15,4) DEFAULT 0 COMMENT '总成本',
  `material_weight` decimal(10,2) COMMENT '物料重量(kg)',
  `finished_qty` int(11) COMMENT '成品数量',
  `unit_cost` decimal(15,4) COMMENT '单位成本',
  `status` varchar(50) DEFAULT 'IN_PROGRESS' COMMENT '状态：IN_PROGRESS(进行中)/COMPLETED(已完成)',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='成本追溯表';
