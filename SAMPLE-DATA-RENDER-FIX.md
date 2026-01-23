# 样品管理 - 数据渲染问题修复

## 🐛 问题现象

API返回的数据：
```json
{
  "customer": "广州胶带有限公司",  // ❌ 字段名错误
  "sampleNo": "S-20251201-001",
  "sendDate": "2025-12-01",
  "status": "已送达",
  "remark": ""
}
```

**问题：**
1. ❌ 字段名是 `customer` 而不是 `customerName`
2. ❌ 缺少必需字段：`contactName`, `contactPhone`, `contactAddress` 等
3. ❌ 编号格式错误：`S-20251201-001` 应该是 `SP20260105001`

## 🔍 问题原因

**这不是我们代码返回的数据！**

可能的原因：
1. 数据库中有旧的测试数据（字段结构不对）
2. 表结构被修改过
3. 有其他接口返回了错误的数据

我们创建的代码返回的数据结构应该是：
```json
{
  "sampleNo": "SP20260105001",
  "customerId": 1,
  "customerName": "广州胶带有限公司",
  "contactName": "张经理",
  "contactPhone": "13800138000",
  "contactAddress": "广东省广州市天河区科技园路88号",
  "sendDate": "2026-01-05",
  "status": "待发货",
  "remark": "首次送样",
  "items": [...]
}
```

## ✅ 解决方案

### 步骤1: 清理旧数据

在Navicat或MySQL Workbench中执行：

```sql
USE erp;

-- 查看当前数据
SELECT * FROM sample_orders;

-- 删除所有旧数据（重要：会清空所有数据）
TRUNCATE TABLE sample_items;
TRUNCATE TABLE sample_status_history;
TRUNCATE TABLE sample_logistics_records;
TRUNCATE TABLE sample_orders;
```

### 步骤2: 插入正确的测试数据

执行脚本：`E:\java\MES\insert-sample-test-data.sql`

或手动执行：

```sql
-- 插入测试订单
INSERT INTO `sample_orders` (
  `sample_no`,
  `customer_id`,
  `customer_name`,
  `contact_name`,
  `contact_phone`,
  `contact_address`,
  `send_date`,
  `status`,
  `remark`
) VALUES 
(
  'SP20260105001',
  1,
  '广州胶带有限公司',
  '张经理',
  '13800138000',
  '广东省广州市天河区科技园路88号',
  '2026-01-05',
  '待发货',
  '首次送样'
);

-- 插入明细
INSERT INTO `sample_items` (
  `sample_no`,
  `material_code`,
  `material_name`,
  `specification`,
  `batch_no`,
  `quantity`,
  `unit`
) VALUES 
(
  'SP20260105001',
  'M001',
  '透明胶带',
  '48mm*50m',
  '20260105-001',
  10,
  '卷'
);
```

### 步骤3: 验证数据

```sql
-- 查看主表数据
SELECT 
  sample_no,
  customer_name,
  contact_name,
  contact_phone,
  send_date,
  status
FROM sample_orders;

-- 查看明细
SELECT * FROM sample_items;
```

### 步骤4: 重启后端并测试

```powershell
# 1. 停止后端
Get-Process -Name "java" | Stop-Process -Force

# 2. 启动后端
cd E:\java\MES
mvn spring-boot:run

# 3. 等待30秒后测试
```

### 步骤5: 刷新浏览器

1. 清除浏览器缓存（Ctrl + Shift + Delete）
2. 强制刷新（Ctrl + F5）
3. 访问送样管理页面

## 📋 预期结果

### ✅ 列表页面应该显示

| 送样编号 | 客户名称 | 联系人 | 联系电话 | 快递单号 | 状态 | 送样日期 |
|---------|---------|--------|----------|----------|------|----------|
| SP20260105001 | 广州胶带有限公司 | 张经理 | 13800138000 | - | 待发货 | 2026-01-05 |

### ✅ 点击详情应该显示

```
送样编号: SP20260105001
客户名称: 广州胶带有限公司
联系人: 张经理
联系电话: 13800138000
收货地址: 广东省广州市天河区科技园路88号
送样日期: 2026-01-05
状态: 待发货
备注: 首次送样

样品明细:
- 物料代码: M001
- 物料名称: 透明胶带
- 规格: 48mm*50m
- 批次号: 20260105-001
- 数量: 10 卷
```

## 🔍 如何诊断数据问题

### 1. 检查API返回的原始数据

在浏览器F12 → Network标签：
1. 刷新页面
2. 找到 `/api/sales/samples` 请求
3. 查看Response内容

**正确的响应结构：**
```json
{
  "code": 20000,
  "message": "查询成功",
  "data": {
    "records": [
      {
        "id": 1,
        "sampleNo": "SP20260105001",
        "customerId": 1,
        "customerName": "广州胶带有限公司",
        "contactName": "张经理",
        "contactPhone": "13800138000",
        "contactAddress": "广东省广州市天河区科技园路88号",
        "sendDate": "2026-01-05",
        "status": "待发货",
        "items": []
      }
    ],
    "total": 1,
    "size": 10,
    "current": 1
  }
}
```

### 2. 检查数据库表结构

```sql
DESC sample_orders;
```

应该看到这些字段：
- `id`
- `sample_no`
- `customer_id`
- `customer_name`  ← 注意是 customer_name 不是 customer
- `contact_name`
- `contact_phone`
- `contact_address`
- ... 等

### 3. 检查MyBatis映射

查看后端日志，看SQL语句：
```
SELECT id, sample_no, customer_id, customer_name, ... FROM sample_orders
```

## 💡 避免此类问题

### 1. 始终使用正确的字段名

数据库字段：`customer_name`  
Java实体：`customerName`  
前端：`customerName`

### 2. 不要手动修改数据库数据

如果要测试，使用SQL脚本插入完整数据，不要只插入部分字段。

### 3. 使用API测试工具

在插入数据后，先用Postman测试API：
```
GET http://localhost:8090/api/sales/samples
```

检查返回的数据结构是否正确。

## 📄 相关文件

**SQL脚本：**
- `E:\java\MES\create-sample-tables.sql` - 建表脚本
- `E:\java\MES\insert-sample-test-data.sql` - 测试数据脚本（新创建）

**代码文件：**
- `SampleOrder.java` - 实体类
- `SampleOrderDTO.java` - DTO类
- `SampleOrderMapper.java` - Mapper接口
- `SampleOrderServiceImpl.java` - 服务实现

---

## 🎯 立即执行

**最快的解决方法：**

1. **在数据库客户端中执行：**
   ```sql
   USE erp;
   SOURCE E:/java/MES/insert-sample-test-data.sql;
   ```

2. **重启后端：**
   ```powershell
   Get-Process -Name "java" | Stop-Process -Force
   cd E:\java\MES
   mvn spring-boot:run
   ```

3. **刷新浏览器测试**

**执行后应该能看到正确的数据了！** 🚀
