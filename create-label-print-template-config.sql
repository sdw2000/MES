SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS label_print_template_config (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
  biz_type VARCHAR(100) NOT NULL COMMENT '业务类型，如 COATING_ROLL_LABEL',
  scene_name VARCHAR(100) DEFAULT NULL COMMENT '场景名称，如 母卷标签',
  template_key VARCHAR(100) NOT NULL COMMENT '模板键，对应本机 BarTender 模板映射键',
  customer_code VARCHAR(100) DEFAULT NULL COMMENT '客户编码，空表示全局规则',
  sort_no INT DEFAULT 1 COMMENT '排序号，越小越优先',
  is_active TINYINT DEFAULT 1 COMMENT '是否启用：1启用 0停用',
  remark VARCHAR(500) DEFAULT NULL COMMENT '备注',
  create_by VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_by VARCHAR(64) DEFAULT NULL COMMENT '更新人',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  KEY idx_label_print_biz_type (biz_type),
  KEY idx_label_print_customer_code (customer_code),
  KEY idx_label_print_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签打印模板配置表，仅标签类打印使用';

INSERT INTO label_print_template_config (biz_type, scene_name, template_key, customer_code, sort_no, is_active, remark, create_by, update_by)
VALUES
('COATING_ROLL_LABEL', '母卷标签', 'COATING_ROLL_LABEL', NULL, 10, 1, '涂布母卷标签默认模板', 'system', 'system'),
('COATING_INBOUND_SHEET', '涂布入库标签单', 'COATING_INBOUND_SHEET', NULL, 20, 1, '如该场景走标签模板时可在此配置；A4/B5单据不使用本表', 'system', 'system');
