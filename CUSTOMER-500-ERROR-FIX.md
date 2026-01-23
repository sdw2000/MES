# 客户管理500错误修复指南

## 问题描述
创建客户时出现 `500 Internal Server Error`

## 根本原因
缺少 `customer_code_sequence` 数据库表，该表用于生成客户编号序列。

## 修复方案

### 方案1：自动修复（推荐）

1. 运行PowerShell修复脚本：
```powershell
cd e:\java\MES
.\fix-customer-db.ps1
```

### 方案2：手动执行SQL

1. 打开MySQL客户端或管理工具（Navicat、MySQL Workbench等）
2. 连接到 `mes` 数据库
3. 执行以下SQL：

```sql
USE mes;

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

### 方案3：执行完整的建表脚本

```bash
cd e:\java\MES
mysql -u root -p123456 mes < create-customer-tables.sql
```

或者使用简化版：
```bash
mysql -u root -p123456 mes < fix-customer-simple.sql
```

## 代码改进

已添加了容错处理，即使序列表不存在，系统也会使用备用方案生成客户编号（基于时间戳）。

### 修改的文件：

1. **CustomerMapper.java**
   - 修复了 `insertSequence` 方法，从 `@Select` 改为 `@Insert`

2. **CustomerServiceImpl.java**
   - 在 `generateCustomerCode` 方法中添加了 try-catch 异常处理
   - 如果序列表不存在，使用时间戳作为备用方案

## 验证修复

1. 创建数据库表后，重启后端服务
2. 在前端尝试创建新客户
3. 检查是否成功生成客户编号

## 测试数据

如果需要测试数据，可以执行：
```bash
mysql -u root -p123456 mes < insert-customer-test-data.sql
```

## 常见问题

### Q: 如何验证表是否创建成功？
```sql
USE mes;
SHOW TABLES LIKE 'customer%';
DESC customer_code_sequence;
```

### Q: 如何查看当前的序列状态？
```sql
SELECT * FROM customer_code_sequence;
```

### Q: 如果还是报500错误怎么办？
1. 检查后端控制台日志，查看具体错误信息
2. 确认数据库连接正常
3. 确认所有客户相关的表都已创建：
   - customers
   - customer_contacts  
   - customer_code_sequence

---
修复日期：2026-01-06
