-- 为销售订单明细增加复卷日期回写字段
ALTER TABLE sales_order_items
  ADD COLUMN rewinding_date DATE NULL COMMENT '复卷日期' AFTER coating_date;
