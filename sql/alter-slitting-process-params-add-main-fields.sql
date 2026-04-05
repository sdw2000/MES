-- 分切工艺参数新增主要维护字段
-- 目标字段：总厚度(total_thickness)、长度(process_length)、宽度(process_width)、生产速度(卷/分 production_speed)

ALTER TABLE slitting_process_params
  ADD COLUMN IF NOT EXISTS total_thickness DECIMAL(10,3) NULL COMMENT '总厚度(μm)' AFTER equipment_code,
  ADD COLUMN IF NOT EXISTS process_length DECIMAL(12,2) NULL COMMENT '长度(mm)' AFTER total_thickness,
  ADD COLUMN IF NOT EXISTS process_width DECIMAL(12,2) NULL COMMENT '宽度(mm)' AFTER process_length,
  ADD COLUMN IF NOT EXISTS production_speed DECIMAL(10,2) NULL COMMENT '生产速度(卷/分)' AFTER process_width;

-- 历史数据兼容：若 production_speed 为空，则继承旧字段 slitting_speed
UPDATE slitting_process_params
SET production_speed = slitting_speed
WHERE production_speed IS NULL AND slitting_speed IS NOT NULL;
