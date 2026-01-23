-- 修改报价单明细表，删除数量、平米数、金额字段
-- 执行日期: 2026-01-05
-- 说明: 简化报价单明细，只保留基本信息和单价

USE erp;

-- 备份现有数据（可选）
-- CREATE TABLE quotation_items_backup AS SELECT * FROM quotation_items;

-- 删除字段
ALTER TABLE `quotation_items` 
  DROP COLUMN `quantity`,
  DROP COLUMN `sqm`,
  DROP COLUMN `amount`;

-- 验证修改
DESC quotation_items;

-- 查看当前数据
SELECT * FROM quotation_items WHERE is_deleted = 0 LIMIT 5;

SELECT 'quotation_items表字段已修改：删除quantity、sqm、amount' AS message;
