-- 为工艺参数表补充设备编码维度（料号 + 工序 + 设备编码）

-- 1) 新增 equipment_code 字段（若不存在）
SET @col_exists := (
  SELECT COUNT(1)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'process_params'
    AND COLUMN_NAME = 'equipment_code'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE process_params ADD COLUMN equipment_code VARCHAR(50) NOT NULL DEFAULT '''' COMMENT ''设备编码'' AFTER process_type',
  'SELECT ''equipment_code exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 清理空值（兼容历史数据）
UPDATE process_params SET equipment_code = '' WHERE equipment_code IS NULL;

-- 3) 将旧唯一索引(料号+工序)替换为新唯一索引(料号+工序+设备编码)
SET @uk_old_exists := (
  SELECT COUNT(1)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'process_params'
    AND INDEX_NAME = 'uk_material_process'
);
SET @sql := IF(
  @uk_old_exists > 0,
  'ALTER TABLE process_params DROP INDEX uk_material_process',
  'SELECT ''uk_material_process not exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @uk_new_exists := (
  SELECT COUNT(1)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'process_params'
    AND INDEX_NAME = 'uk_material_process_equipment'
);
SET @sql := IF(
  @uk_new_exists = 0,
  'ALTER TABLE process_params ADD UNIQUE KEY uk_material_process_equipment (material_code, process_type, equipment_code)',
  'SELECT ''uk_material_process_equipment exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4) 辅助索引
SET @idx_exists := (
  SELECT COUNT(1)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'process_params'
    AND INDEX_NAME = 'idx_equipment_code'
);
SET @sql := IF(
  @idx_exists = 0,
  'ALTER TABLE process_params ADD INDEX idx_equipment_code (equipment_code)',
  'SELECT ''idx_equipment_code exists'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
