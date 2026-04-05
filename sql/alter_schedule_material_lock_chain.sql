-- 标准锁定链路补库脚本（兼容 MySQL 5.7/8.0）
-- 目标：schedule_material_lock 正式承载订单号与卷代码，不再依赖 remark 解析

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_material_lock' AND COLUMN_NAME = 'order_id') = 0,
  'ALTER TABLE schedule_material_lock ADD COLUMN order_id BIGINT NULL COMMENT ''订单ID'' AFTER schedule_id',
  'SELECT ''order_id exists'''
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_material_lock' AND COLUMN_NAME = 'order_no') = 0,
  'ALTER TABLE schedule_material_lock ADD COLUMN order_no VARCHAR(64) NULL COMMENT ''订单号'' AFTER order_id',
  'SELECT ''order_no exists'''
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_material_lock' AND COLUMN_NAME = 'roll_code') = 0,
  'ALTER TABLE schedule_material_lock ADD COLUMN roll_code VARCHAR(64) NULL COMMENT ''卷代码(母卷/复卷)'' AFTER film_stock_detail_id',
  'SELECT ''roll_code exists'''
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_material_lock' AND COLUMN_NAME = 'material_code') = 0,
  'ALTER TABLE schedule_material_lock ADD COLUMN material_code VARCHAR(64) NULL COMMENT ''料号'' AFTER order_no',
  'SELECT ''material_code exists'''
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 生命周期字段（避免 MyBatis Plus 按实体查询时字段不存在）
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_material_lock' AND COLUMN_NAME = 'allocated_time') = 0,
  'ALTER TABLE schedule_material_lock ADD COLUMN allocated_time DATETIME NULL COMMENT ''领料时间'' AFTER locked_time',
  'SELECT ''allocated_time exists'''
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_material_lock' AND COLUMN_NAME = 'locked_by_user_id') = 0,
  'ALTER TABLE schedule_material_lock ADD COLUMN locked_by_user_id BIGINT NULL COMMENT ''锁定操作人ID'' AFTER released_time',
  'SELECT ''locked_by_user_id exists'''
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_material_lock' AND COLUMN_NAME = 'allocated_by_user_id') = 0,
  'ALTER TABLE schedule_material_lock ADD COLUMN allocated_by_user_id BIGINT NULL COMMENT ''领料操作人ID'' AFTER locked_by_user_id',
  'SELECT ''allocated_by_user_id exists'''
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 兼容历史写法：从 remark 回填 order_no / roll_code
UPDATE schedule_material_lock
SET order_no = TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(remark, 'orderNo=', -1), ';', 1))
WHERE (order_no IS NULL OR order_no = '')
  AND remark IS NOT NULL
  AND remark LIKE '%orderNo=%';

UPDATE schedule_material_lock
SET roll_code = TRIM(SUBSTRING_INDEX(SUBSTRING_INDEX(remark, 'rollCode=', -1), ';', 1))
WHERE (roll_code IS NULL OR roll_code = '')
  AND remark IS NOT NULL
  AND remark LIKE '%rollCode=%';

UPDATE schedule_material_lock l
LEFT JOIN tape_stock ts ON ts.id = l.film_stock_id
SET l.material_code = COALESCE(l.material_code, ts.material_code)
WHERE (l.material_code IS NULL OR l.material_code = '');

-- 待补锁记录允许无库存关联（film_stock_id / film_stock_detail_id 可为空）
SET @sql = IF(
  (SELECT IS_NULLABLE FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_material_lock' AND COLUMN_NAME = 'film_stock_id' LIMIT 1) = 'NO',
  'ALTER TABLE schedule_material_lock MODIFY COLUMN film_stock_id BIGINT NULL COMMENT ''被锁定的库存ID''',
  'SELECT ''film_stock_id nullable'''
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT IS_NULLABLE FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_material_lock' AND COLUMN_NAME = 'film_stock_detail_id' LIMIT 1) = 'NO',
  'ALTER TABLE schedule_material_lock MODIFY COLUMN film_stock_detail_id BIGINT NULL COMMENT ''被锁定的库存明细ID''',
  'SELECT ''film_stock_detail_id nullable'''
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 建议索引（提升 today-locks 查询性能）
SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_material_lock' AND INDEX_NAME = 'idx_sml_locked_time_status') = 0,
  'ALTER TABLE schedule_material_lock ADD INDEX idx_sml_locked_time_status(locked_time, lock_status)',
  'SELECT ''idx_sml_locked_time_status exists'''
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_material_lock' AND INDEX_NAME = 'idx_sml_order_no') = 0,
  'ALTER TABLE schedule_material_lock ADD INDEX idx_sml_order_no(order_no)',
  'SELECT ''idx_sml_order_no exists'''
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_material_lock' AND INDEX_NAME = 'idx_sml_roll_code') = 0,
  'ALTER TABLE schedule_material_lock ADD INDEX idx_sml_roll_code(roll_code)',
  'SELECT ''idx_sml_roll_code exists'''
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'schedule_material_lock' AND INDEX_NAME = 'idx_sml_material_code') = 0,
  'ALTER TABLE schedule_material_lock ADD INDEX idx_sml_material_code(material_code)',
  'SELECT ''idx_sml_material_code exists'''
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
