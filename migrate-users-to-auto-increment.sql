-- 迁移 users 表到自增主键的安全步骤
-- 注意：在执行前务必备份当前数据库（导出 users 与所有外键引用 users 的表）
-- 本脚本假设以下：
-- 1) 当前 users 表结构与 com.fine.modle.User 对应，主键为 `id` BIGINT
-- 2) 其它表使用 user_id 外键引用 users(id)，例如 user_roles
-- 3) 你在执行期间可以停用应用写入（建议在维护窗口执行）

START TRANSACTION;

-- 1) 备份原表
DROP TABLE IF EXISTS users_backup;
CREATE TABLE users_backup LIKE users;
INSERT INTO users_backup SELECT * FROM users;

-- 2) 创建新表副本 users_new，增加 old_id 用于映射
DROP TABLE IF EXISTS users_new;
CREATE TABLE users_new LIKE users;
ALTER TABLE users_new ADD COLUMN old_id BIGINT NULL;

-- 3) 设置 users_new 的 id 为自增主键
ALTER TABLE users_new MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY;

-- 4) 将旧数据插入 users_new，并把原 id 写入 old_id
INSERT INTO users_new (username, password, real_name, email, status, created_by, created_at, updated_at, del_flag, old_id)
SELECT username, password, real_name, email, status, created_by, created_at, updated_at, del_flag, id FROM users ORDER BY id;

-- 5) 生成老id->新id 映射表
DROP TABLE IF EXISTS users_id_map;
CREATE TABLE users_id_map AS
SELECT old_id AS old_id, id AS new_id FROM users_new WHERE old_id IS NOT NULL;
ALTER TABLE users_id_map ADD PRIMARY KEY (old_id);

-- 6) 更新所有引用 users.id 的表（示例：user_roles）
-- 注意：如果系统中有其它外键引用 users(id)，请在这里列出并更新它们
-- 例如更新 user_roles
UPDATE user_roles ur
JOIN users_id_map m ON ur.user_id = m.old_id
SET ur.user_id = m.new_id;

-- 7) （可选）更新其它表的 user_id 字段
-- 在执行前请替换和添加需要更新的表：
-- UPDATE some_table t JOIN users_id_map m ON t.user_id = m.old_id SET t.user_id = m.new_id;

-- 8) 删除原外键并重建约束（示例：user_roles）
-- 首先删除外键约束名（如果你知道名字可直接删除）。这里使用 information_schema 来查找并删除。
-- 注意：下面的部分为示例命令，可能需要根据你的 MySQL 版本和外键名称调整。

-- 获取 user_roles 外键 名称（运行在客户端查看并替换到下面 DROP FOREIGN KEY）
-- SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_roles' AND REFERENCED_TABLE_NAME = 'users';

-- 假设外键名为 fk_user_roles_user
-- ALTER TABLE user_roles DROP FOREIGN KEY fk_user_roles_user;
-- 然后重建外键指向新的 users(id)
-- ALTER TABLE user_roles ADD CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE ON UPDATE CASCADE;

-- 9) 替换旧 users 表
DROP TABLE users;
ALTER TABLE users_new RENAME TO users;

-- 10) （可选）移除 users.old_id 和 users_id_map，如果确认没有问题可保留一段时间
ALTER TABLE users DROP COLUMN old_id;
-- DROP TABLE users_id_map; -- 若不再需要，可解除注释删除

COMMIT;

-- 完成后：
-- 1) 检查 users, user_roles 等表，确保引用关系正确。
-- 2) 停用应用->编译并重启后端（User.java 已标注 IdType.AUTO），确保新增用户插入时不再生成超长 id。
-- 3) 前端：可以选择保持 id 为字符串（兼容性好），或者在确认所有 id 较小且安全时转回 Number 处理。

-- 强烈建议先在测试环境执行并验证，再在生产执行。
