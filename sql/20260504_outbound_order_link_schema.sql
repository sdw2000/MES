-- 方案B：销售出库结构化订单关联字段
-- 执行环境：MySQL 8+

ALTER TABLE tape_outbound_request
    ADD COLUMN IF NOT EXISTS order_no VARCHAR(50) NULL COMMENT '关联订单号（结构化）' AFTER material_code,
    ADD COLUMN IF NOT EXISTS order_item_id BIGINT NULL COMMENT '关联订单明细ID（结构化）' AFTER order_no,
    ADD COLUMN IF NOT EXISTS delivery_notice_id BIGINT NULL COMMENT '关联发货通知ID（结构化）' AFTER order_item_id,
    ADD COLUMN IF NOT EXISTS delivery_notice_no VARCHAR(50) NULL COMMENT '关联发货通知单号（结构化）' AFTER delivery_notice_id,
    ADD COLUMN IF NOT EXISTS biz_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL' COMMENT '出库业务类型(MANUAL/SALES_AUTO/SALES_REPAIR)' AFTER delivery_notice_no;

CREATE INDEX IF NOT EXISTS idx_tor_order_no ON tape_outbound_request(order_no);
CREATE INDEX IF NOT EXISTS idx_tor_order_item_id ON tape_outbound_request(order_item_id);
CREATE INDEX IF NOT EXISTS idx_tor_notice_no ON tape_outbound_request(delivery_notice_no);
CREATE INDEX IF NOT EXISTS idx_tor_biz_type_status ON tape_outbound_request(biz_type, status);

-- 历史数据回填（可重复执行）
UPDATE tape_outbound_request
SET biz_type = 'MANUAL'
WHERE biz_type IS NULL OR TRIM(biz_type) = '';
