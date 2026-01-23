# 送样功能字段对照表

## 📋 字段映射规则

**重要提示**：
- 数据库字段使用 **下划线命名法**（snake_case）
- Java实体类使用 **驼峰命名法**（camelCase）
- MyBatis-Plus 会自动进行驼峰转下划线映射

---

## 1️⃣ 送样主表 (sample_orders)

### ✅ 基本信息字段

| 数据库字段 | Java字段 | 类型 | 说明 |
|-----------|---------|------|------|
| `id` | `id` | Long | 主键ID |
| `sample_no` | `sampleNo` | String | 送样编号（SP20260105001） |
| `customer_id` | `customerId` | Long | 客户ID（关联客户表） |
| `customer_name` | `customerName` | String | 客户名称 |
| `contact_name` | `contactName` | String | 联系人姓名 |
| `contact_phone` | `contactPhone` | String | 联系电话 |
| `contact_address` | `contactAddress` | String | 收货地址 |

### ✅ 日期字段

| 数据库字段 | Java字段 | 类型 | 说明 |
|-----------|---------|------|------|
| `send_date` | `sendDate` | LocalDate | 送样日期 |
| `expected_feedback_date` | `expectedFeedbackDate` | LocalDate | 期望反馈日期 |
| `ship_date` | `shipDate` | LocalDate | 发货日期 |
| `delivery_date` | `deliveryDate` | LocalDate | 送达日期 |
| `feedback_date` | `feedbackDate` | LocalDate | 反馈日期 |

### ✅ 物流字段（重要！容易出错）

| 数据库字段 | Java字段 | 类型 | 说明 |
|-----------|---------|------|------|
| **`express_company`** ⚠️ | `expressCompany` | String | 快递公司（顺丰、圆通等） |
| `tracking_number` | `trackingNumber` | String | 快递单号 |
| `logistics_status` | `logisticsStatus` | String | 物流状态 |
| `last_logistics_query_time` | `lastLogisticsQueryTime` | LocalDateTime | 最后物流查询时间 |

**⚠️ 常见错误**：
- ❌ 错误写法：`courier_company`
- ✅ 正确写法：`express_company`

### ✅ 状态和统计字段

| 数据库字段 | Java字段 | 类型 | 说明 |
|-----------|---------|------|------|
| `status` | `status` | String | 状态（待发货、已发货等） |
| **`total_quantity`** | `totalQuantity` | Integer | 总数量（统计明细） |

### ✅ 反馈和转订单字段

| 数据库字段 | Java字段 | 类型 | 说明 |
|-----------|---------|------|------|
| `customer_feedback` | `customerFeedback` | String | 客户反馈 |
| `is_satisfied` | `isSatisfied` | Boolean | 是否满意 |
| `converted_to_order` | `convertedToOrder` | Boolean | 是否已转订单 |
| `order_no` | `orderNo` | String | 关联订单号 |

### ✅ 备注字段

| 数据库字段 | Java字段 | 类型 | 说明 |
|-----------|---------|------|------|
| `remark` | `remark` | String | 备注 |
| `internal_note` | `internalNote` | String | 内部备注（客户不可见） |

### ✅ 系统字段（重要！容易出错）

| 数据库字段 | Java字段 | 类型 | 说明 |
|-----------|---------|------|------|
| **`create_by`** ⚠️ | `createBy` | String | 创建人 |
| **`create_time`** ⚠️ | `createTime` | LocalDateTime | 创建时间 |
| **`update_by`** ⚠️ | `updateBy` | String | 更新人 |
| **`update_time`** ⚠️ | `updateTime` | LocalDateTime | 更新时间 |
| `is_deleted` | `isDeleted` | Boolean | 是否删除（逻辑删除） |

**⚠️ 常见错误**：
- ❌ 错误写法：`created_by`, `created_time`, `updated_by`, `updated_time`
- ✅ 正确写法：`create_by`, `create_time`, `update_by`, `update_time`

---

## 2️⃣ 送样明细表 (sample_items)

### ✅ 基本信息

| 数据库字段 | Java字段 | 类型 | 说明 |
|-----------|---------|------|------|
| `id` | `id` | Long | 主键ID |
| `sample_no` | `sampleNo` | String | 送样编号（外键） |

### ✅ 物料信息（重要！容易出错）

| 数据库字段 | Java字段 | 类型 | 说明 |
|-----------|---------|------|------|
| **`material_code`** ⚠️ | `materialCode` | String | 物料代码 |
| **`material_name`** ⚠️ | `materialName` | String | 物料名称/产品名称 |
| `specification` | `specification` | String | 规格 |
| `model` | `model` | String | 型号 |
| **`batch_no`** | `batchNo` | String | 批次号 |

**⚠️ 常见错误**：
- ❌ 错误写法：`product_code`, `product_name`
- ✅ 正确写法：`material_code`, `material_name`

### ✅ 尺寸信息

| 数据库字段 | Java字段 | 类型 | 说明 |
|-----------|---------|------|------|
| `length` | `length` | BigDecimal | 长度(mm) |
| `width` | `width` | BigDecimal | 宽度(mm) |
| `thickness` | `thickness` | BigDecimal | 厚度(mm) |

### ✅ 数量信息

| 数据库字段 | Java字段 | 类型 | 说明 |
|-----------|---------|------|------|
| `quantity` | `quantity` | Integer | 数量/卷数 |
| `unit` | `unit` | String | 单位（卷、片、米等） |

### ✅ 其他

| 数据库字段 | Java字段 | 类型 | 说明 |
|-----------|---------|------|------|
| `remark` | `remark` | String | 备注（在明细行中） |
| `create_time` | `createTime` | LocalDateTime | 创建时间 |
| `update_time` | `updateTime` | LocalDateTime | 更新时间 |

---

## 3️⃣ 状态历史表 (sample_status_history)

| 数据库字段 | Java字段 | 类型 | 说明 |
|-----------|---------|------|------|
| `id` | `id` | Long | 主键ID |
| `sample_no` | `sampleNo` | String | 送样编号 |
| `old_status` | `oldStatus` | String | 原状态 |
| `new_status` | `newStatus` | String | 新状态 |
| `change_reason` | `changeReason` | String | 变更原因 |
| `change_source` | `changeSource` | String | 变更来源（MANUAL/AUTO） |
| `operator` | `operator` | String | 操作人 |
| `change_time` | `changeTime` | LocalDateTime | 变更时间 |

---

## 4️⃣ 物流记录表 (sample_logistics_records)

### ✅ 基本信息

| 数据库字段 | Java字段 | 类型 | 说明 |
|-----------|---------|------|------|
| `id` | `id` | Long | 主键ID |
| `sample_no` | `sampleNo` | String | 送样编号 |
| `tracking_number` | `trackingNumber` | String | 快递单号 |
| **`express_company`** ⚠️ | `expressCompany` | String | 快递公司 |

**⚠️ 注意**：这个表也是 `express_company`，不是 `courier_company`！

### ✅ 物流信息

| 数据库字段 | Java字段 | 类型 | 说明 |
|-----------|---------|------|------|
| `logistics_status` | `logisticsStatus` | String | 物流状态 |
| `logistics_info` | `logisticsInfo` | String | 物流详情（JSON） |
| `is_signed` | `isSigned` | Boolean | 是否已签收 |

### ✅ 查询信息

| 数据库字段 | Java字段 | 类型 | 说明 |
|-----------|---------|------|------|
| `query_time` | `queryTime` | LocalDateTime | 查询时间 |
| `query_success` | `querySuccess` | Boolean | 查询是否成功 |
| `error_message` | `errorMessage` | String | 错误信息 |

---

## 🔍 快速检查清单

在编写SQL脚本时，请检查以下常见错误：

### ❌ 常见错误字段名

| 错误写法 | 正确写法 | 出现位置 |
|---------|---------|---------|
| `courier_company` | `express_company` | 主表、物流记录表 |
| `product_code` | `material_code` | 明细表 |
| `product_name` | `material_name` | 明细表 |
| `created_by` | `create_by` | 主表 |
| `created_time` | `create_time` | 主表 |
| `updated_by` | `update_by` | 主表 |
| `updated_time` | `update_time` | 主表 |
| `delivery_date` | `send_date` | 主表（送样日期） |

### ✅ 字段验证方法

1. **在数据库中验证**：
```sql
-- 查看表结构
DESC sample_orders;
DESC sample_items;
DESC sample_status_history;
DESC sample_logistics_records;

-- 查看所有字段名
SELECT COLUMN_NAME 
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = 'erp' 
AND TABLE_NAME = 'sample_orders';
```

2. **在Java中验证**：
```java
// 查看实体类的所有字段
SampleOrder order = new SampleOrder();
// 使用IDE的自动完成功能，确认字段名
```

---

## 📝 开发建议

1. **优先参考数据库表结构**：以 `create-sample-tables.sql` 为准
2. **使用IDE自动补全**：编写SQL时，让IDE读取数据库元数据
3. **测试前先验证**：执行 `DESC table_name` 查看表结构
4. **保持一致性**：所有快递相关字段统一使用 `express`，不用 `courier`

---

## 🔗 相关文件

- 表结构定义：`create-sample-tables.sql`
- Java实体类：
  - `SampleOrder.java`
  - `SampleItem.java`
- 测试数据：`clean-and-insert-sample-data.sql`
- 完整部署：`deploy-sample-complete.sql`

---

**最后更新**: 2026-01-05  
**维护者**: AI Assistant
