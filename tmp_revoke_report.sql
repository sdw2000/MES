SELECT id, operator_name, process_type, schedule_id, produced_qty, is_deleted, created_at
FROM manual_schedule_process_report
WHERE operator_name LIKE '%wangchengjun%'
ORDER BY id DESC
LIMIT 10;
