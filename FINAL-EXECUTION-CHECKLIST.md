# 最终执行清单

**创建日期**: 2026-01-05  
**项目**: 报价单管理系统优化

---

## 📌 需要执行的步骤

### 步骤 1: 更新数据库 ⏳
```powershell
cd E:\java\MES
.\update-quotation-table-structure.ps1
```

**预期结果**:
- ✓ 连接到数据库成功
- ✓ 删除 `quantity` 字段成功
- ✓ 删除 `sqm` 字段成功
- ✓ 删除 `amount` 字段成功

**验证**:
```sql
DESC quotation_items;
-- 确认这3个字段已不存在
```

---

### 步骤 2: 更正文档端口号 ⏳
```powershell
cd E:\java\MES
.\batch-fix-port-numbers.ps1
```

**说明**:
- 脚本会自动将所有文档中的 9527 改为 8080
- 会询问是否处理前端 README（建议选择 Y）

**验证**:
```powershell
# 检查是否还有 9527
Select-String -Path E:\java\MES\*.md -Pattern "9527"
Select-String -Path E:\vue\ERP\README*.md -Pattern "9527"
```

---

### 步骤 3: 重新编译后端 ⏳
```powershell
cd E:\java\MES
mvn clean compile
```

**预期结果**:
- ✓ 编译成功
- ✓ 无错误信息

**如果已有后端服务运行**:
1. 停止当前服务（Ctrl+C）
2. 重新编译
3. 重新启动

---

### 步骤 4: 启动服务 ⏳
```powershell
cd E:\java\MES
.\quick-start-services.ps1
```

**说明**:
- 会自动启动后端（8090端口）和前端（8080端口）
- 每个服务会在新窗口中运行

**验证端口**:
```powershell
netstat -ano | findstr ":8080 :8090"
```

**预期输出**:
```
TCP    0.0.0.0:8080    LISTENING    xxxx
TCP    0.0.0.0:8090    LISTENING    xxxx
```

---

### 步骤 5: 测试功能 ⏳

#### 5.1 访问系统
1. 打开浏览器
2. 访问: `http://localhost:8080`
3. 按 `Ctrl+Shift+R` 强制刷新

#### 5.2 检查导航栏
- [ ] 左侧显示 "首页" 而不是 "方恩电子ERP"
- [ ] 侧边栏"报价单管理"显示 clipboard 图标

#### 5.3 测试报价单功能
1. 登录系统（admin/123456）
2. 点击"报价单管理"
3. 点击"新增报价单"

**检查项**:
- [ ] "新增明细行"按钮在右上角
- [ ] 明细表格没有"数量"、"平米数"、"金额"列
- [ ] 明细表格有"备注"列
- [ ] 表格底部没有"总面积"和"总金额"显示

4. 填写客户名称
5. 点击"新增明细行"
6. 填写产品、型号、规格、备注
7. 点击"保存"

**检查项**:
- [ ] 保存成功
- [ ] 列表中能看到新增的报价单
- [ ] 点击"查看"能看到明细信息

---

## 🔍 问题排查

### 问题 1: 数据库更新失败
**可能原因**:
- 数据库服务未启动
- 连接信息错误
- 表中有数据依赖

**解决方法**:
```sql
-- 手动执行SQL
USE erp_system;
ALTER TABLE quotation_items 
  DROP COLUMN quantity,
  DROP COLUMN sqm,
  DROP COLUMN amount;
```

### 问题 2: 前端显示旧界面
**解决方法**:
1. 清除浏览器缓存
2. 按 `Ctrl+Shift+R` 强制刷新
3. 使用无痕模式测试
4. 确认前端服务已重启

### 问题 3: 后端编译错误
**解决方法**:
```powershell
# 清理后重新编译
cd E:\java\MES
mvn clean
mvn compile
```

### 问题 4: 端口被占用
**解决方法**:
```powershell
# 查找占用端口的进程
netstat -ano | findstr ":8080"
netstat -ano | findstr ":8090"

# 结束进程（替换 <PID> 为实际进程ID）
taskkill /F /PID <PID>
```

---

## 📊 完成验证表

| 检查项 | 状态 | 备注 |
|--------|------|------|
| 数据库更新成功 | ⏳ | |
| 文档端口号已更正 | ⏳ | |
| 后端编译成功 | ⏳ | |
| 后端服务启动（8090） | ⏳ | |
| 前端服务启动（8080） | ⏳ | |
| 导航栏显示"首页" | ⏳ | |
| 侧边栏图标为 clipboard | ⏳ | |
| 新增按钮在右边 | ⏳ | |
| 无数量/平米数/金额列 | ⏳ | |
| 有备注列 | ⏳ | |
| 无总面积/总金额 | ⏳ | |
| 保存报价单成功 | ⏳ | |
| 查看报价单成功 | ⏳ | |

---

## 🎯 快速命令总览

```powershell
# 1. 更新数据库
cd E:\java\MES
.\update-quotation-table-structure.ps1

# 2. 更正文档
.\batch-fix-port-numbers.ps1

# 3. 重启服务
.\quick-start-services.ps1

# 4. 验证端口
netstat -ano | findstr ":8080 :8090"

# 5. 访问系统
# 浏览器打开: http://localhost:8080
```

---

## 📝 相关文档

| 文档 | 说明 |
|------|------|
| `QUOTATION-SIMPLIFICATION-REPORT.md` | 报价单简化详细报告 |
| `PORT-CORRECTION-SUMMARY.md` | 端口号更正总结 |
| `PORT-VERIFICATION-CHECKLIST.md` | 端口验证清单 |
| `ICON-SELECTION-GUIDE.md` | 图标选择指南 |
| `quick-start-services.ps1` | 快速启动脚本 |

---

## ✅ 全部完成标志

当所有检查项都打勾后，说明优化工作全部完成！

**最终访问地址**: http://localhost:8080  
**登录账号**: admin / 123456

---

**祝您使用愉快！** 🎉
