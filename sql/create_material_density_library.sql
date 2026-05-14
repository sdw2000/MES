-- 常用材质密度库（研发）
CREATE TABLE IF NOT EXISTS `material_density_library` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `material_en_name` VARCHAR(64) NOT NULL COMMENT '材质英文名',
  `material_cn_name` VARCHAR(64) NOT NULL COMMENT '材质中文名',
  `density` DECIMAL(10,4) NOT NULL COMMENT '密度 g/cm3',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `is_active` TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `create_by` VARCHAR(64) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_by` VARCHAR(64) DEFAULT NULL,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_material_en_name` (`material_en_name`),
  KEY `idx_material_cn_name` (`material_cn_name`),
  KEY `idx_is_active` (`is_active`),
  KEY `idx_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='常用材质密度库';

INSERT INTO `material_density_library`
(`material_en_name`,`material_cn_name`,`density`,`remark`,`is_active`,`deleted`,`create_by`,`update_by`)
VALUES
('BOPP','双向拉伸聚丙烯',0.9100,'常见基材薄膜',1,0,'system','system'),
('PET','聚酯',1.3800,'高强度基材',1,0,'system','system'),
('PP','聚丙烯',0.9000,'通用聚丙烯',1,0,'system','system'),
('PE','聚乙烯',0.9200,'通用聚乙烯',1,0,'system','system'),
('PVC','聚氯乙烯',1.3500,'电工胶带常见基材',1,0,'system','system'),
('PU','聚氨酯',1.2000,'弹性体基材',1,0,'system','system'),
('PS','聚苯乙烯',1.0500,'常见塑料材质',1,0,'system','system'),
('PI','聚酰亚胺',1.4200,'耐高温薄膜材质',1,0,'system','system'),
('Acrylic','丙烯酸胶',1.0200,'常见压敏胶',1,0,'system','system'),
('Silicone','硅胶',1.1000,'硅胶系胶黏剂',1,0,'system','system'),
('Rubber','橡胶胶层',0.9800,'橡胶系胶黏剂',1,0,'system','system'),
('Paper','纸',0.8000,'通用纸类经验值',1,0,'system','system'),
('Glassine Paper','格拉辛纸',0.9500,'离型纸常见材质',1,0,'system','system'),
('Paper Core','纸管',0.7200,'普通纸管经验值',1,0,'system','system')
ON DUPLICATE KEY UPDATE
  material_cn_name = VALUES(material_cn_name),
  density = VALUES(density),
  remark = VALUES(remark),
  is_active = VALUES(is_active),
  deleted = 0,
  update_time = CURRENT_TIMESTAMP;
