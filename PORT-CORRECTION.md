# 🔴 重要：端口号更正

## ⚠️ 端口号配置

### 正确的端口号
- **后端**: `8090`
- **前端**: `8080` ✅ (不是9527)

### 访问地址
```
前端: http://localhost:8080
后端: http://localhost:8090
```

---

## 📝 配置文件

### 前端端口配置
**文件**: `E:\vue\ERP\vue.config.js` (第17行)
```javascript
const port = process.env.port || process.env.npm_config_port || 8080
```

---

## ✅ 更新后的服务检查命令

```powershell
# 检查后端 (8090)
netstat -ano | findstr ":8090"

# 检查前端 (8080)
netstat -ano | findstr ":8080"
```

---

## 🚀 正确的启动流程

### 启动后端
```powershell
cd E:\java\MES
mvn spring-boot:run
```
**监听端口**: 8090

### 启动前端
```powershell
cd E:\vue\ERP
npm run dev
```
**监听端口**: 8080

### 访问系统
```
http://localhost:8080
```

---

**重要**: 所有之前提到9527端口的地方都应该改为8080！
