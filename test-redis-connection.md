# Redis连接测试指南

## 已修复的问题

### 1. **Redis配置优化** ✅
- 添加了连接池配置（Lettuce）
- 设置了30秒的超时时间
- 添加了`commons-pool2`依赖

### 2. **前端超时配置** ✅
- 将axios超时从10秒增加到30秒
- 修改了token传递方式，使用`Authorization: Bearer <token>`

### 3. **JWT过滤器增强** ✅
- 添加了详细的日志输出
- 增强了Redis连接错误处理
- 返回更友好的错误信息

### 4. **登录服务修复** ✅
- **关键修复**: 将Redis写入从异步改为同步
- 这确保了token返回给前端时，用户信息已经存储在Redis中
- 避免了竞态条件导致的401错误

## 测试步骤

### 1. 验证Redis正在运行
```powershell
# 在Redis目录中运行
redis-cli.exe -h 127.0.0.1 -p 6379
# 测试命令
ping
# 应该返回: PONG
```

### 2. 启动Spring Boot应用
```powershell
cd e:\java\MES
mvn spring-boot:run
```

### 3. 查看启动日志
确保看到以下内容：
- Redis连接成功
- 没有Redis连接超时错误
- JwtAuthenticationTokenFilter已加载

### 4. 测试登录流程
使用Postman或前端应用登录，观察日志输出：
```
Token from request: eyJhbGci...
Parsed userId from token: 1
Fetching user from Redis with key: login:1
Redis returned: Data found
User authenticated successfully: username
```

## 配置摘要

### application.properties
```properties
# Redis配置
spring.redis.host=localhost
spring.redis.port=6379
spring.redis.password=
spring.redis.timeout=30000
spring.redis.database=0

# Redis连接池配置
spring.redis.lettuce.pool.max-active=8
spring.redis.lettuce.pool.max-idle=8
spring.redis.lettuce.pool.min-idle=0
spring.redis.lettuce.pool.max-wait=10000ms
spring.redis.lettuce.shutdown-timeout=100ms
```

### 前端request.js
```javascript
timeout: 30000 // 30秒超时
headers['Authorization'] = 'Bearer ' + getToken()
```

## 常见问题排查

### 问题1: 仍然超时
**解决方案**:
1. 检查Redis是否在运行: `redis-cli ping`
2. 检查防火墙是否阻止了6379端口
3. 查看应用日志中的详细错误信息

### 问题2: 401未授权错误
**解决方案**:
1. 清除浏览器缓存和cookies
2. 重新登录获取新token
3. 检查Redis中是否有`login:userId`键: `redis-cli keys login:*`

### 问题3: Redis连接被拒绝
**解决方案**:
1. 确保Redis服务器正在运行
2. 检查Redis配置文件中的bind地址
3. 尝试重启Redis服务

## 性能优化建议

1. **生产环境**: 将JWT过滤器中的`System.out.println`改为使用SLF4J Logger
2. **Redis持久化**: 根据需要配置RDB或AOF
3. **Token过期时间**: 当前设置为100小时，可以根据安全需求调整
4. **连接池大小**: 根据并发量调整`max-active`和`max-idle`

## 下一步

1. 重启Spring Boot应用
2. 重启Vue前端应用
3. 测试完整的登录-请求流程
4. 观察日志确认问题已解决
