-- 为手动排程表增加“手工涂布速度”字段（用于记录计划员在手动排程页输入的速度）
ALTER TABLE manual_schedule
  ADD COLUMN manual_coating_speed DECIMAL(10,2) NULL COMMENT '手工涂布速度(米/分)' AFTER coating_length;

SELECT 'Migration completed: added manual_schedule.manual_coating_speed' AS result;
