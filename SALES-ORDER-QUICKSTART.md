# 🚀 销售订单功能 - 快速开始

## 📋 前提条件

- ✅ Redis 已启动 (localhost:6379)
- ✅ MySQL 数据库已连接 (ssdw8127.mysql.rds.aliyuncs.com)
- ✅ 已完成之前的登录功能修复

---

## 🎯 快速部署（5步完成）

### 第1步: 创建数据库表 (2分钟)

```powershell
# 方法1: 使用MySQL命令行
mysql -h ssdw8127.mysql.rds.aliyuncs.com -u david -p
# 输入密码后执行:
use erp;
source e:\java\MES\database-sales-orders.sql

# 方法2: 使用Navicat或其他MySQL客户端
# 打开 e:\java\MES\database-sales-orders.sql 文件并执行
```

**验证**:
```sql
-- 检查表是否创建成功
SHOW TABLES LIKE 'sales_%';

-- 应该看到:
-- sales_orders
-- sales_order_items

-- 检查测试数据
SELECT COUNT(*) FROM sales_orders;
-- 应该返回 2 (两条测试数据)
```

---

### 第2步: 编译后端 (1分钟)

```powershell
cd e:\java\MES
mvn clean package -DskipTests
```

**预期输出**:
```
[INFO] BUILD SUCCESS
[INFO] Total time: ~20 seconds
```

---

### 第3步: 启动后端 (30秒)

```powershell
# 方法1: 使用启动脚本
.\start-backend.ps1

# 方法2: 直接运行JAR
java -jar target\MES-0.0.1-SNAPSHOT.jar
```

**验证后端启动成功**:
```
查看控制台输出，应该包含:
- Started MesApplication in xxx seconds
- Tomcat started on port(s): 8090
```

**测试后端健康状态**:
```powershell
# 测试登录接口
Invoke-RestMethod -Uri "http://localhost:8090/user/login" -Method POST `
  -Body '{"username":"admin","password":"123456"}' `
  -ContentType "application/json"
```

---

### 第4步: 启动前端 (1分钟)

```powershell
cd e:\vue\ERP
npm run dev
```

**预期输出**:
```
App running at:
- Local:   http://localhost:9527/
- Network: http://192.168.x.x:9527/
```

---

### 第5步: 测试功能 (5分钟)

#### 方式A: 浏览器测试 👍 推荐

1. 打开浏览器访问: `http://localhost:9527`
2. 登录系统
   - 用户名: `admin`
   - 密码: `123456`
3. 导航到: **销售管理 → 销售订单**
4. 点击"新增订单"按钮
5. 填写订单信息并保存
6. 验证订单显示在列表中

#### 方式B: PowerShell脚本测试

```powershell
cd e:\java\MES
.\test-sales-order.ps1
```

这将自动执行：
- ✅ 登录
- ✅ 创建测试订单
- ✅ 查询订单列表
- ✅ 查询订单详情
- ✅ 更新订单

---

## 📸 功能截图说明

### 1. 订单列表页面
```
+---------------------------------------------------------------------+
| 销售订单                                         [新增订单]        |
+---------------------------------------------------------------------+
| 客户         | 订单号        | 总金额 | 总面积 | 下单日期 | 操作 |
+---------------------------------------------------------------------+
| 广州胶带公司 | SO-20260105-001 | 12500 | 500㎡ | 2026-01-05 | ... |
| 深圳封箱厂   | SO-20260105-002 | 8000  | 300㎡ | 2026-01-05 | ... |
+---------------------------------------------------------------------+
```

### 2. 新增/编辑订单对话框
```
+---------------------------------------------------------------------+
| 新增订单                                                    [关闭]  |
+---------------------------------------------------------------------+
| 客户: [___________________________]                                |
| 订单号: [SO-20260105-003]    客户订单号: [_______________]        |
| 下单日期: [2026-01-05]      交货日期: [2026-01-15]               |
| 总金额: [12500.00]          总面积: [500.00㎡]                    |
| 送货地址: [_______________________________________]               |
|                                                                     |
| 物料明细                                        [新增明细行]       |
+---------------------------------------------------------------------+
| # | 物料代码 | 物料名称 | 长度 | 宽度 | 厚度 | 卷数 | ㎡数 | 单价 | 金额 | 操作 |
+---------------------------------------------------------------------+
| 1 | MT-001 | 胶带A | 1000 | 50 | 80 | 10 | 250 | 50 | 12500 | 删除 |
+---------------------------------------------------------------------+
|                                            [取消]  [保存]           |
+---------------------------------------------------------------------+
```

---

## 🧪 测试用例

### 测试用例1: 创建订单

**输入数据**:
```
客户: 测试客户001
客户订单号: TEST-001
下单日期: 2026-01-05
交货日期: 2026-01-15
送货地址: 广州市天河区XXX路

明细:
- 物料代码: MT-001
- 物料名称: 聚丙烯胶带
- 长度: 1000mm
- 宽度: 50mm
- 厚度: 80μm
- 卷数: 10
- 单价: ¥50/㎡
```

**预期结果**:
```
✓ 订单号自动生成: SO-20260105-XXX
✓ 平方米数自动计算: 250.00㎡
✓ 金额自动计算: ¥12,500.00
✓ 订单显示在列表中
```

**后端日志验证**:
```
=== 创建订单 ===
客户: 测试客户001
明细数量: 1
=== 订单创建成功 ===
订单号: SO-20260105-001
客户: 测试客户001
总金额: 12500.00
==================
```

---

### 测试用例2: 查询订单

**操作**: 点击订单列表中的"详情"按钮

**预期结果**:
```
✓ 弹出详情对话框
✓ 显示订单基本信息
✓ 显示订单明细列表
✓ 所有字段正确显示
```

---

### 测试用例3: 编辑订单

**操作**:
1. 点击"编辑"按钮
2. 修改客户名称
3. 添加新的明细行
4. 点击"保存"

**预期结果**:
```
✓ 订单信息更新成功
✓ 总金额和总面积重新计算
✓ 列表中显示更新后的数据
```

---

### 测试用例4: 删除订单

**操作**:
1. 点击"删除"按钮
2. 确认删除

**预期结果**:
```
✓ 订单从列表中消失
✓ 数据库中 is_deleted=1（逻辑删除）
✓ 订单明细也被标记为删除
```

**数据库验证**:
```sql
SELECT * FROM sales_orders WHERE order_no = 'SO-20260105-001';
-- is_deleted 应该是 1

SELECT * FROM sales_order_items WHERE order_id = (
    SELECT id FROM sales_orders WHERE order_no = 'SO-20260105-001'
);
-- 所有明细的 is_deleted 应该都是 1
```

---

## 🔍 API接口测试

### 1. 获取订单列表

```powershell
$token = "你的token"
$headers = @{ "X-Token" = $token }

Invoke-RestMethod -Uri "http://localhost:8090/sales/orders" `
  -Method GET -Headers $headers
```

**响应示例**:
```json
{
  "code": 200,
  "msg": "获取订单列表成功",
  "data": {
    "data": [
      {
        "id": 1,
        "orderNo": "SO-20260105-001",
        "customer": "广州胶带有限公司",
        "totalAmount": 12500.00,
        "totalArea": 500.00,
        "orderDate": "2026-01-05",
        "items": [...]
      }
    ]
  }
}
```

---

### 2. 创建订单

```powershell
$body = @{
  customer = "测试客户"
  orderDate = "2026-01-05"
  deliveryDate = "2026-01-15"
  items = @(
    @{
      materialCode = "MT-001"
      materialName = "测试胶带"
      length = 1000
      width = 50
      thickness = 0.08
      rolls = 10
      unitPrice = 25
    }
  )
} | ConvertTo-Json -Depth 10

Invoke-RestMethod -Uri "http://localhost:8090/sales/orders" `
  -Method POST -Body $body -Headers $headers `
  -ContentType "application/json"
```

---

### 3. 更新订单

```powershell
$body = @{
  orderNo = "SO-20260105-001"
  customer = "更新后的客户"
  orderDate = "2026-01-05"
  items = @(...)
} | ConvertTo-Json -Depth 10

Invoke-RestMethod -Uri "http://localhost:8090/sales/orders" `
  -Method PUT -Body $body -Headers $headers `
  -ContentType "application/json"
```

---

### 4. 删除订单

```powershell
Invoke-RestMethod -Uri "http://localhost:8090/sales/orders?orderNo=SO-20260105-001" `
  -Method DELETE -Headers $headers
```

---

## ⚠️ 常见问题排查

### 问题1: 401 Unauthorized

**症状**: 访问API返回401错误

**原因**: 
- Token未传递或已过期
- 用户未登录

**解决方案**:
```powershell
# 重新登录获取新token
$response = Invoke-RestMethod -Uri "http://localhost:8090/user/login" `
  -Method POST -Body '{"username":"admin","password":"123456"}' `
  -ContentType "application/json"
$token = $response.data.token
```

---

### 问题2: 404 Not Found

**症状**: 访问 `/sales/orders` 返回404

**原因**:
- 后端未启动
- Controller路径不正确

**解决方案**:
```powershell
# 检查后端是否运行
Get-Process | Where-Object {$_.ProcessName -like "*java*"}

# 检查端口占用
netstat -ano | findstr ":8090"

# 重启后端
.\start-backend.ps1
```

---

### 问题3: 数据库连接失败

**症状**: 后端日志显示数据库连接错误

**原因**:
- 数据库配置错误
- 表未创建

**解决方案**:
```powershell
# 检查数据库配置
cat e:\java\MES\src\main\resources\application.properties | Select-String "datasource"

# 重新执行SQL建表脚本
mysql -h ssdw8127.mysql.rds.aliyuncs.com -u david -p erp < database-sales-orders.sql
```

---

### 问题4: 前端请求失败

**症状**: 浏览器控制台显示网络错误

**原因**:
- 跨域问题
- 后端未启动
- API路径错误

**解决方案**:
```javascript
// 检查浏览器控制台的Network标签
// 查看请求URL是否正确: http://localhost:8090/sales/orders

// 检查请求头是否包含Token
// X-Token: eyJhbGciOiJIUzI1NiJ9...
```

---

## 📊 性能优化建议

### 1. 数据库索引优化
```sql
-- 如果订单量很大，建议添加以下索引
CREATE INDEX idx_customer_order ON sales_orders(customer, order_date);
CREATE INDEX idx_material ON sales_order_items(material_code, material_name);
```

### 2. 分页查询
```java
// 未来可以添加分页功能
@GetMapping
public ResponseResult getAllOrders(
    @RequestParam(defaultValue = "1") int page,
    @RequestParam(defaultValue = "10") int size
) {
    Page<SalesOrder> orderPage = new Page<>(page, size);
    // ...
}
```

### 3. Redis缓存
```java
// 可以缓存热点订单数据
@Cacheable(value = "orders", key = "#orderNo")
public SalesOrder getOrderByOrderNo(String orderNo) {
    // ...
}
```

---

## 📝 下一步计划

- [ ] 添加订单状态流转（pending → processing → completed）
- [ ] 添加订单导出为Excel功能
- [ ] 添加订单统计报表
- [ ] 添加订单搜索和高级筛选
- [ ] 添加订单打印功能
- [ ] 集成物料库存系统

---

## ✅ 检查清单

完成以下检查，确保功能正常：

- [ ] 数据库表创建成功
- [ ] 后端编译无错误
- [ ] 后端启动成功（端口8090）
- [ ] 前端启动成功（端口9527）
- [ ] 可以登录系统
- [ ] 可以创建订单
- [ ] 订单号自动生成正确
- [ ] 金额计算正确
- [ ] 可以查询订单列表
- [ ] 可以查看订单详情
- [ ] 可以编辑订单
- [ ] 可以删除订单
- [ ] 后端日志正常

---

## 📞 技术支持

如果遇到问题：

1. **检查后端日志**: 查看 `e:\java\MES` 目录下的控制台输出
2. **检查前端控制台**: 打开浏览器开发者工具（F12）
3. **运行测试脚本**: `.\test-sales-order.ps1`
4. **查看文档**: `SALES-ORDER-IMPLEMENTATION.md`

---

**祝使用愉快！** 🎉
