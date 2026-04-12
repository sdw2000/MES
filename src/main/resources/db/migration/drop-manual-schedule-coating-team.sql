-- 移除手动排程中的涂布班组字段（班组产量以实际报工为准）
ALTER TABLE manual_schedule
    DROP COLUMN IF EXISTS coating_team;
