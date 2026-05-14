-- 涂布报工母卷明细与入库申请增加数字号字段（对应仓库 sequence_no）

ALTER TABLE manual_schedule_coating_roll
    ADD COLUMN IF NOT EXISTS sequence_no INT NULL COMMENT '数字号（对应仓库sequence_no）' AFTER roll_code;

ALTER TABLE tape_inbound_request
    ADD COLUMN IF NOT EXISTS sequence_no INT NULL COMMENT '数字号（对应仓库sequence_no）' AFTER batch_no;
