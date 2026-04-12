START TRANSACTION;

-- 仅针对手动排程主表关联的数据做清理
CREATE TEMPORARY TABLE tmp_ms_ids AS
SELECT id, order_detail_id
FROM manual_schedule;

DELETE l
FROM schedule_material_lock l
JOIN tmp_ms_ids t ON t.id = l.schedule_id;

DELETE e
FROM equipment_occupation e
JOIN tmp_ms_ids t ON t.id = e.schedule_id;

DELETE x
FROM manual_schedule_process_material_issue x
JOIN tmp_ms_ids t ON t.id = x.schedule_id;

DELETE x
FROM manual_schedule_process_report x
JOIN tmp_ms_ids t ON t.id = x.schedule_id;

DELETE x
FROM manual_schedule_coating_roll x
JOIN tmp_ms_ids t ON t.id = x.schedule_id;

DELETE x
FROM manual_schedule_coating_order_lock x
JOIN tmp_ms_ids t ON t.id = x.schedule_id;

DELETE x
FROM manual_schedule_coating_allocation x
JOIN tmp_ms_ids t ON t.id = x.schedule_id;

DELETE sp
FROM schedule_plan sp
JOIN tmp_ms_ids t ON t.order_detail_id = sp.order_detail_id
WHERE sp.stage IN ('COATING','REWINDING','SLITTING');

DELETE ms
FROM manual_schedule ms
JOIN tmp_ms_ids t ON t.id = ms.id;

COMMIT;

-- 校验：应全部为0
SELECT 'manual_schedule' AS t, COUNT(*) AS c FROM manual_schedule
UNION ALL
SELECT 'manual_schedule_coating_allocation', COUNT(*) FROM manual_schedule_coating_allocation
UNION ALL
SELECT 'manual_schedule_coating_order_lock', COUNT(*) FROM manual_schedule_coating_order_lock
UNION ALL
SELECT 'manual_schedule_coating_roll', COUNT(*) FROM manual_schedule_coating_roll
UNION ALL
SELECT 'manual_schedule_process_material_issue', COUNT(*) FROM manual_schedule_process_material_issue
UNION ALL
SELECT 'manual_schedule_process_report', COUNT(*) FROM manual_schedule_process_report
UNION ALL
SELECT 'equipment_occupation(schedule_id in manual)', COUNT(*)
FROM equipment_occupation eo
WHERE EXISTS (SELECT 1 FROM manual_schedule ms WHERE ms.id = eo.schedule_id)
UNION ALL
SELECT 'schedule_material_lock(schedule_id in manual)', COUNT(*)
FROM schedule_material_lock l
WHERE EXISTS (SELECT 1 FROM manual_schedule ms WHERE ms.id = l.schedule_id);
