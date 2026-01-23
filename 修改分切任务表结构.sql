-- 修改分切任务表 (schedule_slitting)
-- 目的: 增加订单号、订单明细号、实际开始时间、操作人等字段

ALTER TABLE schedule_slitting ADD COLUMN `order_id` BIGINT COMMENT '订单ID';
ALTER TABLE schedule_slitting ADD COLUMN `order_no` VARCHAR(64) COMMENT '订单号';
ALTER TABLE schedule_slitting ADD COLUMN `order_item_id` BIGINT COMMENT '订单明细ID';
ALTER TABLE schedule_slitting ADD COLUMN `order_detail_no` VARCHAR(64) COMMENT '订单详情号';
ALTER TABLE schedule_slitting ADD COLUMN `spec` VARCHAR(255) COMMENT '规格信息';
ALTER TABLE schedule_slitting MODIFY COLUMN `plan_start_time` DATETIME COMMENT '计划开始时间(精确到10分钟)';
ALTER TABLE schedule_slitting MODIFY COLUMN `plan_end_time` DATETIME COMMENT '计划结束时间(精确到10分钟)';
ALTER TABLE schedule_slitting ADD COLUMN `actual_start_time` DATETIME COMMENT '实际开始时间';
ALTER TABLE schedule_slitting ADD COLUMN `operator_id` BIGINT COMMENT '操作人ID';
ALTER TABLE schedule_slitting ADD COLUMN `operator_name` VARCHAR(100) COMMENT '操作人名称';
ALTER TABLE schedule_slitting MODIFY COLUMN `remark` VARCHAR(500) COMMENT '备注';

-- 创建索引
CREATE INDEX idx_order_no ON schedule_slitting(`order_no`);
CREATE INDEX idx_order_id ON schedule_slitting(`order_id`);
CREATE INDEX idx_equipment_id ON schedule_slitting(`equipment_id`);
CREATE INDEX idx_plan_date ON schedule_slitting(`plan_date`);
