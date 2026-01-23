# 客户管理 500 错误 - 完整修复方案

## 📋 问题总结

**错误信息**: `POST http://localhost:8090/api/sales/customers 500 (Internal Server Error)`

**根本原因**: 缺少数据库表 `customer_code_sequence`，该表用于生成客户编号序列。

---

## ✅ 已完成的代码修复

### 1. CustomerMapper.java
- ✓ 修复了 `insertSequence` 方法，将 `@Select` 改为 `@Insert` 注解

### 2. CustomerServiceImpl.java  
- ✓ 在 `generateCustomerCode` 方法中添加了容错处理
- ✓ 如果序列表不存在，使用时间戳作为备用方案生成编号

---

## 🔧 数据库修复步骤（三选一）

### 方案 A：使用 Navicat 或其他 MySQL 工具（推荐）

1. 打开 Navicat 或 MySQL Workbench
2. 连接到数据库：`mes`
3. 打开并执行文件：`e:\java\MES\fix-customer-simple.sql`
4. 验证表是否创建成功：
   ```sql
   SHOW TABLES LIKE 'customer%';
   ```

### 方案 B：使用命令行

```cmd
cd e:\java\MES
mysql -u root -p123456 mes < fix-customer-simple.sql
```

如果 mysql 命令不可用，需要先添加到系统 PATH：
- MySQL 8.0: `C:\Program Files\MySQL\MySQL Server 8.0\bin`
- MySQL 5.7: `C:\Program Files\MySQL\MySQL Server 5.7\bin`
- XAMPP: `C:\xampp\mysql\bin`

### 方案 C：手动复制 SQL 执行

连接到 mes 数据库后，执行以下 SQL：

```sql
CREATE TABLE IF NOT EXISTS `customer_code_sequence` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `prefix` VARCHAR(10) NOT NULL COMMENT '客户编号前缀（如ALB、TX等）',
  `current_number` INT(11) NOT NULL DEFAULT 0 COMMENT '当前序号',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prefix` (`prefix`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户编号序列表';
```

---

## 🔍 验证修复

### 1. 验证数据库表
```sql
USE mes;
SHOW TABLES LIKE 'customer%';
DESC customer_code_sequence;
```

应该看到三个表：
- ✓ customers
- ✓ customer_contacts
- ✓ customer_code_sequence

### 2. 重启后端服务
- 如果使用 IDEA，点击重启按钮
- 如果使用命令行，Ctrl+C 停止后重新运行 `mvn spring-boot:run`

### 3. 测试创建客户
1. 打开前端页面：`http://localhost:9528`
2. 进入"销售管理" -> "客户管理"
3. 点击"新增客户"
4. 填写必填信息：
   - 客户名称
   - 客户编号前缀（如：ALB）
   - 至少一个联系人信息
5. 点击"确定"

如果成功，会显示"新增成功"，并自动生成客户编号（如：ALB001）

---

## 🎯 快速验证清单

- [ ] 数据库表 `customer_code_sequence` 已创建
- [ ] 后端服务已重启
- [ ] 前端可以访问客户管理页面
- [ ] 新增客户功能正常工作
- [ ] 客户编号自动生成（格式：前缀+001）

---

## 📁 相关文件

- **SQL 修复脚本**: `fix-customer-simple.sql`
- **完整建表脚本**: `create-customer-tables.sql`
- **测试数据**: `insert-customer-test-data.sql`
- **修复说明**: `CUSTOMER-500-ERROR-FIX.md`

---

## 🐛 如果仍然失败

### 检查后端日志
查看控制台是否有其他错误信息，例如：
- 数据库连接失败
- 字段映射错误
- 权限不足

### 检查数据库连接
在 `application.yml` 或 `application.properties` 中验证：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mes?useSSL=false&serverTimezone=UTC
    username: root
    password: 123456
```

### 查看详细错误
在浏览器开发者工具的 Network 标签中，查看 500 错误的详细响应内容。

---

## 📞 技术支持

如果以上方法都无法解决，请提供：
1. 后端控制台的完整错误日志
2. 浏览器 Network 中 500 错误的 Response
3. 数据库表列表截图 (`SHOW TABLES;`)

---

**修复日期**: 2026-01-06  
**修复状态**: ✅ 代码已修复，等待数据库表创建
