# 端口号验证清单

## ✅ 已验证的配置

### 1. 前端配置 ✅
**文件**: `E:\vue\ERP\vue.config.js` 第17行
```javascript
port: 8080,
```
**状态**: ✓ 正确（8080）

### 2. 后端配置 ✅
**文件**: `E:\java\MES\src\main\resources\application.yml`
```yaml
server:
  port: 8090
```
**状态**: ✓ 正确（8090）

### 3. CORS 配置 ✅
**文件**: `E:\java\MES\src\main\java\com\fine\config\CorsConfig.java`
```java
corsConfiguration.addAllowedOrigin("*");
```
**状态**: ✓ 允许所有源，无需修改

---

## 📋 需要更新的文档

### 已创建更正脚本
- ✅ `PORT-CORRECTION-SUMMARY.md` - 端口号更正总结
- ✅ `batch-fix-port-numbers.ps1` - 批量更正脚本

### 待更正的文档列表

#### A. 后端文档（MES项目）
| 文件 | 路径 | 包含9527次数 |
|------|------|--------------|
| SALES-ORDER-SUMMARY.md | E:\java\MES | 1 |
| SALES-ORDER-README.md | E:\java\MES | 3 |
| SALES-ORDER-QUICKSTART.md | E:\java\MES | 4 |
| SALES-ORDER-IMPLEMENTATION.md | E:\java\MES | 1 |
| SALES-ORDER-COMPLETION-REPORT.md | E:\java\MES | 3 |
| TIMEOUT-DIAGNOSIS.md | E:\java\MES | 1 |
| STARTUP-GUIDE.md | E:\java\MES | 2 |
| ROLLBACK-COMPLETE.md | E:\java\MES | 1 |

#### B. 前端文档（ERP项目）
| 文件 | 路径 | 包含9527次数 |
|------|------|--------------|
| README.zh-CN.md | E:\vue\ERP | 1 |
| README.md | E:\vue\ERP | 1 |
| README.ja.md | E:\vue\ERP | 1 |
| README.es.md | E:\vue\ERP | 1 |

---

## 🚀 执行更正步骤

### 步骤1：运行批量更正脚本
```powershell
cd E:\java\MES
.\batch-fix-port-numbers.ps1
```

脚本会：
1. 自动更正后端文档（A类）
2. 询问是否更正前端README（B类）
3. 显示处理结果统计

### 步骤2：验证更正结果
```powershell
# 在MES目录中搜索是否还有9527
cd E:\java\MES
Select-String -Path *.md -Pattern "9527"

# 在ERP目录中搜索是否还有9527
cd E:\vue\ERP
Select-String -Path README*.md -Pattern "9527"
```

### 步骤3：重启服务
```powershell
# 使用更新后的快速启动脚本
cd E:\java\MES
.\quick-start-services.ps1
```

---

## 🔍 验证端口是否生效

### 检查端口占用
```powershell
netstat -ano | findstr ":8080 :8090"
```

**预期输出**:
```
TCP    0.0.0.0:8080    0.0.0.0:0    LISTENING    12345
TCP    0.0.0.0:8090    0.0.0.0:0    LISTENING    67890
```

### 测试访问
```powershell
# 测试前端
curl http://localhost:8080

# 测试后端
curl http://localhost:8090/actuator/health
```

### 浏览器访问
1. 打开浏览器
2. 访问 `http://localhost:8080`
3. 按 Ctrl+Shift+R 强制刷新（清除缓存）
4. 登录系统测试

---

## 📊 更正前后对比

| 项目 | 更正前 | 更正后 | 状态 |
|------|--------|--------|------|
| 前端端口 | ~~9527~~ | **8080** | ✅ |
| 后端端口 | 8090 | 8090 | ✓ 不变 |
| 文档数量 | 12个文件 | 12个文件 | ⏳ 待更新 |
| 访问地址 | ~~localhost:9527~~ | **localhost:8080** | ✅ |

---

## ⚠️ 注意事项

1. **备份提示**: 
   脚本会直接修改文件，建议先备份重要文档
   ```powershell
   # 创建备份
   Copy-Item E:\java\MES\*.md E:\java\MES\backup\ -Recurse
   ```

2. **编码问题**: 
   脚本使用UTF-8编码保存，确保文档中文显示正常

3. **第三方依赖**: 
   不要修改 `node_modules`、`package-lock.json` 等文件

4. **浏览器缓存**: 
   更正后务必清除浏览器缓存或使用隐身模式测试

---

## 📝 更正记录表

| 日期 | 操作 | 文件数 | 替换次数 | 操作人 | 状态 |
|------|------|--------|----------|--------|------|
| 待填写 | 创建脚本 | - | - | AI助手 | ✅ |
| 待填写 | 执行更正 | - | - | - | ⏳ |
| 待填写 | 验证结果 | - | - | - | ⏳ |
| 待填写 | 重启服务 | - | - | - | ⏳ |

---

## ✅ 完成标志

更正完成后，应该满足以下条件：

- [ ] 所有后端文档中的9527已改为8080
- [ ] 所有前端README中的9527已改为8080
- [ ] 验证搜索无结果：`Select-String -Pattern "9527"`
- [ ] 服务启动在8080端口
- [ ] 浏览器能正常访问 http://localhost:8080
- [ ] 登录功能正常
- [ ] 报价单管理功能正常

---

## 🎯 快速命令

```powershell
# 一键执行所有步骤
cd E:\java\MES

# 1. 更正文档
.\batch-fix-port-numbers.ps1

# 2. 验证更正
Select-String -Path *.md -Pattern "9527"

# 3. 重启服务
.\quick-start-services.ps1

# 4. 检查端口
netstat -ano | findstr ":8080 :8090"
```

---

**当前状态**: ⏳ 等待执行批量更正脚本  
**下一步**: 运行 `batch-fix-port-numbers.ps1`
