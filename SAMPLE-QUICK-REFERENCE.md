# 送样功能快速参考

## 📦 已创建的文件

### 数据库 (1个)
- `create-sample-tables.sql` - 创建4张表

### 后端 (8个)
- `SampleOrder.java` - 实体类
- `SampleItem.java` - 明细实体（含批次号）
- `SampleOrderDTO.java` - DTO
- `LogisticsUpdateDTO.java` - 物流DTO
- `SampleOrderMapper.java` - Mapper
- `SampleItemMapper.java` - Mapper
- `SampleOrderService.java` - Service接口
- `SampleOrderServiceImpl.java` - Service实现
- `SampleController.java` - Controller

### 前端 (1个)
- `samples_new.vue` - 完整送样页面

### 文档 (2个)
- `SAMPLE-FEATURE-DESIGN.md` - 详细设计
- `SAMPLE-FEATURE-IMPLEMENTATION.md` - 实现报告

### 脚本 (1个)
- `deploy-sample-feature.ps1` - 一键部署

---

## 🎯 核心功能

### ✅ 已实现
1. 备注在明细行（不单独占一行）
2. 关联客户表
3. 明细含批次号字段
4. 快递公司可选可输
5. 快递单号自动查询物流
6. 状态自动更新

### 📊 表格列顺序
```
物料代码 | 物料名称 | 型号 | 规格 | 批次号 | 数量 | 单位 | 备注 | 操作
```

### 🚚 快递公司列表
- 顺丰速运、圆通速递、中通快递
- 申通快递、韵达快递、邮政EMS
- 京东物流、德邦快递
- **支持手动输入其他公司**

### 📈 状态流转
```
待发货 → 已发货 → 运输中 → 已签收
         ↓
      已拒收/已取消
```

---

## 🚀 快速部署

### 一键部署
```powershell
cd E:\java\MES
.\deploy-sample-feature.ps1
```

### 手动部署
```powershell
# 1. 数据库
mysql -u root -p < create-sample-tables.sql

# 2. 替换前端
Copy-Item samples_new.vue samples.vue -Force

# 3. 编译后端
cd E:\java\MES
mvn clean compile

# 4. 启动服务
mvn spring-boot:run  # 后端
npm run dev          # 前端（新窗口）
```

---

## 🔍 测试步骤

1. 访问 `http://localhost:8080`
2. 登录系统
3. 进入 **销售管理 → 送样管理**
4. 点击 **新建送样**
5. 选择客户（自动填充联系人）
6. 添加明细（填写批次号）
7. 保存
8. 点击 **物流** 维护快递信息
9. 输入快递单号后点击 **查询**
10. 查看状态自动更新

---

## 💡 关键特性

### 客户关联
- 下拉选择客户
- 自动填充联系人信息
- 支持搜索过滤

### 批次号
- 在明细表格中
- 可选填写
- 建议格式：20260105-01

### 物流查询
- 输入单号自动查询
- 状态自动更新
- 物流轨迹展示

### 备注位置
- ✅ 在明细行中
- ❌ 不单独占一行

---

## 📝 API接口

```
GET    /api/sales/samples              - 列表查询
GET    /api/sales/samples/{no}         - 详情
POST   /api/sales/samples              - 新增
PUT    /api/sales/samples              - 更新
DELETE /api/sales/samples/{no}         - 删除
PUT    /api/sales/samples/{no}/logistics  - 更新物流
GET    /api/sales/samples/{no}/logistics  - 查询物流
GET    /api/sales/samples/generate-no  - 生成编号
```

---

## ⚙️ 配置项

### 快递100 API（可选）
```java
// SampleOrderServiceImpl.java 第200行左右
String apiUrl = "https://poll.kuaidi100.com/poll/query.do";
String customer = "您的Customer";
String key = "您的授权Key";
```

### 客户表关联
```java
// 确保有 CustomerMapper
@Autowired(required = false)
private CustomerMapper customerMapper;
```

---

## 🎉 完成状态

- ✅ 代码：100%完成
- ⏳ 部署：等待执行
- ⏳ 测试：等待验证

**运行部署脚本开始使用！**
