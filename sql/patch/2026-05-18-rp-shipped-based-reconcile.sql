-- RP客户数据对平脚本（以发货单为准）
-- 目标：将 sales_order_items.delivered_qty / remaining_qty / delivered_area / produced_area
--      与“已发货/已收货”的发货通知单聚合值对齐，解决“报工与发货不一致”问题。
--
-- 使用说明：
-- 1) 建议先在测试库执行并验证；
-- 2) 生产执行前先全库备份；
-- 3) 本脚本默认只处理 RP 客户：RP01/GDRP01/JSRP01/JXRP001/LZRP01/SHRP001；
-- 4) 如需按时间范围处理，可在注释位置追加日期条件。

START TRANSACTION;

-- =========================================================
-- 0) 备份将被修改的订单明细（一次性快照）
-- =========================================================
CREATE TABLE IF NOT EXISTS backup_rp_sales_order_items_reconcile_20260518 AS
SELECT *
FROM sales_order_items
WHERE 1 = 0;

INSERT INTO backup_rp_sales_order_items_reconcile_20260518
SELECT soi.*
FROM sales_order_items soi
INNER JOIN sales_orders so ON so.id = soi.order_id
WHERE soi.is_deleted = 0
  AND so.is_deleted = 0
  AND UPPER(TRIM(IFNULL(so.customer, ''))) IN ('RP01', 'GDRP01', 'JSRP01', 'JXRP001', 'LZRP01', 'SHRP001');
  -- 如需按日期范围，可追加：AND so.order_date >= '2026-01-01'

-- =========================================================
-- 1) 对平前差异统计（执行前可先单独跑这一段看差异）
-- =========================================================
DROP TEMPORARY TABLE IF EXISTS tmp_rp_ship_agg;
CREATE TEMPORARY TABLE tmp_rp_ship_agg AS
SELECT
    dni.order_item_id,
    SUM(IFNULL(dni.quantity, 0)) AS shipped_qty
FROM delivery_notice_items dni
INNER JOIN delivery_notices dn ON dn.id = dni.notice_id
INNER JOIN sales_order_items soi ON soi.id = dni.order_item_id
INNER JOIN sales_orders so ON so.id = soi.order_id
WHERE dn.is_deleted = 0
  AND soi.is_deleted = 0
  AND so.is_deleted = 0
  AND dn.status IN ('已发货', 'shipped', '已收货', 'received')
  AND UPPER(TRIM(IFNULL(so.customer, ''))) IN ('RP01', 'GDRP01', 'JSRP01', 'JXRP001', 'LZRP01', 'SHRP001')
GROUP BY dni.order_item_id;

-- 差异预览（可先单独执行）
SELECT
    so.order_no,
    so.customer,
    soi.id AS order_item_id,
    IFNULL(soi.rolls, 0) AS order_rolls,
    IFNULL(soi.delivered_qty, 0) AS current_delivered_qty,
    LEAST(IFNULL(soi.rolls, 0), IFNULL(sa.shipped_qty, 0)) AS target_delivered_qty,
    IFNULL(soi.delivered_qty, 0) - LEAST(IFNULL(soi.rolls, 0), IFNULL(sa.shipped_qty, 0)) AS diff_qty
FROM sales_order_items soi
INNER JOIN sales_orders so ON so.id = soi.order_id
LEFT JOIN tmp_rp_ship_agg sa ON sa.order_item_id = soi.id
WHERE soi.is_deleted = 0
  AND so.is_deleted = 0
  AND UPPER(TRIM(IFNULL(so.customer, ''))) IN ('RP01', 'GDRP01', 'JSRP01', 'JXRP001', 'LZRP01', 'SHRP001')
  AND IFNULL(soi.delivered_qty, 0) <> LEAST(IFNULL(soi.rolls, 0), IFNULL(sa.shipped_qty, 0))
ORDER BY so.order_no, soi.id;

-- =========================================================
-- 2) 明细对平：以发货聚合值回写 delivered_qty/remaining_qty/area/status
-- =========================================================
UPDATE sales_order_items soi
INNER JOIN sales_orders so ON so.id = soi.order_id
LEFT JOIN tmp_rp_ship_agg sa ON sa.order_item_id = soi.id
SET
    soi.delivered_qty = LEAST(IFNULL(soi.rolls, 0), IFNULL(sa.shipped_qty, 0)),
    soi.remaining_qty = GREATEST(IFNULL(soi.rolls, 0) - LEAST(IFNULL(soi.rolls, 0), IFNULL(sa.shipped_qty, 0)), 0),
    soi.production_status = CASE
        WHEN LEAST(IFNULL(soi.rolls, 0), IFNULL(sa.shipped_qty, 0)) <= 0 THEN 'not_started'
        WHEN LEAST(IFNULL(soi.rolls, 0), IFNULL(sa.shipped_qty, 0)) >= IFNULL(soi.rolls, 0) THEN 'completed'
        ELSE 'partial'
    END,
    soi.delivered_area = CASE
        WHEN IFNULL(soi.rolls, 0) <= 0 OR IFNULL(soi.sqm, 0) <= 0 THEN IFNULL(soi.delivered_area, 0)
        ELSE ROUND((IFNULL(soi.sqm, 0) / IFNULL(soi.rolls, 1)) * LEAST(IFNULL(soi.rolls, 0), IFNULL(sa.shipped_qty, 0)), 2)
    END,
    soi.produced_area = CASE
        WHEN IFNULL(soi.rolls, 0) <= 0 OR IFNULL(soi.sqm, 0) <= 0 THEN IFNULL(soi.produced_area, 0)
        ELSE ROUND((IFNULL(soi.sqm, 0) / IFNULL(soi.rolls, 1)) * LEAST(IFNULL(soi.rolls, 0), IFNULL(sa.shipped_qty, 0)), 2)
    END,
    soi.updated_at = NOW()
WHERE soi.is_deleted = 0
  AND so.is_deleted = 0
  AND UPPER(TRIM(IFNULL(so.customer, ''))) IN ('RP01', 'GDRP01', 'JSRP01', 'JXRP001', 'LZRP01', 'SHRP001');

-- =========================================================
-- 3) 订单状态重算（仅RP客户）
--    口径：先看 shipped，再看 delivered_qty（作为 produced）
-- =========================================================
DROP TEMPORARY TABLE IF EXISTS tmp_rp_order_rollup;
CREATE TEMPORARY TABLE tmp_rp_order_rollup AS
SELECT
    so.id AS order_id,
    SUM(IFNULL(soi.rolls, 0)) AS total_rolls,
    SUM(LEAST(IFNULL(soi.rolls, 0), IFNULL(soi.delivered_qty, 0))) AS produced_rolls,
    SUM(LEAST(IFNULL(soi.rolls, 0), IFNULL(sa.shipped_qty, 0))) AS shipped_rolls
FROM sales_orders so
INNER JOIN sales_order_items soi ON soi.order_id = so.id AND soi.is_deleted = 0
LEFT JOIN tmp_rp_ship_agg sa ON sa.order_item_id = soi.id
WHERE so.is_deleted = 0
  AND UPPER(TRIM(IFNULL(so.customer, ''))) IN ('RP01', 'GDRP01', 'JSRP01', 'JXRP001', 'LZRP01', 'SHRP001')
GROUP BY so.id;

UPDATE sales_orders so
INNER JOIN tmp_rp_order_rollup r ON r.order_id = so.id
SET
    so.status = CASE
        WHEN IFNULL(r.total_rolls, 0) <= 0 THEN 'CREATED'
        WHEN IFNULL(r.shipped_rolls, 0) >= IFNULL(r.total_rolls, 0) THEN 'SHIPPED_FULL'
        WHEN IFNULL(r.shipped_rolls, 0) > 0 THEN 'SHIPPED_PARTIAL'
        WHEN IFNULL(r.produced_rolls, 0) >= IFNULL(r.total_rolls, 0) THEN 'PRODUCED'
        WHEN IFNULL(r.produced_rolls, 0) > 0 THEN 'IN_PRODUCTION'
        ELSE 'CREATED'
    END,
    so.updated_at = NOW()
WHERE so.is_deleted = 0;

-- =========================================================
-- 4) 对平后校验
-- =========================================================
SELECT
    COUNT(1) AS diff_item_count
FROM sales_order_items soi
INNER JOIN sales_orders so ON so.id = soi.order_id
LEFT JOIN tmp_rp_ship_agg sa ON sa.order_item_id = soi.id
WHERE soi.is_deleted = 0
  AND so.is_deleted = 0
  AND UPPER(TRIM(IFNULL(so.customer, ''))) IN ('RP01', 'GDRP01', 'JSRP01', 'JXRP001', 'LZRP01', 'SHRP001')
  AND IFNULL(soi.delivered_qty, 0) <> LEAST(IFNULL(soi.rolls, 0), IFNULL(sa.shipped_qty, 0));

COMMIT;

-- 回滚参考（需要人工确认后执行）：
-- START TRANSACTION;
-- UPDATE sales_order_items soi
-- INNER JOIN backup_rp_sales_order_items_reconcile_20260518 b ON b.id = soi.id
-- SET soi.delivered_qty = b.delivered_qty,
--     soi.remaining_qty = b.remaining_qty,
--     soi.production_status = b.production_status,
--     soi.delivered_area = b.delivered_area,
--     soi.produced_area = b.produced_area,
--     soi.updated_at = NOW();
-- COMMIT;
