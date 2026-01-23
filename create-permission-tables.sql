-- =====================================================
-- 权限管理表结构
-- =====================================================

-- 1. 角色表 (如果不存在则创建)
CREATE TABLE IF NOT EXISTS roles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '角色ID',
    name VARCHAR(50) NOT NULL UNIQUE COMMENT '角色标识(如admin/sales)',
    display_name VARCHAR(100) COMMENT '显示名称',
    description VARCHAR(255) COMMENT '角色描述',
    status TINYINT DEFAULT 1 COMMENT '状态(1启用 0禁用)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 2. 用户角色关联表 (如果不存在则创建)
CREATE TABLE IF NOT EXISTS user_roles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_user_id (user_id),
    KEY idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 3. 初始化角色数据
INSERT INTO roles (name, display_name, description, status) VALUES
('admin', '超级管理员', '拥有系统所有权限，可进行导入导出操作', 1),
('sales', '销售人员', '销售管理相关权限', 1),
('warehouse', '仓库管理员', '库存管理相关权限', 1),
('production', '生产人员', '生产管理相关权限', 1),
('rd', '研发人员', '研发管理相关权限', 1)
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name);

-- 4. 为现有admin用户分配admin角色
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r 
WHERE u.username = 'admin' AND r.name = 'admin'
ON DUPLICATE KEY UPDATE role_id = role_id;

-- 5. 查看结果
SELECT * FROM roles;
SELECT ur.*, u.username, r.name as role_name 
FROM user_roles ur 
LEFT JOIN users u ON ur.user_id = u.id 
LEFT JOIN roles r ON ur.role_id = r.id;
