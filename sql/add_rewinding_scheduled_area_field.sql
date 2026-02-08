-- 为 manual_schedule 表添加复卷已排程面积字段
-- 用于跟踪涂布完成后已排入复卷的面积

USE erp;

-- 添加 rewinding_scheduled_area 字段
ALTER TABLE manual_schedule 
ADD COLUMN rewinding_scheduled_area DECIMAL(10, 2) DEFAULT 0 COMMENT '复卷已排程面积(㎡)' 
AFTER coating_area;

-- 验证字段添加
DESC manual_schedule;

-- 说明：
-- 1. rewinding_scheduled_area 记录从涂布产出中已经被排入复卷的面积
-- 2. coating_area - rewinding_scheduled_area = 剩余待复卷面积
-- 3. 当剩余面积为0时，状态从 COATING_SCHEDULED 转为 REWINDING_SCHEDULED
