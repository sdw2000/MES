-- 配方管理升级脚本（2026-05-04）
-- 目标：
-- 1) 支持胶水类型、工艺温度、工艺车速分字段
-- 2) 文件编号统一为 F00001 流水
-- 3) 胶水型号统一为 GLU-00001 流水
-- 4) 版次统一重置为 A/00

ALTER TABLE tape_formula
    ADD COLUMN IF NOT EXISTS glue_type VARCHAR(20) NULL COMMENT '胶水类型：亚克力/橡胶/硅胶/PU胶' AFTER glue_model,
    ADD COLUMN IF NOT EXISTS process_temperature VARCHAR(255) NULL COMMENT '工艺温度' AFTER coating_area,
    ADD COLUMN IF NOT EXISTS process_speed VARCHAR(255) NULL COMMENT '工艺车速' AFTER process_temperature;

-- 兼容旧数据：把 process_remark 中的“温度=”“车速=”解析回新字段（可重复执行）
UPDATE tape_formula
SET process_temperature = CASE
        WHEN (process_temperature IS NULL OR process_temperature = '')
             AND process_remark LIKE '%温度=%'
        THEN SUBSTRING_INDEX(SUBSTRING_INDEX(process_remark, '温度=', -1), ';', 1)
        ELSE process_temperature
    END,
    process_speed = CASE
        WHEN (process_speed IS NULL OR process_speed = '')
             AND process_remark LIKE '%车速=%'
        THEN SUBSTRING_INDEX(SUBSTRING_INDEX(process_remark, '车速=', -1), ';', 1)
        ELSE process_speed
    END;

-- 一次性重编：文件编号 F00001...
SET @f_seq := 0;
UPDATE tape_formula tf
JOIN (
    SELECT id, (@f_seq := @f_seq + 1) AS seq
    FROM tape_formula
    ORDER BY id ASC
) s ON s.id = tf.id
SET tf.formula_no = CONCAT('F', LPAD(s.seq, 5, '0'));

-- 一次性重编：胶水型号 GLU-00001...
SET @g_seq := 0;
UPDATE tape_formula tf
JOIN (
    SELECT id, (@g_seq := @g_seq + 1) AS seq
    FROM tape_formula
    ORDER BY id ASC
) s ON s.id = tf.id
SET tf.glue_model = CONCAT('GLU-', LPAD(s.seq, 5, '0'));

-- 版次统一重置
UPDATE tape_formula
SET version = 'A/00'
WHERE version IS NULL OR version <> 'A/00';
