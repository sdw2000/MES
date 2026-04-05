-- 设备日历与设备人员排班：计划时间从 DATE 升级为 DATETIME
-- 执行前请先备份

ALTER TABLE `equipment_daily_status`
  MODIFY COLUMN `plan_date` DATETIME NOT NULL COMMENT '计划时间';

ALTER TABLE `equipment_staff_assignment`
  MODIFY COLUMN `plan_date` DATETIME NOT NULL COMMENT '计划时间';

-- 将历史仅日期数据统一补齐到 08:00:00（仅处理时分秒为00:00:00的数据）
UPDATE `equipment_daily_status`
SET `plan_date` = DATE_ADD(`plan_date`, INTERVAL 8 HOUR)
WHERE TIME(`plan_date`) = '00:00:00';

UPDATE `equipment_staff_assignment`
SET `plan_date` = DATE_ADD(`plan_date`, INTERVAL 8 HOUR)
WHERE TIME(`plan_date`) = '00:00:00';
