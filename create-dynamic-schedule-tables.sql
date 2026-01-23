-- ========================================
-- 动态排程系统核心表结构
-- 作者：GitHub Copilot
-- 日期：2026-01-16
-- 描述：客户优先级、物料锁定、涂布排程等核心表
-- ========================================

-- ========== 1. 客户交易统计表（用于计算优先级） ==========
CREATE TABLE IF NOT EXISTS `customer_transaction_stats` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `customer_id` BIGINT NOT NULL COMMENT '客户ID',
  `customer_name` VARCHAR(200) NOT NULL COMMENT '客户名称',
  `payment_terms` INT NOT NULL DEFAULT 30 COMMENT '账期（月数）',
  `last_3m_amount` DECIMAL(15,2) DEFAULT 0.00 COMMENT '近3个月成交总金额',
  `last_3m_order_count` INT DEFAULT 0 COMMENT '近3个月订单数量',
  `avg_monthly_amount` DECIMAL(15,2) DEFAULT 0.00 COMMENT '月均成交金额',
  `stats_date` DATE NOT NULL COMMENT '统计日期',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_date` (`customer_id`, `stats_date`),
  INDEX `idx_customer_id` (`customer_id`),
  INDEX `idx_stats_date` (`stats_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户交易统计表';

-- ========== 2. 客户料号单价统计表（用于单价得分） ==========
CREATE TABLE IF NOT EXISTS `customer_material_price_stats` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `customer_id` BIGINT NOT NULL COMMENT '客户ID',
  `material_code` VARCHAR(100) NOT NULL COMMENT '物料编号',
  `material_name` VARCHAR(200) COMMENT '物料名称',
  `last_3m_total_qty` INT DEFAULT 0 COMMENT '近3个月总成交数量',
  `last_3m_total_amount` DECIMAL(15,2) DEFAULT 0.00 COMMENT '近3个月总成交金额',
  `avg_unit_price` DECIMAL(10,4) DEFAULT 0.00 COMMENT '平均单价',
  `stats_date` DATE NOT NULL COMMENT '统计日期',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_material_date` (`customer_id`, `material_code`, `stats_date`),
  INDEX `idx_customer_id` (`customer_id`),
  INDEX `idx_material_code` (`material_code`),
  INDEX `idx_stats_date` (`stats_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户料号单价统计表';

-- ========== 3. 订单客户优先级计算表 ==========
CREATE TABLE IF NOT EXISTS `order_customer_priority` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_id` BIGINT NOT NULL COMMENT '订单ID',
  `order_no` VARCHAR(100) NOT NULL COMMENT '订单编号',
  `customer_id` BIGINT NOT NULL COMMENT '客户ID',
  `customer_name` VARCHAR(200) NOT NULL COMMENT '客户名称',
  `payment_terms_score` DECIMAL(10,2) DEFAULT 0.00 COMMENT '账期得分',
  `avg_amount_score` DECIMAL(10,2) DEFAULT 0.00 COMMENT '月均成交金额得分',
  `unit_price_score` DECIMAL(10,2) DEFAULT 0.00 COMMENT '单价得分',
  `total_score` DECIMAL(10,2) DEFAULT 0.00 COMMENT '总得分',
  `priority_level` VARCHAR(20) DEFAULT 'NORMAL' COMMENT '优先级级别：HIGH/MEDIUM/NORMAL/LOW',
  `order_time` DATETIME NOT NULL COMMENT '下单时间',
  `calculated_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '计算时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_id` (`order_id`),
  INDEX `idx_customer_id` (`customer_id`),
  INDEX `idx_total_score` (`total_score` DESC),
  INDEX `idx_order_time` (`order_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单客户优先级表';

-- ========== 4. 订单物料锁定表（核心表） ==========
CREATE TABLE IF NOT EXISTS `order_material_lock` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '锁定ID',
  `lock_no` VARCHAR(50) NOT NULL COMMENT '锁定单号',
  `order_id` BIGINT NOT NULL COMMENT '订单ID',
  `order_no` VARCHAR(100) NOT NULL COMMENT '订单编号',
  `order_item_id` BIGINT NOT NULL COMMENT '订单明细ID',
  `customer_name` VARCHAR(200) NOT NULL COMMENT '客户名称',
  `customer_priority` DECIMAL(10,2) DEFAULT 0.00 COMMENT '客户优先级得分',
  `material_code` VARCHAR(100) NOT NULL COMMENT '物料编号',
  `material_spec` VARCHAR(200) COMMENT '物料规格',
  `stock_id` BIGINT NOT NULL COMMENT '库存ID',
  `stock_qr_code` VARCHAR(100) NOT NULL COMMENT '物料二维码',
  `locked_qty` INT NOT NULL COMMENT '锁定数量',
  `shared_order_count` INT DEFAULT 1 COMMENT '共用订单数（多单共用时>1）',
  `lock_status` VARCHAR(20) DEFAULT 'LOCKED' COMMENT '锁定状态：LOCKED/RELEASED/ISSUED',
  `issue_status` VARCHAR(20) DEFAULT 'PENDING' COMMENT '领料状态：PENDING/ISSUED/CANCELLED',
  `locked_by` VARCHAR(50) NOT NULL COMMENT '锁定操作人',
  `locked_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '锁定时间',
  `released_at` DATETIME COMMENT '解锁时间',
  `issued_at` DATETIME COMMENT '领料时间',
  `remark` VARCHAR(500) COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lock_no` (`lock_no`),
  INDEX `idx_order_id` (`order_id`),
  INDEX `idx_order_item_id` (`order_item_id`),
  INDEX `idx_stock_id` (`stock_id`),
  INDEX `idx_stock_qr_code` (`stock_qr_code`),
  INDEX `idx_lock_status` (`lock_status`),
  INDEX `idx_customer_priority` (`customer_priority` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单物料锁定表';

-- ========== 5. 涂布原材料锁定表 ==========
CREATE TABLE IF NOT EXISTS `coating_material_lock` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '锁定ID',
  `lock_no` VARCHAR(50) NOT NULL COMMENT '锁定单号',
  `coating_task_id` BIGINT COMMENT '涂布任务ID',
  `coating_task_no` VARCHAR(50) COMMENT '涂布任务编号',
  `material_code` VARCHAR(100) NOT NULL COMMENT '原材料编号',
  `material_name` VARCHAR(200) NOT NULL COMMENT '原材料名称',
  `material_type` VARCHAR(50) NOT NULL COMMENT '材料类型：BASE_FILM/ADHESIVE/OTHER',
  `stock_id` BIGINT NOT NULL COMMENT '库存ID',
  `stock_qr_code` VARCHAR(100) NOT NULL COMMENT '物料二维码',
  `locked_qty` DECIMAL(15,2) NOT NULL COMMENT '锁定数量',
  `unit` VARCHAR(20) DEFAULT 'KG' COMMENT '单位',
  `lock_status` VARCHAR(20) DEFAULT 'LOCKED' COMMENT '锁定状态：LOCKED/RELEASED/CONSUMED',
  `locked_by` VARCHAR(50) NOT NULL COMMENT '锁定操作人',
  `locked_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '锁定时间',
  `released_at` DATETIME COMMENT '解锁时间',
  `consumed_at` DATETIME COMMENT '消耗时间',
  `remark` VARCHAR(500) COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lock_no` (`lock_no`),
  INDEX `idx_coating_task_id` (`coating_task_id`),
  INDEX `idx_stock_id` (`stock_id`),
  INDEX `idx_stock_qr_code` (`stock_qr_code`),
  INDEX `idx_lock_status` (`lock_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='涂布原材料锁定表';

-- ========== 6. 待涂布订单池表 ==========
CREATE TABLE IF NOT EXISTS `pending_coating_order_pool` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `pool_no` VARCHAR(50) NOT NULL COMMENT '池编号（按料号）',
  `material_code` VARCHAR(100) NOT NULL COMMENT '物料编号',
  `material_name` VARCHAR(200) NOT NULL COMMENT '物料名称',
  `order_id` BIGINT NOT NULL COMMENT '订单ID',
  `order_no` VARCHAR(100) NOT NULL COMMENT '订单编号',
  `order_item_id` BIGINT NOT NULL COMMENT '订单明细ID',
  `customer_name` VARCHAR(200) NOT NULL COMMENT '客户名称',
  `customer_priority` DECIMAL(10,2) DEFAULT 0.00 COMMENT '客户优先级得分',
  `shortage_qty` INT NOT NULL COMMENT '缺口数量',
  `shortage_area` DECIMAL(15,2) NOT NULL COMMENT '缺口面积（㎡）',
  `pool_status` VARCHAR(20) DEFAULT 'WAITING' COMMENT '状态：WAITING/COATING/COMPLETED/CANCELLED',
  `coating_task_id` BIGINT COMMENT '关联涂布任务ID',
  `added_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `completed_at` DATETIME COMMENT '完成时间',
  PRIMARY KEY (`id`),
  INDEX `idx_pool_no` (`pool_no`),
  INDEX `idx_material_code` (`material_code`),
  INDEX `idx_order_id` (`order_id`),
  INDEX `idx_pool_status` (`pool_status`),
  INDEX `idx_customer_priority` (`customer_priority` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待涂布订单池表';

-- ========== 6b. 待复卷订单池表 ==========
CREATE TABLE IF NOT EXISTS `pending_rewinding_order_pool` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `pool_no` VARCHAR(50) NOT NULL COMMENT '池编号（按料号）',
  `material_code` VARCHAR(100) NOT NULL COMMENT '物料编号',
  `material_name` VARCHAR(200) NOT NULL COMMENT '物料名称',
  `order_id` BIGINT NOT NULL COMMENT '订单ID',
  `order_no` VARCHAR(100) NOT NULL COMMENT '订单编号',
  `order_item_id` BIGINT NOT NULL COMMENT '订单明细ID',
  `customer_name` VARCHAR(200) NOT NULL COMMENT '客户名称',
  `customer_priority` DECIMAL(10,2) DEFAULT 0.00 COMMENT '客户优先级得分',
  `shortage_qty` INT NOT NULL COMMENT '缺口数量',
  `shortage_area` DECIMAL(15,2) NOT NULL COMMENT '缺口面积（㎡）',
  `pool_status` VARCHAR(20) DEFAULT 'WAITING' COMMENT '状态：WAITING/REWINDING/COMPLETED/CANCELLED',
  `rewinding_task_id` BIGINT COMMENT '关联复卷任务ID',
  `added_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `completed_at` DATETIME COMMENT '完成时间',
  PRIMARY KEY (`id`),
  INDEX `idx_pool_no` (`pool_no`),
  INDEX `idx_material_code` (`material_code`),
  INDEX `idx_order_id` (`order_id`),
  INDEX `idx_pool_status` (`pool_status`),
  INDEX `idx_customer_priority` (`customer_priority` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待复卷订单池表';

-- ========== 6c. 待分切订单池表 ==========
CREATE TABLE IF NOT EXISTS `pending_slitting_order_pool` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `pool_no` VARCHAR(50) NOT NULL COMMENT '池编号（按料号）',
  `material_code` VARCHAR(100) NOT NULL COMMENT '物料编号',
  `material_name` VARCHAR(200) NOT NULL COMMENT '物料名称',
  `order_id` BIGINT NOT NULL COMMENT '订单ID',
  `order_no` VARCHAR(100) NOT NULL COMMENT '订单编号',
  `order_item_id` BIGINT NOT NULL COMMENT '订单明细ID',
  `customer_name` VARCHAR(200) NOT NULL COMMENT '客户名称',
  `customer_priority` DECIMAL(10,2) DEFAULT 0.00 COMMENT '客户优先级得分',
  `shortage_qty` INT NOT NULL COMMENT '缺口数量',
  `shortage_area` DECIMAL(15,2) NOT NULL COMMENT '缺口面积（㎡）',
  `pool_status` VARCHAR(20) DEFAULT 'WAITING' COMMENT '状态：WAITING/SLITTING/COMPLETED/CANCELLED',
  `slitting_task_id` BIGINT COMMENT '关联分切任务ID',
  `added_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `completed_at` DATETIME COMMENT '完成时间',
  PRIMARY KEY (`id`),
  INDEX `idx_pool_no` (`pool_no`),
  INDEX `idx_material_code` (`material_code`),
  INDEX `idx_order_id` (`order_id`),
  INDEX `idx_pool_status` (`pool_status`),
  INDEX `idx_customer_priority` (`customer_priority` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待分切订单池表';

-- ========== 7. 涂布排程合并记录表 ==========
CREATE TABLE IF NOT EXISTS `coating_merge_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `coating_task_id` BIGINT NOT NULL COMMENT '涂布任务ID',
  `coating_task_no` VARCHAR(50) NOT NULL COMMENT '涂布任务编号',
  `material_code` VARCHAR(100) NOT NULL COMMENT '物料编号',
  `merged_order_count` INT DEFAULT 0 COMMENT '合并订单数',
  `total_area` DECIMAL(15,2) DEFAULT 0.00 COMMENT '总涂布面积',
  `min_coating_area` DECIMAL(15,2) DEFAULT 0.00 COMMENT '最小涂布量',
  `actual_coating_area` DECIMAL(15,2) DEFAULT 0.00 COMMENT '实际涂布面积',
  `merge_date` DATE NOT NULL COMMENT '合并日期',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_coating_task_id` (`coating_task_id`),
  INDEX `idx_material_code` (`material_code`),
  INDEX `idx_merge_date` (`merge_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='涂布排程合并记录表';

-- ========== 初始化一些测试数据 ==========

-- 插入客户账期数据（假设已有customer表）
INSERT IGNORE INTO `customer_transaction_stats` 
(`customer_id`, `customer_name`, `payment_terms`, `last_3m_amount`, `last_3m_order_count`, `avg_monthly_amount`, `stats_date`)
SELECT 
  c.id,
  c.customer_name,
  COALESCE(c.payment_terms, 30) as payment_terms,
  0.00,
  0,
  0.00,
  CURDATE()
FROM customer c
WHERE c.is_deleted = 0
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- 创建序列号生成表
CREATE TABLE IF NOT EXISTS `lock_no_sequence` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `prefix` VARCHAR(20) NOT NULL COMMENT '前缀：LOCK/CTLOCK',
  `date_str` VARCHAR(10) NOT NULL COMMENT '日期字符串 YYYYMMDD',
  `current_seq` INT NOT NULL DEFAULT 0 COMMENT '当前序号',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prefix_date` (`prefix`, `date_str`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='锁定单号序列表';

-- 完成提示
SELECT '✅ 动态排程核心表创建完成！' AS result;
SELECT '📋 已创建表：' AS info;
SELECT '  1. customer_transaction_stats - 客户交易统计表' AS tables;
SELECT '  2. customer_material_price_stats - 客户料号单价统计表' AS tables;
SELECT '  3. order_customer_priority - 订单客户优先级表' AS tables;
SELECT '  4. order_material_lock - 订单物料锁定表' AS tables;
SELECT '  5. coating_material_lock - 涂布原材料锁定表' AS tables;
SELECT '  6. pending_coating_order_pool - 待涂布订单池表' AS tables;
SELECT '  7. coating_merge_record - 涂布排程合并记录表' AS tables;
SELECT '  8. lock_no_sequence - 锁定单号序列表' AS tables;
