USE erp;
ALTER TABLE schedule_coating ADD COLUMN order_no VARCHAR(50) COMMENT 'order_number';
ALTER TABLE schedule_coating ADD COLUMN order_id BIGINT COMMENT 'order_id';
ALTER TABLE schedule_coating ADD COLUMN order_item_id BIGINT COMMENT 'order_item_id';
SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_NAME='schedule_coating' AND COLUMN_NAME IN ('order_no', 'order_id', 'order_item_id');