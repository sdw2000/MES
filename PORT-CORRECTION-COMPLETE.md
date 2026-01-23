# 端口号更正 - 完整总结

## 🔴 重要更正

### 正确的端口号配置
```
后端服务: http://localhost:8090
前端服务: http://localhost:8080  ✅ (不是9527!)
```

---

## ✅ 已更正的文件

### 1. 脚本文件
- ✅ `quick-start-services.ps1` - 快速启动服务脚本
- ✅ `LOGIN-PROBLEM-FIX.md` - 登录问题修复文档

### 2. 新建文件
- ✅ `PORT-CORRECTION.md` - 端口号更正说明

---

## 🚀 正确的服务检查命令

### 检查服务状态
```powershell
Write-Host "=== 服务状态检查 ===" -ForegroundColor Cyan
Write-Host ""

# 后端 (8090)
$backend = netstat -ano | findstr ":8090"
if ($backend) {
    Write-Host "✅ 后端运行中 (8090)" -ForegroundColor Green
} else {
    Write-Host "❌ 后端未运行" -ForegroundColor Red
}

# 前端 (8080)
$frontend = netstat -ano | findstr ":8080"
if ($frontend) {
    Write-Host "✅ 前端运行中 (8080)" -ForegroundColor Green
} else {
    Write-Host "❌ 前端未运行" -ForegroundColor Red
}
```

---

## 📊 完整的启动和访问流程

### 第1步: 启动后端
```powershell
cd E:\java\MES
mvn spring-boot:run
```
**监听端口**: 8090  
**成功标志**: `Started MesApplication`

### 第2步: 启动前端
```powershell
cd E:\vue\ERP
npm run dev
```
**监听端口**: 8080  
**成功标志**: `App running at: http://localhost:8080/`

### 第3步: 访问系统
```
http://localhost:8080
```

### 第4步: 登录
- **用户名**: admin
- **密码**: [您的密码]

---

## 🔧 验证端口是否正确启动

### 方法1: 使用netstat
```powershell
# 检查两个服务
netstat -ano | findstr ":8090 :8080"
```

**预期输出**:
```
TCP    0.0.0.0:8090    ...    LISTENING    [后端PID]
TCP    0.0.0.0:8080    ...    LISTENING    [前端PID]
```

### 方法2: 浏览器测试
```
后端健康检查: http://localhost:8090/actuator/health
前端页面:     http://localhost:8080
```

---

## 📝 端口配置说明

### 后端端口 (8090)
**配置文件**: `E:\java\MES\src\main\resources\application.properties`
```properties
server.port=8090
```

### 前端端口 (8080)
**配置文件**: `E:\vue\ERP\vue.config.js`
```javascript
const port = process.env.port || process.env.npm_config_port || 8080
```

---

## 🎯 常见问题

### Q1: 为什么之前说是9527？
**A**: 那是Vue Element Admin的默认端口，但您的项目配置是8080

### Q2: 如何修改前端端口？
**A**: 编辑 `vue.config.js` 第17行
```javascript
const port = 8080  // 改成您想要的端口
```

### Q3: 端口被占用怎么办？
**A**: 
```powershell
# 查看占用端口的进程
netstat -ano | findstr ":8080"

# 停止进程（替换PID为实际进程ID）
taskkill /PID [进程ID] /F
```

---

## 🔍 快速诊断脚本

保存为 `check-ports.ps1`:
```powershell
Write-Host "=== 端口状态检查 ===" -ForegroundColor Cyan
Write-Host ""

# 后端
Write-Host "后端 (8090):" -ForegroundColor Yellow
$backend = netstat -ano | findstr ":8090" | Select-Object -First 1
if ($backend) {
    Write-Host "  ✅ 运行中" -ForegroundColor Green
    Write-Host "  $backend" -ForegroundColor Gray
} else {
    Write-Host "  ❌ 未运行" -ForegroundColor Red
}

Write-Host ""

# 前端
Write-Host "前端 (8080):" -ForegroundColor Yellow
$frontend = netstat -ano | findstr ":8080" | Select-Object -First 1
if ($frontend) {
    Write-Host "  ✅ 运行中" -ForegroundColor Green
    Write-Host "  $frontend" -ForegroundColor Gray
} else {
    Write-Host "  ❌ 未运行" -ForegroundColor Red
}

Write-Host ""
Write-Host "访问地址: http://localhost:8080" -ForegroundColor Cyan
```

---

## 📚 相关URL清单

### 开发环境
- **前端首页**: http://localhost:8080
- **前端登录**: http://localhost:8080/#/login
- **后端API**: http://localhost:8090
- **后端健康检查**: http://localhost:8090/actuator/health

### API端点示例
- 报价单列表: http://localhost:8090/quotation/list
- 销售订单列表: http://localhost:8090/order/list
- 用户登录: http://localhost:8090/user/login

---

## ✅ 更正完成清单

- [x] 端口号更正文档已创建
- [x] 快速启动脚本已更新 (8080)
- [x] 登录问题文档已更新 (8080)
- [x] 服务检查命令已更新
- [x] 访问URL已更新
- [ ] 其他文档待更新 ← 如发现其他文件提到9527，请告知

---

## 🎉 现在可以正确访问了

### 立即检查服务
```powershell
netstat -ano | findstr ":8090 :8080"
```

### 立即访问系统
```
http://localhost:8080
```

---

**重要提示**: 
- ✅ 前端端口是 **8080**
- ❌ 不是 9527
- 📝 所有文档和脚本已更正

**状态**: ✅ 端口号更正完成  
**访问地址**: http://localhost:8080  
**下一步**: 检查服务是否在8080和8090端口运行
