# 销售订单功能实现文档

## 📋 概述

本文档描述了销售订单（Sales Order）功能的完整实现，包括前端、后端、数据库的所有改动。

**实现日期**: 2026-01-05  
**功能模块**: 销售订单管理（下单、编辑、删除、查询）

---

## 🎯 功能需求

### 前端字段（来自 orders.vue）
- **订单主信息**:
  - `orderNo` - 订单号（系统自动生成，格式：SO-YYYYMMDD-XXX）
  - `customer` - 客户名称
  - `customerOrderNo` - 客户订单号
  - `totalAmount` - 总金额（自动计算）
  - `totalArea` - 总面积/㎡（自动计算）
  - `orderDate` - 下单日期
  - `deliveryDate` - 交货日期
  - `deliveryAddress` - 送货地址

- **订单明细（items 数组）**:
  - `materialCode` - 物料代码
  - `materialName` - 物料名称
  - `length` - 长度/mm
  - `width` - 宽度/mm
  - `thickness` - 厚度/mm（前端显示为μm）
  - `rolls` - 卷数
  - `sqm` - 平方米数（计算：长×宽×卷数÷1,000,000）
  - `unitPrice` - 单价（每平方米）
  - `amount` - 金额（计算：平方米数×单价）
  - `remark` - 备注

### 功能接口
1. **GET** `/sales/orders` - 获取所有订单列表
2. **POST** `/sales/orders` - 创建新订单
3. **PUT** `/sales/orders` - 更新订单
4. **DELETE** `/sales/orders?orderNo=xxx` - 删除订单（逻辑删除）
5. **GET** `/sales/orders/{orderNo}` - 获取订单详情

---

## 📁 文件清单

### 前端文件（Vue.js）
- ✅ **创建**: `e:\vue\ERP\src\api\sales.js` - API接口定义
- ✅ **修改**: `e:\vue\ERP\src\views\sales\orders.vue` - 使用新的API

### 后端文件（Spring Boot）
- ✅ **创建**: `e:\java\MES\src\main\java\com\fine\modle\SalesOrder.java` - 订单实体类
- ✅ **创建**: `e:\java\MES\src\main\java\com\fine\modle\SalesOrderItem.java` - 订单明细实体类
- ✅ **创建**: `e:\java\MES\src\main\java\com\fine\Dao\SalesOrderMapper.java` - 订单Mapper
- ✅ **创建**: `e:\java\MES\src\main\java\com\fine\Dao\SalesOrderItemMapper.java` - 订单明细Mapper
- ✅ **创建**: `e:\java\MES\src\main\java\com\fine\service\SalesOrderService.java` - 订单Service接口
- ✅ **创建**: `e:\java\MES\src\main\java\com\fine\serviceIMPL\SalesOrderServiceImpl.java` - 订单Service实现
- ✅ **创建**: `e:\java\MES\src\main\java\com\fine\controller\SalesOrderController.java` - 订单Controller

### 数据库文件
- ✅ **创建**: `e:\java\MES\database-sales-orders.sql` - 数据库表结构及测试数据

---

## 🗄️ 数据库设计

### 1. 销售订单主表（sales_orders）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT(20) | 主键ID，自增 |
| order_no | VARCHAR(50) | 订单号，唯一索引 |
| customer | VARCHAR(200) | 客户名称 |
| customer_order_no | VARCHAR(50) | 客户订单号 |
| total_amount | DECIMAL(12,2) | 总金额（元） |
| total_area | DECIMAL(12,2) | 总面积（平方米） |
| order_date | DATE | 下单日期 |
| delivery_date | DATE | 交货日期 |
| delivery_address | VARCHAR(500) | 送货地址 |
| status | VARCHAR(20) | 订单状态 |
| remark | TEXT | 备注 |
| created_by | VARCHAR(50) | 创建人 |
| updated_by | VARCHAR(50) | 更新人 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |
| is_deleted | TINYINT(1) | 逻辑删除标记 |

**索引**:
- 主键: `id`
- 唯一索引: `order_no`
- 普通索引: `customer`, `order_date`, `status`, `is_deleted`, `created_at`

### 2. 销售订单明细表（sales_order_items）

| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT(20) | 主键ID，自增 |
| order_id | BIGINT(20) | 关联的订单ID |
| material_code | VARCHAR(50) | 物料代码 |
| material_name | VARCHAR(200) | 物料名称 |
| length | DECIMAL(10,2) | 长度（毫米） |
| width | DECIMAL(10,2) | 宽度（毫米） |
| thickness | DECIMAL(10,3) | 厚度（毫米） |
| rolls | INT(11) | 卷数 |
| sqm | DECIMAL(12,2) | 平方米数 |
| unit_price | DECIMAL(10,2) | 单价（每平方米） |
| amount | DECIMAL(12,2) | 金额 |
| remark | VARCHAR(500) | 备注 |
| created_by | VARCHAR(50) | 创建人 |
| updated_by | VARCHAR(50) | 更新人 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |
| is_deleted | TINYINT(1) | 逻辑删除标记 |

**索引**:
- 主键: `id`
- 外键: `order_id` → `sales_orders(id)`
- 普通索引: `material_code`, `is_deleted`

---

## 🔧 实现细节

### 1. 订单号生成规则
```java
格式: SO-YYYYMMDD-XXX
示例: SO-20260105-001

生成逻辑:
1. 获取当前日期（YYYYMMDD）
2. 查询当天最大订单号
3. 序号自增（001-999）
```

### 2. 金额计算逻辑

**平方米数计算**:
```
sqm = (length × width × rolls) ÷ 1,000,000
```

**金额计算**:
```
amount = sqm × unitPrice
```

**总金额计算**:
```
totalAmount = Σ(各明细的amount)
```

**总面积计算**:
```
totalArea = Σ(各明细的sqm)
```

### 3. 事务处理

所有涉及主表+明细表的操作都使用 `@Transactional` 注解确保数据一致性：
- 创建订单：先插入主表，再插入明细
- 更新订单：更新主表，逻辑删除旧明细，插入新明细
- 删除订单：逻辑删除主表和所有明细

### 4. 逻辑删除

使用 MyBatis-Plus 的 `@TableLogic` 注解：
```java
@TableLogic
private Integer isDeleted;  // 0-未删除，1-已删除
```

---

## 📝 使用说明

### 第1步: 创建数据库表

```bash
# 连接到MySQL数据库
mysql -h ssdw8127.mysql.rds.aliyuncs.com -u david -p erp

# 执行SQL文件
source e:\java\MES\database-sales-orders.sql
```

或者在MySQL客户端中直接执行 `database-sales-orders.sql` 文件。

### 第2步: 编译后端

```powershell
cd e:\java\MES
mvn clean package -DskipTests
```

### 第3步: 启动后端

```powershell
cd e:\java\MES
java -jar target\MES-0.0.1-SNAPSHOT.jar
```

或使用启动脚本:
```powershell
.\start-backend.ps1
```

### 第4步: 启动前端

```powershell
cd e:\vue\ERP
npm run dev
```

### 第5步: 访问系统

1. 打开浏览器访问: `http://localhost:9527`
2. 登录系统
3. 导航到：**销售管理 → 销售订单**

---

## 🧪 测试步骤

### 1. 创建订单测试

1. 点击"新增订单"按钮
2. 填写客户信息：`测试客户ABC`
3. 填写订单日期：选择今天
4. 填写交货日期：选择未来日期
5. 填写送货地址：`测试地址123号`
6. 点击"新增明细行"
7. 填写物料信息：
   - 物料代码: `MT-TEST-001`
   - 物料名称: `测试胶带`
   - 长度: `1000`
   - 宽度: `50`
   - 厚度: `80`（μm）
   - 卷数: `10`
   - 单价: `25`
8. 查看自动计算的平方米数和金额
9. 点击"保存"
10. 验证订单号自动生成（格式：SO-20260105-XXX）

### 2. 查询订单测试

1. 在订单列表中查看刚创建的订单
2. 点击"详情"按钮查看订单详情
3. 验证所有字段显示正确

### 3. 编辑订单测试

1. 点击"编辑"按钮
2. 修改客户名称
3. 添加新的明细行
4. 点击"保存"
5. 验证更新成功

### 4. 删除订单测试

1. 点击"删除"按钮
2. 确认删除
3. 验证订单从列表中消失（逻辑删除）

---

## 🔍 后端日志验证

### 创建订单日志
```
=== 创建订单 ===
客户: 测试客户ABC
明细数量: 1
=== 订单创建成功 ===
订单号: SO-20260105-001
客户: 测试客户ABC
总金额: 250.00
==================
```

### 更新订单日志
```
=== 更新订单 ===
订单号: SO-20260105-001
=== 订单更新成功 ===
订单号: SO-20260105-001
==================
```

### 删除订单日志
```
=== 删除订单 ===
订单号: SO-20260105-001
=== 订单删除成功 ===
订单号: SO-20260105-001
==================
```

---

## 🔐 权限控制

所有销售订单接口都需要 `admin` 权限：
```java
@PreAuthorize("hasAuthority('admin')")
```

---

## 📊 数据库查询示例

### 查询所有订单及明细
```sql
SELECT 
  so.order_no,
  so.customer,
  so.total_amount,
  soi.material_code,
  soi.material_name,
  soi.sqm,
  soi.amount
FROM sales_orders so
LEFT JOIN sales_order_items soi ON so.id = soi.order_id
WHERE so.is_deleted = 0 AND soi.is_deleted = 0
ORDER BY so.created_at DESC;
```

### 统计客户订单总额
```sql
SELECT 
  customer,
  COUNT(*) as order_count,
  SUM(total_amount) as total_amount
FROM sales_orders 
WHERE is_deleted = 0
GROUP BY customer
ORDER BY total_amount DESC;
```

---

## ⚠️ 注意事项

1. **订单号唯一性**: 系统自动生成，不允许手动修改已存在的订单号
2. **金额精度**: 所有金额保留2位小数
3. **事务回滚**: 如果明细保存失败，整个订单会回滚
4. **逻辑删除**: 删除操作不会物理删除数据，只是标记 `is_deleted=1`
5. **权限验证**: 需要登录且具有 `admin` 权限才能操作

---

## 🐛 常见问题

### Q1: 创建订单时返回401错误
**原因**: 未登录或token过期  
**解决**: 重新登录系统

### Q2: 订单号重复
**原因**: 并发创建订单导致  
**解决**: 在数据库层面添加了唯一索引，系统会自动重试

### Q3: 金额计算不正确
**原因**: 前端输入的数值未正确转换  
**解决**: 后端会强制转换为 `BigDecimal` 类型并重新计算

### Q4: 订单明细为空
**原因**: 前端验证时跳过了空行  
**解决**: 至少需要填写一行有效的明细数据

---

## 📞 技术支持

如有问题，请查看后端控制台日志：
```powershell
# 查看Spring Boot日志
cd e:\java\MES
type nul > logs\application.log
java -jar target\MES-0.0.1-SNAPSHOT.jar | Tee-Object -FilePath logs\application.log
```

---

## ✅ 实现状态

- [x] 数据库表结构设计
- [x] 后端实体类创建
- [x] 后端Mapper层创建
- [x] 后端Service层实现
- [x] 后端Controller实现
- [x] 前端API接口封装
- [x] 前端页面集成
- [x] SQL建表脚本
- [x] 测试数据准备
- [x] 文档编写

**状态**: ✅ 实现完成，待测试

---

## 📅 更新记录

| 日期 | 版本 | 说明 |
|------|------|------|
| 2026-01-05 | 1.0 | 初始版本，完成全部功能实现 |

---

**END OF DOCUMENT**
