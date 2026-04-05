-- 修改报价单明细表，删除规格型号字段
-- 执行日期: 2026-02-24

USE erp;

-- 备份现有数据（可选）
-- CREATE TABLE quotation_items_backup_spec AS SELECT * FROM quotation_items;

-- 删除字段
ALTER TABLE `quotation_items`
  DROP COLUMN `specifications`;

-- 验证修改
DESC quotation_items;

SELECT 'quotation_items表字段已修改：删除specifications' AS message;
