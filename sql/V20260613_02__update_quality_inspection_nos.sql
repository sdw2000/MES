-- 历史质检单号重写策略：PQC + 日期码(yyMMdd) + 4位序列号
-- 1. 先将唯一索引列清空/重设为一个绝对唯一的临时值，避免更新过程中的唯一性冲突
UPDATE quality_inspection SET inspection_no = CONCAT('TEMP_', id) WHERE is_deleted = 0;

-- 2. 按照日期分组并生成新的 PQC 编号
-- 使用子查询为每天的记录分配一个从1开始的序号
UPDATE quality_inspection q
JOIN (
    SELECT 
        id,
        ROW_NUMBER() OVER (PARTITION BY DATE(inspection_time) ORDER BY id ASC) as daily_seq,
        DATE_FORMAT(inspection_time, '%y%m%d') as date_part
    FROM quality_inspection
) seq_table ON q.id = seq_table.id
SET q.inspection_no = CONCAT('PQC', seq_table.date_part, LPAD(seq_table.daily_seq, 4, '0'));

-- 3. 校验是否有遗漏（有些 inspection_time 可能是 null，兜底到 created_at）
UPDATE quality_inspection 
SET inspection_no = CONCAT('PQC', DATE_FORMAT(COALESCE(inspection_time, created_at, NOW()), '%y%m%d'), LPAD(id % 10000, 4, '0'))
WHERE inspection_no LIKE 'TEMP_%';

-- 查看前 20 条修改结果
SELECT id, inspection_no, inspection_time, overall_result 
FROM quality_inspection 
ORDER BY inspection_time DESC 
LIMIT 20;
