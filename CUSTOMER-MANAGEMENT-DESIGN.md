# 客户管理功能 - 数据库表结构设计

**设计日期**: 2026-01-06  
**参考**: 订单和订单明细的设计模式

---

## 📋 表结构设计

### 1️⃣ 客户主表 (customers)

| 字段名 | 类型 | 长度 | 必填 | 说明 | 示例 |
|-------|------|------|------|------|------|
| `id` | BIGINT | - | ✅ | 主键ID（自增） | 1 |
| `customer_code` | VARCHAR | 50 | ✅ | 客户编码（唯一） | CUS20260106001 |
| `customer_name` | VARCHAR | 200 | ✅ | 客户名称 | 阿里巴巴集团 |
| `short_name` | VARCHAR | 100 | ❌ | 客户简称 | 阿里 |
| `customer_type` | VARCHAR | 20 | ✅ | 客户类型 | 企业客户/个人客户 |
| `customer_level` | VARCHAR | 20 | ✅ | 客户等级 | A级/B级/C级 |
| `industry` | VARCHAR | 100 | ❌ | 所属行业 | 互联网 |
| `tax_number` | VARCHAR | 50 | ❌ | 纳税人识别号 | 91330000XXXXXXXX |
| `legal_person` | VARCHAR | 50 | ❌ | 法人代表 | 马云 |
| `registered_capital` | DECIMAL | (15,2) | ❌ | 注册资本（万元） | 10000.00 |
| `registered_address` | VARCHAR | 500 | ❌ | 注册地址 | 杭州市余杭区... |
| `business_address` | VARCHAR | 500 | ❌ | 经营地址 | 杭州市西湖区... |
| `contact_address` | VARCHAR | 500 | ❌ | 联系地址（默认收货地址） | 杭州市滨江区... |
| `company_phone` | VARCHAR | 50 | ❌ | 公司电话 | 0571-88888888 |
| `company_fax` | VARCHAR | 50 | ❌ | 公司传真 | 0571-88888889 |
| `company_email` | VARCHAR | 100 | ❌ | 公司邮箱 | contact@alibaba.com |
| `website` | VARCHAR | 200 | ❌ | 公司网站 | www.alibaba.com |
| `business_scope` | TEXT | - | ❌ | 经营范围 | 互联网信息服务... |
| `credit_code` | VARCHAR | 50 | ❌ | 统一社会信用代码 | 91330000XXXXXXXX |
| `credit_limit` | DECIMAL | (15,2) | ❌ | 信用额度（元） | 1000000.00 |
| `payment_terms` | VARCHAR | 50 | ❌ | 付款条件 | 月结30天 |
| `tax_rate` | DECIMAL | (5,2) | ❌ | 税率(%) | 13.00 |
| `bank_name` | VARCHAR | 100 | ❌ | 开户银行 | 中国工商银行杭州分行 |
| `bank_account` | VARCHAR | 50 | ❌ | 银行账号 | 6222021234567890 |
| `source` | VARCHAR | 50 | ❌ | 客户来源 | 网络推广/老客户介绍 |
| `sales_person` | VARCHAR | 50 | ❌ | 销售负责人 | 张三 |
| `sales_department` | VARCHAR | 50 | ❌ | 所属部门 | 销售一部 |
| `status` | VARCHAR | 20 | ✅ | 状态 | 正常/冻结/黑名单 |
| `remark` | VARCHAR | 500 | ❌ | 备注 | 重要客户 |
| `create_by` | VARCHAR | 50 | ❌ | 创建人 | admin |
| `create_time` | DATETIME | - | ✅ | 创建时间 | 2026-01-06 10:00:00 |
| `update_by` | VARCHAR | 50 | ❌ | 更新人 | admin |
| `update_time` | DATETIME | - | ✅ | 更新时间 | 2026-01-06 10:00:00 |
| `is_deleted` | TINYINT | 1 | ✅ | 是否删除（逻辑删除） | 0 |

---

### 2️⃣ 客户联系人表 (customer_contacts)

| 字段名 | 类型 | 长度 | 必填 | 说明 | 示例 |
|-------|------|------|------|------|------|
| `id` | BIGINT | - | ✅ | 主键ID（自增） | 1 |
| `customer_id` | BIGINT | - | ✅ | 客户ID（外键） | 1 |
| `contact_name` | VARCHAR | 50 | ✅ | 联系人姓名 | 张经理 |
| `contact_gender` | VARCHAR | 10 | ❌ | 性别 | 男/女 |
| `contact_position` | VARCHAR | 50 | ❌ | 职位 | 采购经理 |
| `contact_department` | VARCHAR | 50 | ❌ | 所属部门 | 采购部 |
| `contact_phone` | VARCHAR | 50 | ✅ | 联系电话 | 13800138000 |
| `contact_mobile` | VARCHAR | 50 | ❌ | 手机号码 | 13800138000 |
| `contact_email` | VARCHAR | 100 | ❌ | 邮箱 | zhang@alibaba.com |
| `contact_wechat` | VARCHAR | 50 | ❌ | 微信号 | zhang_wechat |
| `contact_qq` | VARCHAR | 20 | ❌ | QQ号 | 123456789 |
| `contact_address` | VARCHAR | 500 | ❌ | 联系地址 | 杭州市余杭区... |
| `is_primary` | TINYINT | 1 | ✅ | 是否主联系人 | 1 |
| `is_decision_maker` | TINYINT | 1 | ✅ | 是否决策人 | 1 |
| `birthday` | DATE | - | ❌ | 生日 | 1980-01-01 |
| `hobby` | VARCHAR | 200 | ❌ | 爱好 | 篮球、阅读 |
| `remark` | VARCHAR | 500 | ❌ | 备注 | VIP客户 |
| `sort_order` | INT | - | ✅ | 排序（数字越小越靠前） | 1 |
| `create_time` | DATETIME | - | ✅ | 创建时间 | 2026-01-06 10:00:00 |
| `update_time` | DATETIME | - | ✅ | 更新时间 | 2026-01-06 10:00:00 |

---

## 🔑 主要索引和约束

### 客户主表
```sql
PRIMARY KEY (`id`)
UNIQUE KEY `uk_customer_code` (`customer_code`)
KEY `idx_customer_name` (`customer_name`)
KEY `idx_customer_type` (`customer_type`)
KEY `idx_customer_level` (`customer_level`)
KEY `idx_status` (`status`)
KEY `idx_sales_person` (`sales_person`)
KEY `idx_create_time` (`create_time`)
```

### 客户联系人表
```sql
PRIMARY KEY (`id`)
KEY `idx_customer_id` (`customer_id`)
KEY `idx_contact_name` (`contact_name`)
KEY `idx_is_primary` (`is_primary`)
KEY `idx_contact_phone` (`contact_phone`)
CONSTRAINT `fk_customer_contacts_customer_id` 
  FOREIGN KEY (`customer_id`) 
  REFERENCES `customers` (`id`) 
  ON DELETE CASCADE ON UPDATE CASCADE
```

---

## 📊 数据字典

### 客户类型 (customer_type)
- `企业客户` - 企业/公司客户
- `个人客户` - 个人客户

### 客户等级 (customer_level)
- `A级客户` - 重要客户，大额订单
- `B级客户` - 一般客户，中等订单
- `C级客户` - 小客户，小额订单
- `潜在客户` - 尚未成交的潜在客户

### 客户状态 (status)
- `正常` - 可以正常交易
- `冻结` - 暂停交易（如欠款）
- `黑名单` - 禁止交易

### 付款条件 (payment_terms)
- `现款现货` - 先付款后发货
- `货到付款` - 收货后付款
- `月结30天` - 月底结算，次月30天内付款
- `月结60天` - 月底结算，次月60天内付款
- `预付30%` - 预付30%，发货前付清

---

## 🎯 功能特点

### 1. 客户主表特点
- ✅ 完整的企业信息（营业执照信息）
- ✅ 财务信息（信用额度、付款条件、税率）
- ✅ 银行信息（开户行、账号）
- ✅ 销售信息（负责人、部门、来源）
- ✅ 逻辑删除（不真实删除数据）

### 2. 联系人表特点
- ✅ 一个客户可以有多个联系人
- ✅ 区分主联系人和决策人
- ✅ 支持排序（方便前端显示）
- ✅ 级联删除（删除客户时自动删除联系人）
- ✅ 记录个人信息（生日、爱好）方便维护客户关系

### 3. 关系设计
```
customers (1) ←→ (N) customer_contacts
   主表                  联系人表
   
类似于：
sales_orders (1) ←→ (N) sales_order_details
   订单主表              订单明细
```

---

## 🔄 与其他模块的关联

### 关联到送样单
```java
// SampleOrder.java
private Long customerId;           // 外键关联客户ID
private String customerName;       // 冗余字段，提高查询效率
```

### 关联到销售订单
```java
// SalesOrder.java  
private Long customerId;           // 外键关联客户ID
private String customerName;       // 冗余字段
```

---

## ✅ 建议和优化

### 1. 编号生成规则
```
客户编码格式: CUS + 年月日(8位) + 流水号(3位)
示例: CUS20260106001, CUS20260106002
```

### 2. 数据冗余策略
在订单、送样等关联表中，建议同时存储：
- `customer_id` - 关联ID（用于关联查询）
- `customer_name` - 客户名称（冗余，提高查询效率）

### 3. 字段精简建议
如果觉得字段太多，可以考虑精简为"基础版"：

**基础版客户表（必需字段）**：
- 基本信息：`customer_code`, `customer_name`, `short_name`
- 类型等级：`customer_type`, `customer_level`
- 联系方式：`company_phone`, `company_email`, `contact_address`
- 销售信息：`sales_person`, `sales_department`
- 状态管理：`status`, `remark`
- 系统字段：`create_by`, `create_time`, `update_by`, `update_time`, `is_deleted`

**基础版联系人表（必需字段）**：
- 基本信息：`customer_id`, `contact_name`, `contact_phone`
- 联系方式：`contact_email`, `contact_address`
- 标记：`is_primary`, `sort_order`
- 系统字段：`create_time`, `update_time`

---

## 📝 请确认

请确认以下内容：

1. **表结构是否满足需求？**
   - [ ] 客户主表字段是否合适？
   - [ ] 联系人表字段是否合适？
   - [ ] 是否需要增加或删除某些字段？

2. **使用完整版还是精简版？**
   - [ ] 完整版（所有字段）
   - [ ] 精简版（只保留核心字段）
   - [ ] 自定义（指定需要的字段）

3. **数据字典是否需要调整？**
   - [ ] 客户类型是否需要增加？
   - [ ] 客户等级是否需要调整？
   - [ ] 付款条件是否需要修改？

4. **功能需求确认**
   - [ ] 是否需要客户导入/导出功能？
   - [ ] 是否需要联系人批量添加？
   - [ ] 是否需要客户标签功能？

---

**下一步**：

确认表结构后，我将：
1. ✅ 创建SQL建表脚本
2. ✅ 创建Java实体类（Customer、CustomerContact、CustomerDTO）
3. ✅ 创建Mapper层（CustomerMapper、CustomerContactMapper）
4. ✅ 创建Service层（CustomerService、CustomerServiceImpl）
5. ✅ 创建Controller层（CustomerController）
6. ✅ 创建前端API（customer.js）
7. ✅ 创建前端页面（customers.vue）

**请告诉我你的选择！** 🎯
