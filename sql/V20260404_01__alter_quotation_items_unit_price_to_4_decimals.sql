-- 报价明细单价保留4位小数，避免保存时被四舍五入为2位
ALTER TABLE quotation_items
  MODIFY COLUMN unit_price DECIMAL(18,4) DEFAULT NULL COMMENT '单价（每平方米，4位小数）';
