# 分切任务功能 - README

## 📋 本次交付物

本方案完成了MES/ERP系统中**分切任务管理功能**的完整实现,支持:

✅ **11个业务字段显示**
- 分切任务号 / 订单号 / 详情号 / 规格 / 数量 / 机台号
- 计划开始时间 / 计划结束时间 / 实际开始时间
- 操作人 / 状态 / 备注

✅ **10分钟精度时间排程**
- 自动四舍五入到10分钟粒度
- 例: 14:23 → 14:20, 14:26 → 14:30

✅ **自动生成任务号和规格**
- 任务号: SLT-YYYYMMDD-XXXXX
- 规格: 料号-宽度mm x 长度m x 卷数卷

✅ **完整的CRUD操作界面**
- 新增/编辑/删除任务
- 任务状态流转(待生产 → 进行中 → 已完成)
- 多条件查询过滤

---

## 🚀 快速开始 (5分钟)

### 1️⃣ 执行数据库迁移
```bash
mysql -h ssdw8127.mysql.rds.aliyuncs.com -u david -pdadazhengzheng@feng erp < 修改分切任务表结构.sql
```

### 2️⃣ 编译后端
```bash
cd e:\java\MES
mvn clean package -DskipTests
```

### 3️⃣ 启动服务
```bash
# 后端 (端口8090)
java -jar target\MES-0.0.1-SNAPSHOT.jar

# 前端 (端口9527, 新窗口执行)
cd e:\vue\ERP
npm run dev
```

### 4️⃣ 验证
- 打开: http://localhost:9527/production-management/slitting
- 新增任务 → 验证任务号自动生成 → 验证时间精度(14:23→14:20)

---

## 📂 文件清单

### 后端代码 (Java)
```
e:\java\MES\src\main\java\com\fine\
├─ controller\production\ProductionScheduleController.java
│  └─ 新增: 6个REST API端点
├─ service\production\ProductionScheduleService.java
│  └─ 新增: 4个接口方法
└─ serviceIMPL\production\ProductionScheduleServiceImpl.java
   └─ 新增: 5个业务方法 + 2个工具方法
```

### 前端代码 (Vue)
```
e:\vue\ERP\src\
├─ api\slittingTasks.js
│  └─ 新增/已存在: 7个API函数
├─ views\production\
│  ├─ slittingTasks.vue
│  │  └─ 修改: 引用新组件
│  └─ components\SlittingTasksNew.vue
│     └─ 已存在: 完整UI组件
```

### 数据库脚本
```
e:\java\MES\修改分切任务表结构.sql
└─ 新增: 7个字段 + 4个索引
```

### 文档
```
e:\java\MES\
├─ 分切任务功能-快速部署指南.md ⭐ 快速开始必读
├─ 分切任务功能实施-状态报告.md ← 状态查询
├─ 分切任务功能完整实现方案.md ⭐⭐ 深度理解
├─ 分切任务表结构修改-完整实现清单.md ← 细节对照
├─ 分切任务功能实现-工作完成总结.md ← 最终总结
├─ 分切任务功能-资源索引.md ← 完整导航
└─ README.md ← 本文件
```

---

## 🔧 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 2.7.18 | 后端框架 |
| MyBatis Plus | 3.5.3.1 | ORM框架 |
| Vue.js | 2.6.10 | 前端框架 |
| Element UI | 2.13.2 | UI组件库 |
| MySQL | 8.0 | 数据库 |

---

## ✨ 核心特性

### 1. 10分钟时间精度
```java
// 实现位置: ProductionScheduleServiceImpl.roundTimeToTenMinutes()
int minute = cal.get(Calendar.MINUTE);
int roundedMinute = (minute + 5) / 10 * 10;
```

### 2. 自动任务号生成
```java
// 实现位置: ProductionScheduleServiceImpl.generateSlittingTaskNo()
// 格式: SLT-YYYYMMDD-XXXXX
// 示例: SLT-20241220-00001
```

### 3. 自动规格生成
```java
// 实现位置: ProductionScheduleServiceImpl.addSlittingTask()
String spec = String.format("%s-%smm x %sm x %d卷",
    materialCode, targetWidth, slitLength, planRolls);
// 示例: FT-001-50mm x 100m x 100卷
```

### 4. 多条件查询
```java
// 支持: orderNo, status, planDate, equipmentId
// 分页: pageNum, pageSize(默认20)
// 排序: 按创建时间倒序
```

---

## 📊 API 端点

| 方法 | URL | 功能 |
|------|-----|------|
| GET | `/api/production/schedule/slitting/list` | 查询列表(分页) |
| POST | `/api/production/schedule/slitting/add` | 新增任务 |
| PUT | `/api/production/schedule/slitting/update` | 更新任务 |
| DELETE | `/api/production/schedule/slitting/delete/{id}` | 删除任务 |
| POST | `/api/production/schedule/slitting/start/{id}` | 开始任务 |
| POST | `/api/production/schedule/slitting/complete/{id}` | 完成任务 |

### 示例请求

**新增任务:**
```bash
curl -X POST http://localhost:8090/api/production/schedule/slitting/add \
  -H "Content-Type: application/json" \
  -d '{
    "orderNo": "ORD-2024-001",
    "materialCode": "FT-001",
    "targetWidth": 50,
    "slitLength": 100,
    "planRolls": 100,
    "planStartTime": "2024-12-20 14:23:00",
    "planEndTime": "2024-12-20 15:33:00"
  }'
```

返回:
```json
{
  "code": 200,
  "data": {
    "taskNo": "SLT-20241220-00001",
    "spec": "FT-001-50mm x 100m x 100卷",
    "planStartTime": "2024-12-20 14:20:00",
    "planEndTime": "2024-12-20 15:30:00"
  }
}
```

---

## 🧪 测试场景

### 场景1: 新增并验证自动生成
1. 点击"新增"
2. 填写表单(注意输入 14:23 的时间)
3. 点击"保存"
4. 验证:
   - ✅ 任务号自动生成(SLT-20241220-XXXXX)
   - ✅ 规格自动生成(料号-宽度-长度-卷数)
   - ✅ 14:23 自动调整为 14:20

### 场景2: 状态流转
1. 点击任务行的"开始"按钮
2. 验证状态变为"进行中"
3. 点击"完成"按钮
4. 验证状态变为"已完成"

### 场景3: 查询过滤
1. 在"订单号"输入: ORD-2024-001
2. 验证只显示该订单的任务
3. 改变"状态"过滤条件
4. 验证列表更新

---

## ⚠️ 常见问题

### Q: 时间没有四舍五入怎么办?
A: 检查后端是否启动, 确认 roundTimeToTenMinutes() 方法在 addSlittingTask() 中被调用

### Q: 前端显示404怎么办?
A: 
1. 清除浏览器缓存 (Ctrl+Shift+Delete)
2. 检查 slittingTasks.vue 是否正确引用了 SlittingTasksNew 组件
3. 重启前端服务 (npm run dev)

### Q: 数据没有保存怎么办?
A: 
1. 检查浏览器开发者工具 Network 标签
2. 确认 POST 请求返回 200 状态码
3. 检查数据库是否执行了 SQL 迁移脚本

### Q: 如何回滚?
A: 
```bash
# 1. 恢复数据库备份
mysql -h host -u user -p < backup_2024-12-20.sql

# 2. 重启后端(使用旧版本jar)
java -jar target\MES-0.0.1-SNAPSHOT-backup.jar

# 3. 恢复前端(git回滚)
cd e:\vue\ERP
git checkout HEAD -- src/views/production/slittingTasks.vue
npm run dev
```

---

## 📚 文档导航

| 文档 | 用途 | 适合人群 |
|------|------|--------|
| 快速部署指南.md | 5分钟快速部署 | 所有人 |
| 状态报告.md | 了解实施进度 | 项目经理 |
| 完整实现方案.md | 深度理解实现原理 | 开发人员 |
| 完整实现清单.md | 代码修改对照 | 代码审查者 |
| 工作完成总结.md | 最终交付物总结 | 所有人 |
| 资源索引.md | 完整资源导航 | 所有人 |

**建议阅读顺序:**
1. README.md (本文件) - 5分钟了解概况
2. 快速部署指南.md - 10分钟部署
3. 完整实现方案.md - 30分钟深入理解
4. 其他文档 - 按需查阅

---

## ✅ 检查清单

部署前请确认:

- [ ] SQL迁移脚本已执行
- [ ] 后端编译成功(mvn clean package -DskipTests)
- [ ] 后端启动无错误(查看日志)
- [ ] 前端启动无错误(npm run dev)
- [ ] 能打开分切任务页面(http://localhost:9527/production-management/slitting)
- [ ] 能新增任务
- [ ] 时间精度正确(14:23→14:20)
- [ ] 任务号自动生成
- [ ] 规格自动生成
- [ ] 状态流转功能正常

---

## 📞 支持

### 遇到问题?
1. 查看本 README 的"常见问题"部分
2. 查看 [分切任务功能-快速部署指南.md](分切任务功能-快速部署指南.md) 的"常见问题排查"部分
3. 查看 [分切任务功能-资源索引.md](分切任务功能-资源索引.md) 的"常见问题快速导航"部分

### 需要更多详情?
- API文档: 查看 [分切任务功能实现-工作完成总结.md](分切任务功能实现-工作完成总结.md)
- 实现细节: 查看 [分切任务功能完整实现方案.md](分切任务功能完整实现方案.md)
- 修改清单: 查看 [分切任务表结构修改-完整实现清单.md](分切任务表结构修改-完整实现清单.md)

---

## 📈 项目统计

- **总代码行数**: ~600行
- **后端代码**: ~150行 (Java)
- **前端代码**: ~430行 (Vue)
- **数据库脚本**: ~35行 (SQL)
- **文档数**: 7份
- **API端点**: 6个
- **修改文件**: 4个
- **新增数据库字段**: 7个
- **新增索引**: 4个
- **预计部署时间**: 1-2小时

---

## 🎯 下一步

1. **立即执行** - 按照"快速开始"部分执行部署
2. **验证功能** - 按照"测试场景"部分验证
3. **上线部署** - 部署到生产环境
4. **文档更新** - 根据实际情况更新本文档

---

**版本**: v1.0  
**完成时间**: 2024-12-20  
**状态**: ✅ 已准备就绪,等待部署  
**下一步**: 执行数据库迁移 → 编译打包 → 启动测试

祝您部署顺利! 🎉
