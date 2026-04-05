-- 为手动排程表增加“涂布宽度/涂布长度”字段
-- 兼容 MySQL 5.7/8.0（不使用 IF NOT EXISTS）
ALTER TABLE manual_schedule
  ADD COLUMN coating_width DECIMAL(10,2) NULL COMMENT 'coating width mm',
  ADD COLUMN coating_length DECIMAL(10,2) NULL COMMENT 'coating length m';

-- 可选：若需要历史数据回填（按订单明细规格兜底）
UPDATE manual_schedule ms
LEFT JOIN sales_order_items soi ON ms.order_detail_id = soi.id
SET ms.coating_width = IFNULL(ms.coating_width, soi.width),
    ms.coating_length = IFNULL(ms.coating_length, soi.length)
WHERE ms.schedule_type = 'COATING';
