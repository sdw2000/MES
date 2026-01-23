# 送样功能 - 立即开始使用

## 🎯 快速部署（3步）

### 步骤1: 创建数据库表

```powershell
# 在MySQL中执行
mysql -u root -p
```

然后在MySQL命令行中：

```sql
USE erp_system;
SOURCE E:/java/MES/create-sample-tables.sql;

-- 验证表是否创建成功
SHOW TABLES LIKE 'sample%';

-- 应该看到4张表：
-- sample_orders
-- sample_items  
-- sample_status_history
-- sample_logistics_records
```

### 步骤2: 替换前端文件

```powershell
# 备份原文件
Copy-Item E:\vue\ERP\src\views\sales\samples.vue E:\vue\ERP\src\views\sales\samples.vue.backup -ErrorAction SilentlyContinue

# 使用新文件
Copy-Item E:\vue\ERP\src\views\sales\samples_new.vue E:\vue\ERP\src\views\sales\samples.vue -Force

Write-Host "✓ 前端文件已更新" -ForegroundColor Green
```

### 步骤3: 重启服务

#### 如果服务正在运行：
```powershell
# 停止Java进程（后端）
Get-Process -Name "java" -ErrorAction SilentlyContinue | Stop-Process -Force

# 停止Node进程（前端）
Get-Process -Name "node" -ErrorAction SilentlyContinue | Stop-Process -Force

Start-Sleep -Seconds 2
```

#### 启动后端：
```powershell
cd E:\java\MES
Start-Process powershell -ArgumentList "-NoExit", "-Command", "mvn spring-boot:run"
```

#### 启动前端（等待5秒）：
```powershell
Start-Sleep -Seconds 5
cd E:\vue\ERP
Start-Process powershell -ArgumentList "-NoExit", "-Command", "npm run dev"
```

---

## ✅ 验证部署

### 1. 检查服务端口
```powershell
netstat -ano | findstr ":8080 :8090"
```

应该看到：
```
TCP    0.0.0.0:8080    LISTENING    xxxx
TCP    0.0.0.0:8090    LISTENING    xxxx
```

### 2. 访问系统
1. 打开浏览器
2. 访问：`http://localhost:8080`
3. 登录：admin / 123456
4. 点击：**销售管理 → 送样管理**

### 3. 测试功能
- ✅ 点击"新建送样"
- ✅ 选择客户（如果有客户数据）
- ✅ 填写联系人信息
- ✅ 点击"新增明细"
- ✅ 填写：物料代码、名称、型号、规格、**批次号**、数量、备注
- ✅ 保存
- ✅ 点击"物流"按钮
- ✅ 选择快递公司（或手动输入）
- ✅ 输入快递单号
- ✅ 保存

---

## 📊 功能清单

### 核心功能
- ✅ 关联客户（自动填充联系人）
- ✅ 批次号在明细表格中
- ✅ 备注在明细行（不单独占行）
- ✅ 快递公司可选可输
- ✅ 快递单号自动查询物流
- ✅ 状态自动更新

### 明细表格列
```
物料代码 | 物料名称 | 型号 | 规格 | 批次号 | 数量 | 单位 | 备注 | 操作
```

### 状态流转
```
待发货 → 已发货 → 运输中 → 已签收
```

---

## 🔍 故障排查

### 问题1: 表已存在错误
```sql
-- 删除旧表
DROP TABLE IF EXISTS sample_logistics_records;
DROP TABLE IF EXISTS sample_status_history;
DROP TABLE IF EXISTS sample_items;
DROP TABLE IF EXISTS sample_orders;

-- 重新执行建表脚本
SOURCE E:/java/MES/create-sample-tables.sql;
```

### 问题2: 前端页面空白
```powershell
# 检查文件是否替换成功
Get-Content E:\vue\ERP\src\views\sales\samples.vue | Select-Object -First 5

# 应该看到：
# <template>
#   <div class="sales-samples">
#     <el-card>
```

### 问题3: 接口404错误
- 确认后端服务已启动
- 检查 `SampleController.java` 是否存在
- 查看后端控制台是否有编译错误

### 问题4: 没有客户数据
```sql
-- 临时插入测试客户
INSERT INTO customers (id, name, contact, phone, address) 
VALUES (1, '测试客户', '张三', '13800138000', '广东省深圳市');
```

---

## 📝 快速命令合集

### 一次性部署（复制粘贴执行）

```powershell
# 1. 替换前端文件
Copy-Item E:\vue\ERP\src\views\sales\samples_new.vue E:\vue\ERP\src\views\sales\samples.vue -Force

# 2. 停止现有服务
Get-Process -Name "java" -ErrorAction SilentlyContinue | Stop-Process -Force
Get-Process -Name "node" -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 2

# 3. 启动后端
cd E:\java\MES
Start-Process powershell -ArgumentList "-NoExit", "-Command", "mvn spring-boot:run"

# 4. 等待5秒后启动前端
Start-Sleep -Seconds 5
cd E:\vue\ERP
Start-Process powershell -ArgumentList "-NoExit", "-Command", "npm run dev"

Write-Host ""
Write-Host "✓ 服务启动中，请等待30-60秒..." -ForegroundColor Green
Write-Host "✓ 然后访问: http://localhost:8080" -ForegroundColor Cyan
```

---

## 📚 相关文档

- **详细设计**: `SAMPLE-FEATURE-DESIGN.md`
- **实现报告**: `SAMPLE-FEATURE-IMPLEMENTATION.md`
- **快速参考**: `SAMPLE-QUICK-REFERENCE.md`

---

## 🎉 完成！

**现在就开始使用送样功能吧！**

1. 执行上面的数据库脚本
2. 运行快速命令合集
3. 访问 http://localhost:8080
4. 进入 销售管理 → 送样管理

**有问题随时告诉我！** 🚀
