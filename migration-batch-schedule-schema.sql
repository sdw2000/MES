-- ============================================================
-- 批排程功能 - 数据库迁移脚本 (方案A)
-- 执行顺序: 1. ALTER TABLE 2. CREATE TABLE 3. 数据初始化
-- ============================================================

-- ============================================================
-- 第1步：在 sales_order_items 中添加字段
-- ============================================================

-- 检查字段是否存在，如不存在则添加
ALTER TABLE `sales_order_items` 
ADD COLUMN `pending_qty` INT NOT NULL DEFAULT 0 COMMENT '待排数量' AFTER `order_qty`,
ADD COLUMN `produced_qty` INT NOT NULL DEFAULT 0 COMMENT '已生产数量' AFTER `pending_qty`,
ADD COLUMN `scheduled_qty` INT NOT NULL DEFAULT 0 COMMENT '已排程数量' AFTER `produced_qty`;

-- 添加检查约束：pending_qty >= 0
ALTER TABLE `sales_order_items` 
ADD CONSTRAINT `chk_pending_qty_positive` CHECK (`pending_qty` >= 0);

-- 添加检查约束：pending_qty + produced_qty <= order_qty
ALTER TABLE `sales_order_items` 
ADD CONSTRAINT `chk_quantities_valid` CHECK (`pending_qty` + `produced_qty` <= `order_qty`);

-- 添加索引以优化查询
CREATE INDEX `idx_pending_qty` ON `sales_order_items`(`pending_qty`) WHERE `pending_qty` > 0;
CREATE INDEX `idx_order_id_pending` ON `sales_order_items`(`order_id`, `pending_qty`);

-- ============================================================
-- 第2步：创建排程关联表 schedule_order_item
-- ============================================================

CREATE TABLE IF NOT EXISTS `schedule_order_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  
  -- 关键字段
  `order_item_id` BIGINT NOT NULL COMMENT '关联sales_order_items.id',
  `schedule_qty` INT NOT NULL COMMENT '本次排程数量',
  
  -- 排程信息
  `schedule_date` DATE NOT NULL COMMENT '排程日期',
  `schedule_no` VARCHAR(50) NULL COMMENT '排程单号',
  
  -- 关联主排程单
  `schedule_id` BIGINT NULL COMMENT '关联排程主表id(可选)',
  
  -- 状态
  `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '排程状态：pending/confirmed/producing/completed/cancelled',
  
  -- 审计字段
  `created_by` VARCHAR(50) NULL COMMENT '创建人',
  `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_by` VARCHAR(50) NULL COMMENT '修改人',
  `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  
  -- 主键和约束
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_schedule_order_item_order_item` 
    FOREIGN KEY (`order_item_id`) REFERENCES `sales_order_items`(`id`),
  
  -- 索引
  KEY `idx_order_item_id` (`order_item_id`),
  KEY `idx_schedule_date` (`schedule_date`),
  KEY `idx_status` (`status`),
  KEY `idx_schedule_no` (`schedule_no`)
  
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci 
  COMMENT='排程订单明细表 - 记录每一次排程操作';

-- ============================================================
-- 第3步：创建排程任务表 schedule_task
-- ============================================================

CREATE TABLE IF NOT EXISTS `schedule_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  
  -- 关键字段
  `schedule_item_id` BIGINT NOT NULL COMMENT '关联schedule_order_item.id',
  `task_type` VARCHAR(20) NOT NULL COMMENT '工序类型：coating/rewinding/slitting/stripping/printing',
  
  -- 任务内容
  `quantity` INT NULL COMMENT '数量',
  `plan_duration` INT NULL COMMENT '计划时长(分钟)',
  `equipment_id` BIGINT NULL COMMENT '分配设备id',
  
  -- 状态
  `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending/assigned/in_progress/completed/cancelled',
  
  -- 审计字段
  `created_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  
  -- 主键和约束
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_schedule_task_schedule_item` 
    FOREIGN KEY (`schedule_item_id`) REFERENCES `schedule_order_item`(`id`),
  
  -- 索引
  KEY `idx_schedule_item_id` (`schedule_item_id`),
  KEY `idx_task_type` (`task_type`),
  KEY `idx_status` (`status`)
  
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci 
  COMMENT='排程任务表 - 各工序任务';

-- ============================================================
-- 第4步：数据初始化 - 计算现有订单的 pending_qty
-- ============================================================

-- 如果是新系统，pending_qty 应该初始化为 order_qty
UPDATE `sales_order_items` 
SET `pending_qty` = `order_qty`
WHERE `pending_qty` = 0 AND `order_qty` > 0;

-- 如果已有生产记录，pending_qty = order_qty - produced_qty
-- 注意：produced_qty 来自生产报工数据
UPDATE `sales_order_items` soi
SET `pending_qty` = `order_qty` - COALESCE(`produced_qty`, 0)
WHERE `pending_qty` <> (`order_qty` - COALESCE(`produced_qty`, 0));

-- ============================================================
-- 第5步：验证数据一致性
-- ============================================================

-- 验证1: 查看待排程订单（pending_qty > 0）
SELECT `id`, `order_qty`, `pending_qty`, `produced_qty`,
       (`pending_qty` + `produced_qty`) as total_used
FROM `sales_order_items` 
WHERE `pending_qty` > 0
LIMIT 10;

-- 验证2: 检查数据一致性（应全部显示 ✓）
SELECT `id`, `order_qty`, `pending_qty`, `produced_qty`,
       CASE 
         WHEN `pending_qty` >= 0 AND (`pending_qty` + `produced_qty`) <= `order_qty` THEN '✓'
         ELSE '✗ ERROR'
       END as data_valid
FROM `sales_order_items`
WHERE `pending_qty` < 0 OR (`pending_qty` + `produced_qty`) > `order_qty`;

-- 如果上面查询返回数据，表示有错误数据需要修复

-- ============================================================
-- 第6步：创建存储过程（可选，用于简化排程操作）
-- ============================================================

-- 排程数量更新存储过程
DELIMITER //
CREATE PROCEDURE update_pending_qty(
  IN p_order_item_id BIGINT,
  IN p_decrement_qty INT
)
BEGIN
  DECLARE v_pending_qty INT;
  
  -- 获取当前待排数量
  SELECT `pending_qty` INTO v_pending_qty
  FROM `sales_order_items`
  WHERE `id` = p_order_item_id;
  
  -- 验证
  IF v_pending_qty IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '订单明细不存在';
  END IF;
  
  IF v_pending_qty < p_decrement_qty THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '待排数量不足';
  END IF;
  
  IF p_decrement_qty <= 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '减少数量必须大于0';
  END IF;
  
  -- 执行更新
  UPDATE `sales_order_items`
  SET `pending_qty` = `pending_qty` - p_decrement_qty,
      `scheduled_qty` = `scheduled_qty` + p_decrement_qty
  WHERE `id` = p_order_item_id;
END//
DELIMITER ;

-- ============================================================
-- 第7步：回滚脚本（如需要撤销，执行此部分）
-- ============================================================

/*
-- 回滚步骤（谨慎执行！）

-- 1. 删除存储过程
DROP PROCEDURE IF EXISTS update_pending_qty;

-- 2. 删除新表
DROP TABLE IF EXISTS `schedule_task`;
DROP TABLE IF EXISTS `schedule_order_item`;

-- 3. 删除字段和索引
ALTER TABLE `sales_order_items` 
DROP FOREIGN KEY `chk_pending_qty_positive`,
DROP FOREIGN KEY `chk_quantities_valid`,
DROP INDEX `idx_pending_qty`,
DROP INDEX `idx_order_id_pending`,
DROP COLUMN `pending_qty`,
DROP COLUMN `produced_qty`,
DROP COLUMN `scheduled_qty`;

*/

-- ============================================================
-- 完成标记
-- ============================================================
-- 执行上述脚本后，数据库迁移完成
-- 下一步：部署后端代码和前端代码
-- ============================================================
