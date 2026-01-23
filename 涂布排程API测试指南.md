# 涂布排程API测试指南

## 🚀 快速测试步骤

### 前置条件
✅ 后端服务运行在 http://localhost:8090
✅ 数据库已执行修复脚本
✅ sales_order_items 表有待排程订单数据

---

## 📝 测试用例

### 1. 查看待排程订单（按物料汇总）

**请求：**
```http
GET http://localhost:8090/api/production/pending-orders/group-by-material
```

**预期返回：**
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "materialCode": "1011-R02-1507-B01-0400",
      "materialName": "22μm蓝色PET胶带",
      "orderQty": 3,
      "pendingQty": 1500,
      "pendingArea": 85.50,
      "deliveryDate": "2026-01-20",
      "customer": "客户A, 客户B",
      "minProductionArea": 50.00,
      "recommendedWidth": 1040
    }
  ]
}
```

**验证点：**
- ✅ pendingQty = quantity - scheduled_qty
- ✅ pendingArea = pendingQty × width × length / 1000000
- ✅ 显示MOQ和推荐宽度

---

### 2. 查看特定物料的待排程订单

**请求：**
```http
GET http://localhost:8090/api/production/pending-orders/by-material/1011-R02-1507-B01-0400
```

**预期返回：**
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "orderItemId": 101,
      "orderId": 50,
      "orderNo": "SO-2026001",
      "materialCode": "1011-R02-1507-B01-0400",
      "materialName": "22μm蓝色PET胶带",
      "customer": "客户A",
      "orderQty": 500,
      "scheduledQty": 0,
      "pendingQty": 500,
      "width": 300,
      "length": 200,
      "pendingArea": 30.00,
      "deliveryDate": "2026-01-20",
      "minProductionArea": 50.00,
      "recommendedWidth": 1040
    },
    {
      "orderItemId": 102,
      "orderId": 51,
      "orderNo": "SO-2026002",
      ...
    }
  ]
}
```

**验证点：**
- ✅ 显示每个订单的详细信息
- ✅ 按交货日期排序

---

### 3. 自动涂布排程（核心功能）

**请求：**
```http
POST http://localhost:8090/api/production/pending-orders/auto-schedule-coating
Content-Type: application/json

{
  "materialCode": "1011-R02-1507-B01-0400",
  "filmWidth": 1040,
  "operator": "admin"
}
```

**预期返回：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskCount": 3,
    "message": "自动排程成功，共创建3个涂布任务",
    "tasks": [
      {
        "id": 1,
        "taskNo": "CT-20260115-001",
        "orderItemId": 101,
        "orderId": 50,
        "orderNo": "SO-2026001",
        "materialCode": "1011-R02-1507-B01-0400",
        "materialName": "22μm蓝色PET胶带",
        "planSqm": 30.00,
        "filmWidth": 1040,
        "jumboWidth": 1040,
        "planDuration": 45,
        "status": "pending",
        "createBy": "admin",
        "createTime": "2026-01-15 14:30:00"
      },
      {
        "id": 2,
        "taskNo": "CT-20260115-002",
        "orderItemId": 102,
        ...
      },
      {
        "id": 3,
        "taskNo": "CT-20260115-003",
        "orderItemId": 103,
        ...
      }
    ]
  }
}
```

**验证点：**
- ✅ 为该物料的所有待排程订单创建涂布任务
- ✅ 每个任务有唯一的taskNo
- ✅ 正确关联orderItemId, orderId, orderNo
- ✅ materialCode和materialName从订单获取
- ✅ planSqm = pending_area
- ✅ filmWidth = 请求参数
- ✅ 状态为pending

**后续验证：**
```sql
-- 1. 查看涂布任务
SELECT * FROM schedule_coating 
WHERE task_no LIKE 'CT-20260115%' 
ORDER BY id DESC;

-- 2. 验证订单已排程数量更新
SELECT id, material_code, quantity, scheduled_qty, 
       (quantity - scheduled_qty) AS pending_qty
FROM sales_order_items
WHERE material_code = '1011-R02-1507-B01-0400';
```

**预期SQL结果：**
- schedule_coating 表新增3条记录
- 每条记录的 order_item_id 不为空
- sales_order_items 的 scheduled_qty 字段已增加

---

### 4. 批量排程（选择性排程）

**请求：**
```http
POST http://localhost:8090/api/production/pending-orders/batch-schedule-coating
Content-Type: application/json

{
  "orderItemIds": [101, 102],
  "filmWidth": 1040,
  "planDate": "2026-01-16",
  "operator": "admin"
}
```

**预期返回：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskCount": 2,
    "message": "批量排程成功，共创建2个涂布任务",
    "tasks": [...]
  }
}
```

**验证点：**
- ✅ 只为指定的订单明细创建任务
- ✅ 其他逻辑同自动排程

---

### 5. 分页查询待排程订单

**请求：**
```http
GET http://localhost:8090/api/production/pending-orders/list?pageNum=1&pageSize=10&materialCode=1011-R02
```

**预期返回：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [...],
    "total": 15,
    "size": 10,
    "current": 1,
    "pages": 2
  }
}
```

---

## 🛠️ 物料配置管理API

### 6. 创建物料配置

**请求：**
```http
POST http://localhost:8090/api/material-config/create
Content-Type: application/json

{
  "materialCode": "1011-R02-1507-B01-0400",
  "materialName": "22μm蓝色PET胶带",
  "materialType": "coating",
  "minProductionArea": 50.00,
  "setupTime": 30,
  "unitTime": 0.5,
  "recommendedWidth": 1040,
  "recommendedThickness": 22,
  "lossRate": 5.00,
  "qualifiedRate": 95.00,
  "isActive": 1,
  "remark": "常规涂布产品"
}
```

**预期返回：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "materialCode": "1011-R02-1507-B01-0400",
    ...
  }
}
```

---

### 7. 查询物料配置

**请求：**
```http
GET http://localhost:8090/api/material-config/by-code/1011-R02-1507-B01-0400
```

**预期返回：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "materialCode": "1011-R02-1507-B01-0400",
    "minProductionArea": 50.00,
    "setupTime": 30,
    "unitTime": 0.5,
    ...
  }
}
```

---

### 8. 物料配置列表

**请求：**
```http
GET http://localhost:8090/api/material-config/list?pageNum=1&pageSize=10&materialType=coating&isActive=1
```

---

### 9. 更新物料配置

**请求：**
```http
PUT http://localhost:8090/api/material-config/update
Content-Type: application/json

{
  "id": 1,
  "minProductionArea": 60.00,
  "setupTime": 35
}
```

---

### 10. 批量导入物料配置

**请求：**
```http
POST http://localhost:8090/api/material-config/batch-import
Content-Type: application/json

[
  {
    "materialCode": "1011-R02-1507-B01-0400",
    "minProductionArea": 50.00,
    ...
  },
  {
    "materialCode": "1011-R02-1507-B01-0600",
    "minProductionArea": 60.00,
    ...
  }
]
```

---

## 🔍 调试技巧

### 查看后端日志
```bash
# 在后端控制台查看输出
=== 开始自动涂布排程 ===
物料编号: 1011-R02-1507-B01-0400
薄膜宽度: 1040mm
找到 3 个待排程订单
总待排程面积: 85.5㎡
创建涂布任务: CT-20260115-001, 订单: SO-2026001
创建涂布任务: CT-20260115-002, 订单: SO-2026002
创建涂布任务: CT-20260115-003, 订单: SO-2026003
=== 自动涂布排程完成，共创建 3 个任务 ===
```

### 数据库验证查询

**1. 查看待排程订单数量**
```sql
SELECT 
    material_code,
    COUNT(*) AS order_count,
    SUM(quantity - IFNULL(scheduled_qty, 0)) AS total_pending_qty,
    SUM((quantity - IFNULL(scheduled_qty, 0)) * width * length / 1000000) AS total_pending_area
FROM sales_order_items
WHERE quantity > IFNULL(scheduled_qty, 0)
GROUP BY material_code;
```

**2. 查看涂布任务关联情况**
```sql
SELECT 
    sc.task_no,
    sc.order_no,
    sc.order_item_id,
    sc.material_code,
    sc.material_name,
    sc.plan_sqm,
    soi.quantity,
    soi.scheduled_qty
FROM schedule_coating sc
LEFT JOIN sales_order_items soi ON sc.order_item_id = soi.id
ORDER BY sc.create_time DESC
LIMIT 10;
```

**3. 检查物料配置**
```sql
SELECT 
    material_code,
    material_name,
    min_production_area AS MOQ,
    setup_time,
    unit_time,
    recommended_width,
    is_active
FROM material_production_config
WHERE is_active = 1;
```

---

## ⚠️ 常见问题排查

### 问题1：返回空数据
**原因：** 没有待排程订单
**检查：**
```sql
-- 确认有未排程的订单
SELECT * FROM sales_order_items 
WHERE quantity > IFNULL(scheduled_qty, 0)
LIMIT 5;
```

### 问题2：自动排程失败
**原因：** 物料编号不存在或订单已全部排程
**检查：**
1. 物料编号是否正确
2. 是否有 pending_qty > 0 的订单
3. 后端日志输出

### 问题3：任务创建了但没有订单信息
**原因：** schedule_coating 表缺少 order_item_id 字段
**解决：** 执行数据库修复脚本

### 问题4：MOQ校验不生效
**原因：** material_production_config 表没有该物料配置
**解决：** 先创建物料配置

---

## 🎯 完整测试流程

```bash
# 1. 准备测试数据（SQL）
INSERT INTO material_production_config ...

# 2. 查看待排程订单
GET /api/production/pending-orders/group-by-material

# 3. 执行自动排程
POST /api/production/pending-orders/auto-schedule-coating
{
  "materialCode": "xxx",
  "filmWidth": 1040,
  "operator": "admin"
}

# 4. 验证结果
GET /api/production/schedule/coating/list

# 5. 检查数据库
SELECT * FROM schedule_coating ORDER BY id DESC LIMIT 5;
SELECT * FROM sales_order_items WHERE scheduled_qty > 0;
```

---

## 📧 问题反馈

如遇到问题，请提供：
1. API请求完整内容
2. 返回结果
3. 后端日志输出
4. 数据库查询结果截图

---

**测试版本**: v1.0  
**最后更新**: 2026-01-15
