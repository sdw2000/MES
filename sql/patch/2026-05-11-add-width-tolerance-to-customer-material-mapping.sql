-- 客户物料映射：增加宽度公差字段（用于出货检测自动带入宽度判定公差）
ALTER TABLE customer_material_mapping
  ADD COLUMN IF NOT EXISTS width_tolerance DECIMAL(10,3) NULL COMMENT '宽度公差(mm)' AFTER customer_width;
