-- ===================================================================
-- 客户表新增订单号前缀/后缀字段
-- ===================================================================

ALTER TABLE customers
  ADD COLUMN order_no_prefix VARCHAR(20) DEFAULT NULL COMMENT '订单号前缀' AFTER industry,
  ADD COLUMN order_no_suffix VARCHAR(20) DEFAULT NULL COMMENT '订单号后缀' AFTER order_no_prefix;

SELECT 'Migration completed: Added order_no_prefix/order_no_suffix to customers' AS result;
