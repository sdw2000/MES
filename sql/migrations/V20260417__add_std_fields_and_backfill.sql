-- V20260417__add_std_fields_and_backfill.sql
-- 说明：新增统一流水表的标准量字段，并提供基础回填脚本。
-- 使用前请在测试库验证并备份 `stock_flow_log` 表。

START TRANSACTION;

-- 1) 添加列（兼容老数据，允许 NULL）
ALTER TABLE stock_flow_log
  ADD COLUMN std_change_quantity DECIMAL(15,3) NULL AFTER change_quantity,
  ADD COLUMN std_unit VARCHAR(32) NULL AFTER std_change_quantity,
  ADD COLUMN std_before_quantity DECIMAL(15,3) NULL AFTER before_quantity,
  ADD COLUMN std_after_quantity DECIMAL(15,3) NULL AFTER after_quantity;

-- 2) 建议索引（可选：根据查询样式再调整）
CREATE INDEX IF NOT EXISTS idx_stockflow_type_material_time ON stock_flow_log (stock_type, material_code, create_time);

-- 3) 回填示例：仅做常见单位的简单换算
-- 注意：卷(roll/卷) 到 面积(㎡) 或重量(kg) 的换算需要物料规格（宽度、单卷面积或单重），此处无法自动处理，保留 NULL。

-- 将常见单位标准化为 kg / L / ㎡。其他未知单位保持 NULL。
UPDATE stock_flow_log
SET
  std_unit = CASE
    WHEN LOWER(unit) IN ('g','gram','grams') THEN 'kg'
    WHEN LOWER(unit) IN ('kg','kilogram','kilograms') THEN 'kg'
    WHEN LOWER(unit) IN ('ml','milliliter','milliliters') THEN 'L'
    WHEN LOWER(unit) IN ('l','liter','litre','liters') THEN 'L'
    WHEN unit IN ('㎡','m2','sqm') THEN '㎡'
    WHEN LOWER(unit) IN ('卷','juan','roll','rolls') THEN NULL
    ELSE NULL
  END,
  std_change_quantity = CASE
    WHEN LOWER(unit) IN ('g','gram','grams') THEN COALESCE(change_quantity,0) / 1000
    WHEN LOWER(unit) IN ('kg','kilogram','kilograms') THEN COALESCE(change_quantity,0)
    WHEN LOWER(unit) IN ('ml','milliliter','milliliters') THEN COALESCE(change_quantity,0) / 1000
    WHEN LOWER(unit) IN ('l','liter','litre','liters') THEN COALESCE(change_quantity,0)
    WHEN unit IN ('㎡','m2','sqm') THEN COALESCE(change_quantity,0)
    ELSE NULL
  END,
  std_before_quantity = CASE
    WHEN LOWER(unit) IN ('g','gram','grams') THEN COALESCE(before_quantity,0) / 1000
    WHEN LOWER(unit) IN ('kg','kilogram','kilograms') THEN COALESCE(before_quantity,0)
    WHEN LOWER(unit) IN ('ml','milliliter','milliliters') THEN COALESCE(before_quantity,0) / 1000
    WHEN LOWER(unit) IN ('l','liter','litre','liters') THEN COALESCE(before_quantity,0)
    WHEN unit IN ('㎡','m2','sqm') THEN COALESCE(before_quantity,0)
    ELSE NULL
  END,
  std_after_quantity = CASE
    WHEN LOWER(unit) IN ('g','gram','grams') THEN COALESCE(after_quantity,0) / 1000
    WHEN LOWER(unit) IN ('kg','kilogram','kilograms') THEN COALESCE(after_quantity,0)
    WHEN LOWER(unit) IN ('ml','milliliter','milliliters') THEN COALESCE(after_quantity,0) / 1000
    WHEN LOWER(unit) IN ('l','liter','litre','liters') THEN COALESCE(after_quantity,0)
    WHEN unit IN ('㎡','m2','sqm') THEN COALESCE(after_quantity,0)
    ELSE NULL
  END
WHERE unit IS NOT NULL;

COMMIT;

-- 回滚脚本（如需撤销变更）
-- ALTER TABLE stock_flow_log
--   DROP COLUMN std_change_quantity,
--   DROP COLUMN std_unit,
--   DROP COLUMN std_before_quantity,
--   DROP COLUMN std_after_quantity;

-- 使用说明：
-- 1) 在测试库执行本脚本。执行前请备份：
--    mysqldump -h <host> -u <user> -p<pass> <db> stock_flow_log > stock_flow_log_backup.sql
-- 2) 若使用 Flyway/Liquibase，请把本文件转为相应迁移格式并在 CI 中运行。
-- 3) 回填后，建议检查样例记录（特别是单位为 '卷'/'roll' 的记录），并视情况手工回填或编写更复杂回填逻辑（依赖物料规格）。
