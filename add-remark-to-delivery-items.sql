-- 为发货通知明细表添加备注字段
ALTER TABLE delivery_notice_items 
ADD COLUMN remark VARCHAR(500) DEFAULT '' COMMENT '备注' AFTER total_weight;
