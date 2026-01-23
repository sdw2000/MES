# API快速参考指南

## 排程管理API端点清单

### 1. 排程主表接口

#### 获取排程列表
```
GET /api/production/schedule/list
参数：pageNum, pageSize, scheduleNo, scheduleType, status, startDate, endDate
```

#### 获取排程详情
```
GET /api/production/schedule/{id}
```

#### 创建排程
```
POST /api/production/schedule
Body: ProductionSchedule对象
```

#### 更新排程
```
PUT /api/production/schedule/{id}
Body: ProductionSchedule对象
```

#### 删除排程
```
DELETE /api/production/schedule/{id}
参数：operator
```

#### 确认排程
```
POST /api/production/schedule/{id}/confirm
参数：operator
```

#### 取消排程
```
POST /api/production/schedule/{id}/cancel
参数：operator
```

#### 智能排程
```
POST /api/production/schedule/auto
Body: {
  "details": [
    {"order_item_id": 123, "schedule_qty": 10},
    ...
  ],
  "scheduleDate": "2024-01-01",
  "operator": "admin"
}
```

---

### 2. 排程明细接口 ⭐ 新增

#### 获取排程明细列表（分页）
```
GET /api/production/schedule/{scheduleId}/items
参数：pageNum, pageSize, status, materialCode

响应示例：
{
  "code": 0,
  "msg": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "schedule_id": 123,
        "order_id": 456,
        "material_code": "ABC001",
        "width": 500,
        "length": 1000,
        "rolls": 10,
        "status": "pending"
      },
      ...
    ],
    "total": 100,
    "pageNum": 1,
    "pageSize": 10,
    "pages": 10,
    "hasNextPage": true
  }
}
```

---

### 3. 涂布计划接口 ⭐ 改进

#### 获取涂布任务列表
```
GET /api/production/schedule/coating/list
参数：
  - pageNum (默认1)
  - pageSize (默认10)
  - scheduleId (可选，新增)
  - planDate (可选)
  - status (可选)
  - equipmentId (可选)
  - materialCode (可选，新增)

响应示例：
{
  "code": 0,
  "msg": "success",
  "data": {
    "list": [
      {
        "id": 1,
        "schedule_id": 123,
        "task_no": "CT-20240101-001",
        "material_code": "ABC001",
        "plan_length": 15.0,      // 涂布长度(m)
        "plan_sqm": 7.5,           // 涂布面积(m²)
        "jumbo_width": 500,        // 母卷宽度(mm)
        "coating_speed": 20,       // 涂布速度(m/分钟)
        "plan_duration": 45,       // 计划时长(分钟)
        "status": "pending"
      },
      ...
    ],
    "total": 50,
    "pageNum": 1,
    "pageSize": 10,
    "pages": 5,
    "hasNextPage": true
  }
}
```

#### 添加涂布任务
```
POST /api/production/schedule/coating
Body: ScheduleCoating对象
```

#### 更新涂布任务
```
PUT /api/production/schedule/coating/{id}
Body: ScheduleCoating对象
```

#### 开始涂布任务
```
POST /api/production/schedule/coating/{id}/start
参数：operator
```

#### 完成涂布任务
```
POST /api/production/schedule/coating/{id}/complete
参数：outputBatchNo, operator
```

---

### 4. 复卷计划接口 ⭐ 改进

#### 获取复卷任务列表
```
GET /api/production/schedule/rewinding/list
参数：
  - pageNum (默认1)
  - pageSize (默认10)
  - scheduleId (可选，新增)
  - planDate (可选)
  - status (可选)
  - equipmentId (可选)
  - materialCode (可选，新增)

返回值包含：list, total, pageNum, pageSize, pages, hasNextPage
```

#### 添加复卷任务
```
POST /api/production/schedule/rewinding
Body: ScheduleRewinding对象
```

#### 更新复卷任务
```
PUT /api/production/schedule/rewinding/{id}
Body: ScheduleRewinding对象
```

---

### 5. 分切计划接口 ⭐ 改进

#### 获取分切任务列表
```
GET /api/production/schedule/slitting/list
参数：
  - pageNum (默认1)
  - pageSize (默认10)
  - scheduleId (可选，新增)
  - planDate (可选)
  - status (可选)
  - equipmentId (可选)
  - materialCode (可选，新增)

返回值包含：list, total, pageNum, pageSize, pages, hasNextPage
```

#### 添加分切任务
```
POST /api/production/schedule/slitting
Body: ScheduleSlitting对象
```

#### 更新分切任务
```
PUT /api/production/schedule/slitting/{id}
Body: ScheduleSlitting对象
```

---

### 6. 分条计划接口 ⭐ 改进

#### 获取分条任务列表
```
GET /api/production/schedule/stripping/list
参数：
  - pageNum (默认1)
  - pageSize (默认10)
  - scheduleId (可选，新增)
  - planDate (可选)
  - status (可选)
  - equipmentId (可选)
  - materialCode (可选，新增)

返回值包含：list, total, pageNum, pageSize, pages, hasNextPage
```

#### 添加分条任务
```
POST /api/production/schedule/stripping
Body: ScheduleStripping对象
```

#### 更新分条任务
```
PUT /api/production/schedule/stripping/{id}
Body: ScheduleStripping对象
```

---

### 7. 其他接口

#### 获取待排程的订单明细
```
GET /api/production/schedule/pending-orders
参数：pageNum, pageSize, customerLevel, materialCode
```

#### 获取排程统计数据
```
GET /api/production/schedule/statistics
```

#### 获取报工记录列表
```
GET /api/production/schedule/report/list
参数：pageNum, pageSize, planDate, status
```

#### 获取今日产量统计
```
GET /api/production/schedule/report/today-output
```

#### 获取设备状态看板
```
GET /api/production/schedule/board/equipment
参数：planDate
```

#### 获取生产进度看板
```
GET /api/production/schedule/board/progress
```

---

## 涂布面积计算说明

### 计算流程

**输入数据**：订单明细列表
```
订单1：宽度500mm, 长度1000mm, 数量10卷
订单2：宽度500mm, 长度1000mm, 数量5卷
```

**计算过程**：

1️⃣ **计算订单总面积**
```
订单面积 = Σ(宽 × 长 × 数量) / 1,000,000
         = (500×1000×10 + 500×1000×5) / 1,000,000
         = 7,500,000 / 1,000,000
         = 7.5 m²
```

2️⃣ **计算涂布长度**
```
涂布长度(mm) = Σ(长 × 数量)
            = (1000×10 + 1000×5)
            = 15,000 mm
涂布长度(m) = 15,000 / 1000 = 15 m
```

3️⃣ **确定涂布材料宽度**
```
涂布材料宽度 = MAX(订单宽度)
           = 500 mm
```

4️⃣ **计算涂布面积**
```
涂布面积 = 涂布长度(m) × 宽度(mm) / 1,000,000
         = 15 × 500 / 1,000,000
         = 7,500 / 1,000,000
         = 0.0075 m²
```

5️⃣ **计算涂布时间**
```
涂布速度 = 20 m/分钟（默认值）
涂布时间 = 涂布长度 / 速度
         = 15 / 20
         = 0.75 分钟
         = 1 分钟（四舍五入）
```

### 关键区别

| 指标 | 订单面积 | 涂布面积 |
|------|--------|--------|
| 定义 | 原始订单的总面积 | 材料宽度 × 涂布长度 |
| 计算 | 宽 × 长 × 数量 | 宽 × (Σ长×数量) |
| 用途 | 用于销售统计 | 用于生产计划 |
| 示例 | 7.5 m² | 0.0075 m² |

---

## 常见问题

### Q1: 如何获取某个排程的所有明细？
```bash
GET /api/production/schedule/123/items?pageNum=1&pageSize=50
```

### Q2: 如何按状态筛选涂布任务？
```bash
GET /api/production/schedule/coating/list?status=pending&pageNum=1&pageSize=10
```

### Q3: 涂布速度如何设置？
答：涂布速度从订单参数或设备参数获取，默认为20m/分钟。

### Q4: 分页大小有限制吗？
答：建议每页10-50条记录，过大会影响性能。

### Q5: 如何获取下一页数据？
```bash
# 第二页
GET /api/production/schedule/123/items?pageNum=2&pageSize=10

# 检查 hasNextPage 字段判断是否有下一页
```

---

## 调试技巧

### 1. 启用详细日志
```properties
# application.properties
logging.level.com.fine.serviceIMPL.production.ProductionScheduleServiceImpl=DEBUG
logging.level.com.fine.Dao.production=DEBUG
```

### 2. 常见错误排查

**问题**：获取排程明细返回空列表
- 检查scheduleId是否存在
- 检查是否有matching的过滤条件

**问题**：涂布时间计算不正确
- 检查涂布速度参数
- 检查涂布长度计算是否正确

**问题**：分页参数无效
- 确保pageNum >= 1
- 确保pageSize > 0

### 3. 测试API的cURL命令

```bash
# 获取排程明细
curl -X GET "http://localhost:8080/api/production/schedule/123/items?pageNum=1&pageSize=10" \
  -H "Authorization: Bearer YOUR_TOKEN"

# 获取涂布任务
curl -X GET "http://localhost:8080/api/production/schedule/coating/list?scheduleId=123&pageNum=1" \
  -H "Authorization: Bearer YOUR_TOKEN"

# 获取分切任务（按日期和状态筛选）
curl -X GET "http://localhost:8080/api/production/schedule/slitting/list?planDate=2024-01-01&status=pending&pageNum=1" \
  -H "Authorization: Bearer YOUR_TOKEN"
```

---

**更新时间**：2024年  
**版本**：1.0  
**状态**：✅ 生产环境就绪
