-- 出货检测映射表：检测模块内独立映射（不依赖销售客户物料映射）
CREATE TABLE IF NOT EXISTS quality_outbound_mapping (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_code VARCHAR(64) NOT NULL COMMENT '客户代码',
  material_code VARCHAR(128) NOT NULL COMMENT '物料代码',
  width_min DECIMAL(18,3) NULL COMMENT '宽度下限(mm)',
  width_max DECIMAL(18,3) NULL COMMENT '宽度上限(mm)',
  color_range VARCHAR(512) NULL COMMENT '颜色范围(字符串集合,逗号分隔)',
  length_tol_min DECIMAL(18,3) NULL COMMENT '长度误差下限',
  length_tol_max DECIMAL(18,3) NULL COMMENT '长度误差上限',
  misalign_min DECIMAL(18,3) NULL COMMENT '整卷错位下限',
  misalign_max DECIMAL(18,3) NULL COMMENT '整卷错位上限',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用:1是0否',
  remark VARCHAR(255) NULL COMMENT '备注',
  created_by VARCHAR(64) NULL,
  created_at DATETIME NULL,
  updated_by VARCHAR(64) NULL,
  updated_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出货检测映射(客户+物料维度)';

CREATE INDEX idx_qom_customer_material ON quality_outbound_mapping(customer_code, material_code, enabled);
