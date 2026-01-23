# 🔧 删除功能修复说明

## 🐛 问题描述

**症状**: 删除订单时前端显示"删除成功"，但刷新页面后订单仍然显示

**原因**: 删除方法中手动设置 `is_deleted = 1` 与 MyBatis-Plus 的 `@TableLogic` 自动逻辑删除功能冲突

---

## ✅ 修复内容

### 问题代码（之前）

```java
// ❌ 错误：手动设置 is_deleted
@Override
@Transactional(rollbackFor = Exception.class)
public ResponseResult deleteOrder(String orderNo) {
    // 查询订单
    LambdaQueryWrapper<SalesOrder> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(SalesOrder::getOrderNo, orderNo);
    SalesOrder order = salesOrderMapper.selectOne(queryWrapper);
    
    // 手动设置删除标记
    order.setIsDeleted(1);  // ❌ 这样做不会触发 MyBatis-Plus 的逻辑删除
    salesOrderMapper.updateById(order);
    
    // ... 删除明细
}
```

**问题**:
1. 直接 `updateById()` 只是更新字段，不会触发 MyBatis-Plus 的逻辑删除机制
2. 查询时虽然过滤了 `is_deleted = 0`，但手动更新可能不生效

### 修复代码（现在）

```java
// ✅ 正确：使用 MyBatis-Plus 的标准删除方法
@Override
@Transactional(rollbackFor = Exception.class)
public ResponseResult deleteOrder(String orderNo) {
    try {
        // 查询订单（不包括已删除的）
        LambdaQueryWrapper<SalesOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SalesOrder::getOrderNo, orderNo)
                   .eq(SalesOrder::getIsDeleted, 0);  // ✅ 只查询未删除的
        SalesOrder order = salesOrderMapper.selectOne(queryWrapper);
        
        if (order == null) {
            return new ResponseResult(404, "订单不存在或已被删除");
        }
        
        System.out.println("=== 开始删除订单 ===");
        System.out.println("订单号: " + orderNo);
        System.out.println("订单ID: " + order.getId());
        
        // ✅ 使用 MyBatis-Plus 的逻辑删除（会自动设置 is_deleted = 1）
        int result = salesOrderMapper.deleteById(order.getId());
        
        // 逻辑删除订单明细
        LambdaQueryWrapper<SalesOrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(SalesOrderItem::getOrderId, order.getId())
                  .eq(SalesOrderItem::getIsDeleted, 0);
        List<SalesOrderItem> items = salesOrderItemMapper.selectList(itemWrapper);
        
        System.out.println("订单明细数量: " + items.size());
        
        for (SalesOrderItem item : items) {
            // ✅ 使用 deleteById() 自动处理逻辑删除
            salesOrderItemMapper.deleteById(item.getId());
        }
        
        System.out.println("=== 订单删除成功 ===");
        System.out.println("删除结果: " + result);
        
        return new ResponseResult(200, "删除订单成功", data);
    } catch (Exception e) {
        // 错误处理...
    }
}
```

---

## 🔍 技术原理

### MyBatis-Plus 逻辑删除

#### 1. 实体类配置

```java
@TableName("sales_orders")
public class SalesOrder {
    // ...
    
    @TableLogic  // ✅ 标记为逻辑删除字段
    private Integer isDeleted;
}
```

#### 2. 配置文件设置

```properties
# application.properties
mybatis-plus.global-config.db-config.logic-delete-value=1      # 删除后的值
mybatis-plus.global-config.db-config.logic-not-delete-value=0  # 未删除的值
```

#### 3. 自动行为

**删除操作** (`deleteById()`):
```sql
-- MyBatis-Plus 自动转换为：
UPDATE sales_orders SET is_deleted = 1 WHERE id = ? AND is_deleted = 0
```

**查询操作** (`selectList()`, `selectById()`, 等):
```sql
-- MyBatis-Plus 自动添加条件：
SELECT * FROM sales_orders WHERE ... AND is_deleted = 0
```

**更新操作** (`updateById()`):
```sql
-- MyBatis-Plus 自动添加条件：
UPDATE sales_orders SET ... WHERE id = ? AND is_deleted = 0
```

### 为什么手动设置不生效？

```java
// ❌ 错误方式
order.setIsDeleted(1);
salesOrderMapper.updateById(order);

// 实际执行的 SQL:
// UPDATE sales_orders SET is_deleted = 1, ... WHERE id = ? AND is_deleted = 0
// 问题：is_deleted = 0 条件可能导致更新失败
```

```java
// ✅ 正确方式
salesOrderMapper.deleteById(order.getId());

// 实际执行的 SQL:
// UPDATE sales_orders SET is_deleted = 1 WHERE id = ? AND is_deleted = 0
// MyBatis-Plus 自动处理所有逻辑
```

---

## 🚀 测试步骤

### 1. 重启后端

在 IDEA 中：
1. 停止当前运行的 MesApplication
2. 重新运行 MesApplication
3. 等待启动完成（看到 "Tomcat started on port 8090"）

### 2. 测试删除功能

在浏览器中：
1. 访问订单页面: http://localhost:8080/#/sales/orders
2. 点击任意订单的 **[删除]** 按钮
3. 在确认对话框中点击 **[确定]**
4. 观察：
   - ✅ 显示"删除成功"提示
   - ✅ 订单从列表中消失
   - ✅ 列表自动刷新

### 3. 查看后端日志

在 IDEA 控制台应该看到：

```
=== 开始删除订单 ===
订单号: SO-20250105-001
订单ID: 1
订单明细数量: 2
=== 订单删除成功 ===
删除结果: 1
==================
```

### 4. 验证数据库

在数据库中执行：

```sql
-- 查看已删除的订单
SELECT * FROM sales_orders WHERE order_no = 'SO-20250105-001';

-- 应该看到 is_deleted = 1
```

```sql
-- 查看未删除的订单
SELECT * FROM sales_orders WHERE is_deleted = 0;

-- 应该只看到未删除的订单
```

---

## 📊 预期结果

### 成功的表现

| 操作 | 预期结果 |
|------|---------|
| 点击删除按钮 | ✅ 显示确认对话框 |
| 确认删除 | ✅ 显示"删除成功"提示 |
| 列表刷新 | ✅ 被删除的订单消失 |
| 后端日志 | ✅ 显示删除成功信息 |
| 数据库 | ✅ is_deleted = 1 |
| 再次查询 | ✅ 不返回已删除订单 |

### 如果仍然有问题

#### 问题 A: 删除后订单仍然显示

**检查**:
1. 后端是否重启成功？
2. 后端日志是否显示删除成功？
3. 数据库中 `is_deleted` 字段是否为 1？

**解决**:
```bash
# 1. 确认后端重启
# 在 IDEA 中查看控制台输出

# 2. 查看数据库
mysql> SELECT id, order_no, is_deleted FROM sales_orders WHERE order_no = 'SO-XXXXXXXX-XXX';

# 3. 如果 is_deleted 还是 0，手动更新
mysql> UPDATE sales_orders SET is_deleted = 1 WHERE order_no = 'SO-XXXXXXXX-XXX';
```

#### 问题 B: 删除提示失败

**检查**:
1. 浏览器 Console 的错误信息
2. Network 标签中的 API 响应
3. 后端日志的错误堆栈

**常见错误**:
- 401: Token 过期，需要重新登录
- 404: 订单不存在或已删除
- 500: 服务器错误，查看后端日志

#### 问题 C: 前端没有刷新

**检查**:
```javascript
// 确认 deleteOrder 方法中有调用 fetchOrders
async deleteOrder(row) {
  try {
    const res = await deleteOrder(row.orderNo)
    if (res && res.code === 200) {
      await this.fetchOrders()  // ✅ 这行很重要
      this.$message.success('删除成功')
    }
  } catch (e) {
    this.$message.error('删除失败')
  }
}
```

---

## 🔧 修复文件清单

### 修改的文件

1. **SalesOrderServiceImpl.java**
   - 修改了 `deleteOrder()` 方法
   - 从手动设置改为使用 `deleteById()`
   - 添加了详细的日志输出

### 新增的文件

1. **fix-delete-function.ps1**
   - 自动化测试脚本
   - 编译和验证功能

2. **DELETE-FUNCTION-FIX.md**
   - 本修复说明文档

---

## 💡 最佳实践

### 使用 MyBatis-Plus 逻辑删除

```java
// ✅ 推荐：使用 MyBatis-Plus 提供的方法
mapper.deleteById(id);              // 逻辑删除单条
mapper.deleteBatchIds(ids);         // 逻辑删除多条
mapper.delete(queryWrapper);        // 按条件逻辑删除

// ❌ 不推荐：手动设置 is_deleted
entity.setIsDeleted(1);
mapper.updateById(entity);
```

### 查询时自动过滤

```java
// ✅ 无需手动添加 is_deleted 条件
LambdaQueryWrapper<Entity> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(Entity::getName, "test");
List<Entity> list = mapper.selectList(wrapper);
// MyBatis-Plus 自动添加: WHERE name = 'test' AND is_deleted = 0
```

### 如果需要查询已删除的数据

```java
// 使用原生 SQL 或者关闭逻辑删除
@Select("SELECT * FROM table_name WHERE is_deleted = 1")
List<Entity> selectDeletedRecords();
```

---

## 📚 参考资料

### MyBatis-Plus 官方文档

- [逻辑删除](https://baomidou.com/pages/6b03c5/)
- [CRUD 接口](https://baomidou.com/pages/49cc81/)

### 相关注解

- `@TableLogic`: 标记逻辑删除字段
- `@TableId`: 主键标识
- `@TableName`: 表名映射

---

## ✅ 总结

### 问题
删除订单后，前端显示成功但订单仍然出现在列表中

### 原因
手动设置 `is_deleted = 1` 不会触发 MyBatis-Plus 的逻辑删除机制

### 解决方案
使用 `mapper.deleteById()` 替代手动更新

### 关键改变
```java
// 之前
order.setIsDeleted(1);
salesOrderMapper.updateById(order);

// 现在
salesOrderMapper.deleteById(order.getId());
```

### 效果
- ✅ 删除后订单立即从列表消失
- ✅ 数据库正确标记为已删除
- ✅ 后续查询自动过滤已删除数据

---

**修复完成！重启后端并测试即可。** 🎉
