-- 分切纸箱规格（按料号维护）
CREATE TABLE IF NOT EXISTS `slitting_carton_spec` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `material_code` VARCHAR(100) NOT NULL COMMENT '料号',
  `spec_name` VARCHAR(100) NOT NULL COMMENT '规格名称',
  `length_mm` INT NOT NULL COMMENT '纸箱长(mm)',
  `width_mm` INT NOT NULL COMMENT '纸箱宽(mm)',
  `height_mm` INT NOT NULL COMMENT '纸箱高(mm)',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
  `remark` VARCHAR(255) NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` VARCHAR(64) NULL COMMENT '创建人',
  `update_by` VARCHAR(64) NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_material_spec` (`material_code`, `spec_name`),
  KEY `idx_material_status` (`material_code`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分切纸箱规格(按料号维护)';
