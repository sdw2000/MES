-- =====================================================
-- 精简排程系统 - 数据库增强脚本
-- 创建日期: 2026-01-16
-- 说明: 基于客户优先级的动态排程系统
-- =====================================================

USE erp;

-- =====================================================
-- 阶段1.1: 客户数据与历史交易增强
-- =====================================================

-- 1.1.1 客户表增加字段：账期月数
ALTER TABLE customers 
ADD COLUMN IF NOT EXISTS `payment_period_months` INT DEFAULT 1 COMMENT '账期月数（1/2/3/>3个月）',
ADD INDEX `idx_payment_period` (`payment_period_months`);

-- 1.1.2 创建客户历史成交统计表
DROP TABLE IF EXISTS `customer_transaction_summary`;
CREATE TABLE `customer_transaction_summary` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `customer_id` BIGINT(20) NOT NULL COMMENT '客户ID',
  `customer_name` VARCHAR(200) NOT NULL COMMENT '客户名称',
  `recent_3m_total_amount` DECIMAL(15,2) DEFAULT 0.00 COMMENT '近3个月总成交金额（元）',
  `recent_3m_avg_amount` DECIMAL(15,2) DEFAULT 0.00 COMMENT '近3个月月均成交金额（元）',
  `transaction_count` INT DEFAULT 0 COMMENT '成交次数',
  `last_transaction_date` DATE DEFAULT NULL COMMENT '最后一次成交日期',
  `is_new_customer` TINYINT(1) DEFAULT 1 COMMENT '是否新客户：1-是，0-否',
  `stats_updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '统计更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_id` (`customer_id`),
  KEY `idx_customer_name` (`customer_name`),
  KEY `idx_is_new` (`is_new_customer`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户历史成交统计表';

-- 1.1.3 创建客户料号成交明细统计表
DROP TABLE IF EXISTS `customer_material_stats`;
CREATE TABLE `customer_material_stats` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `customer_id` BIGINT(20) NOT NULL COMMENT '客户ID',
  `material_code` VARCHAR(50) NOT NULL COMMENT '料号',
  `recent_3m_quantity` DECIMAL(12,2) DEFAULT 0.00 COMMENT '近3个月成交数量',
  `recent_3m_amount` DECIMAL(15,2) DEFAULT 0.00 COMMENT '近3个月成交金额（元）',
  `avg_unit_price` DECIMAL(10,2) DEFAULT 0.00 COMMENT '近3个月平均单价（元/平方米）',
  `last_transaction_date` DATE DEFAULT NULL COMMENT '最后一次成交日期',
  `stats_updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '统计更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_customer_material` (`customer_id`, `material_code`),
  KEY `idx_material_code` (`material_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户料号成交明细统计表';

-- 1.1.4 创建料号基准价格表
DROP TABLE IF EXISTS `material_base_price`;
CREATE TABLE `material_base_price` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `material_code` VARCHAR(50) NOT NULL COMMENT '料号',
  `material_name` VARCHAR(200) DEFAULT NULL COMMENT '料号名称',
  `recent_3m_total_quantity` DECIMAL(12,2) DEFAULT 0.00 COMMENT '近3个月总成交数量',
  `recent_3m_total_amount` DECIMAL(15,2) DEFAULT 0.00 COMMENT '近3个月总成交金额（元）',
  `avg_unit_price` DECIMAL(10,2) DEFAULT 0.00 COMMENT '近3个月平均单价（元/平方米）',
  `stats_updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '统计更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_material_code` (`material_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='料号基准价格表';

-- =====================================================
-- 阶段1.2: 物料与库存增强
-- =====================================================

-- 1.2.1 TapeStock 表增加产品类型和锁定状态字段（如果不存在）
ALTER TABLE tape_stock 
ADD COLUMN IF NOT EXISTS `product_type` VARCHAR(20) DEFAULT 'finished' COMMENT '产品类型：finished-成品，rewound-复卷，jumbo-母卷',
ADD COLUMN IF NOT EXISTS `lock_status` VARCHAR(20) DEFAULT 'unlocked' COMMENT '锁定状态：unlocked-未锁定，locked-已锁定，issued-已发料',
ADD COLUMN IF NOT EXISTS `unique_qr_code` VARCHAR(100) DEFAULT NULL COMMENT '唯一二维码（一物一码）',
ADD INDEX `idx_product_type` (`product_type`),
ADD INDEX `idx_lock_status` (`lock_status`),
ADD INDEX `idx_unique_qr` (`unique_qr_code`);

-- 1.2.2 FilmStock 表增加产品类型字段
ALTER TABLE film_stock 
ADD COLUMN IF NOT EXISTS `product_type` VARCHAR(20) DEFAULT 'jumbo' COMMENT '产品类型：finished-成品，rewound-复卷，jumbo-母卷',
ADD INDEX `idx_product_type` (`product_type`);

-- 1.2.3 创建物料生产配置表
DROP TABLE IF EXISTS `material_production_config`;
CREATE TABLE `material_production_config` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `material_code` VARCHAR(50) NOT NULL COMMENT '料号',
  `material_name` VARCHAR(200) DEFAULT NULL COMMENT '料号名称',
  `color_code` VARCHAR(50) DEFAULT NULL COMMENT '颜色代码',
  `color_name` VARCHAR(50) DEFAULT NULL COMMENT '颜色名称',
  `thickness` INT DEFAULT NULL COMMENT '厚度（μm）',
  `moq_sqm` DECIMAL(12,2) DEFAULT 0.00 COMMENT '最小涂布量（平方米）',
  `moq_length` DECIMAL(12,2) DEFAULT 0.00 COMMENT '最小涂布长度（米）',
  `recommended_width` INT DEFAULT NULL COMMENT '推荐薄膜宽度（mm）',
  `coating_speed` DECIMAL(8,2) DEFAULT 30.00 COMMENT '涂布速度（米/分钟）',
  `rewinding_speed` DECIMAL(8,2) DEFAULT 50.00 COMMENT '复卷速度（米/分钟）',
  `slitting_speed` DECIMAL(8,2) DEFAULT 60.00 COMMENT '分切速度（米/分钟）',
  `changeover_time_coating` INT DEFAULT 10 COMMENT '涂布换单时间（分钟）',
  `changeover_time_rewinding` INT DEFAULT 5 COMMENT '复卷换单时间（分钟）',
  `changeover_time_slitting` INT DEFAULT 5 COMMENT '分切换单时间（分钟）',
  `min_coating_duration` INT DEFAULT 10 COMMENT '最小涂布时长（分钟）',
  `min_rewinding_duration` INT DEFAULT 1 COMMENT '最小复卷时长（分钟）',
  `min_slitting_duration` INT DEFAULT 1 COMMENT '最小分切时长（分钟）',
  `qc_packaging_time` INT DEFAULT 30 COMMENT '质检包装时长（分钟）',
  `waste_rate` DECIMAL(5,2) DEFAULT 0.00 COMMENT '损耗率（%）',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_material_code` (`material_code`),
  KEY `idx_color_code` (`color_code`),
  KEY `idx_thickness` (`thickness`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料生产配置表';

-- =====================================================
-- 阶段1.3: 设备与日历
-- =====================================================

-- 1.3.1 Equipment 表增加设备类型和上次生产料号字段
ALTER TABLE equipment 
ADD COLUMN IF NOT EXISTS `equipment_category` VARCHAR(20) DEFAULT NULL COMMENT '设备分类：coating-涂布，rewinding-复卷，slitting-分切',
ADD COLUMN IF NOT EXISTS `last_material_code` VARCHAR(50) DEFAULT NULL COMMENT '上次生产料号（用于判断换单）',
ADD COLUMN IF NOT EXISTS `last_production_time` DATETIME DEFAULT NULL COMMENT '上次生产时间',
ADD INDEX `idx_equipment_category` (`equipment_category`),
ADD INDEX `idx_last_material` (`last_material_code`);

-- 1.3.2 创建设备日历/占用表
DROP TABLE IF EXISTS `equipment_schedule`;
CREATE TABLE `equipment_schedule` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `equipment_id` BIGINT(20) NOT NULL COMMENT '设备ID',
  `equipment_code` VARCHAR(50) NOT NULL COMMENT '设备编号',
  `equipment_category` VARCHAR(20) NOT NULL COMMENT '设备类型：coating-涂布，rewinding-复卷，slitting-分切',
  `task_id` BIGINT(20) DEFAULT NULL COMMENT '关联任务ID（涂布/复卷/分切任务ID）',
  `task_type` VARCHAR(20) DEFAULT NULL COMMENT '任务类型：coating-涂布，rewinding-复卷，slitting-分切',
  `material_code` VARCHAR(50) DEFAULT NULL COMMENT '生产料号',
  `order_id` BIGINT(20) DEFAULT NULL COMMENT '关联订单ID',
  `start_time` DATETIME NOT NULL COMMENT '占用开始时间',
  `end_time` DATETIME NOT NULL COMMENT '占用结束时间',
  `status` VARCHAR(20) DEFAULT 'scheduled' COMMENT '状态：scheduled-已排程，in_progress-进行中，completed-已完成，cancelled-已取消',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_equipment_id` (`equipment_id`),
  KEY `idx_equipment_code` (`equipment_code`),
  KEY `idx_task` (`task_id`, `task_type`),
  KEY `idx_time_range` (`start_time`, `end_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备日历占用表';

-- =====================================================
-- 阶段1.4: 锁定与发料表
-- =====================================================

-- 1.4.1 创建订单物料锁定表（增强版）
DROP TABLE IF EXISTS `order_material_lock`;
CREATE TABLE `order_material_lock` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '锁定ID',
  `order_id` BIGINT(20) NOT NULL COMMENT '订单ID',
  `order_no` VARCHAR(50) NOT NULL COMMENT '订单编号',
  `order_item_id` BIGINT(20) DEFAULT NULL COMMENT '订单明细ID',
  `customer_id` BIGINT(20) DEFAULT NULL COMMENT '客户ID',
  `customer_name` VARCHAR(200) NOT NULL COMMENT '客户名称',
  `customer_priority_score` DECIMAL(6,2) DEFAULT 0.00 COMMENT '客户优先级得分',
  `material_code` VARCHAR(50) NOT NULL COMMENT '料号',
  `material_spec` VARCHAR(200) DEFAULT NULL COMMENT '规格描述',
  `material_qr_code` VARCHAR(100) NOT NULL COMMENT '物料二维码（唯一）',
  `stock_type` VARCHAR(20) NOT NULL COMMENT '库存类型：finished-成品，rewound-复卷，jumbo-母卷，film-薄膜',
  `stock_table_name` VARCHAR(50) NOT NULL COMMENT '库存表名：tape_stock, film_stock等',
  `stock_record_id` BIGINT(20) NOT NULL COMMENT '库存记录ID',
  `locked_quantity` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '锁定数量（卷数或面积）',
  `locked_area` DECIMAL(12,2) DEFAULT 0.00 COMMENT '锁定面积（平方米）',
  `shared_order_count` INT DEFAULT 1 COMMENT '共享订单数（多单共用时）',
  `shared_order_details` TEXT DEFAULT NULL COMMENT '共享订单占用详情（JSON格式）',
  `lock_status` VARCHAR(20) DEFAULT 'locked' COMMENT '锁定状态：locked-已锁定，issued-已触发领料，released-已释放',
  `lock_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '锁定时间',
  `issue_triggered` TINYINT(1) DEFAULT 0 COMMENT '领料触发状态：0-未触发，1-已触发',
  `issue_time` DATETIME DEFAULT NULL COMMENT '领料时间',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`, `order_no`),
  KEY `idx_customer` (`customer_id`, `customer_name`),
  KEY `idx_material_qr` (`material_qr_code`),
  KEY `idx_stock` (`stock_table_name`, `stock_record_id`),
  KEY `idx_lock_status` (`lock_status`),
  KEY `idx_priority` (`customer_priority_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单物料锁定表';

-- 1.4.2 创建订单发料单表
DROP TABLE IF EXISTS `order_material_issue`;
CREATE TABLE `order_material_issue` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '发料ID',
  `issue_no` VARCHAR(50) NOT NULL COMMENT '发料单号（自动生成）',
  `order_id` BIGINT(20) NOT NULL COMMENT '订单ID',
  `order_no` VARCHAR(50) NOT NULL COMMENT '订单编号',
  `lock_id` BIGINT(20) NOT NULL COMMENT '关联锁定记录ID',
  `material_code` VARCHAR(50) NOT NULL COMMENT '料号',
  `material_qr_code` VARCHAR(100) NOT NULL COMMENT '物料二维码',
  `stock_type` VARCHAR(20) NOT NULL COMMENT '库存类型',
  `issued_quantity` DECIMAL(12,2) NOT NULL COMMENT '发料数量',
  `issued_area` DECIMAL(12,2) DEFAULT 0.00 COMMENT '发料面积（平方米）',
  `warehouse_keeper` VARCHAR(50) DEFAULT NULL COMMENT '仓管员',
  `receiver` VARCHAR(50) DEFAULT NULL COMMENT '领料人',
  `workshop` VARCHAR(50) DEFAULT NULL COMMENT '领料车间',
  `issue_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发料时间',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_issue_no` (`issue_no`),
  KEY `idx_order` (`order_id`, `order_no`),
  KEY `idx_lock_id` (`lock_id`),
  KEY `idx_material_qr` (`material_qr_code`),
  KEY `idx_issue_time` (`issue_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单发料单表';

-- 1.4.3 创建涂布原材料锁定表
DROP TABLE IF EXISTS `coating_material_lock`;
CREATE TABLE `coating_material_lock` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '锁定ID',
  `coating_task_id` BIGINT(20) NOT NULL COMMENT '涂布任务ID（schedule_coating.id）',
  `coating_task_no` VARCHAR(50) NOT NULL COMMENT '涂布任务单号',
  `material_code` VARCHAR(50) NOT NULL COMMENT '原材料料号',
  `material_name` VARCHAR(200) DEFAULT NULL COMMENT '原材料名称',
  `material_type` VARCHAR(20) NOT NULL COMMENT '原材料类型：base-基材，adhesive-胶水，other-其他',
  `material_qr_code` VARCHAR(100) NOT NULL COMMENT '原材料二维码',
  `locked_quantity` DECIMAL(12,2) NOT NULL COMMENT '锁定数量',
  `unit` VARCHAR(20) DEFAULT NULL COMMENT '单位：卷、kg、L等',
  `lock_status` VARCHAR(20) DEFAULT 'locked' COMMENT '锁定状态：locked-已锁定，issued-已领料，released-已释放',
  `lock_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '锁定时间',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_coating_task` (`coating_task_id`, `coating_task_no`),
  KEY `idx_material_qr` (`material_qr_code`),
  KEY `idx_material_type` (`material_type`),
  KEY `idx_lock_status` (`lock_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='涂布原材料锁定表';

-- =====================================================
-- 阶段2: 销售订单表增强
-- =====================================================

-- 2.1 sales_orders 表增加优先级和出货预测字段
ALTER TABLE sales_orders 
ADD COLUMN IF NOT EXISTS `customer_priority_score` DECIMAL(6,2) DEFAULT 0.00 COMMENT '客户优先级得分',
ADD COLUMN IF NOT EXISTS `priority_rank` INT DEFAULT 999 COMMENT '优先级排名（越小越高）',
ADD COLUMN IF NOT EXISTS `estimated_delivery_time` DATETIME DEFAULT NULL COMMENT '预计出货时间',
ADD COLUMN IF NOT EXISTS `delivery_delay_risk` TINYINT(1) DEFAULT 0 COMMENT '延期风险：0-无风险，1-有延期风险',
ADD INDEX `idx_priority` (`customer_priority_score`, `priority_rank`),
ADD INDEX `idx_estimated_delivery` (`estimated_delivery_time`);

-- 2.2 sales_order_items 表增加排程相关字段
ALTER TABLE sales_order_items 
ADD COLUMN IF NOT EXISTS `scheduled_qty` DECIMAL(12,2) DEFAULT 0.00 COMMENT '已排程数量',
ADD COLUMN IF NOT EXISTS `pending_qty` DECIMAL(12,2) DEFAULT 0.00 COMMENT '待排程数量',
ADD COLUMN IF NOT EXISTS `scheduled_sqm` DECIMAL(12,2) DEFAULT 0.00 COMMENT '已排程面积',
ADD COLUMN IF NOT EXISTS `pending_sqm` DECIMAL(12,2) DEFAULT 0.00 COMMENT '待排程面积',
ADD INDEX `idx_pending` (`pending_qty`, `pending_sqm`);

-- =====================================================
-- 阶段3: 排程任务表增强
-- =====================================================

-- 3.1 schedule_coating 表增加优先级和锁定字段
ALTER TABLE schedule_coating 
ADD COLUMN IF NOT EXISTS `customer_priority_score` DECIMAL(6,2) DEFAULT 0.00 COMMENT '客户优先级得分',
ADD COLUMN IF NOT EXISTS `material_lock_id` BIGINT(20) DEFAULT NULL COMMENT '关联物料锁定ID',
ADD COLUMN IF NOT EXISTS `moq_confirmed` TINYINT(1) DEFAULT 0 COMMENT 'MOQ确认：0-未确认，1-已人工确认',
ADD COLUMN IF NOT EXISTS `moq_confirmed_value` DECIMAL(12,2) DEFAULT NULL COMMENT 'MOQ人工确认值',
ADD INDEX `idx_priority` (`customer_priority_score`),
ADD INDEX `idx_material_lock` (`material_lock_id`);

-- 3.2 schedule_rewinding 表增加优先级字段
ALTER TABLE schedule_rewinding 
ADD COLUMN IF NOT EXISTS `customer_priority_score` DECIMAL(6,2) DEFAULT 0.00 COMMENT '客户优先级得分',
ADD COLUMN IF NOT EXISTS `source_type` VARCHAR(20) DEFAULT NULL COMMENT '来源类型：coating-涂布产出，stock-母卷库存',
ADD COLUMN IF NOT EXISTS `source_id` BIGINT(20) DEFAULT NULL COMMENT '来源ID（涂布任务ID或库存ID）',
ADD INDEX `idx_priority` (`customer_priority_score`),
ADD INDEX `idx_source` (`source_type`, `source_id`);

-- 3.3 schedule_slitting 表增加优先级字段
ALTER TABLE schedule_slitting 
ADD COLUMN IF NOT EXISTS `customer_priority_score` DECIMAL(6,2) DEFAULT 0.00 COMMENT '客户优先级得分',
ADD COLUMN IF NOT EXISTS `source_type` VARCHAR(20) DEFAULT NULL COMMENT '来源类型：rewinding-复卷产出，stock-复卷库存',
ADD COLUMN IF NOT EXISTS `source_id` BIGINT(20) DEFAULT NULL COMMENT '来源ID（复卷任务ID或库存ID）',
ADD INDEX `idx_priority` (`customer_priority_score`),
ADD INDEX `idx_source` (`source_type`, `source_id`);

-- =====================================================
-- 初始化数据与索引优化
-- =====================================================

-- 为现有客户初始化统计数据（新客户基础分20分）
INSERT INTO customer_transaction_summary (customer_id, customer_name, is_new_customer)
SELECT id, customer_name, 1 
FROM customers 
WHERE id NOT IN (SELECT customer_id FROM customer_transaction_summary);

-- 刷新统计数据的存储过程（后续可定期执行）
DELIMITER //
DROP PROCEDURE IF EXISTS refresh_customer_stats//
CREATE PROCEDURE refresh_customer_stats()
BEGIN
    -- 更新客户历史成交统计
    INSERT INTO customer_transaction_summary (
        customer_id, customer_name, recent_3m_total_amount, 
        recent_3m_avg_amount, transaction_count, last_transaction_date, is_new_customer
    )
    SELECT 
        c.id,
        c.customer_name,
        COALESCE(SUM(so.total_amount), 0) as recent_3m_total_amount,
        COALESCE(SUM(so.total_amount) / 3, 0) as recent_3m_avg_amount,
        COUNT(so.id) as transaction_count,
        MAX(so.order_date) as last_transaction_date,
        CASE WHEN COUNT(so.id) = 0 THEN 1 ELSE 0 END as is_new_customer
    FROM customers c
    LEFT JOIN sales_orders so ON c.customer_name = so.customer 
        AND so.order_date >= DATE_SUB(CURDATE(), INTERVAL 3 MONTH)
        AND so.status IN ('processing', 'completed')
    GROUP BY c.id, c.customer_name
    ON DUPLICATE KEY UPDATE
        recent_3m_total_amount = VALUES(recent_3m_total_amount),
        recent_3m_avg_amount = VALUES(recent_3m_avg_amount),
        transaction_count = VALUES(transaction_count),
        last_transaction_date = VALUES(last_transaction_date),
        is_new_customer = VALUES(is_new_customer),
        stats_updated_at = NOW();
    
    -- 更新料号基准价格
    INSERT INTO material_base_price (
        material_code, material_name, recent_3m_total_quantity,
        recent_3m_total_amount, avg_unit_price
    )
    SELECT 
        soi.material_code,
        soi.material_name,
        SUM(soi.sqm) as recent_3m_total_quantity,
        SUM(soi.amount) as recent_3m_total_amount,
        CASE WHEN SUM(soi.sqm) > 0 THEN SUM(soi.amount) / SUM(soi.sqm) ELSE 0 END as avg_unit_price
    FROM sales_order_items soi
    INNER JOIN sales_orders so ON soi.order_id = so.id
    WHERE so.order_date >= DATE_SUB(CURDATE(), INTERVAL 3 MONTH)
        AND so.status IN ('processing', 'completed')
    GROUP BY soi.material_code, soi.material_name
    ON DUPLICATE KEY UPDATE
        recent_3m_total_quantity = VALUES(recent_3m_total_quantity),
        recent_3m_total_amount = VALUES(recent_3m_total_amount),
        avg_unit_price = VALUES(avg_unit_price),
        stats_updated_at = NOW();
END//
DELIMITER ;

-- 执行一次初始化统计
CALL refresh_customer_stats();

-- =====================================================
-- 完成提示
-- =====================================================
SELECT '精简排程系统数据库增强完成！' as message;
SELECT '已创建表：' as info, 
'customer_transaction_summary, customer_material_stats, material_base_price, 
material_production_config, equipment_schedule, order_material_lock, 
order_material_issue, coating_material_lock' as tables;
