-- filepath: e:\java\MES\reset-permission-tables.sql
-- =====================================================
-- 权限管理表结构重置脚本
-- 说明：
-- 1. 重建 users, roles, user_roles 表
-- 2. id 使用 INT (满足用户“人数不多”的需求)
-- 3. 密码 '123456' 对应的 BCrypt hash: $2a$10$7JB720yubVSZv5W56jdx.euT/eCNqCj4MO0y7vWHssbLGKhrt3Kq

-- 为了确保干净，先删除旧表
DROP TABLE IF EXISTS user_roles;
DROP TABLE IF EXISTS roles;
DROP TABLE IF EXISTS users;

-- 1. 用户表
CREATE TABLE users (
    id INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    password VARCHAR(100) NOT NULL COMMENT '密码',
    real_name VARCHAR(50) COMMENT '真实姓名',
    email VARCHAR(100) COMMENT '邮箱',
    status TINYINT DEFAULT 0 COMMENT '帐号状态（0正常 1停用）',
    created_by INT COMMENT '创建人',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    del_flag TINYINT DEFAULT 0 COMMENT '删除标志（0代表未删除，1代表已删除）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 2. 角色表
CREATE TABLE roles (
    id INT NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    name VARCHAR(50) NOT NULL COMMENT '角色标识',
    display_name VARCHAR(100) COMMENT '显示名称',
    description VARCHAR(255) COMMENT '角色描述',
    status TINYINT DEFAULT 1 COMMENT '状态(1启用 0禁用)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 3. 用户角色关联表
CREATE TABLE user_roles (
    id INT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id INT NOT NULL COMMENT '用户ID',
    role_id INT NOT NULL COMMENT '角色ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_user_id (user_id),
    KEY idx_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 4. 初始化数据

-- 插入角色
INSERT INTO roles (id, name, display_name, description, status) VALUES
(1, 'admin', '超级管理员', '系统所有权限', 1),
(2, 'sales', '销售人员', '销售模块权限', 1),
(3, 'warehouse', '仓库管理员', '库存模块权限', 1);

-- 插入用户 (admin / 123456)
-- 使用 hash: $2a$10$7JB720yubVSZv5W56jdx.euT/eCNqCj4MO0y7vWHssbLGKhrt3Kq
INSERT INTO users (id, username, password, real_name, status) VALUES
(1, 'admin', '$2a$10$7JB720yubVSZv5W56jdx.euT/eCNqCj4MO0y7vWHssbLGKhrt3Kq', '管理员', 0);

-- 关联用户角色
INSERT INTO user_roles (user_id, role_id) VALUES
(1, 1);

