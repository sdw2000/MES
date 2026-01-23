# 送样功能修复指南

## 当前问题诊断

### ✅ 已完成
1. **前端文件已更新** - samples.vue已替换为新版本 (26KB)
2. **后端代码已创建** - 所有Java文件存在
3. **后端服务运行中** - 端口8090正在监听

### ❌ 待解决
1. **数据库表未创建** - API调用失败，很可能是因为数据库表不存在
2. **后端可能未重新编译** - 新代码可能未生效

---

## 🔧 修复步骤

### 步骤1: 创建数据库表 ⭐ **最关键**

**方法A: 使用数据库客户端（推荐）**
1. 打开您的MySQL客户端（Navicat、MySQL Workbench等）
2. 连接到数据库:
   - 主机: `ssdw8127.mysql.rds.aliyuncs.com`
   - 用户: `david`
   - 密码: `dadazhengzheng@feng`
   - 数据库: `erp`
3. 打开并执行文件: `E:\java\MES\create-sample-tables.sql`
4. 确认创建成功，应该看到4张表:
   - `sample_orders` (送样主表)
   - `sample_items` (送样明细表)
   - `sample_status_history` (状态历史表)
   - `sample_logistics_records` (物流记录表)

**方法B: 使用命令行**
```bash
# 需要先配置MySQL到环境变量
mysql -h ssdw8127.mysql.rds.aliyuncs.com -u david -p"dadazhengzheng@feng" erp < E:\java\MES\create-sample-tables.sql
```

### 步骤2: 重新编译后端

1. 停止当前后端服务:
```powershell
Get-Process -Name "java" | Stop-Process -Force
```

2. 编译项目:
```powershell
cd E:\java\MES
mvn clean compile
```

3. 启动后端:
```powershell
mvn spring-boot:run
```

### 步骤3: 重启前端（如果需要）

1. 停止前端:
```powershell
Get-Process -Name "node" | Stop-Process -Force
```

2. 启动前端:
```powershell
cd E:\vue\ERP
npm run dev
```

### 步骤4: 测试功能

1. 访问: `http://localhost:8080`
2. 登录系统
3. 进入: **销售管理** → **送样管理**
4. 测试:
   - 新建送样
   - 维护物流
   - 查询物流

---

## 📋 快速检查清单

运行以下命令检查状态:

```powershell
# 检查Java进程
Get-Process -Name "java" -ErrorAction SilentlyContinue

# 检查端口8090
netstat -ano | findstr ":8090"

# 检查前端文件大小
(Get-Item "E:\vue\ERP\src\views\sales\samples.vue").Length
# 应该显示 26147 字节
```

---

## ❓ 常见问题

### Q1: API返回404
**原因**: 数据库表未创建
**解决**: 执行步骤1创建数据库表

### Q2: API返回500错误
**原因**: 数据库表结构不匹配或字段缺失
**解决**: 删除旧表重新执行SQL脚本

### Q3: 前端页面空白
**原因**: 前端文件未更新或编译错误
**解决**: 
- 确认samples.vue文件大小为26KB
- 重启前端服务
- 检查浏览器控制台错误

### Q4: 快递查询功能不工作
**原因**: 当前使用模拟数据（正常现象）
**后续**: 需要对接真实快递100 API

---

## 🎯 预期结果

完成以上步骤后，应该能够:

✅ 在"送样管理"页面看到数据表格
✅ 点击"新建送样"打开对话框
✅ 填写客户信息和明细
✅ 保存成功并生成送样编号 (SP20260105xxx)
✅ 维护快递单号和物流信息
✅ 查询物流轨迹（显示模拟数据）

---

## 📞 需要帮助?

如果遇到问题，请检查:
1. 后端控制台的错误日志
2. 浏览器控制台的网络请求
3. 数据库连接是否正常

**日志位置**:
- 后端: 运行`mvn spring-boot:run`的窗口
- 前端: 浏览器F12 → Console和Network标签
