# 📋 报价单管理系统 - 快速启动指南

## 🎯 系统概述

完整的报价单管理系统，包含报价单主表和明细表，支持完整的CRUD操作。

### 核心功能
- ✅ 报价单列表查询
- ✅ 新增报价单
- ✅ 编辑报价单
- ✅ 查看报价单详情
- ✅ 删除报价单（逻辑删除）
- ✅ 自动生成报价单号（QT-YYYYMMDD-XXX）
- ✅ 自动计算平米数和金额
- ✅ 报价有效期管理
- ✅ 报价状态管理（草稿/已提交/已接受/已拒绝/已过期）

---

## 🚀 5分钟快速部署

### 步骤 1: 创建数据库表

```powershell
# 1. 打开PowerShell，进入项目目录
cd E:\java\MES

# 2. 连接MySQL并执行SQL
mysql -u root -p < database-quotations.sql

# 或者手动执行：
# mysql -u root -p
# use mes_db;
# source E:/java/MES/database-quotations.sql;
```

### 步骤 2: 验证后端编译

```powershell
# 进入后端项目目录
cd E:\java\MES

# 清理并编译
mvn clean compile

# 预期结果：BUILD SUCCESS
```

### 步骤 3: 启动后端服务

```powershell
# 方式1：使用Maven
mvn spring-boot:run

# 方式2：直接运行JAR
java -jar MES.jar

# 预期结果：
# 服务启动在 http://localhost:8090
# 看到日志：Started MesApplication in XX seconds
```

### 步骤 4: 启动前端服务

```powershell
# 打开新的PowerShell窗口
cd E:\vue\ERP

# 启动前端
npm run dev

# 预期结果：
# 前端启动在 http://localhost:8080
# 自动打开浏览器
```

### 步骤 5: 访问报价单管理页面

```
浏览器访问：http://localhost:8080
登录后访问：报价单管理菜单
```

---

## 📊 数据库结构

### 1. 报价单主表 (`quotations`)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键ID |
| quotation_no | VARCHAR(50) | 报价单号（QT-YYYYMMDD-XXX） |
| customer | VARCHAR(200) | 客户名称 |
| contact_person | VARCHAR(100) | 联系人 |
| contact_phone | VARCHAR(50) | 联系电话 |
| total_amount | DECIMAL(15,2) | 总金额 |
| total_area | DECIMAL(15,2) | 总面积（平方米） |
| quotation_date | DATE | 报价日期 |
| valid_until | DATE | 有效期截止日期 |
| status | VARCHAR(20) | 状态（draft/submitted/accepted/rejected/expired） |
| remark | TEXT | 备注 |
| created_by | VARCHAR(100) | 创建人 |
| updated_by | VARCHAR(100) | 更新人 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |
| is_deleted | TINYINT(1) | 逻辑删除标记 |

### 2. 报价单明细表 (`quotation_items`)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键ID |
| quotation_id | BIGINT | 关联的报价单ID |
| material_code | VARCHAR(50) | 物料代码 |
| material_name | VARCHAR(200) | 物料名称 |
| specifications | VARCHAR(200) | 规格型号 |
| length | DECIMAL(10,2) | 长度（毫米） |
| width | DECIMAL(10,2) | 宽度（毫米） |
| thickness | DECIMAL(10,6) | 厚度（微米） |
| quantity | INT | 数量（卷数） |
| unit | VARCHAR(20) | 单位 |
| sqm | DECIMAL(15,2) | 平方米数（自动计算） |
| unit_price | DECIMAL(15,2) | 单价（每平方米） |
| amount | DECIMAL(15,2) | 金额（自动计算） |
| remark | VARCHAR(500) | 备注 |
| is_deleted | TINYINT(1) | 逻辑删除标记 |

---

## 🔧 API接口说明

### 基础URL
```
http://localhost:8090/quotation
```

### 接口列表

#### 1. 获取报价单列表
```
GET /quotation/list
Authorization: Bearer {token}

Response:
{
  "code": 200,
  "msg": "获取报价单列表成功",
  "data": {
    "data": [
      {
        "id": 1,
        "quotationNo": "QT-20260105-001",
        "customer": "广州包装材料有限公司",
        "totalAmount": 15000.00,
        "items": [...]
      }
    ]
  }
}
```

#### 2. 获取报价单详情
```
GET /quotation/detail/{quotationId}
Authorization: Bearer {token}
```

#### 3. 创建报价单
```
POST /quotation/create
Authorization: Bearer {token}
Content-Type: application/json

Body:
{
  "customer": "客户名称",
  "contactPerson": "联系人",
  "contactPhone": "13800138000",
  "quotationDate": "2026-01-05",
  "validUntil": "2026-02-05",
  "status": "draft",
  "remark": "备注信息",
  "items": [
    {
      "materialCode": "MT-001",
      "materialName": "BOPP薄膜",
      "specifications": "透明",
      "length": 1000,
      "width": 500,
      "thickness": 50,
      "quantity": 20,
      "unit": "卷",
      "unitPrice": 50.00
    }
  ]
}
```

#### 4. 更新报价单
```
PUT /quotation/update
Authorization: Bearer {token}
Content-Type: application/json

Body: 同创建接口，需包含id字段
```

#### 5. 删除报价单
```
DELETE /quotation/delete/{quotationId}
Authorization: Bearer {token}
```

---

## 💡 自动计算公式

### 平方米数计算
```javascript
sqm = (length × width × quantity) / 1,000,000
```

**示例：**
- 长度：1000mm
- 宽度：500mm
- 数量：20卷
- 平方米数 = (1000 × 500 × 20) / 1,000,000 = 10.00 ㎡

### 金额计算
```javascript
amount = sqm × unitPrice
```

**示例：**
- 平方米数：10.00 ㎡
- 单价：50.00 元/㎡
- 金额 = 10.00 × 50.00 = 500.00 元

### 总计算
```javascript
totalArea = Σ(item.sqm)     // 所有明细的平方米数之和
totalAmount = Σ(item.amount) // 所有明细的金额之和
```

---

## 📂 项目文件清单

### 后端文件 (7个)
```
E:\java\MES\src\main\java\com\fine\
├── modle\
│   ├── Quotation.java          ✅ 报价单实体类
│   └── QuotationItem.java      ✅ 报价单明细实体类
├── Dao\
│   ├── QuotationMapper.java    ✅ 报价单Mapper
│   └── QuotationItemMapper.java ✅ 报价单明细Mapper
├── service\
│   └── QuotationService.java   ✅ 服务接口
├── serviceIMPL\
│   └── QuotationServiceImpl.java ✅ 服务实现类
└── controller\
    └── QuotationController.java ✅ REST控制器
```

### 前端文件 (2个)
```
E:\vue\ERP\src\
├── api\
│   └── quotation.js             ✅ API接口封装
└── views\sales\
    └── quotations.vue           ✅ 报价单管理页面
```

### 数据库文件
```
E:\java\MES\
└── database-quotations.sql      ✅ 数据库初始化脚本
```

---

## 🧪 功能测试清单

### 1️⃣ 报价单列表
- [ ] 页面正常加载
- [ ] 显示测试数据（2条报价单）
- [ ] 分页功能正常
- [ ] 状态标签颜色正确

### 2️⃣ 新增报价单
- [ ] 点击"新增报价单"按钮
- [ ] 填写客户信息
- [ ] 添加明细行
- [ ] 自动计算平米数和金额
- [ ] 自动生成报价单号
- [ ] 保存成功

### 3️⃣ 编辑报价单
- [ ] 点击"编辑"按钮
- [ ] 修改客户信息
- [ ] 修改明细
- [ ] 重新计算金额
- [ ] 更新成功

### 4️⃣ 查看详情
- [ ] 点击"详情"按钮
- [ ] 显示完整信息
- [ ] 明细表格正确

### 5️⃣ 删除报价单
- [ ] 点击"删除"按钮
- [ ] 确认对话框
- [ ] 逻辑删除成功
- [ ] 列表刷新

---

## 🎨 报价状态说明

| 状态 | 代码 | 说明 | 标签颜色 |
|------|------|------|----------|
| 草稿 | draft | 正在编辑中，未提交 | 灰色 |
| 已提交 | submitted | 已提交给客户 | 橙色 |
| 已接受 | accepted | 客户已接受报价 | 绿色 |
| 已拒绝 | rejected | 客户拒绝报价 | 红色 |
| 已过期 | expired | 已超过有效期 | 灰色 |

---

## 🔥 快速测试命令

### 测试数据库
```powershell
mysql -u root -p -e "USE mes_db; SELECT COUNT(*) as quotations_count FROM quotations WHERE is_deleted=0;"
mysql -u root -p -e "USE mes_db; SELECT COUNT(*) as items_count FROM quotation_items WHERE is_deleted=0;"
```

### 测试后端API
```powershell
# 获取报价单列表
curl -X GET http://localhost:8090/quotation/list `
  -H "Authorization: Bearer YOUR_TOKEN"

# 创建报价单
curl -X POST http://localhost:8090/quotation/create `
  -H "Authorization: Bearer YOUR_TOKEN" `
  -H "Content-Type: application/json" `
  -d '{"customer":"测试客户","quotationDate":"2026-01-05","validUntil":"2026-02-05","items":[]}'
```

---

## ❓ 常见问题

### Q1: 报价单号没有自动生成？
**A**: 检查后端日志，确保generateQuotationNo()方法正常执行。报价单号格式：QT-YYYYMMDD-XXX。

### Q2: 自动计算不正确？
**A**: 检查长度、宽度、数量是否都已填写，公式：平米数 = (长×宽×数量) / 1,000,000。

### Q3: 删除后仍然显示？
**A**: 使用的是逻辑删除，检查is_deleted字段是否为1。查询时会自动过滤已删除数据。

### Q4: 有效期如何管理？
**A**: valid_until字段存储有效期截止日期，可以通过定时任务自动将过期报价单状态改为expired。

---

## 📞 技术支持

如遇问题，请检查：
1. 后端日志：`E:\java\MES\logs\`
2. 前端控制台：浏览器F12 -> Console
3. 网络请求：浏览器F12 -> Network
4. 数据库数据：`SELECT * FROM quotations WHERE is_deleted=0`

---

## 🎉 部署完成！

现在您可以：
1. 访问 http://localhost:8080
2. 登录系统
3. 进入"报价单管理"
4. 开始使用完整的报价单功能！

**祝您使用愉快！** 🚀
