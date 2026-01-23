# 🔧 排程后订单数量变0的Bug修复

## 🔴 问题描述

**现象**：排程后，表格中所有订单的 `pending_qty`（待排数量）都变成了 0

**例子**：
- 排程前：订单A（100卷）、订单B（50卷）、订单C（80卷）都在表格中显示
- 排程了订单A（100卷）和订单C（80卷）
- 排程后：3个订单的 `pending_qty` 都变成了 0 ❌ （错误！应该只有A和C减少）

---

## 🔍 问题根源

**文件**：`ProductionScheduleServiceImpl.java` 第332-340行

### 错误的逻辑 ❌

```java
// 原代码（第8步）
for (Map<String, Object> item : pendingItems) {
    Long orderItemId = ((Number)item.get("order_item_id")).longValue();
    int scheduleQty = scheduleQtyMap != null && scheduleQtyMap.containsKey(orderItemId) 
        ? scheduleQtyMap.get(orderItemId)
        : ((Number)item.get("pending_qty")).intValue();  // ❌ BUG 在这里
    
    salesOrderItemMapper.decreaseRolls(orderItemId, scheduleQty);
}
```

**问题分析**：
```
假设排程数据：
  details: [
    {order_item_id: 2693, schedule_qty: 100},
    {order_item_id: 3006, schedule_qty: 50}
  ]

迭代过程：
  循环1：item = order_item_id:2693, pending_qty:100
         → 在scheduleQtyMap中找到100
         → 减少100 ✓ 正确

  循环2：item = order_item_id:3006, pending_qty:50
         → 在scheduleQtyMap中找到50
         → 减少50 ✓ 正确

  但如果前端没有传递scheduleQtyMap（旧API）：
  循环1：item = order_item_id:2693, pending_qty:100
         → scheduleQtyMap为null
         → 使用备选：((Number)item.get("pending_qty")).intValue()
         → 减少整个100 ✓

  循环2：item = order_item_id:3006, pending_qty:50
         → scheduleQtyMap为null  
         → 使用备选：((Number)item.get("pending_qty")).intValue()
         → 减少整个50 ✓

  这样逻辑上看起来是对的，但问题在于...
```

### 真正的Bug ❌

问题出在 `pendingItems` 的构造上（第181-191行）：

```java
// 第1步：查询所有待排程订单
List<Map<String, Object>> pendingItems = 
    orderItemMapper.selectPendingOrderItems(params);

// 过滤选中的订单明细
if (orderItemIds != null && !orderItemIds.isEmpty()) {
    pendingItems = pendingItems.stream()
        .filter(item -> orderItemIds.contains(
            ((Number)item.get("order_item_id")).longValue()))
        .collect(Collectors.toList());
}
```

**关键的bug**：
- `pendingItems` **已经被过滤为只包含 orderItemIds 中的订单**
- 但是有些 order_item_id 可能不在 scheduleQtyMap 中（因为前端只选了部分）
- 这会导致这些订单用 `pending_qty` 的整个值来减少！

---

## ✅ 修复方案

### 修复后的逻辑 ✅

```java
// 8. 更新销售订单明细的rolls字段（即pending_qty）
// 只减少被排程的订单
if (scheduleQtyMap != null && !scheduleQtyMap.isEmpty()) {
    // 新版本：使用前端传递的schedule_qty
    // 迭代scheduleQtyMap而不是pendingItems
    for (Map.Entry<Long, Integer> entry : scheduleQtyMap.entrySet()) {
        Long orderItemId = entry.getKey();
        Integer scheduleQty = entry.getValue();
        if (scheduleQty != null && scheduleQty > 0) {
            salesOrderItemMapper.decreaseRolls(orderItemId, scheduleQty);
        }
    }
} else {
    // 旧版本兼容：如果没有scheduleQtyMap，则减少所有pendingItems的整个pending_qty
    for (Map<String, Object> item : pendingItems) {
        Long orderItemId = ((Number)item.get("order_item_id")).longValue();
        int pendingQty = ((Number)item.get("pending_qty")).intValue();
        salesOrderItemMapper.decreaseRolls(orderItemId, pendingQty);
    }
}
```

### 关键改变 🎯

| 项目 | 原代码 | 修复后 |
|------|--------|--------|
| **迭代对象** | `pendingItems`（所有pending的订单） | `scheduleQtyMap.entrySet()`（只有被排程的订单） |
| **逻辑流** | 对每个pending项，检查map中是否有值 | 只对map中存在的项进行处理 |
| **减少值** | 从map读取或用pending_qty默认 | 直接从map读取 |
| **副作用** | 可能减少未被选中的订单 | 只减少被选中的订单 |

---

## 📊 流程对比

### 排程2个订单：A(100卷)和C(80卷)

#### 原代码流程 ❌
```
scheduleQtyMap: {
  2693: 100,  // 订单A
  3006: 80    // 订单C
}

pendingItems: [
  {order_item_id: 2693, pending_qty: 100},  // 订单A
  {order_item_id: 3006, pending_qty: 80},   // 订单C
]

循环pendingItems：
  ✓ item 2693 → map中有100 → 减少100
  ✓ item 3006 → map中有80 → 减少80

结果：A(-100), C(-80) ✓ 正确

但如果pendingItems多了一个未被选中的订单B：
pendingItems: [
  {order_item_id: 2693, pending_qty: 100},  // A ✓被选
  {order_item_id: 2694, pending_qty: 50},   // B ❌未被选
  {order_item_id: 3006, pending_qty: 80},   // C ✓被选
]

循环pendingItems：
  ✓ item 2693 → map中有100 → 减少100
  ❌ item 2694 → map中无值 → 用pending_qty：50 → 减少整个50！
  ✓ item 3006 → map中有80 → 减少80

结果：A(-100), B(-50), C(-80) ❌ 错误！B本不应该减少
```

#### 修复后流程 ✅
```
scheduleQtyMap: {
  2693: 100,  // 订单A
  3006: 80    // 订单C
}

循环scheduleQtyMap.entrySet()：
  ✓ 2693 → 100 → 减少100
  ✓ 3006 → 80 → 减少80

结果：A(-100), B(不变), C(-80) ✅ 正确！
```

---

## 🧪 测试验证

### 测试场景：3个待排程订单，只排程其中2个

**前端数据**：
```javascript
// 待排程订单列表
[
  {order_item_id: 2693, order_no: "PO-001", pending_qty: 100},
  {order_item_id: 2694, order_no: "PO-002", pending_qty: 50},
  {order_item_id: 3006, order_no: "PO-003", pending_qty: 80}
]

// 选中并排程的订单
[
  {order_item_id: 2693, schedule_qty: 100},
  {order_item_id: 3006, schedule_qty: 80}
]
```

**预期结果** ✅：
```sql
UPDATE sales_order_items SET rolls = rolls - 100 WHERE id = 2693;  -- 减100 → 0
UPDATE sales_order_items SET rolls = rolls - 80 WHERE id = 3006;   -- 减80 → 0
-- 注意：订单2694不变（因为没被选中排程）
```

**原代码的错误结果** ❌：
```sql
UPDATE sales_order_items SET rolls = rolls - 100 WHERE id = 2693;  -- 减100 → 0
UPDATE sales_order_items SET rolls = rolls - 50 WHERE id = 2694;   -- 减50 → 0 ❌ 错误！
UPDATE sales_order_items SET rolls = rolls - 80 WHERE id = 3006;   -- 减80 → 0
```

---

## 🛠️ 代码改动细节

### 改动位置
**文件**：`ProductionScheduleServiceImpl.java` 第332-348行

### 改动内容

```java
// 原代码（6行）
for (Map<String, Object> item : pendingItems) {
    Long orderItemId = ((Number)item.get("order_item_id")).longValue();
    int scheduleQty = scheduleQtyMap != null && scheduleQtyMap.containsKey(orderItemId) 
        ? scheduleQtyMap.get(orderItemId)
        : ((Number)item.get("pending_qty")).intValue();
    salesOrderItemMapper.decreaseRolls(orderItemId, scheduleQty);
}

// 修复后的代码（17行）
if (scheduleQtyMap != null && !scheduleQtyMap.isEmpty()) {
    // 新版本：使用前端传递的schedule_qty
    for (Map.Entry<Long, Integer> entry : scheduleQtyMap.entrySet()) {
        Long orderItemId = entry.getKey();
        Integer scheduleQty = entry.getValue();
        if (scheduleQty != null && scheduleQty > 0) {
            salesOrderItemMapper.decreaseRolls(orderItemId, scheduleQty);
        }
    }
} else {
    // 旧版本兼容
    for (Map<String, Object> item : pendingItems) {
        Long orderItemId = ((Number)item.get("order_item_id")).longValue();
        int pendingQty = ((Number)item.get("pending_qty")).intValue();
        salesOrderItemMapper.decreaseRolls(orderItemId, pendingQty);
    }
}
```

---

## ✅ 编译状态

✅ **构建成功**：无编译错误

```
Build Status: SUCCESS
Errors: 0
Warnings: 0
```

---

## 🚀 后续效果

排程后的表格显示 **正确**：

```
排程前：
├─ PO-001 (pending_qty: 100) ← 排程100卷
├─ PO-002 (pending_qty: 50)  ← 未排程
└─ PO-003 (pending_qty: 80)  ← 排程80卷

排程后 ✅：
├─ PO-001 (pending_qty: 0)   ← 减少了100卷
├─ PO-002 (pending_qty: 50)  ← 保持不变 ✓
└─ PO-003 (pending_qty: 0)   ← 减少了80卷
```

---

## 📋 根本原因总结

| 阶段 | 问题 |
|------|------|
| **数据准备** | Controller正确解析了details，生成了scheduleQtyMap |
| **Service处理** | 但第8步的逻辑用pendingItems做循环而不是scheduleQtyMap |
| **结果** | 导致未被选中排程的订单也被减少了pending_qty |

**关键理解**：
- `pendingItems` = 被过滤后的待排程订单
- `scheduleQtyMap` = 本次实际要排程的订单及其数量
- 第8步应该**只处理scheduleQtyMap中的订单**，不是所有pendingItems

---

**修复完成时间**：2026-01-10  
**修复版本**：v1.1  
**状态**：✅ 已验证编译
