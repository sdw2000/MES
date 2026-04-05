-- 为手动排程中出现的涂布料号补录默认工艺参数（避免前端速度列为空）
INSERT INTO process_params (
  material_code,
  process_type,
  equipment_code,
  coating_speed,
  status,
  create_time,
  update_time,
  remark
)
SELECT DISTINCT
  ms.material_code,
  'COATING' AS process_type,
  '' AS equipment_code,
  20 AS coating_speed,
  1 AS status,
  NOW() AS create_time,
  NOW() AS update_time,
  '系统补录默认涂布速度' AS remark
FROM manual_schedule ms
WHERE ms.schedule_type = 'COATING'
  AND ms.material_code IS NOT NULL
  AND ms.material_code <> ''
  AND NOT EXISTS (
    SELECT 1
    FROM process_params pp
    WHERE pp.material_code = ms.material_code
      AND pp.process_type = 'COATING'
      AND IFNULL(pp.equipment_code, '') = ''
  );

SELECT material_code, process_type, equipment_code, coating_speed
FROM process_params
WHERE process_type = 'COATING' AND status = 1
ORDER BY update_time DESC
LIMIT 50;
