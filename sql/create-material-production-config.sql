-- 创建物料生产配置表
USE erp;

CREATE TABLE material_production_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  material_code VARCHAR(50) NOT NULL COMMENT '物料编号',
  material_name VARCHAR(100) COMMENT '物料名称',
  material_type VARCHAR(20) COMMENT '物料类型：coating-涂布,printing-印刷',
  
  min_production_qty INT COMMENT '最小生产数量',
  min_production_area DECIMAL(10,2) COMMENT '最小生产面积(㎡)',
  standard_batch_size INT COMMENT '标准批量',
  max_batch_size INT COMMENT '最大批量',
  
  setup_time INT COMMENT '调机时间(分钟)',
  unit_time DECIMAL(10,2) COMMENT '单位时间(分钟/㎡)',
  cleanup_time INT COMMENT '清理时间(分钟)',
  
  loss_rate DECIMAL(5,2) DEFAULT 5.00 COMMENT '损耗率(%)',
  qualified_rate DECIMAL(5,2) DEFAULT 95.00 COMMENT '合格率(%)',
  
  unit_cost DECIMAL(10,4) COMMENT '单位成本(元/㎡)',
  
  recommended_width INT COMMENT '推荐薄膜宽度(mm)',
  recommended_thickness INT COMMENT '推荐厚度(μm)',
  
  is_active TINYINT DEFAULT 1 COMMENT '是否启用',
  remark VARCHAR(500) COMMENT '备注',
  
  create_by VARCHAR(50) COMMENT '创建人',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by VARCHAR(50) COMMENT '更新人',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  
  UNIQUE KEY uk_material_code (material_code),
  INDEX idx_material_type (material_type),
  INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料生产配置表';

-- 插入示例数据
INSERT INTO material_production_config 
  (material_code, material_name, material_type, min_production_area, setup_time, unit_time, recommended_width, recommended_thickness, loss_rate)
VALUES
  ('1011-R02-1507-B01-0400', '22μm蓝色PET胶带', 'coating', 50.00, 30, 0.5, 1040, 22, 5.00),
  ('1011-R02-1507-B01-0600', '21μm绿色保护膜', 'coating', 60.00, 30, 0.5, 1040, 21, 5.00),
  ('103-WW-55-T01-0015', 'PET保护膜', 'coating', 80.00, 30, 0.6, 1280, 55, 6.00)
ON DUPLICATE KEY UPDATE
  min_production_area = VALUES(min_production_area),
  setup_time = VALUES(setup_time),
  unit_time = VALUES(unit_time),
  update_time = NOW();

SELECT '✓ 物料生产配置表创建完成' AS status;
