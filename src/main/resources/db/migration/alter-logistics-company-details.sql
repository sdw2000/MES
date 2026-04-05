-- 扩展物流公司字段
ALTER TABLE logistics_companies
  ADD COLUMN IF NOT EXISTS company_code VARCHAR(50) COMMENT '公司编码' AFTER company_name,
  ADD COLUMN IF NOT EXISTS company_address VARCHAR(255) COMMENT '公司地址' AFTER company_code,
  ADD COLUMN IF NOT EXISTS contact_name VARCHAR(50) COMMENT '联系人' AFTER contact_phone,
  ADD COLUMN IF NOT EXISTS contact_mobile VARCHAR(50) COMMENT '联系手机' AFTER contact_name,
  ADD COLUMN IF NOT EXISTS contact_email VARCHAR(100) COMMENT '联系邮箱' AFTER contact_mobile;

SELECT 'Migration completed!' AS result;
