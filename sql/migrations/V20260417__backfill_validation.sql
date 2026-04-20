-- V20260417__backfill_validation.sql
-- 回填后验证脚本：抽样校验、单位分布、NULL 统计与一致性检查
-- 使用说明：在测试库运行此脚本，查看结果并手工核验异常样例。

-- 1) 总行数
SELECT COUNT(*) AS total_rows FROM stock_flow_log;

-- 2) 原始单位分布（按 unit）
SELECT unit, COUNT(*) AS cnt
FROM stock_flow_log
GROUP BY unit
ORDER BY cnt DESC;

-- 3) 标准单位分布（按 std_unit）
SELECT std_unit, COUNT(*) AS cnt
FROM stock_flow_log
GROUP BY std_unit
ORDER BY cnt DESC;

-- 4) std_unit / std_change_quantity 为 NULL 的统计
SELECT
  SUM(CASE WHEN std_unit IS NULL THEN 1 ELSE 0 END) AS null_std_unit_count,
  SUM(CASE WHEN std_change_quantity IS NULL THEN 1 ELSE 0 END) AS null_std_change_count,
  SUM(CASE WHEN std_before_quantity IS NULL THEN 1 ELSE 0 END) AS null_std_before_count,
  SUM(CASE WHEN std_after_quantity IS NULL THEN 1 ELSE 0 END) AS null_std_after_count
FROM stock_flow_log;

-- 5) 按物料查看 std_unit 为空比例较高的前 50 个物料（便于人工审查）
SELECT material_code, COUNT(*) AS total_cnt,
  SUM(CASE WHEN std_unit IS NULL THEN 1 ELSE 0 END) AS null_std_cnt,
  ROUND(SUM(CASE WHEN std_unit IS NULL THEN 1 ELSE 0 END)/COUNT(*)*100,2) AS null_pct
FROM stock_flow_log
GROUP BY material_code
HAVING total_cnt > 0
ORDER BY null_std_cnt DESC
LIMIT 50;

-- 6) 检查 std_before + std_change ≈ std_after（允许微小浮点误差 0.001）
SELECT COUNT(*) AS mismatch_count
FROM stock_flow_log
WHERE std_before_quantity IS NOT NULL
  AND std_change_quantity IS NOT NULL
  AND std_after_quantity IS NOT NULL
  AND ABS((std_before_quantity + std_change_quantity) - std_after_quantity) > 0.001;

-- 列出部分不一致样例供人工核对
SELECT id, stock_type, material_code, unit, change_quantity, std_unit, std_change_quantity, std_before_quantity, std_after_quantity,
  (std_before_quantity + std_change_quantity) AS calc_after, std_after_quantity,
  ABS((std_before_quantity + std_change_quantity) - std_after_quantity) AS diff
FROM stock_flow_log
WHERE std_before_quantity IS NOT NULL
  AND std_change_quantity IS NOT NULL
  AND std_after_quantity IS NOT NULL
  AND ABS((std_before_quantity + std_change_quantity) - std_after_quantity) > 0.001
LIMIT 200;

-- 7) 针对原始单位为 g/ml 的校验：确认换算是否合理（g -> kg, ml -> L）
SELECT id, unit, change_quantity, std_unit, std_change_quantity
FROM stock_flow_log
WHERE LOWER(unit) IN ('g','gram','grams','ml','milliliter','milliliters')
LIMIT 200;

-- 8) 查看原始单位为“卷/roll” 的样例（需要基于物料规格做手工回填）
SELECT id, stock_type, material_code, product_name, unit, change_quantity, before_quantity, after_quantity
FROM stock_flow_log
WHERE LOWER(unit) IN ('卷','juan','roll','rolls')
LIMIT 200;

-- 9) 拆分按时间的回填效果：统计回填前后每日新增 std 填充数量
SELECT DATE(create_time) AS dt,
  COUNT(*) AS total_rows,
  SUM(CASE WHEN std_unit IS NOT NULL THEN 1 ELSE 0 END) AS std_filled
FROM stock_flow_log
GROUP BY DATE(create_time)
ORDER BY dt DESC
LIMIT 30;

-- 10) 建议抽样检查（运行 mysql client 并导出结果供审阅）
-- 示例：导出不一致样例到 CSV
-- 在 PowerShell 下：
-- mysql -h <host> -u <user> -p<pass> <db> -e "source sql/migrations/V20260417__backfill_validation.sql" > backfill_validation_output.txt

-- 备注：如需更复杂的回填（例如卷->㎡/kg），需要物料表中包含：单卷面积或单卷重量或薄膜宽度/长度信息，才能写出可靠回填逻辑。
