-- 发货通知模板编辑指南（手工维护版）
-- 说明：
-- 1) 模板定义：biz_type = 'delivery_notice_template'
-- 2) 客户默认模板：biz_type = 'delivery_notice_default'
-- 3) template_key 必须唯一标识一个模板（建议英文小写+下划线）
-- 4) remark 可放 JSON 配置（当前前端已使用字段：compact）

-- =========================
-- A. 查看当前模板定义
-- =========================
SELECT id, biz_type, scene_name, template_key, customer_code, sort_no, is_active, remark, update_time
FROM label_print_template_config
WHERE biz_type = 'delivery_notice_template'
ORDER BY sort_no ASC, update_time DESC;

-- =========================
-- B. 新增模板（示例）
-- =========================
INSERT INTO label_print_template_config
(biz_type, scene_name, template_key, customer_code, sort_no, is_active, remark, create_by, create_time, update_by, update_time)
VALUES
('delivery_notice_template', '标准发货通知模板（默认）', 'delivery_notice_standard_v1', NULL, 1, 1, '{"compact":false}', 'system', NOW(), 'system', NOW()),
('delivery_notice_template', '简版发货通知模板', 'delivery_notice_simple_v1', NULL, 2, 1, '{"compact":true}', 'system', NOW(), 'system', NOW()),
('delivery_notice_template', '超简版发货通知模板', 'delivery_notice_mini_v1', NULL, 3, 1, '{"compact":true}', 'system', NOW(), 'system', NOW());

-- 如果 template_key 已存在，请改用 C 段 UPDATE。

-- =========================
-- C. 编辑模板（名称、排序、启用状态、配置）
-- =========================
UPDATE label_print_template_config
SET scene_name = '简版发货通知模板（新版）',
    sort_no = 2,
    is_active = 1,
    remark = '{"compact":true}',
    update_by = 'system',
    update_time = NOW()
WHERE biz_type = 'delivery_notice_template'
  AND template_key = 'delivery_notice_simple_v1';

-- =========================
-- D. 删除模板（谨慎）
-- =========================
DELETE FROM label_print_template_config
WHERE biz_type = 'delivery_notice_template'
  AND template_key = 'delivery_notice_mini_v1';

-- =========================
-- E. 设置客户默认模板（UPSERT）
-- =========================
INSERT INTO label_print_template_config
(biz_type, scene_name, template_key, customer_code, sort_no, is_active, remark, create_by, create_time, update_by, update_time)
VALUES
('delivery_notice_default', '发货通知客户默认模板', 'delivery_notice_simple_v1', 'CUST001', 1, 1, NULL, 'system', NOW(), 'system', NOW())
ON DUPLICATE KEY UPDATE
template_key = VALUES(template_key),
is_active = 1,
update_by = VALUES(update_by),
update_time = VALUES(update_time);

-- 注：若你的表上没有对应唯一键（biz_type + customer_code），请改用先查再 UPDATE/INSERT 的方式。

-- =========================
-- F. 查看客户默认模板
-- =========================
SELECT id, customer_code, template_key, is_active, update_time
FROM label_print_template_config
WHERE biz_type = 'delivery_notice_default'
ORDER BY update_time DESC;

-- =========================
-- G. 生效检查
-- =========================
-- 1) 打开发货通知页面，点击某行“打印”
-- 2) 弹窗下拉应出现新增模板
-- 3) 点“设为当前客户默认模板”后，再次打印同客户应自动选中该模板
