-- 修改报价单主表，删除总金额、总面积字段
-- 执行日期: 2026-02-24

USE erp;

-- 备份现有数据（可选）
-- CREATE TABLE quotations_backup AS SELECT * FROM quotations;

-- 删除字段
ALTER TABLE `quotations`
  DROP COLUMN `total_amount`,
  DROP COLUMN `total_area`;

-- 验证修改
DESC quotations;

SELECT 'quotations表字段已修改：删除total_amount、total_area' AS message;
