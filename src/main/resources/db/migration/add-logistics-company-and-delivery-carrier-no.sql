-- 新增物流公司表
CREATE TABLE IF NOT EXISTS logistics_companies (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  company_name VARCHAR(100) NOT NULL COMMENT '公司名称',
  contact_phone VARCHAR(50) DEFAULT NULL COMMENT '联系电话',
  status VARCHAR(20) DEFAULT 'active' COMMENT '状态',
  remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
  created_at DATETIME DEFAULT NULL COMMENT '创建时间',
  updated_at DATETIME DEFAULT NULL COMMENT '更新时间',
  is_deleted INT DEFAULT 0 COMMENT '逻辑删除',
  UNIQUE KEY uk_logistics_company_name (company_name)
);

-- 发货通知新增物流单号
ALTER TABLE delivery_notices
  ADD COLUMN IF NOT EXISTS carrier_no VARCHAR(100) COMMENT '物流单号' AFTER carrier_name;

SELECT 'Migration completed!' AS result;
