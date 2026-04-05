-- 复卷/分切工艺参数独立视图表
-- 基于 process_params 单表按工序拆分，页面CRUD仍走 process_params，数据实时同步可见

CREATE OR REPLACE VIEW rewinding_process_params AS
SELECT *
FROM process_params
WHERE process_type = 'REWINDING';

CREATE OR REPLACE VIEW slitting_process_params AS
SELECT *
FROM process_params
WHERE process_type = 'SLITTING';
