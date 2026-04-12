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
    END AS production_status_new
  FROM calc
)
SELECT
  COUNT(1) AS total_items,
  SUM(CASE WHEN IFNULL(soi.delivered_qty,0) <> n.delivered_qty_new THEN 1 ELSE 0 END) AS diff_delivered,
  SUM(CASE WHEN IFNULL(soi.remaining_qty,0) <> n.remaining_qty_new THEN 1 ELSE 0 END) AS diff_remaining,
  SUM(CASE WHEN LOWER(IFNULL(soi.production_status,'')) <> n.production_status_new THEN 1 ELSE 0 END) AS diff_status,
  SUM(CASE WHEN IFNULL(soi.delivered_qty,0) <> n.delivered_qty_new
        OR IFNULL(soi.remaining_qty,0) <> n.remaining_qty_new
        OR LOWER(IFNULL(soi.production_status,'')) <> n.production_status_new THEN 1 ELSE 0 END) AS diff_any
FROM normalized n
JOIN sales_order_items soi ON soi.id = n.order_detail_id;