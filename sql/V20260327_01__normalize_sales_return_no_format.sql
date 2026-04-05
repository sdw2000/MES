-- 统一历史销售退货单号格式为: RTyyMMdd-001（按日重排）
-- 示例: RT260316-001

START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS tmp_sales_return_no_map;

CREATE TEMPORARY TABLE tmp_sales_return_no_map AS
SELECT
    s.id,
    CONCAT(
        'RT',
        DATE_FORMAT(COALESCE(s.return_date, DATE(s.created_at), CURRENT_DATE), '%y%m%d'),
        '-',
        LPAD(
            ROW_NUMBER() OVER (
                PARTITION BY COALESCE(s.return_date, DATE(s.created_at), CURRENT_DATE)
                ORDER BY COALESCE(s.created_at, '1970-01-01 00:00:00'), s.id
            ),
            3,
            '0'
        )
    ) AS new_return_no
FROM sales_return_orders s;

UPDATE sales_return_orders s
JOIN tmp_sales_return_no_map t ON s.id = t.id
SET s.return_no = t.new_return_no
WHERE s.return_no <> t.new_return_no;

COMMIT;

-- 校验：查看最近30条
SELECT id, return_no, customer, return_date, created_at
FROM sales_return_orders
ORDER BY return_date DESC, created_at DESC, id DESC
LIMIT 30;
