-- =====================================================
-- 胶带库存表结构调整 - 增加二维码字段，优化规格字段
-- =====================================================

-- 1. 添加缺失的字段（逐条执行，报错则跳过）
ALTER TABLE tape_stock ADD COLUMN sequence_no INT COMMENT '序列号（同一母卷的第几个子卷）';
ALTER TABLE tape_stock ADD COLUMN original_length INT COMMENT '原始长度(m)';
ALTER TABLE tape_stock ADD COLUMN current_length INT COMMENT '当前剩余长度(m)';

-- 2. 更新现有数据
UPDATE tape_stock SET qr_code = batch_no WHERE qr_code IS NULL OR qr_code = '';
UPDATE tape_stock SET roll_type = '母卷' WHERE roll_type IS NULL OR roll_type = '';
UPDATE tape_stock SET original_length = length WHERE original_length IS NULL AND length IS NOT NULL;
UPDATE tape_stock SET current_length = length WHERE current_length IS NULL AND length IS NOT NULL;

-- 3. 验证数据
SELECT id, batch_no, qr_code, roll_type, thickness, width, length, original_length, current_length 
FROM tape_stock LIMIT 10;
+