-- 2026-03-14
-- 目的：补齐领料单主表/明细表，修复“Table 'erp.material_issue_order' doesn't exist”

CREATE TABLE IF NOT EXISTS `material_issue_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `issue_no` VARCHAR(32) NOT NULL COMMENT '领料单号',
  `plan_date` DATE DEFAULT NULL COMMENT '计划日期',
  `material_code` VARCHAR(128) DEFAULT NULL COMMENT '料号(汇总)',
  `order_no` VARCHAR(128) DEFAULT NULL COMMENT '订单号(汇总)',
  `total_area` DECIMAL(18,2) DEFAULT 0 COMMENT '合计面积',
  `item_count` INT DEFAULT 0 COMMENT '明细条数',
  `status` VARCHAR(32) DEFAULT 'CREATED' COMMENT '状态',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` TINYINT(1) DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_issue_no` (`issue_no`),
  KEY `idx_plan_date` (`plan_date`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_material_code` (`material_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产领料单主表';

CREATE TABLE IF NOT EXISTS `material_issue_order_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `issue_order_id` BIGINT NOT NULL,
  `lock_id` BIGINT NOT NULL COMMENT '对应锁定记录ID',
  `schedule_id` BIGINT DEFAULT NULL,
  `order_id` BIGINT DEFAULT NULL,
  `order_no` VARCHAR(64) DEFAULT NULL,
  `material_code` VARCHAR(128) DEFAULT NULL,
  `film_stock_id` BIGINT DEFAULT NULL,
  `issued_area` DECIMAL(18,2) DEFAULT 0,
  `lock_status_before` VARCHAR(32) DEFAULT NULL COMMENT '领料前锁定状态',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `is_deleted` TINYINT(1) DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_issue_order_id` (`issue_order_id`),
  KEY `idx_lock_id` (`lock_id`),
  KEY `idx_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='生产领料单明细表';

-- 说明：为兼容 MySQL 5.7，本脚本仅使用 CREATE TABLE IF NOT EXISTS。
-- 若历史环境已存在旧表且字段不全，请按实际字段差异执行 ALTER TABLE 补齐。
