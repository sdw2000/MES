CREATE TABLE IF NOT EXISTS purchase_supplier_contacts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
  supplier_id BIGINT NOT NULL COMMENT '供应商ID',
  contact_name VARCHAR(64) NULL COMMENT '联系人姓名',
  contact_position VARCHAR(64) NULL COMMENT '职位',
  contact_phone VARCHAR(32) NULL COMMENT '联系电话',
  contact_email VARCHAR(128) NULL COMMENT '邮箱',
  contact_wechat VARCHAR(64) NULL COMMENT '微信',
  is_primary TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否主联系人:0否1是',
  is_decision_maker TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否决策人:0否1是',
  remark VARCHAR(255) NULL COMMENT '备注',
  created_at DATETIME NULL COMMENT '创建时间',
  updated_at DATETIME NULL COMMENT '更新时间',
  is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除:0否1是',
  KEY idx_supplier_id (supplier_id),
  KEY idx_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购供应商联系人表';
