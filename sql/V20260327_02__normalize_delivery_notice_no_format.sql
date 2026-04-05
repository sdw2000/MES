-- 统一历史发货单号格式为：FineyyMMdd + 三位序号
-- 示例：Fine260310061

START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS tmp_delivery_notice_no_map;

CREATE TEMPORARY TABLE tmp_delivery_notice_no_map AS
SELECT
    d.id,
    CONCAT(
        'Fine',
        DATE_FORMAT(COALESCE(DATE(d.delivery_date), DATE(d.created_at), CURRENT_DATE), '%y%m%d'),
        LPAD(
            ROW_NUMBER() OVER (
                PARTITION BY COALESCE(DATE(d.delivery_date), DATE(d.created_at), CURRENT_DATE)
                ORDER BY COALESCE(d.created_at, '1970-01-01 00:00:00'), d.id
            ),
            3,
            '0'
        )
    ) AS new_notice_no
FROM delivery_notices d;

UPDATE delivery_notices d
JOIN tmp_delivery_notice_no_map t ON d.id = t.id
SET d.notice_no = t.new_notice_no;

COMMIT;

-- 校验：查看最近30条
SELECT id, notice_no, order_no, customer, delivery_date, created_at, is_deleted
FROM delivery_notices
ORDER BY COALESCE(delivery_date, DATE(created_at)) DESC, created_at DESC, id DESC
LIMIT 30;
