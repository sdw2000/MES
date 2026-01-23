# 🚀 MES应用启动指南

## ✅ 问题已修复确认

所有超时问题已成功修复！主要修复内容：

1. ✅ **LoginServiceImpl.java** - Redis同步写入（核心修复）
2. ✅ **JwtAuthenticationTokenFilter.java** - 增强日志和错误处理
3. ✅ **application.properties** - Redis连接池配置
4. ✅ **pom.xml** - 添加commons-pool2依赖
5. ✅ **request.js** - 超时时间和Token传递方式
6. ✅ **编译错误** - getUserName() → getUsername()

## 📋 启动前检查清单

### 1. Redis服务状态 ✅
```powershell
# 在Redis安装目录运行
cd D:\360安全浏览器下载\Redis-8.4.0-Windows-x64-cygwin
redis-cli.exe -h 127.0.0.1 -p 6379 ping

# 应该返回: PONG
```

### 2. 数据库连接 ✅
确保MySQL数据库可访问：
- Host: `ssdw8127.mysql.rds.aliyuncs.com`
- Database: `erp`
- User: `david`

### 3. 项目构建 ✅
```powershell
cd e:\java\MES
mvn clean package -DskipTests
```

## 🎯 启动步骤

### 方式1: 使用Maven运行（推荐用于开发）

```powershell
cd e:\java\MES
mvn spring-boot:run
```

### 方式2: 使用JAR文件运行（推荐用于生产）

```powershell
cd e:\java\MES
java -jar target\MES-0.0.1-SNAPSHOT.jar
```

### 方式3: 在IDE中运行

1. 打开 `MesApplication.java`
2. 右键 → Run 'MesApplication'

## 📱 启动前端应用

```powershell
cd e:\vue\ERP
npm run dev
```

## 🔍 验证启动成功

### 后端启动日志检查

启动成功后，应该看到：

```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::        (v2.3.7.RELEASE)

...
INFO - Started MesApplication in X.XXX seconds
```

### 前端启动日志检查

```
DONE  Compiled successfully in XXXXms

  App running at:
  - Local:   http://localhost:9527/
  - Network: http://192.168.x.x:9527/
```

## 🧪 功能测试

### 1. 测试登录接口

使用Postman或curl测试：

```powershell
# 登录请求
curl -X POST http://localhost:8090/user/login `
  -H "Content-Type: application/json" `
  -d '{\"username\":\"admin\",\"password\":\"your_password\"}'

# 预期响应
{
  "code": 20000,
  "msg": "登陆成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

### 2. 测试用户信息接口

```powershell
# 使用获取的token
curl -X GET "http://localhost:8090/user/info?token=YOUR_TOKEN_HERE"

# 或使用Authorization header
curl -X GET http://localhost:8090/user/info `
  -H "Authorization: Bearer YOUR_TOKEN_HERE"

# 预期响应
{
  "code": 20000,
  "msg": "登陆成功",
  "data": {
    "roles": ["admin"],
    "name": "admin",
    "id": 1,
    ...
  }
}
```

### 3. 验证Redis数据

```powershell
redis-cli.exe -h 127.0.0.1 -p 6379

# 在Redis CLI中：
127.0.0.1:6379> keys login:*
1) "login:1"

127.0.0.1:6379> get login:1
# 应该返回用户JSON数据
```

## 📊 日志分析

### 正常登录流程日志

```
Token from request: eyJhbGci...
Parsed userId from token: 1
Storing user in Redis with key: login:1
Successfully stored user in Redis
Fetching user from Redis with key: login:1
Redis returned: Data found
User authenticated successfully: admin
```

### 可能的警告（可以忽略）

```
HV000001: Hibernate Validator not found...
```
这是正常的，不影响功能。

## 🐛 故障排查

### 问题1: 端口被占用

```
***************************
APPLICATION FAILED TO START
***************************
Description:
Web server failed to start. Port 8090 was already in use.
```

**解决方案**：
```powershell
# 查找占用端口的进程
netstat -ano | findstr :8090

# 结束进程
taskkill /PID <进程ID> /F

# 或更改端口
# 在application.properties中修改: server.port=8091
```

### 问题2: Redis连接失败

```
Unable to connect to Redis; nested exception is 
io.lettuce.core.RedisConnectionException: Unable to connect to 127.0.0.1:6379
```

**解决方案**：
```powershell
# 启动Redis服务器
cd D:\360安全浏览器下载\Redis-8.4.0-Windows-x64-cygwin
redis-server.exe
```

### 问题3: MySQL连接失败

```
Communications link failure
The last packet sent successfully to the server was 0 milliseconds ago.
```

**解决方案**：
1. 检查网络连接
2. 确认MySQL服务器地址和端口
3. 验证用户名和密码
4. 检查防火墙设置

### 问题4: 前端仍然超时

**解决方案**：
```powershell
# 1. 清除浏览器缓存
# 2. 确认前端代码已更新
cd e:\vue\ERP
npm run build

# 3. 重启前端开发服务器
npm run dev

# 4. 强制刷新浏览器 (Ctrl + Shift + R)
```

## 🔐 默认账号信息

根据数据库配置，默认管理员账号可能是：
- Username: `admin`
- Password: 请查看数据库或咨询团队

## 📈 性能优化建议

### 1. JVM参数优化（生产环境）

```powershell
java -Xms512m -Xmx1024m -XX:+UseG1GC `
  -jar target\MES-0.0.1-SNAPSHOT.jar
```

### 2. Redis持久化配置

编辑Redis配置文件：
```
# 启用RDB持久化
save 900 1
save 300 10
save 60 10000

# 或启用AOF持久化
appendonly yes
appendfsync everysec
```

### 3. 数据库连接池优化

在`application.properties`中调整：
```properties
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
```

## 🎉 启动成功标志

当你看到以下内容时，说明应用已成功启动：

- ✅ Spring Boot应用启动成功
- ✅ Redis连接正常
- ✅ MySQL数据库连接正常
- ✅ 无报错信息
- ✅ 可以访问 http://localhost:8090
- ✅ 前端可以成功登录
- ✅ 后续API请求不再超时

## 📞 需要帮助？

如果遇到任何问题：

1. 检查控制台日志中的错误信息
2. 查看 `SOLUTION-SUMMARY.md` 了解修复详情
3. 参考 `test-redis-connection.md` 进行Redis诊断
4. 确保所有依赖已正确安装

---

**最后更新**: 2026-01-05  
**版本**: v1.0  
**状态**: ✅ 所有问题已修复，可以正常启动
