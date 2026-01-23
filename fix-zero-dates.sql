-- 修复零日期值问题
-- 将所有 0000-00-00 日期转换为 NULL

USE erp;

-- 修复 sample_orders 表中的零日期
UPDATE sample_orders SET send_date = NULL WHERE send_date = '0000-00-00';
UPDATE sample_orders SET expected_feedback_date = NULL WHERE expected_feedback_date = '0000-00-00';
UPDATE sample_orders SET ship_date = NULL WHERE ship_date = '0000-00-00';
UPDATE sample_orders SET delivery_date = NULL WHERE delivery_date = '0000-00-00';
UPDATE sample_orders SET feedback_date = NULL WHERE feedback_date = '0000-00-00';

-- 显示修复后的数据
SELECT id, sample_no, send_date, ship_date, delivery_date FROM sample_orders;
