-- 涂布过程检验（母卷逐卷必检）索引补强
-- 执行库：erp

SET @db = DATABASE();

-- quality_inspection: 按 类型+节点+卷码 查询是否已检
SET @idx_qi := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = @db
    AND table_name = 'quality_inspection'
    AND index_name = 'idx_qi_type_node_roll_deleted'
);
SET @sql_qi := IF(
  @idx_qi = 0,
  'ALTER TABLE quality_inspection ADD INDEX idx_qi_type_node_roll_deleted (inspection_type, process_node, roll_code, is_deleted)',
  'SELECT ''idx_qi_type_node_roll_deleted exists'''
);
PREPARE stmt_qi FROM @sql_qi;
EXECUTE stmt_qi;
DEALLOCATE PREPARE stmt_qi;

-- manual_schedule_coating_roll: 母卷号反查
SET @idx_cr := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = @db
    AND table_name = 'manual_schedule_coating_roll'
    AND index_name = 'idx_mscr_roll_deleted'
);
SET @sql_cr := IF(
  @idx_cr = 0,
  'ALTER TABLE manual_schedule_coating_roll ADD INDEX idx_mscr_roll_deleted (roll_code, is_deleted)',
  'SELECT ''idx_mscr_roll_deleted exists'''
);
PREPARE stmt_cr FROM @sql_cr;
EXECUTE stmt_cr;
DEALLOCATE PREPARE stmt_cr;
