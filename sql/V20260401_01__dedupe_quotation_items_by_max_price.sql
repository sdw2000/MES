-- 目的：同一客户+料号仅保留“最高价格”一组，设为版本1，其它删除/逻辑删除
-- 说明：使用 MySQL 8 窗口函数按 unit_price DESC, quotation_date DESC, id DESC 取第一条

-- 0) 备份（可重复执行）
CREATE TABLE IF NOT EXISTS quotation_items_backup_20260401_v4 AS
SELECT * FROM quotation_items;

CREATE TABLE IF NOT EXISTS quotation_item_versions_backup_20260401_v4 AS
SELECT * FROM quotation_item_versions;

-- 1) 生成保留版本清单（每客户+料号仅保留最高价）
DROP TEMPORARY TABLE IF EXISTS keep_qiv;
CREATE TEMPORARY TABLE keep_qiv AS
SELECT id, quotation_item_id, customer, material_code
FROM (
  SELECT v.*, 
         ROW_NUMBER() OVER (
           PARTITION BY v.customer, v.material_code
           ORDER BY v.unit_price DESC, v.quotation_date DESC, v.id DESC
         ) AS rn
  FROM quotation_item_versions v
  WHERE v.material_code IS NOT NULL AND v.material_code <> ''
) t
WHERE t.rn = 1;

-- 2) 保留的版本统一设为版本1
UPDATE quotation_item_versions v
JOIN keep_qiv k ON v.id = k.id
SET v.version_no = 1;

-- 3) 删除其他版本（仅保留每客户+料号最高价一条）
DELETE v FROM quotation_item_versions v
LEFT JOIN keep_qiv k ON v.id = k.id
WHERE k.id IS NULL;

-- 4) 逻辑删除对应的报价明细（只处理有版本记录的明细）
UPDATE quotation_items qi
JOIN quotation_item_versions v ON v.quotation_item_id = qi.id
LEFT JOIN keep_qiv k ON v.quotation_item_id = k.quotation_item_id
SET qi.is_deleted = 1
WHERE k.quotation_item_id IS NULL AND qi.is_deleted = 0;

-- 5) 校验
SELECT customer, material_code, COUNT(*) AS cnt
FROM quotation_item_versions
GROUP BY customer, material_code
HAVING cnt > 1;

SELECT COUNT(*) AS remaining_active_items
FROM quotation_items qi
WHERE qi.is_deleted = 0;
