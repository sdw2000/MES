# 📚 Redis数据查看指南

## 🔑 我们的Redis键值规则

根据代码分析，您的应用在Redis中存储的数据键值为：

### 登录用户数据
**键名格式**: `login:{userId}`

**示例**:
- `login:1` - 用户ID为1的登录信息
- `login:2` - 用户ID为2的登录信息
- `login:123` - 用户ID为123的登录信息

**值内容**: 用户的完整登录信息（JSON格式），包括：
- 用户基本信息（id, username, realName等）
- 权限列表（permissions）
- 其他认证信息

---

## 🛠️ 查看Redis数据的方法

### 方法1: 使用Redis CLI（命令行）

#### 1️⃣ 打开命令行进入Redis目录
```powershell
cd D:\360安全浏览器下载\Redis-8.4.0-Windows-x64-cygwin
```

#### 2️⃣ 启动Redis CLI
```powershell
redis-cli.exe -h 127.0.0.1 -p 6379
```

#### 3️⃣ 常用查询命令

**查看所有login相关的键**
```redis
keys login:*
```
输出示例：
```
1) "login:1"
2) "login:2"
```

**查看特定键的值**
```redis
get login:1
```
输出示例（JSON格式）：
```json
{"user":{"id":1,"username":"admin","realName":"管理员",...},"permissions":["admin",...]}
```

**查看键的类型**
```redis
type login:1
```
输出: `string`

**查看键的过期时间**
```redis
ttl login:1
```
输出: 
- `-1` 表示永不过期
- `-2` 表示键不存在
- 正数表示剩余秒数

**查看所有键**
```redis
keys *
```

**删除特定键**
```redis
del login:1
```

**清空所有数据**（⚠️ 慎用）
```redis
flushall
```

**退出Redis CLI**
```redis
exit
```

---

### 方法2: 使用PowerShell一行命令

**查看所有login键**
```powershell
cd "D:\360安全浏览器下载\Redis-8.4.0-Windows-x64-cygwin"
.\redis-cli.exe -h 127.0.0.1 -p 6379 keys "login:*"
```

**获取特定键的值**
```powershell
.\redis-cli.exe -h 127.0.0.1 -p 6379 get "login:1"
```

**查看所有键**
```powershell
.\redis-cli.exe -h 127.0.0.1 -p 6379 keys "*"
```

---

### 方法3: 使用Redis可视化工具（推荐）

#### 推荐工具：
1. **Redis Desktop Manager (RESP.app)** - 免费
2. **Another Redis Desktop Manager** - 免费开源
3. **RedisInsight** - Redis官方，免费

#### 下载地址：
- RESP: https://resp.app/
- Another Redis Desktop Manager: https://github.com/qishibo/AnotherRedisDesktopManager
- RedisInsight: https://redis.com/redis-enterprise/redis-insight/

#### 连接配置：
- Host: `127.0.0.1` 或 `localhost`
- Port: `6379`
- Password: (空，因为您没有设置密码)

---

## 📊 实际示例

### 登录后Redis中的数据

当用户ID为1的用户（admin）登录后，Redis中会有：

**键名**: `login:1`

**值**（JSON格式，已格式化）:
```json
{
  "user": {
    "id": 1,
    "username": "admin",
    "realName": "管理员",
    "password": null,
    "status": "0",
    "createdBy": null,
    "createdAt": "2024-01-01T00:00:00",
    "updatedAt": "2024-01-01T00:00:00",
    "delFlag": 0
  },
  "permissions": [
    "admin",
    "user:list",
    "user:add",
    "user:edit",
    "user:delete"
  ],
  "authorities": [
    {"authority": "admin"}
  ],
  "enabled": true,
  "credentialsNonExpired": true,
  "accountNonExpired": true,
  "accountNonLocked": true
}
```

---

## 🔍 调试技巧

### 1. 检查用户是否登录
```redis
exists login:1
```
返回 `1` 表示键存在，`0` 表示不存在

### 2. 查看登录用户数量
```redis
keys login:*
```
统计返回的键数量

### 3. 查看特定用户的权限
```redis
get login:1
```
然后在JSON中查找 `permissions` 字段

### 4. 模拟用户登出（删除Redis中的登录信息）
```redis
del login:1
```

---

## 🧪 快速测试脚本

保存以下内容为 `test-redis.ps1`：

```powershell
$redisPath = "D:\360安全浏览器下载\Redis-8.4.0-Windows-x64-cygwin"
Set-Location $redisPath

Write-Host "=== Redis数据查询 ===" -ForegroundColor Cyan
Write-Host ""

Write-Host "1. 所有login键:" -ForegroundColor Yellow
.\redis-cli.exe -h 127.0.0.1 -p 6379 keys "login:*"
Write-Host ""

Write-Host "2. login:1的值:" -ForegroundColor Yellow
.\redis-cli.exe -h 127.0.0.1 -p 6379 get "login:1"
Write-Host ""

Write-Host "3. 所有键的数量:" -ForegroundColor Yellow
$count = .\redis-cli.exe -h 127.0.0.1 -p 6379 dbsize
Write-Host "总共: $count 个键"
```

运行:
```powershell
.\test-redis.ps1
```

---

## 💡 常见问题

### Q1: 为什么看不到login:*键？
**A**: 可能原因：
1. 用户还没有登录
2. Redis中的数据已过期或被清除
3. 应用没有成功写入Redis

### Q2: 如何清除所有登录信息？
**A**: 
```redis
keys login:* | xargs redis-cli del
```
或在Redis CLI中：
```redis
del login:1 login:2 login:3
```

### Q3: 数据何时会被删除？
**A**: 
- 用户主动登出时（调用 `/user/logout`）
- Redis重启且没有持久化
- 手动删除

### Q4: 如何备份Redis数据？
**A**: 
```powershell
# 触发RDB快照
redis-cli.exe -h 127.0.0.1 -p 6379 save
# 备份dump.rdb文件
```

---

## 📝 代码中的Redis操作

### 写入（登录时）
```java
// LoginServiceImpl.java
redisCache.setCacheObject("login:" + userId, JSON.toJSONString(loginUser));
```

### 读取（请求验证时）
```java
// JwtAuthenticationTokenFilter.java
String redisKey = "login:" + userid;
LoginUser loginUser = redisCache.getCacheObject(redisKey);
```

### 删除（登出时）
```java
// LoginServiceImpl.java
redisCache.deleteObject("login:" + userid);
```

---

**总结**: 您的应用使用 `login:{userId}` 格式存储登录用户信息，可以使用Redis CLI或可视化工具轻松查看和管理这些数据。
