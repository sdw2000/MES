-- 入库申请新增“供商批次号”字段，用于来料追踪
ALTER TABLE tape_inbound_request
  ADD COLUMN customer_batch_no VARCHAR(128) NULL COMMENT '供商批次号（追踪）' AFTER batch_no;

-- 历史数据回填：默认用原生产批次号兜底
UPDATE tape_inbound_request
SET customer_batch_no = batch_no
WHERE (customer_batch_no IS NULL OR customer_batch_no = '')
  AND batch_no IS NOT NULL
  AND batch_no <> '';

-- 常用查询索引（按供商批次追溯）
CREATE INDEX idx_tape_inbound_request_customer_batch_no
  ON tape_inbound_request (customer_batch_no);
