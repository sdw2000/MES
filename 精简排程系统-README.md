# 精简排程系统 - README

## 🚀 快速开始

这是一个基于客户优先级的智能排程系统，已完成后端核心功能开发。

### 1️⃣ 数据库初始化

```powershell
# 在 PowerShell 中执行
cd e:\java\MES
$sql = Get-Content -Path sql/scheduling_system_enhancement_v2.sql -Encoding UTF8
$sql | mysql -h ssdw8127.mysql.rds.aliyuncs.com -u david -pdadazhengzheng@feng erp --default-character-set=utf8mb4
```

### 2️⃣ 启动后端

```bash
cd e:\java\MES
mvn spring-boot:run
```

### 3️⃣ 测试API

```bash
# 批量排程
curl -X POST http://localhost:8090/api/schedule/batch \
  -H "Content-Type: application/json" \
  -d '[{"orderId":1,"orderNo":"SO-001","customerId":100,"materialCode":"FT-001","requiredSqm":5000,"paymentTerm":"prepaid"}]'
```

---

## 📚 核心文档

| 文档 | 说明 |
|------|------|
| [实施指南](精简排程系统-实施指南.md) | 详细实施步骤和时间表 |
| [交付清单](精简排程系统-完整交付清单-FINAL.md) | 所有交付物和文件结构 |
| [完成报告](精简排程系统-实施完成报告.md) | 功能说明、API文档、验收标准 |

---

## 🎯 核心功能

### ✅ 客户优先级评分
```
总分 = 新客户(20分) + 账期(-20~+10分) + 月均额(每3万1分) + 单价偏差(±5~10分)
```

### ✅ 库存三级匹配
```
成品库存 → 复卷库存 → 母卷库存 → 涂布生产
```

### ✅ 设备并行排程
- 涂布：3台
- 复卷：5台
- 分切：10台

### ✅ 交付时间预测
```
涂布 → 复卷 → 分切 → 预计交付时间 + 延期风险评估
```

---

## 📦 已交付内容

- ✅ 8张数据库表
- ✅ 8个实体类
- ✅ 6个Mapper接口 + 4个XML
- ✅ 9个Service服务
- ✅ 1个REST控制器（5个API接口）
- ✅ 1个测试类
- ✅ 3份技术文档

---

## 🔑 配置信息

**数据库：**
- Host: `ssdw8127.mysql.rds.aliyuncs.com`
- Database: `erp`
- User: `david`
- Password: `dadazhengzheng@feng`

**后端端口：** `8090`

---

## 📋 API接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/schedule/batch` | POST | 批量订单排程 |
| `/api/schedule/coating/confirm-moq` | POST | MOQ手工确认 |
| `/api/schedule/delivery/risk-report` | GET | 延期风险报告 |
| `/api/schedule/delivery/forecast/{id}` | GET | 订单交付预测 |
| `/api/schedule/replan` | POST | 重新排程 |

---

## 🧪 快速测试

```java
@Autowired
private SchedulingSystemQuickTest quickTest;

// 执行全部测试
quickTest.runAllTests();
```

---

## 📞 技术支持

如遇问题请查阅：
- [实施完成报告](精简排程系统-实施完成报告.md) - 详细功能说明
- [交付清单](精简排程系统-完整交付清单-FINAL.md) - 文件结构和验证方法

**版本：** v1.0  
**状态：** ✅ 后端核心功能已完成  
**日期：** 2024年
