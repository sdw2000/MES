-- V20260417__stock_flow_indexes.sql
-- 目标：提升 stock_flow_log 常用查询性能，并提供归档前置索引。
-- 说明：MySQL 8.0+ 可使用 IF NOT EXISTS，若版本不支持请改为先查询 information_schema 再执行。

START TRANSACTION;

-- 1) 常用筛选：类型 + 料号 + 时间
CREATE INDEX IF NOT EXISTS idx_sfl_type_material_time
    ON stock_flow_log (stock_type, material_code, create_time);

-- 2) 库存对象追踪：库存类型 + 库存ID + 时间（查看某库存全流水）
CREATE INDEX IF NOT EXISTS idx_sfl_stockid_time
    ON stock_flow_log (stock_type, stock_id, create_time);

-- 3) 批次追踪：批次 + 时间
CREATE INDEX IF NOT EXISTS idx_sfl_batch_time
    ON stock_flow_log (batch_no, create_time);

-- 4) 业务单号追踪：引用单号 + 时间
CREATE INDEX IF NOT EXISTS idx_sfl_ref_time
    ON stock_flow_log (ref_no, create_time);

-- 5) 操作类型统计：类型 + 操作 + 时间
CREATE INDEX IF NOT EXISTS idx_sfl_type_op_time
    ON stock_flow_log (stock_type, type, create_time);

COMMIT;

-- 回滚（按需）
-- DROP INDEX idx_sfl_type_material_time ON stock_flow_log;
-- DROP INDEX idx_sfl_stockid_time ON stock_flow_log;
-- DROP INDEX idx_sfl_batch_time ON stock_flow_log;
-- DROP INDEX idx_sfl_ref_time ON stock_flow_log;
-- DROP INDEX idx_sfl_type_op_time ON stock_flow_log;
