# 📋 报价单管理系统 - 文档索引

## 🚀 快速导航

### 🎯 快速开始
**推荐新用户从这里开始** → [`QUOTATION-QUICKSTART.md`](QUOTATION-QUICKSTART.md)
- 5分钟快速部署
- 详细步骤说明
- API接口文档
- 测试清单

### 📊 实现总结
**查看实现完成情况** → [`QUOTATION-SUMMARY.md`](QUOTATION-SUMMARY.md)
- 交付清单
- 核心功能
- 快速开始
- 测试建议

### 📝 完整报告
**查看技术细节** → [`QUOTATION-IMPLEMENTATION-COMPLETE.md`](QUOTATION-IMPLEMENTATION-COMPLETE.md)
- 文件清单
- 数据库结构
- API详细说明
- 注意事项

### 💾 数据库脚本
**初始化数据库** → [`database-quotations.sql`](database-quotations.sql)
- 创建表结构
- 插入测试数据
- 验证查询

### 🔧 初始化脚本
**自动化部署** → [`setup-quotation-database.ps1`](setup-quotation-database.ps1)
- 自动执行SQL
- 验证数据
- 显示结果

---

## 📂 项目文件结构

### 后端文件
```
E:\java\MES\src\main\java\com\fine\
├── modle\
│   ├── Quotation.java          # 报价单实体
│   └── QuotationItem.java      # 报价单明细实体
├── Dao\
│   ├── QuotationMapper.java    # 报价单Mapper
│   └── QuotationItemMapper.java # 报价单明细Mapper
├── service\
│   └── QuotationService.java   # 服务接口
├── serviceIMPL\
│   └── QuotationServiceImpl.java # 服务实现
└── controller\
    └── QuotationController.java # REST控制器
```

### 前端文件
```
E:\vue\ERP\src\
├── api\
│   └── quotation.js            # API接口封装
└── views\sales\
    └── quotations.vue          # 报价单管理页面
```

### 数据库文件
```
E:\java\MES\
├── database-quotations.sql              # SQL脚本
└── setup-quotation-database.ps1         # 初始化脚本
```

### 文档文件
```
E:\java\MES\
├── QUOTATION-README.md                  # 本文档
├── QUOTATION-QUICKSTART.md              # 快速启动指南
├── QUOTATION-SUMMARY.md                 # 实现总结
└── QUOTATION-IMPLEMENTATION-COMPLETE.md # 完整报告
```

---

## 🎯 核心功能

### ✅ 报价单管理
- 查看报价单列表（分页）
- 新增报价单
- 编辑报价单
- 查看报价单详情
- 删除报价单（逻辑删除）

### ✅ 自动化功能
- 自动生成报价单号（QT-YYYYMMDD-XXX）
- 自动计算平米数：(长×宽×数量) / 1,000,000
- 自动计算金额：平米数 × 单价
- 自动计算总金额和总面积

### ✅ 报价单特有功能
- 客户联系人和电话管理
- 报价有效期管理
- 报价状态管理（草稿/已提交/已接受/已拒绝/已过期）

---

## 🚀 5步快速启动

### 步骤 1: 初始化数据库
```powershell
cd E:\java\MES
.\setup-quotation-database.ps1
```

### 步骤 2: 编译后端
```powershell
cd E:\java\MES
mvn clean compile
```

### 步骤 3: 启动后端
```powershell
mvn spring-boot:run
```

### 步骤 4: 启动前端
```powershell
cd E:\vue\ERP
npm run dev
```

### 步骤 5: 访问系统
```
http://localhost:8080
登录后进入"报价单管理"
```

---

## 📊 API接口

### 基础URL
```
http://localhost:8090/quotation
```

### 接口列表
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /list | 获取报价单列表 |
| GET | /detail/{id} | 获取报价单详情 |
| POST | /create | 创建报价单 |
| PUT | /update | 更新报价单 |
| DELETE | /delete/{id} | 删除报价单 |

---

## 📋 数据库表

### quotations (报价单主表)
- id, quotation_no, customer, contact_person, contact_phone
- total_amount, total_area, quotation_date, valid_until
- status, remark, created_by, updated_by
- created_at, updated_at, is_deleted

### quotation_items (报价单明细表)
- id, quotation_id, material_code, material_name, specifications
- length, width, thickness, quantity, unit
- sqm, unit_price, amount, remark
- created_by, updated_by, created_at, updated_at, is_deleted

---

## 💡 自动计算公式

```javascript
// 平米数计算
sqm = (length × width × quantity) / 1,000,000

// 金额计算
amount = sqm × unitPrice

// 总计
totalArea = Σ(sqm)
totalAmount = Σ(amount)
```

**示例：**
- 长度：1000mm，宽度：500mm，数量：20卷
- 平米数 = (1000 × 500 × 20) / 1,000,000 = 10.00㎡
- 单价：50.00元/㎡
- 金额 = 10.00 × 50.00 = 500.00元

---

## 🎨 报价状态

| 状态 | 代码 | 说明 | 颜色 |
|------|------|------|------|
| 草稿 | draft | 正在编辑 | 灰色 |
| 已提交 | submitted | 已提交给客户 | 橙色 |
| 已接受 | accepted | 客户已接受 | 绿色 |
| 已拒绝 | rejected | 客户拒绝 | 红色 |
| 已过期 | expired | 超过有效期 | 灰色 |

---

## 🧪 功能测试清单

### 数据库测试
- [ ] quotations表创建成功
- [ ] quotation_items表创建成功
- [ ] 测试数据插入成功
- [ ] 外键关联正确

### 后端测试
- [ ] 项目编译成功（mvn clean compile）
- [ ] 服务启动成功（mvn spring-boot:run）
- [ ] API接口可访问

### 前端测试
- [ ] 报价单列表显示
- [ ] 新增报价单
- [ ] 编辑报价单
- [ ] 查看详情
- [ ] 删除报价单
- [ ] 自动计算平米数
- [ ] 自动计算金额
- [ ] 报价单号自动生成
- [ ] 状态标签显示正确

---

## 📝 与销售订单的对比

| 特性 | 销售订单 | 报价单 |
|------|----------|--------|
| 单号格式 | SO-YYYYMMDD-XXX | QT-YYYYMMDD-XXX |
| 主表 | sales_orders | quotations |
| 明细表 | sales_order_items | quotation_items |
| 特有字段 | 送货地址、客户订单号 | 联系人、电话、有效期 |
| 状态 | pending/processing/completed/cancelled | draft/submitted/accepted/rejected/expired |
| 日期 | 下单日期、交货日期 | 报价日期、有效期截止日期 |

---

## ❓ 常见问题

### Q1: 如何修改数据库密码？
**A**: 编辑 `setup-quotation-database.ps1`，修改 `$dbPassword` 变量。

### Q2: 报价单号没有自动生成？
**A**: 检查后端日志，确保 `generateQuotationNo()` 方法正常执行。

### Q3: 自动计算不正确？
**A**: 确保长度、宽度、数量都已填写，公式：平米数 = (长×宽×数量) / 1,000,000。

### Q4: 删除后仍然显示？
**A**: 使用的是逻辑删除，检查 `is_deleted` 字段。查询时会自动过滤已删除数据。

### Q5: 如何查看测试数据？
**A**: 
```sql
SELECT * FROM quotations WHERE is_deleted=0;
SELECT * FROM quotation_items WHERE is_deleted=0;
```

---

## 📞 技术支持

### 检查日志
```
后端日志：E:\java\MES\logs\
前端控制台：浏览器 F12 -> Console
网络请求：浏览器 F12 -> Network
```

### 数据库查询
```sql
-- 查看报价单
SELECT * FROM quotations WHERE is_deleted=0 ORDER BY created_at DESC;

-- 查看报价明细
SELECT * FROM quotation_items WHERE is_deleted=0;

-- 查看报价单统计
SELECT 
    COUNT(*) as total,
    SUM(total_amount) as total_revenue,
    status
FROM quotations 
WHERE is_deleted=0 
GROUP BY status;
```

---

## 🎉 部署完成

报价单管理系统已经**完全实现**并**可以立即使用**！

### 立即开始使用
```powershell
# 1. 初始化数据库
cd E:\java\MES
.\setup-quotation-database.ps1

# 2. 启动后端
mvn spring-boot:run

# 3. 启动前端（新窗口）
cd E:\vue\ERP
npm run dev

# 4. 访问系统
# http://localhost:8080
```

---

## 📚 相关文档

- **销售订单系统**: `SALES-ORDER-README.md`
- **快速启动指南**: `SALES-ORDER-QUICKSTART.md`
- **问题修复记录**: `PROBLEM-FIXED.md`
- **测试清单**: `TESTING-CHECKLIST.md`

---

**祝您使用愉快！如有任何问题，请参考相关文档或联系开发人员。** 🚀
