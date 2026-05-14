-- RP01 多模板规则（按料号精确 + 前缀 + 默认）
-- 执行前请确认数据库已包含 quality_report_template_rule 新结构字段：
-- material_code, material_prefix, priority, remark

START TRANSACTION;

-- 1) 清理 RP01 出货规则（仅示例客户）
DELETE FROM quality_report_template_rule
WHERE customer_code = 'RP01'
  AND inspection_type = 'outbound';

-- 2) 精确匹配：最高优先级
INSERT INTO quality_report_template_rule
(customer_code, inspection_type, material_code, material_prefix, priority, template_code, enabled, remark, created_by, updated_by)
VALUES
('RP01', 'outbound', 'A01100096', NULL, 200, 'OUTBOUND_RP01_A', 1, 'RP01-A 料号精确模板', 'seed', 'seed'),
('RP01', 'outbound', 'A01100097', NULL, 200, 'OUTBOUND_RP01_B', 1, 'RP01-B 料号精确模板', 'seed', 'seed');

-- 3) 前缀匹配：次优先级
INSERT INTO quality_report_template_rule
(customer_code, inspection_type, material_code, material_prefix, priority, template_code, enabled, remark, created_by, updated_by)
VALUES
('RP01', 'outbound', NULL, 'A011', 100, 'OUTBOUND_RP01_PREFIX', 1, 'RP01 前缀模板（A011*）', 'seed', 'seed');

-- 4) 默认兜底：最低优先级
INSERT INTO quality_report_template_rule
(customer_code, inspection_type, material_code, material_prefix, priority, template_code, enabled, remark, created_by, updated_by)
VALUES
('RP01', 'outbound', NULL, NULL, 0, 'OUTBOUND_RP01', 1, 'RP01 默认模板', 'seed', 'seed');

COMMIT;

-- 验证查询（示例）
-- A01100096 => OUTBOUND_RP01_A
-- A01100097 => OUTBOUND_RP01_B
-- A01112345 => OUTBOUND_RP01_PREFIX
-- B99123456 => OUTBOUND_RP01
SELECT id, customer_code, inspection_type, material_code, material_prefix, priority, template_code, enabled, remark, updated_at
FROM quality_report_template_rule
WHERE customer_code='RP01' AND inspection_type='outbound'
ORDER BY priority DESC, updated_at DESC, id DESC;
