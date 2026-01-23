# 🔍 30秒超时问题诊断指南

## 🚨 问题现象
第一个登录请求就超时（30秒），没有返回token。

## 🎯 可能的原因

### 1. **数据库连接问题** (最可能)
- ✅ 数据库是阿里云RDS（远程数据库）
- ✅ 原配置validation-timeout只有250ms（太短）
- ✅ 没有设置连接超时参数

### 2. **网络延迟**
- 连接到阿里云RDS需要公网访问
- 可能存在网络波动或防火墙限制

### 3. **CORS预检请求**
- 原配置有冲突：`allowedOrigin("*")` + `setAllowCredentials(true)`
- 可能导致OPTIONS预检请求失败

### 4. **数据库查询慢**
- 登录需要查询用户表和权限表
- 如果没有索引或数据量大会很慢

---

## ✅ 已实施的修复

### 1. 优化数据库连接配置
```properties
# 添加连接和Socket超时（30秒）
spring.datasource.url=jdbc:mysql://...?connectTimeout=30000&socketTimeout=30000

# 优化Hikari连接池
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.validation-timeout=5000 (从250ms增加)
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=5
```

### 2. 修复CORS配置
```java
// 使用具体的origin而不是"*"
configuration.addAllowedOrigin("http://localhost:9527");
// 添加预检缓存（避免每次都OPTIONS请求）
configuration.setMaxAge(3600L);
```

### 3. 添加详细性能日志
在登录流程中记录每个步骤的耗时：
- 数据库认证耗时
- JWT生成耗时
- Redis写入耗时
- 总耗时

### 4. 增强日志级别
```properties
logging.level.com.fine=DEBUG
logging.level.com.zaxxer.hikari=DEBUG
```

---

## 🧪 诊断步骤

### 步骤1: 测试数据库连接
```powershell
# 使用MySQL客户端测试
mysql -h ssdw8127.mysql.rds.aliyuncs.com -u david -p erp
```

### 步骤2: 查看后端启动日志
重启后端应用，查看：
1. Hikari连接池初始化是否成功
2. 是否有数据库连接错误
3. Redis连接是否正常

### 步骤3: 使用测试脚本直接测试登录
```powershell
cd e:\java\MES
.\test-login.ps1 -Username "admin" -Password "你的密码"
```

### 步骤4: 查看详细日志输出
登录时后端应该输出：
```
========== 登录开始 ==========
用户名: admin
开始数据库认证...
数据库认证完成，耗时: XXms
生成JWT token...
JWT生成完成，耗时: XXms
存储用户信息到Redis...
Storing user in Redis with key: login:1
Successfully stored user in Redis，耗时: XXms
登录总耗时: XXms
========== 登录完成 ==========
```

---

## 🔧 如何使用

### 1. 停止当前后端应用 (Ctrl+C)

### 2. 重新启动后端
```powershell
cd e:\java\MES
mvn clean spring-boot:run
```

### 3. 测试登录
方式A - 使用PowerShell脚本：
```powershell
.\test-login.ps1 -Username "admin" -Password "你的密码"
```

方式B - 使用Postman：
- URL: http://localhost:8090/user/login
- Method: POST
- Headers: Content-Type: application/json
- Body (raw JSON):
```json
{
  "username": "admin",
  "password": "你的密码"
}
```

方式C - 直接启动前端测试：
```powershell
cd e:\vue\ERP
npm run dev
```
然后在浏览器登录

---

## 📊 预期结果

### 如果是数据库连接问题
日志会显示：
```
开始数据库认证...
(等待30秒)
Communications link failure / Connection timeout
```

### 如果是数据库查询慢
日志会显示：
```
开始数据库认证...
(等待很久)
数据库认证完成，耗时: 25000ms  ← 很长时间
```

### 如果是Redis问题
日志会显示：
```
存储用户信息到Redis...
Failed to store user in Redis，耗时: XXms
io.lettuce.core.RedisConnectionException
```

### 正常情况（目标）
```
========== 登录开始 ==========
用户名: admin
开始数据库认证...
数据库认证完成，耗时: 150ms
生成JWT token...
JWT生成完成，耗时: 5ms
存储用户信息到Redis...
Successfully stored user in Redis，耗时: 10ms
登录总耗时: 180ms  ← 应该在1秒以内
========== 登录完成 ==========
```

---

## 💡 进一步排查

### 如果数据库连接超时
1. 检查网络连接：`ping ssdw8127.mysql.rds.aliyuncs.com`
2. 检查阿里云RDS白名单设置
3. 检查本地防火墙设置
4. 尝试使用内网连接（如果有VPN）

### 如果数据库查询慢
1. 检查users表是否有username索引
2. 检查user_roles和roles表的连接查询性能
3. 考虑添加数据库索引
4. 查看MySQL慢查询日志

### 如果Redis超时
1. 确认Redis正在运行：`redis-cli ping`
2. 检查Redis配置文件
3. 尝试重启Redis服务

---

## 📁 修改的文件

1. ✅ `application.properties` - 数据库和日志配置
2. ✅ `SecurityConfig.java` - CORS配置修复
3. ✅ `LoginServiceImpl.java` - 添加性能日志
4. ✅ `test-login.ps1` - 登录测试脚本（新建）

---

## 🚀 快速操作

```powershell
# 1. 重新编译（已在进行中）
cd e:\java\MES
mvn clean compile -DskipTests

# 2. 重启后端
mvn spring-boot:run

# 3. 新终端测试登录
.\test-login.ps1

# 4. 查看后端日志分析耗时
```

---

**请执行上述步骤，并将后端日志中的详细输出（特别是耗时信息）反馈给我！**
