# 端口号更正总结

## 📌 重要说明

根据实际配置文件 `E:\vue\ERP\vue.config.js` 第17行：
```javascript
port: 8080,
```

**正确的前端端口是 8080，不是 9527！**

---

## ✅ 实际端口配置

| 服务 | 端口 | 配置文件 |
|------|------|----------|
| **后端** | **8090** | `application.yml` |
| **前端** | **8080** | `vue.config.js` 第17行 |

---

## 🔍 已发现的文档错误

以下文档中错误地使用了 9527 端口：

### 1. 项目README文件
- `E:\vue\ERP\README.zh-CN.md` (第201行)
- `E:\vue\ERP\README.md` (第183行)
- `E:\vue\ERP\README.ja.md` (第164行)
- `E:\vue\ERP\README.es.md` (第168行)

### 2. 销售订单相关文档
- `E:\java\MES\SALES-ORDER-SUMMARY.md` (第327行)
- `E:\java\MES\SALES-ORDER-README.md` (多处)
- `E:\java\MES\SALES-ORDER-QUICKSTART.md` (多处)
- `E:\java\MES\SALES-ORDER-IMPLEMENTATION.md` (第217行)
- `E:\java\MES\SALES-ORDER-COMPLETION-REPORT.md` (多处)

### 3. 其他文档
- `E:\java\MES\TIMEOUT-DIAGNOSIS.md` (第44行 - CORS配置)
- `E:\java\MES\STARTUP-GUIDE.md` (第90-91行)
- `E:\java\MES\ROLLBACK-COMPLETE.md` (第129行)

---

## 🛠️ 建议操作

### 选项1：批量更正（推荐）
运行批量更正脚本：
```powershell
cd E:\java\MES
.\batch-fix-port-numbers.ps1
```

### 选项2：手动更正
使用文本编辑器的"全局替换"功能：
- 查找：`9527`
- 替换：`8080`
- 范围：以上列出的文档文件

### 选项3：不更正（如果9527是历史配置）
如果这些文档是针对旧版本的，可以保持不变。

---

## ⚠️ 注意事项

1. **不要修改以下文件**：
   - `package-lock.json`
   - `node_modules` 目录
   - 任何第三方库的文档

2. **CORS配置检查**：
   如果后端 CORS 配置中有 9527，需要改为 8080：
   ```java
   // WebSecurityConfig.java 或 CorsConfig.java
   configuration.addAllowedOrigin("http://localhost:8080"); // ← 确认是8080
   ```

3. **浏览器缓存**：
   更正后需要清除浏览器缓存或使用 Ctrl+Shift+R 强制刷新。

---

## 📝 更正后的访问方式

### 开发环境
```
前端: http://localhost:8080
后端: http://localhost:8090
```

### 服务启动验证
```powershell
# 检查端口占用
netstat -ano | findstr ":8080 :8090"

# 应该看到：
# TCP    0.0.0.0:8080           LISTENING       <PID1>
# TCP    0.0.0.0:8090           LISTENING       <PID2>
```

---

## 📅 更新记录

| 日期 | 操作 | 状态 |
|------|------|------|
| 2024-01-XX | 发现端口号错误 | ✅ |
| 2024-01-XX | 创建更正文档 | ✅ |
| 2024-01-XX | 等待批量更正 | ⏳ |

---

## 🎯 总结

**请记住：前端端口是 8080，不是 9527！**

如有疑问，请查看：
- 配置文件：`E:\vue\ERP\vue.config.js` 第17行
- 快速启动脚本：`E:\java\MES\quick-start-services.ps1`
- 完整报告：`E:\java\MES\PORT-CORRECTION-COMPLETE.md`
