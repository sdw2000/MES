-- 用户表增加人员关联字段（用户 <-> 人员表）
-- 执行前请先确认数据库连接到 erp 库

ALTER TABLE users
  ADD COLUMN staff_id BIGINT NULL COMMENT '关联人员ID（production_staff.id）' AFTER real_name,
  ADD INDEX idx_users_staff_id (staff_id);

-- 可选：如需强约束，再执行外键（确认两边引擎/字符集一致后开启）
-- ALTER TABLE users
--   ADD CONSTRAINT fk_users_staff
--   FOREIGN KEY (staff_id) REFERENCES production_staff(id);
