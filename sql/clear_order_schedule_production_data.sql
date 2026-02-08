-- 清空订单/排程/生产相关数据（不动仓库库存表）
-- 适用库：erp
-- 注意：会清空订单与排程、生产执行相关表数据

SET FOREIGN_KEY_CHECKS = 0;

-- 订单与订单明细
TRUNCATE TABLE sales_order_items;
TRUNCATE TABLE sales_orders;
TRUNCATE TABLE order_details;
TRUNCATE TABLE orders;

-- 订单锁定与预处理
TRUNCATE TABLE order_material_lock;
TRUNCATE TABLE order_material_issue;
TRUNCATE TABLE order_preprocessing;
TRUNCATE TABLE material_lock_shortage;
TRUNCATE TABLE schedule_material_lock;
TRUNCATE TABLE schedule_material_allocation;
TRUNCATE TABLE coating_material_lock;

-- 排程主表与任务明细
TRUNCATE TABLE production_schedule;
TRUNCATE TABLE batch_schedules;
TRUNCATE TABLE coating_schedule;
TRUNCATE TABLE schedule_coating;
TRUNCATE TABLE schedule_rewinding;
TRUNCATE TABLE schedule_slitting;
TRUNCATE TABLE schedule_stripping;
TRUNCATE TABLE schedule_printing;
TRUNCATE TABLE schedule_approval_log;
TRUNCATE TABLE equipment_schedule;

-- 待排程池/进度/日志
TRUNCATE TABLE pending_coating_pool;
TRUNCATE TABLE pending_coating_order_pool;
TRUNCATE TABLE pending_rewinding_order_pool;
TRUNCATE TABLE pending_slitting_order_pool;
TRUNCATE TABLE order_schedule_progress;
TRUNCATE TABLE urgent_insert_order;

-- 生产执行结果/报工
TRUNCATE TABLE slitting_task;
TRUNCATE TABLE slitting_result;
TRUNCATE TABLE production_report;
TRUNCATE TABLE cost_tracking;
TRUNCATE TABLE quality_inspection;
TRUNCATE TABLE quality_inspection_item;
TRUNCATE TABLE quality_disposition;

SET FOREIGN_KEY_CHECKS = 1;
