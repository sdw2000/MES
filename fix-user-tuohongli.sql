-- =====================================================
-- 修复用户 tuohongli 登录和权限问题
-- 请在 MySQL 客户端中执行此脚本
-- =====================================================

-- 1. 检查用户是否存在
SELECT '=== 检查用户 tuohongli ===' as step;
SELECT * FROM users WHERE username = 'tuohongli';

-- 2. 如果用户不存在，创建用户（密码为 123456 的 BCrypt 加密）
INSERT INTO users (username, password, real_name, status, created_at)
SELECT 'tuohongli', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '陀洪丽', '0', NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'tuohongli');

-- 3. 更新密码为正确的 BCrypt 格式
UPDATE users 
SET password = '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi'
WHERE username = 'tuohongli';

-- 4. 确保 warehouse 角色存在
INSERT INTO roles (name, display_name, description, status)
SELECT 'warehouse', '仓库管理员', '库存管理相关权限', 1
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'warehouse');

-- 5. 为用户分配 warehouse 角色
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id 
FROM users u, roles r 
WHERE u.username = 'tuohongli' AND r.name = 'warehouse'
AND NOT EXISTS (
    SELECT 1 FROM user_roles ur2 
    WHERE ur2.user_id = u.id AND ur2.role_id = r.id
);

-- 6. 验证修复结果
SELECT '=== 验证修复结果 ===' as step;
SELECT u.id, u.username, u.real_name, u.status, GROUP_CONCAT(r.name) as roles
FROM users u 
LEFT JOIN user_roles ur ON u.id = ur.user_id 
LEFT JOIN roles r ON ur.role_id = r.id 
WHERE u.username = 'tuohongli'
GROUP BY u.id;

-- =====================================================
-- 权限说明：
-- warehouse: 可访问库存管理、入库、出库、库存流水等功能
-- =====================================================
