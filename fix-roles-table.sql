-- =====================================================
-- 修复 roles 表结构 - 添加缺失的字段
-- =====================================================

-- 查看当前表结构
DESCRIBE roles;

-- 添加缺失的字段（逐条执行，如果报错说明字段已存在，跳过即可）
-- 如果 display_name 不存在
ALTER TABLE roles ADD COLUMN display_name VARCHAR(100) COMMENT '显示名称';

-- 如果 description 不存在  
ALTER TABLE roles ADD COLUMN description VARCHAR(255) COMMENT '角色描述';

-- 如果 status 不存在
ALTER TABLE roles ADD COLUMN status TINYINT DEFAULT 1 COMMENT '状态(1启用 0禁用)';

-- 如果 created_at 不存在
ALTER TABLE roles ADD COLUMN created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';

-- 如果 updated_at 不存在
ALTER TABLE roles ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';

-- 更新现有角色的显示名称
UPDATE roles SET display_name = '超级管理员', description = '拥有系统所有权限' WHERE name = 'admin';
UPDATE roles SET display_name = '销售人员', description = '销售管理相关权限' WHERE name = 'sales';
UPDATE roles SET display_name = '仓库管理员', description = '库存管理相关权限' WHERE name = 'warehouse';
UPDATE roles SET display_name = '生产人员', description = '生产管理相关权限' WHERE name = 'production';
UPDATE roles SET display_name = '研发人员', description = '研发管理相关权限' WHERE name = 'rd';

-- 验证结果
SELECT * FROM roles;
