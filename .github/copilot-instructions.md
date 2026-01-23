# MES/ERP 系统开发指南

## 架构概览

这是一个双仓库项目：**MES 后端 (Java)** + **ERP 前端 (Vue.js)**，用于制造执行系统(MES)和企业资源规划(ERP)。

```
MES (e:\java\MES)          ← Spring Boot 2.7.18 + MyBatis Plus 后端
ERP (e:\vue\ERP)          ← Vue 2.6.10 + Element UI 前端
                           ↓
共享数据库: MySQL @ ssdw8127.mysql.rds.aliyuncs.com:3306/erp
共享缓存: Redis @ localhost:6379
```

## 核心业务领域

### 涂布排程系统 (Coating Scheduling)
项目最复杂的核心模块，负责胶带生产的智能排程。完整排程算法流程：

#### 1. 客户优先级评分算法
```java
// 实现: CustomerPriorityServiceImpl.calculateOrderPriority()
总分 = 账期得分 + 月均交易额得分 + 单价得分

• 账期得分 (paymentTermsScore):
  - 预付款: +10分
  - 月结30天: 0分
  - 月结60天: -10分
  - 月结90天: -20分

• 月均交易额得分 (avgAmountScore):
  - 近3个月月均交易额 ÷ 30,000
  - 例: 月均10万 → 10万/3万 = 3.33分

• 单价得分 (unitPriceScore):
  - 根据客户历史单价与行业基准价偏差计算
  - (客户历史均价 / 行业均价 - 1) × 50
  - 单价高10% → +5分，单价低5% → -2.5分

优先级级别:
  - HIGH: 总分 >= 10
  - MEDIUM: 0 <= 总分 < 10
  - LOW: 总分 < 0
```

#### 2. 三级库存匹配算法
```java
// 实现: InventoryAllocationService
订单需求 5000㎡ → 逐级匹配:

【第一级】成品库存 (rollType='分切卷'):
  - 料号完全匹配 + availableArea > 0
  - 找到 2000㎡ → 锁定 → 剩余需求 3000㎡

【第二级】复卷库存 (rollType='复卷'):
  - 料号前缀匹配 + availableArea > 0
  - 找到 1500㎡ → 锁定 → 剩余需求 1500㎡

【第三级】母卷库存 (rollType='母卷'):
  - 料号前缀匹配 + availableArea > 0
  - 找到 1000㎡ → 锁定 → 剩余需求 500㎡

【缺口处理】剩余 500㎡ → 触发涂布生产
```

#### 3. 涂布排程算法
```java
// 实现: CoatingScheduleService.generateCoatingTasks()

// 3.1 按颜色+厚度分组
订单A: FT-001-38mm (红色30, 100μm) 3000㎡
订单B: FT-002-38mm (红色30, 100μm) 2000㎡
订单C: FT-003-50mm (蓝色20, 150μm) 4000㎡
→ 分组: {
    "红色30_100": [A, B],  // 合并同色同厚度
    "蓝色20_150": [C]
  }

// 3.2 MOQ检查 (Minimum Order Quantity)
如果批次总量 < MOQ:
  - needConfirmation = true
  - 系统提示: "未达MOQ(最小起订量)，是否继续？"
  - 或推荐: "等待更多同类订单合并"
  
// 3.3 薄膜宽度选择
查询可用薄膜库存:
  SELECT width, available_area, available_rolls
  FROM film_stock
  WHERE thickness = 订单厚度 AND available_area > 0
  ORDER BY width ASC

用户选择宽度后 → 锁定薄膜库存 (ScheduleMaterialLock)

// 3.4 设备分配 (3台涂布机轮询)
coating-1: 最近任务结束于 2024-12-20 10:00
coating-2: 最近任务结束于 2024-12-20 08:30  ← 最早可用
coating-3: 最近任务结束于 2024-12-20 11:15

批次1分配到 coating-2
开始时间 = 08:30 + 换单时间(1小时)
```

#### 4. 设备并行调度算法
```java
// 实现: OrderScheduleOrchestrator
涂布(3台) → 复卷(5台) → 分切(10台)

依赖关系:
  涂布任务完成 → 创建复卷任务
  复卷任务完成 → 创建分切任务
  分切任务完成 → 计算预计交付时间

时间计算:
  任务耗时 = 计划面积 ÷ 设备速度
  开始时间 = MAX(设备最早可用时间, 前道工序完成时间) + 换单时间
```

#### 5. 库存锁定机制
```java
// 实现: MaterialLockService + TapeStock.version
使用乐观锁防止库存超卖:

状态流转:
  pending → locked → allocated → consumed → released

关键字段:
  - TapeStock.version: 乐观锁版本号
  - TapeStock.available_area: 可用面积
  - TapeStock.reserved_area: 已锁定面积
  
锁定操作 (带重试):
  UPDATE tape_stock 
  SET available_area = available_area - #{lockArea},
      reserved_area = reserved_area + #{lockArea},
      version = version + 1
  WHERE id = #{id} AND version = #{oldVersion}
    AND available_area >= #{lockArea}
```

**关键实体**: `TapeStock`, `ScheduleMaterialLock`, `ScheduleMaterialAllocation`, `ProductionSchedule`, `OrderCustomerPriority`  
**核心Service**: `CustomerPriorityService`, `InventoryAllocationService`, `CoatingScheduleService`, `MaterialLockService`, `OrderScheduleOrchestrator`  
**参考文档**: `涂布排程逻辑完整解析.md`, `库存锁定机制-完整实现总结.md`, `精简排程系统-实施完成报告.md`

### 其他业务模块
- **销售订单** (`Order`, `OrderDetail`): 订单主记录 + 明细，关联客户、物料、交付状态
- **报价管理** (`Quotation`): 送样、报价、客户优先级管理
- **原料仓库** (`RawMaterialStock`, `FilmStock`): 原材料和薄膜库存管理
- **生产管理** (`ProductionStaff`, `Equipment`): 人员、设备、工艺参数

## 技术栈约定

### 后端 (MES)
- **框架**: Spring Boot 2.7.18 + Spring Security + MyBatis Plus 3.5.3.1
- **数据库**: MySQL 8.0 + Druid 连接池 + Redis
- **包结构**: `com.fine.{controller, service, serviceIMPL, Dao, mapper, entity, modle, config}`
- **MyBatis Plus 分页**: 使用 `Page<T>` 和 `IPage<T>`，避免手写 limit/offset
- **逻辑删除**: 全局配置 `logic-delete-value=1, logic-not-delete-value=0`
- **响应封装**: 统一使用 `ResponseResult<T>` 包装返回值

#### 重要约定
```java
// ✅ 正确：使用 MyBatis Plus 分页
@GetMapping("/list")
public ResponseResult<IPage<Order>> list(@RequestParam int current, @RequestParam int size) {
    Page<Order> page = new Page<>(current, size);
    IPage<Order> result = orderService.page(page);
    return ResponseResult.success(result);
}

// ✅ 正确：逻辑删除（框架自动处理）
orderService.removeById(id);  // 实际执行 UPDATE ... SET deleted=1

// ❌ 错误：手写分页 SQL (会绕过 MyBatis Plus 拦截器)
@Select("SELECT * FROM orders LIMIT #{offset}, #{size}")
```

### 前端 (ERP)
- **框架**: Vue 2.6.10 + Vue Router 3.0.2 + Vuex 3.1.0 + Element UI 2.13.2
- **API 封装**: `@/utils/request.js` (基于 axios，自动注入 token)
- **目录结构**:
  - `src/api/` - API 接口定义(如 `sales.js`, `coatingSchedule.js`)
  - `src/views/` - 页面组件(如 `sales/`, `production/`, `stock/`)
  - `src/store/` - Vuex 状态管理
  - `src/router/` - 路由配置

#### 前端 API 调用模式
```javascript
// 1. 在 src/api/xxx.js 中定义接口
import request from '@/utils/request'
export function getOrders(params) {
  return request({ url: '/sales/orders', method: 'get', params })
}

// 2. 在组件中调用
import { getOrders } from '@/api/sales'
getOrders({ page: 1, size: 20 }).then(response => {
  this.tableData = response.data
})
```

## 开发工作流

### 启动后端 (MES)
```powershell
cd e:\java\MES

# 1. 启动 Redis (必须)
.\start-redis.ps1

# 2. 启动后端 (推荐使用以下命令)
Write-Host "启动MES后端..." -ForegroundColor Cyan; Start-Process -FilePath "java" -ArgumentList "-jar","target\MES-0.0.1-SNAPSHOT.jar" -NoNewWindow -PassThru | Out-Null; Start-Sleep -Seconds 5; Write-Host "检查服务状态..." -ForegroundColor Yellow; $conn = Get-NetTCPConnection -LocalPort 8090 -ErrorAction SilentlyContinue; if ($conn) { Write-Host "✓ 后端已启动成功！" -ForegroundColor Green; Write-Host "  访问地址: http://localhost:8090" -ForegroundColor Gray } else { Write-Host "× 启动可能失败，请检查日志" -ForegroundColor Red }

# 或使用脚本（如果脚本无语法错误）:
# .\start-backend.ps1

# 或手动编译运行:
# mvn clean package -DskipTests
# java -jar target\MES-0.0.1-SNAPSHOT.jar
```

后端启动端口: **8090**  
Swagger 文档: `http://localhost:8090/swagger-ui.html`

### 启动前端 (ERP)
```powershell
cd e:\vue\ERP
npm run dev    # 开发模式，热重载
# 或 npm run build:prod    # 生产构建
```

前端访问地址: `http://localhost:9527`  
后端 API 地址: `http://localhost:8090` (配置在 `.env.development`)

### 数据库操作
```powershell
# 快速连接数据库
mysql -h ssdw8127.mysql.rds.aliyuncs.com -u david -pdadazhengzheng@feng erp

# 执行 SQL 脚本
Get-Content sql/xxx.sql -Encoding UTF8 | mysql -h ... -u david -pdadazhengzheng@feng erp
```

## 编码规范

### 命名约定
- **Java 类名**: PascalCase (`OrderService`, `TapeStockMapper`)
- **Java 方法**: camelCase (`getOrderById`, `lockInventory`)
- **数据库表名**: snake_case (`tape_stock`, `schedule_material_lock`)
- **前端组件**: PascalCase (`OrderList.vue`)
- **前端方法**: camelCase (`fetchOrderList`)

### REST API 约定
- **URL 格式**: `/模块名/资源名`，如 `/sales/orders`, `/stock/tape`
- **HTTP 方法**: GET(查询) / POST(新增) / PUT(修改) / DELETE(删除)
- **权限控制**: 使用 `@PreAuthorize("hasAuthority('admin')")` 注解

### 常见陷阱

#### 后端
1. **端口冲突**: 确保 8090 端口未被占用，Redis 必须启动在 6379
2. **逻辑删除**: 不要在 SQL 中手写 `deleted=0`，MyBatis Plus 自动处理
3. **分页查询**: 使用 `Page<T>` 而非手写 `LIMIT`，避免绕过插件
4. **乐观锁**: 涉及库存的实体必须有 `@Version` 字段(如 `TapeStock.version`)

#### 前端
1. **跨域**: 开发模式使用 `vue.config.js` 的 proxy 配置，无需后端 CORS
2. **Token**: `request.js` 拦截器自动添加 `X-Token` 头，登录后 token 存储在 Vuex
3. **分页**: Element UI 的 `<el-pagination>` 组件 `current-page` 从 1 开始，后端接口也从 1 开始
4. **⚠️ 状态码不一致**: 部分后端 API 返回 `code: 200`，部分返回 `code: 20000`，前端需兼容两种写法：
   ```javascript
   // ✅ 正确：兼容两种状态码
   if (res.code === 200 || res.code === 20000) {
     // 处理成功响应
   }
   
   // ❌ 错误：只检查一种状态码
   if (res.code === 20000) {  // 可能会漏掉 code:200 的响应
   ```

## 参考文档

- **涂布排程**: `涂布排程逻辑完整解析.md`, `涂布排程调试指南.md`
- **库存锁定**: `库存锁定机制-完整实现总结.md`, `库存锁定机制-快速部署指南.md`
- **精简排程**: `精简排程系统-README.md`
- **快速启动**: `后端启动指南.txt`, `STARTUP-GUIDE.md`

根目录下有大量 `*.md` 文档记录了每个功能的详细设计、实施过程和验证方法，遇到问题时优先查阅相关文档。
