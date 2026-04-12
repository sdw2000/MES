START TRANSACTION;

WITH report_agg AS (
  SELECT
    ms.order_detail_id,
    SUM(CASE WHEN r.process_type = 'COATING' THEN IFNULL(r.produced_qty,0) ELSE 0 END) AS coating_qty,
    SUM(CASE WHEN r.process_type = 'REWINDING' THEN IFNULL(r.produced_qty,0) ELSE 0 END) AS rewinding_qty,
    SUM(CASE WHEN r.process_type = 'SLITTING' THEN IFNULL(r.produced_qty,0) ELSE 0 END) AS slitting_qty,
    MAX(CASE WHEN r.process_type = 'COATING' AND r.proceed_next_process = 0 THEN 1 ELSE 0 END) AS coating_stop,
    MAX(CASE WHEN r.process_type = 'REWINDING' AND r.proceed_next_process = 0 THEN 1 ELSE 0 END) AS rewinding_stop,
    MAX(CASE WHEN r.process_type = 'SLITTING' AND r.proceed_next_process = 0 THEN 1 ELSE 0 END) AS slitting_stop
  FROM manual_schedule_process_report r
  JOIN manual_schedule ms ON ms.id = r.schedule_id
  WHERE r.is_deleted = 0
    AND ms.order_detail_id IS NOT NULL
  GROUP BY ms.order_detail_id
), calc AS (
  SELECT
    soi.id AS order_detail_id,
    GREATEST(IFNULL(soi.rolls,0),0) AS rolls,
    IFNULL(soi.width,0) AS width_mm,
    IFNULL(soi.length,0) AS length_m,
    IFNULL(soi.sqm,0) AS sqm,
    CASE
      WHEN IFNULL(ra.slitting_stop,0) = 1 THEN IFNULL(ra.slitting_qty,0)
      WHEN IFNULL(ra.rewinding_stop,0) = 1 THEN IFNULL(ra.rewinding_qty,0)
      WHEN IFNULL(ra.coating_stop,0) = 1 THEN IFNULL(ra.coating_qty,0)
      WHEN IFNULL(ra.slitting_qty,0) > 0 THEN IFNULL(ra.slitting_qty,0)
      ELSE 0
    END AS raw_completed
  FROM sales_order_items soi
  LEFT JOIN report_agg ra ON ra.order_detail_id = soi.id
  WHERE IFNULL(soi.is_deleted,0) = 0
), normalized AS (
  SELECT
    order_detail_id,
    rolls,
    LEAST(GREATEST(CAST(ROUND(raw_completed,0) AS SIGNED),0), rolls) AS delivered_qty_new,
    GREATEST(rolls - LEAST(GREATEST(CAST(ROUND(raw_completed,0) AS SIGNED),0), rolls), 0) AS remaining_qty_new,
    CASE
      WHEN GREATEST(rolls - LEAST(GREATEST(CAST(ROUND(raw_completed,0) AS SIGNED),0), rolls), 0) <= 0 THEN 'completed'
      WHEN LEAST(GREATEST(CAST(ROUND(raw_completed,0) AS SIGNED),0), rolls) <= 0 THEN 'not_started'
      ELSE 'partial'
    END AS production_status_new,
    LEAST(
      ROUND((width_mm / 1000.0) * length_m * LEAST(GREATEST(CAST(ROUND(raw_completed,0) AS SIGNED),0), rolls), 2),
      ROUND(sqm, 2)
    ) AS produced_area_new
  FROM calc
)
UPDATE sales_order_items soi
JOIN normalized n ON n.order_detail_id = soi.id
SET soi.delivered_qty = n.delivered_qty_new,
    soi.remaining_qty = n.remaining_qty_new,
    soi.production_status = n.production_status_new,
    soi.produced_area = n.produced_area_new,
    soi.scheduled_qty = CASE
      WHEN n.remaining_qty_new <= 0 THEN n.rolls
      ELSE soi.scheduled_qty
    END,
    soi.updated_at = NOW();

UPDATE sales_orders so
JOIN (
  SELECT
    soi.order_id,
    CASE
      WHEN COUNT(1) > 0
       AND SUM(CASE WHEN (IFNULL(soi.remaining_qty,0) <= 0 OR LOWER(IFNULL(soi.production_status,'')) = 'completed') THEN 1 ELSE 0 END) = COUNT(1)
      THEN 'completed'
      ELSE 'processing'
    END AS new_status
  FROM sales_order_items soi
  WHERE IFNULL(soi.is_deleted,0) = 0
    AND soi.order_id IS NOT NULL
  GROUP BY soi.order_id
) s ON s.order_id = so.id
SET so.status = CASE
      WHEN LOWER(IFNULL(so.status,'')) IN ('cancelled','canceled','closed') THEN so.status
      ELSE s.new_status
    END,
    so.updated_at = NOW()
WHERE IFNULL(so.is_deleted,0) = 0;

COMMIT;
