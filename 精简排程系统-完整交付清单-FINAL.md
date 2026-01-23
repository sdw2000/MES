# 精简排程系统 - 完整交付清单

## 📦 交付时间：2024年（根据实施指南）

---

## ✅ 数据库层（已完成）

### 1. 数据库表（8个）
- ✅ `customer_transaction_summary` - 客户交易汇总表
- ✅ `customer_material_stats` - 客户料号统计表
- ✅ `material_base_price` - 料号基准价格表
- ✅ `material_production_config` - 物料生产配置表
- ✅ `equipment_schedule` - 设备日历占用表
- ✅ `order_material_lock` - 订单物料锁定表
- ✅ `order_material_issue` - 订单物料出库单表
- ✅ `coating_material_lock` - 涂布原料锁定表

**验证方法：**
```sql
USE erp;
SHOW TABLES LIKE '%customer_transaction%';
SHOW TABLES LIKE '%material_%';
SHOW TABLES LIKE '%equipment_schedule%';
SHOW TABLES LIKE '%lock%';
```

---

## ✅ 实体类层（8个）

### 2. 客户统计实体
- ✅ `CustomerTransactionSummary.java` - 客户交易汇总实体
- ✅ `CustomerMaterialStats.java` - 客户料号统计实体

### 3. 生产配置实体
- ✅ `MaterialBasePrice.java` - 料号基准价格实体
- ✅ `MaterialProductionConfig.java` - 物料生产配置实体
- ✅ `EquipmentSchedule.java` - 设备日历实体

### 4. 库存锁定实体
- ✅ `OrderMaterialLock.java` - 订单物料锁定实体
- ✅ `CoatingMaterialLock.java` - 涂布原料锁定实体
- ✅ `OrderMaterialIssue.java` - 订单物料出库单实体

---

## ✅ Mapper接口层（6个）

### 5. Mapper接口
- ✅ `CustomerTransactionSummaryMapper.java`
- ✅ `CustomerMaterialStatsMapper.java`
- ✅ `MaterialBasePriceMapper.java`
- ✅ `MaterialProductionConfigMapper.java`
- ✅ `EquipmentScheduleMapper.java`
- ✅ `OrderMaterialLockMapper.java`

### 6. Mapper XML（4个）
- ✅ `CustomerTransactionSummaryMapper.xml` - 客户统计刷新SQL
- ✅ `CustomerMaterialStatsMapper.xml` - 客户料号统计刷新SQL
- ✅ `MaterialBasePriceMapper.xml` - 料号基准价格刷新SQL
- ✅ `OrderMaterialLockMapper.xml` - 批量释放锁定SQL

---

## ✅ 服务层（9个）

### 7. 🔴 最高优先级服务
- ✅ `CustomerPriorityService.java` - **客户优先级评分服务**
  - 新客户固定20分
  - 账期评分：预付+10，月结30天0分，月结60天-10分，月结90天-20分
  - 月均交易额评分：月均额/30000（每3万贡献1分）
  - 单价偏差评分：(客户均价/行业均价 - 1) * 50
  
- ✅ `InventoryAllocationService.java` - **库存三级匹配服务**
  - 第一级：成品库存（分切卷）
  - 第二级：复卷库存（需二次分切）
  - 第三级：母卷库存（需涂布+复卷+分切）
  - 自动锁定库存

### 8. 🟡 中等优先级服务
- ✅ `MaterialLockService.java` - **物料锁定服务**
  - 多订单共享库存
  - 优先级抢占机制
  - 锁定状态管理：locked/released/consumed

- ✅ `CoatingScheduleService.java` - **涂布排程服务**
  - 按颜色分组（减少换单）
  - MOQ检查（< MOQ需手工确认）
  - 3台涂布机并行分配
  - 换单时间计算（颜色切换）
  - 涂布速度 + QC包装时间

### 9. 🟢 普通优先级服务
- ✅ `RewindingScheduleService.java` - **复卷排程服务**
  - 5台复卷机并行
  - 依赖涂布完成时间
  - 换单时间 + 复卷速度
  
- ✅ `SlittingScheduleService.java` - **分切排程服务**
  - 10台分切机并行
  - 依赖复卷完成时间
  - 换单时间 + 分切速度
  
- ✅ `DeliveryForecastService.java` - **交付时间预测服务**
  - 计算涂布→复卷→分切全流程时间
  - 识别延期风险：HIGH/MEDIUM/LOW
  - 延期天数计算

### 10. 🎯 总协调器
- ✅ `OrderScheduleOrchestrator.java` - **订单排程总协调器**
  - 统筹6步流程：
    1. 客户优先级评分
    2. 库存三级匹配
    3. 涂布排程
    4. 复卷排程
    5. 分切排程
    6. 交付时间预测

---

## ✅ 控制器层（1个）

### 11. REST API
- ✅ `OrderScheduleController.java`
  - `POST /api/schedule/batch` - 批量订单排程
  - `POST /api/schedule/coating/confirm-moq` - MOQ手工确认
  - `GET /api/schedule/delivery/risk-report` - 延期风险报告
  - `GET /api/schedule/delivery/forecast/{orderId}` - 订单交付预测
  - `POST /api/schedule/replan` - 重新排程

---

## 📊 核心功能特性

### 客户优先级评分
```java
总分 = 新客户分(0或20) + 账期分(-20~+10) + 月均额分(月均额/30000) + 单价偏差分((客户价/行业价-1)*50)
```

### 库存分配策略
```
成品库存（分切卷） → 满足 ✓ 返回
   ↓ 不足
复卷库存（需二次分切） → 满足 ✓ 返回
   ↓ 不足
母卷库存（需完整生产） → 满足 ✓ 返回
   ↓ 不足
缺口 → 安排涂布生产
```

### 涂布排程规则
- **颜色分组**：相同颜色合并排程
- **MOQ检查**：
  - 数量 >= MOQ → 自动排程
  - 数量 < MOQ → 需手工确认或推荐合并其他订单
- **设备分配**：3台涂布机并行，分配到最早可用设备
- **换单时间**：颜色切换需清洗设备

### 设备并行调度
- 涂布：3台（coating-1, coating-2, coating-3）
- 复卷：5台（rewinding-1 ~ rewinding-5）
- 分切：10台（slitting-1 ~ slitting-10）

---

## 📁 文件结构总览

```
e:\java\MES\
├── sql/
│   └── scheduling_system_enhancement_v2.sql  ✅ 数据库脚本
│
├── src/main/java/com/fine/
│   ├── model/
│   │   ├── customer/
│   │   │   ├── CustomerTransactionSummary.java  ✅
│   │   │   └── CustomerMaterialStats.java       ✅
│   │   ├── production/
│   │   │   ├── MaterialBasePrice.java           ✅
│   │   │   ├── MaterialProductionConfig.java    ✅
│   │   │   └── EquipmentSchedule.java           ✅
│   │   └── inventory/
│   │       ├── OrderMaterialLock.java           ✅
│   │       ├── CoatingMaterialLock.java         ✅
│   │       └── OrderMaterialIssue.java          ✅
│   │
│   ├── mapper/
│   │   ├── customer/
│   │   │   ├── CustomerTransactionSummaryMapper.java  ✅
│   │   │   └── CustomerMaterialStatsMapper.java       ✅
│   │   ├── production/
│   │   │   ├── MaterialBasePriceMapper.java           ✅
│   │   │   ├── MaterialProductionConfigMapper.java    ✅
│   │   │   └── EquipmentScheduleMapper.java           ✅
│   │   └── inventory/
│   │       └── OrderMaterialLockMapper.java           ✅
│   │
│   ├── service/
│   │   ├── customer/
│   │   │   └── CustomerPriorityService.java           ✅
│   │   ├── inventory/
│   │   │   ├── InventoryAllocationService.java        ✅
│   │   │   └── MaterialLockService.java               ✅
│   │   ├── production/
│   │   │   ├── CoatingScheduleService.java            ✅
│   │   │   ├── RewindingScheduleService.java          ✅
│   │   │   ├── SlittingScheduleService.java           ✅
│   │   │   └── DeliveryForecastService.java           ✅
│   │   └── orchestrator/
│   │       └── OrderScheduleOrchestrator.java         ✅
│   │
│   └── controller/
│       └── schedule/
│           └── OrderScheduleController.java           ✅
│
└── src/main/resources/mapper/
    ├── customer/
    │   ├── CustomerTransactionSummaryMapper.xml       ✅
    │   └── CustomerMaterialStatsMapper.xml            ✅
    ├── production/
    │   └── MaterialBasePriceMapper.xml                ✅
    └── inventory/
        └── OrderMaterialLockMapper.xml                ✅
```

---

## 🚀 快速启动指南

### 1. 数据库初始化
```bash
# 使用 PowerShell
cd e:\java\MES
$content = Get-Content -Path sql/scheduling_system_enhancement_v2.sql -Encoding UTF8
$content | mysql -h ssdw8127.mysql.rds.aliyuncs.com -u david -pdadazhengzheng@feng erp --default-character-set=utf8mb4
```

### 2. 启动后端
```bash
cd e:\java\MES
mvn spring-boot:run
```

### 3. 测试 API
```bash
# 批量排程
curl -X POST http://localhost:8090/api/schedule/batch \
  -H "Content-Type: application/json" \
  -d '[
    {
      "orderId": 1,
      "orderNo": "SO-2024-001",
      "customerId": 100,
      "materialCode": "FT-001-38mm",
      "requiredSqm": 5000,
      "paymentTerm": "net30",
      "customerRequiredDate": "2024-12-31"
    }
  ]'

# 查询延期风险
curl http://localhost:8090/api/schedule/delivery/risk-report

# 查询订单交付预测
curl http://localhost:8090/api/schedule/delivery/forecast/1
```

---

## 📝 配置说明

### application.properties
```properties
# 数据库连接（已验证）
spring.datasource.url=jdbc:mysql://ssdw8127.mysql.rds.aliyuncs.com:3306/erp
spring.datasource.username=david
spring.datasource.password=dadazhengzheng@feng

# MyBatis Mapper位置
mybatis-plus.mapper-locations=classpath*:mapper/**/*.xml
```

---

## ⚙️ 核心配置参数

### 客户优先级评分参数
- 新客户基础分：**20分**
- 账期评分：
  - prepaid（预付）：**+10分**
  - net30（月结30天）：**0分**
  - net60（月结60天）：**-10分**
  - net90（月结90天）：**-20分**
- 月均交易额系数：**30000**（每3万贡献1分）
- 单价偏差系数：**50**

### 设备配置
- 涂布机：**3台** (ID: 1, 2, 3)
- 复卷机：**5台** (ID: 11-15)
- 分切机：**10台** (ID: 21-30)

---

## 📊 统计数据刷新

定期刷新统计表（建议每日凌晨执行）：

```java
@Scheduled(cron = "0 0 1 * * ?")  // 每天凌晨1点
public void refreshStatistics() {
    customerTransactionSummaryMapper.refreshAllStats();
    customerMaterialStatsMapper.refreshAllStats();
    materialBasePriceMapper.refreshAllStats();
}
```

---

## 🎯 下一步工作

### 前端开发（Vue.js）
1. 订单批量排程页面
2. 涂布排程看板（颜色分组+MOQ确认）
3. 设备日历甘特图（3+5+10设备）
4. 交付时间预测仪表盘
5. 延期风险预警页面

### 测试验证
1. 单元测试（各Service）
2. 集成测试（全流程排程）
3. 压力测试（1000订单并发排程）

### 数据准备
1. 初始化 `material_production_config` 表（料号MOQ、速度、换单时间）
2. 初始化设备基础数据
3. 导入历史订单数据用于统计刷新

---

## 📞 技术支持

如有问题请参考：
- [精简排程系统-实施指南.md](精简排程系统-实施指南.md)
- API文档：http://localhost:8090/swagger-ui.html（如已配置Swagger）
- 日志位置：`logs/mes-application.log`

---

**交付日期：** 2024年（根据实施计划）  
**版本：** v1.0  
**状态：** ✅ 后端核心功能已完成
