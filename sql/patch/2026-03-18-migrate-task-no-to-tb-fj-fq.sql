-- =====================================================
-- 任务号统一迁移脚本（TB/FJ/FQ）
-- 目标：
--   涂布   schedule_coating.task_no   -> TB-yyMMdd-两位
--   复卷   schedule_rewinding.task_no -> FJ-yyMMdd-两位
--   分切   schedule_slitting.task_no  -> FQ-yyMMdd-三位
--
-- 同步关联字段：
--   production_report.task_no
--   quality_inspection.task_no
--   coating_material_lock.coating_task_no
--
-- 执行前建议先备份数据库。
-- 适用环境：MySQL 8+
-- =====================================================

START TRANSACTION;

-- -----------------------------------------------------
-- 1) 生成映射表（按计划日期 + id 重新编号）
-- -----------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_taskno_map_coating;
CREATE TEMPORARY TABLE tmp_taskno_map_coating AS
SELECT
    t.id,
    t.task_no AS old_task_no,
    CONCAT('TB-', DATE_FORMAT(t.biz_date, '%y%m%d'), '-', LPAD(t.seq_no, 2, '0')) AS new_task_no
FROM (
    SELECT
        sc.id,
        sc.task_no,
        COALESCE(sc.plan_date, DATE(sc.create_time), CURDATE()) AS biz_date,
        ROW_NUMBER() OVER (
            PARTITION BY COALESCE(sc.plan_date, DATE(sc.create_time), CURDATE())
            ORDER BY sc.id
        ) AS seq_no
    FROM schedule_coating sc
) t;

DROP TEMPORARY TABLE IF EXISTS tmp_taskno_map_rewinding;
CREATE TEMPORARY TABLE tmp_taskno_map_rewinding AS
SELECT
    t.id,
    t.task_no AS old_task_no,
    CONCAT('FJ-', DATE_FORMAT(t.biz_date, '%y%m%d'), '-', LPAD(t.seq_no, 2, '0')) AS new_task_no
FROM (
    SELECT
        sr.id,
        sr.task_no,
        COALESCE(sr.plan_date, DATE(sr.create_time), CURDATE()) AS biz_date,
        ROW_NUMBER() OVER (
            PARTITION BY COALESCE(sr.plan_date, DATE(sr.create_time), CURDATE())
            ORDER BY sr.id
        ) AS seq_no
    FROM schedule_rewinding sr
) t;

DROP TEMPORARY TABLE IF EXISTS tmp_taskno_map_slitting;
CREATE TEMPORARY TABLE tmp_taskno_map_slitting AS
SELECT
    t.id,
    t.task_no AS old_task_no,
    CONCAT('FQ-', DATE_FORMAT(t.biz_date, '%y%m%d'), '-', LPAD(t.seq_no, 3, '0')) AS new_task_no
FROM (
    SELECT
        ss.id,
        ss.task_no,
        COALESCE(ss.plan_date, DATE(ss.create_time), CURDATE()) AS biz_date,
        ROW_NUMBER() OVER (
            PARTITION BY COALESCE(ss.plan_date, DATE(ss.create_time), CURDATE())
            ORDER BY ss.id
        ) AS seq_no
    FROM schedule_slitting ss
) t;

-- -----------------------------------------------------
-- 2) 更新主任务表 task_no（先写临时值规避唯一索引冲突）
-- -----------------------------------------------------
UPDATE schedule_coating sc
JOIN tmp_taskno_map_coating m ON m.id = sc.id
SET sc.task_no = CONCAT('__MIG_TB__', sc.id);

UPDATE schedule_rewinding sr
JOIN tmp_taskno_map_rewinding m ON m.id = sr.id
SET sr.task_no = CONCAT('__MIG_FJ__', sr.id);

UPDATE schedule_slitting ss
JOIN tmp_taskno_map_slitting m ON m.id = ss.id
SET ss.task_no = CONCAT('__MIG_FQ__', ss.id);

UPDATE schedule_coating sc
JOIN tmp_taskno_map_coating m ON m.id = sc.id
SET sc.task_no = m.new_task_no;

UPDATE schedule_rewinding sr
JOIN tmp_taskno_map_rewinding m ON m.id = sr.id
SET sr.task_no = m.new_task_no;

UPDATE schedule_slitting ss
JOIN tmp_taskno_map_slitting m ON m.id = ss.id
SET ss.task_no = m.new_task_no;

-- -----------------------------------------------------
-- 3) 同步 production_report.task_no
-- -----------------------------------------------------
UPDATE production_report pr
JOIN tmp_taskno_map_coating m ON pr.task_id = m.id
SET pr.task_no = m.new_task_no
WHERE UPPER(pr.task_type) = 'COATING';

UPDATE production_report pr
JOIN tmp_taskno_map_rewinding m ON pr.task_id = m.id
SET pr.task_no = m.new_task_no
WHERE UPPER(pr.task_type) = 'REWINDING';

UPDATE production_report pr
JOIN tmp_taskno_map_slitting m ON pr.task_id = m.id
SET pr.task_no = m.new_task_no
WHERE UPPER(pr.task_type) = 'SLITTING';

-- task_id 异常时，尝试按旧 task_no 兜底同步
UPDATE production_report pr
JOIN tmp_taskno_map_coating m ON pr.task_no = m.old_task_no
SET pr.task_no = m.new_task_no
WHERE (pr.task_id IS NULL OR pr.task_id = 0)
  AND UPPER(pr.task_type) = 'COATING';

UPDATE production_report pr
JOIN tmp_taskno_map_rewinding m ON pr.task_no = m.old_task_no
SET pr.task_no = m.new_task_no
WHERE (pr.task_id IS NULL OR pr.task_id = 0)
  AND UPPER(pr.task_type) = 'REWINDING';

UPDATE production_report pr
JOIN tmp_taskno_map_slitting m ON pr.task_no = m.old_task_no
SET pr.task_no = m.new_task_no
WHERE (pr.task_id IS NULL OR pr.task_id = 0)
  AND UPPER(pr.task_type) = 'SLITTING';

-- -----------------------------------------------------
-- 4) 同步 quality_inspection.task_no
-- -----------------------------------------------------
UPDATE quality_inspection qi
JOIN tmp_taskno_map_coating m ON qi.task_id = m.id
SET qi.task_no = m.new_task_no
WHERE UPPER(qi.task_type) = 'COATING';

UPDATE quality_inspection qi
JOIN tmp_taskno_map_rewinding m ON qi.task_id = m.id
SET qi.task_no = m.new_task_no
WHERE UPPER(qi.task_type) = 'REWINDING';

UPDATE quality_inspection qi
JOIN tmp_taskno_map_slitting m ON qi.task_id = m.id
SET qi.task_no = m.new_task_no
WHERE UPPER(qi.task_type) = 'SLITTING';

-- task_id 异常时，尝试按旧 task_no 兜底同步
UPDATE quality_inspection qi
JOIN tmp_taskno_map_coating m ON qi.task_no = m.old_task_no
SET qi.task_no = m.new_task_no
WHERE (qi.task_id IS NULL OR qi.task_id = 0)
  AND UPPER(qi.task_type) = 'COATING';

UPDATE quality_inspection qi
JOIN tmp_taskno_map_rewinding m ON qi.task_no = m.old_task_no
SET qi.task_no = m.new_task_no
WHERE (qi.task_id IS NULL OR qi.task_id = 0)
  AND UPPER(qi.task_type) = 'REWINDING';

UPDATE quality_inspection qi
JOIN tmp_taskno_map_slitting m ON qi.task_no = m.old_task_no
SET qi.task_no = m.new_task_no
WHERE (qi.task_id IS NULL OR qi.task_id = 0)
  AND UPPER(qi.task_type) = 'SLITTING';

-- -----------------------------------------------------
-- 5) 同步 coating_material_lock.coating_task_no
-- -----------------------------------------------------
UPDATE coating_material_lock cml
JOIN tmp_taskno_map_coating m ON cml.coating_task_id = m.id
SET cml.coating_task_no = m.new_task_no;

-- coating_task_id 异常时按旧单号兜底
UPDATE coating_material_lock cml
JOIN tmp_taskno_map_coating m ON cml.coating_task_no = m.old_task_no
SET cml.coating_task_no = m.new_task_no
WHERE cml.coating_task_id IS NULL OR cml.coating_task_id = 0;

COMMIT;

-- -----------------------------------------------------
-- 6) 校验查询（执行后可手动检查）
-- -----------------------------------------------------
SELECT 'schedule_coating' AS table_name, COUNT(*) AS cnt FROM schedule_coating WHERE task_no NOT REGEXP '^TB-[0-9]{6}-[0-9]{2,}$'
UNION ALL
SELECT 'schedule_rewinding', COUNT(*) FROM schedule_rewinding WHERE task_no NOT REGEXP '^FJ-[0-9]{6}-[0-9]{2,}$'
UNION ALL
SELECT 'schedule_slitting', COUNT(*) FROM schedule_slitting WHERE task_no NOT REGEXP '^FQ-[0-9]{6}-[0-9]{3,}$';

SELECT 'production_report-mismatch' AS check_name, COUNT(*) AS cnt
FROM production_report pr
LEFT JOIN schedule_coating sc ON UPPER(pr.task_type) = 'COATING' AND pr.task_id = sc.id
LEFT JOIN schedule_rewinding sr ON UPPER(pr.task_type) = 'REWINDING' AND pr.task_id = sr.id
LEFT JOIN schedule_slitting ss ON UPPER(pr.task_type) = 'SLITTING' AND pr.task_id = ss.id
WHERE UPPER(pr.task_type) IN ('COATING','REWINDING','SLITTING')
  AND pr.task_id IS NOT NULL
  AND (
      (UPPER(pr.task_type) = 'COATING' AND sc.id IS NOT NULL AND pr.task_no <> sc.task_no)
      OR (UPPER(pr.task_type) = 'REWINDING' AND sr.id IS NOT NULL AND pr.task_no <> sr.task_no)
      OR (UPPER(pr.task_type) = 'SLITTING' AND ss.id IS NOT NULL AND pr.task_no <> ss.task_no)
  );

SELECT 'quality_inspection-mismatch' AS check_name, COUNT(*) AS cnt
FROM quality_inspection qi
LEFT JOIN schedule_coating sc ON UPPER(qi.task_type) = 'COATING' AND qi.task_id = sc.id
LEFT JOIN schedule_rewinding sr ON UPPER(qi.task_type) = 'REWINDING' AND qi.task_id = sr.id
LEFT JOIN schedule_slitting ss ON UPPER(qi.task_type) = 'SLITTING' AND qi.task_id = ss.id
WHERE UPPER(qi.task_type) IN ('COATING','REWINDING','SLITTING')
  AND qi.task_id IS NOT NULL
  AND (
      (UPPER(qi.task_type) = 'COATING' AND sc.id IS NOT NULL AND qi.task_no <> sc.task_no)
      OR (UPPER(qi.task_type) = 'REWINDING' AND sr.id IS NOT NULL AND qi.task_no <> sr.task_no)
      OR (UPPER(qi.task_type) = 'SLITTING' AND ss.id IS NOT NULL AND qi.task_no <> ss.task_no)
  );

SELECT 'coating_material_lock-mismatch' AS check_name, COUNT(*) AS cnt
FROM coating_material_lock cml
JOIN schedule_coating sc ON cml.coating_task_id = sc.id
WHERE cml.coating_task_no <> sc.task_no;
