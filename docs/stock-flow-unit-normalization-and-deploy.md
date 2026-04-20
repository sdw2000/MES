# stock_flow_log 单位规范化与部署执行说明

## 1. 单位规范化规则（后端 `UnitService`）

实现位置：
- [src/main/java/com/fine/service/UnitService.java](src/main/java/com/fine/service/UnitService.java)
- [src/main/java/com/fine/serviceIMPL/UnitServiceImpl.java](src/main/java/com/fine/serviceIMPL/UnitServiceImpl.java)

### 1.1 标准单位
- 重量标准：`kg`
- 体积标准：`L`
- 面积标准：`㎡`
- 计数标准：`卷`（`桶`默认保持计数单位，不做强制跨维度）

### 1.2 默认支持的单位别名
- 重量：`g/gram/克`，`kg/kilogram/千克`，`t/ton/吨`
- 体积：`ml/毫升`，`L/l/liter/升`，`m3/立方米`
- 面积：`㎡/m2/sqm/m²`
- 计数：`卷/roll/rolls`，`桶/barrel/drum`

### 1.3 上下文换算（可选）
`UnitService` 支持上下文参数（`Map<String, BigDecimal>`）：
- `bucketVolumeL`：每桶升数（用于 `桶 -> L`）
- `rollAreaSqm`：每卷面积（用于 `卷 -> ㎡`）

> 无上下文时：
> - `桶`保持为`桶`
> - `卷`保持为`卷`

## 2. 已补充测试

测试文件：
- [src/test/java/com/fine/service/UnitServiceImplTest.java](src/test/java/com/fine/service/UnitServiceImplTest.java)

覆盖点：
- g↔kg、ml↔L、t↔kg
- 单位别名归一
- 未知单位透传
- 上下文换算：桶->L、卷->㎡、㎡->卷
- 跨量纲转换保护

## 3. 数据库执行顺序（建议测试库先跑）

1) 字段与回填：
- [sql/migrations/V20260417__add_std_fields_and_backfill.sql](sql/migrations/V20260417__add_std_fields_and_backfill.sql)

2) 索引：
- [sql/migrations/V20260417__stock_flow_indexes.sql](sql/migrations/V20260417__stock_flow_indexes.sql)

3) 回填验证：
- [sql/migrations/V20260417__backfill_validation.sql](sql/migrations/V20260417__backfill_validation.sql)

## 4. 生产部署清单（可直接照执行）

### 4.1 变更前
- 备份 `stock_flow_log`：
  - `mysqldump -h <host> -u <user> -p<pass> <db> stock_flow_log > stock_flow_log_backup.sql`
- 确认应用版本包含 `UnitService` 新实现。

### 4.2 变更中
- 执行字段/回填脚本
- 执行索引脚本
- 重启后端应用

### 4.3 变更后
- 执行验证脚本并留存输出
- 抽查近 1 天新增流水：`std_unit/std_change_quantity` 填充情况
- 对 `卷/桶` 样本进行人工核查（是否需要补充上下文回填）

## 5. 回滚策略
- 索引回滚：按索引名 `DROP INDEX`
- 字段回滚：按迁移脚本中的注释执行 `ALTER TABLE ... DROP COLUMN`
- 应用回滚：回退到上一个稳定版本并重启

## 6. 已知限制
- `卷 <-> ㎡`、`桶 <-> L` 在无规格上下文时不强制换算。
- 若需要完全可比统计，请在业务层补齐 `rollAreaSqm` / `bucketVolumeL` 上下文或落地物料规格主数据。
