# 🚀 送样管理功能 - 立即可用指南

## 📊 当前状态

### ✅ 已完成的工作
1. **前端文件**: `samples.vue` 已更新 (26KB，1000+行完整功能)
2. **后端代码**: 8个Java文件已创建，无编译错误
3. **数据库脚本**: `create-sample-tables.sql` 已就绪
4. **后端服务**: 正在重新编译和启动中

### ⚠️ **唯一剩余步骤: 创建数据库表**

这是功能无法使用的根本原因！

---

## 🎯 立即执行 - 创建数据库表

### 方法1: 使用数据库客户端（最简单） ⭐推荐

#### 如果您使用 **Navicat**:
1. 打开Navicat
2. 连接到数据库:
   - 主机: `ssdw8127.mysql.rds.aliyuncs.com`
   - 用户名: `david`
   - 密码: `dadazhengzheng@feng`
   - 数据库: `erp`
3. 点击"查询" → "新建查询"
4. 打开文件: `E:\java\MES\create-sample-tables.sql`
5. 点击"运行"按钮
6. 确认成功信息

#### 如果您使用 **MySQL Workbench**:
1. 打开MySQL Workbench
2. 创建新连接（或使用现有连接）
3. File → Open SQL Script → 选择 `E:\java\MES\create-sample-tables.sql`
4. 点击闪电图标（执行）
5. 确认成功

#### 如果您使用 **phpMyAdmin**:
1. 登录phpMyAdmin
2. 选择 `erp` 数据库
3. 点击"SQL"标签
4. 复制粘贴 `create-sample-tables.sql` 的内容
5. 点击"执行"

### 方法2: 使用在线工具
如果您的阿里云RDS有Web管理界面:
1. 登录阿里云控制台
2. 找到RDS实例: `ssdw8127.mysql.rds.aliyuncs.com`
3. 使用DMS（数据管理）打开SQL窗口
4. 粘贴SQL脚本并执行

### 方法3: 手动复制SQL（应急方案）
如果上述方法都不可用，我可以帮您将SQL分段执行。

---

## ✅ 执行SQL后的验证

在数据库中运行以下命令，应该看到4张表:

```sql
SHOW TABLES LIKE 'sample%';
```

预期输出:
```
sample_items
sample_logistics_records
sample_orders
sample_status_history
```

---

## 🧪 测试功能

### 步骤1: 确认后端已启动
打开浏览器访问:
```
http://localhost:8090/api/sales/samples
```

如果看到JSON数据（即使是空数组`[]`），说明成功！

### 步骤2: 测试前端
1. 访问: `http://localhost:8080`
2. 登录系统
3. 左侧菜单: **销售管理** → **送样管理**
4. 应该看到完整的页面:
   - 搜索栏（客户名称、状态、快递单号）
   - "新建送样"按钮
   - 数据表格（目前为空）

### 步骤3: 创建测试数据
1. 点击"新建送样"
2. 填写表单:
   ```
   客户名称: 测试客户
   联系人: 张三
   联系电话: 13800138000
   收货地址: 广东省深圳市南山区
   送样日期: 选择今天
   ```
3. 添加明细:
   ```
   物料名称: 测试产品
   型号: TP-001
   规格: 1200*600
   批次号: B20260105
   数量: 5
   单位: 卷
   备注: 测试样品
   ```
4. 点击"确定"

### 预期结果:
- ✅ 成功提示
- ✅ 生成编号: `SP20260105001`
- ✅ 列表显示新记录
- ✅ 可以编辑、删除、维护物流

---

## 🔍 故障排查

### 问题1: 数据库表创建失败
**症状**: SQL执行报错
**原因**: 可能表已存在或权限不足
**解决**:
```sql
-- 先删除旧表（如果存在）
DROP TABLE IF EXISTS sample_logistics_records;
DROP TABLE IF EXISTS sample_status_history;
DROP TABLE IF EXISTS sample_items;
DROP TABLE IF EXISTS sample_orders;
DROP FUNCTION IF EXISTS generate_sample_no;

-- 然后重新执行完整SQL脚本
```

### 问题2: API返回404
**症状**: 浏览器F12看到404错误
**原因**: 后端未重启或编译失败
**解决**:
1. 检查后端窗口是否显示"Started MesApplication"
2. 重启后端:
   ```powershell
   Get-Process -Name "java" | Stop-Process -Force
   cd E:\java\MES
   mvn spring-boot:run
   ```

### 问题3: 页面显示空白
**症状**: 送样管理页面不显示
**原因**: 前端未刷新
**解决**:
1. 强制刷新浏览器: `Ctrl + F5`
2. 清除缓存: `Ctrl + Shift + Delete`
3. 或重启前端服务

### 问题4: 快递查询无响应
**说明**: 这是正常的！当前使用模拟数据
**未来**: 需要对接快递100 API并配置key

---

## 📦 完整文件清单

### 数据库脚本 (1个)
```
E:\java\MES\create-sample-tables.sql    ← 需要执行此文件
```

### 后端文件 (8个)
```
E:\java\MES\src\main\java\com\fine\
├── modle\
│   ├── SampleOrder.java
│   ├── SampleItem.java
│   ├── SampleOrderDTO.java
│   └── LogisticsUpdateDTO.java
├── Dao\
│   ├── SampleOrderMapper.java
│   └── SampleItemMapper.java
├── service\
│   └── SampleOrderService.java
├── serviceIMPL\
│   └── SampleOrderServiceImpl.java
└── controller\
    └── SampleController.java
```

### 前端文件 (1个)
```
E:\vue\ERP\src\views\sales\samples.vue  ← 已更新
```

---

## 📞 下一步

**现在请执行**: 

1. ⭐ **打开数据库客户端**
2. ⭐ **执行 `create-sample-tables.sql`**
3. ⭐ **访问 `http://localhost:8080` 测试功能**

完成这3步后，送样管理功能即可完全使用！

---

**提示**: 如果您不确定如何执行SQL，请告诉我您使用的数据库管理工具，我可以提供详细步骤。
