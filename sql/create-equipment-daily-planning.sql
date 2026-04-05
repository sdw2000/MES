-- 设备日历状态 + 设备人员排班

-- 1) 设备日状态表
CREATE TABLE IF NOT EXISTS `equipment_daily_status` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `equipment_id` BIGINT NOT NULL,
  `equipment_code` VARCHAR(64) NOT NULL,
  `plan_date` DATETIME NOT NULL,
  `daily_status` VARCHAR(32) NOT NULL DEFAULT 'OPEN',
  `reason` VARCHAR(255) NULL,
  `min_staff_required` INT NOT NULL DEFAULT 1,
  `required_skill_level` VARCHAR(32) NULL,
  `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` VARCHAR(64) NULL,
  `update_by` VARCHAR(64) NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_equipment_date` (`equipment_id`, `plan_date`),
  KEY `idx_equipment_code_date` (`equipment_code`, `plan_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备日状态';

-- 2) 设备-人员-班次排班表
CREATE TABLE IF NOT EXISTS `equipment_staff_assignment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `equipment_id` BIGINT NOT NULL,
  `equipment_code` VARCHAR(64) NOT NULL,
  `plan_date` DATETIME NOT NULL,
  `shift_id` BIGINT NOT NULL,
  `shift_code` VARCHAR(64) NULL,
  `shift_name` VARCHAR(64) NULL,
  `staff_id` BIGINT NOT NULL,
  `staff_code` VARCHAR(64) NULL,
  `staff_name` VARCHAR(64) NULL,
  `role_name` VARCHAR(32) NULL,
  `on_duty` TINYINT NOT NULL DEFAULT 1,
  `remark` VARCHAR(255) NULL,
  `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `create_by` VARCHAR(64) NULL,
  `update_by` VARCHAR(64) NULL,
  PRIMARY KEY (`id`),
  KEY `idx_equipment_date` (`equipment_id`, `plan_date`),
  KEY `idx_staff_date` (`staff_id`, `plan_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备人员排班';

-- 3) 设备排程配置增加默认规则字段（可选，兼容旧版MySQL）
SET @db = DATABASE();
SET @tbl = 'equipment_schedule_config';

SET @c1 = (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = @tbl AND COLUMN_NAME = 'min_staff_required'
);
SET @sql1 = IF(@c1 = 0,
  'ALTER TABLE equipment_schedule_config ADD COLUMN min_staff_required INT NOT NULL DEFAULT 1',
  'SELECT 1'
);
PREPARE stmt1 FROM @sql1;
EXECUTE stmt1;
DEALLOCATE PREPARE stmt1;

SET @c2 = (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = @db AND TABLE_NAME = @tbl AND COLUMN_NAME = 'required_skill_level'
);
SET @sql2 = IF(@c2 = 0,
  'ALTER TABLE equipment_schedule_config ADD COLUMN required_skill_level VARCHAR(32) NULL',
  'SELECT 1'
);
PREPARE stmt2 FROM @sql2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
