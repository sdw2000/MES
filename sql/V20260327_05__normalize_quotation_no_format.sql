-- 历史报价单号修复：QT-20260313-001 -> QT-260313-001
-- 同步更新 quotations 与 quotation_item_versions

START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS tmp_quotation_no_map;
CREATE TEMPORARY TABLE tmp_quotation_no_map AS
SELECT
    q.id,
    q.quotation_no AS old_no,
    CONCAT(
        'QT-',
        DATE_FORMAT(COALESCE(q.quotation_date, DATE(q.created_at), CURDATE()), '%y%m%d'),
        '-',
        LPAD(
            ROW_NUMBER() OVER (
                PARTITION BY DATE_FORMAT(COALESCE(q.quotation_date, DATE(q.created_at), CURDATE()), '%Y-%m-%d')
                ORDER BY COALESCE(q.quotation_date, DATE(q.created_at), CURDATE()), q.id
            ),
            3,
            '0'
        )
    ) AS new_no
FROM quotations q;

UPDATE quotations q
JOIN tmp_quotation_no_map m ON q.id = m.id
SET q.quotation_no = m.new_no
WHERE COALESCE(q.quotation_no, '') <> COALESCE(m.new_no, '');

SET @has_version_table = (
    SELECT COUNT(*)
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'quotation_item_versions'
);
SET @sql = IF(
    @has_version_table > 0,
    'UPDATE quotation_item_versions v JOIN tmp_quotation_no_map m ON v.quotation_id = m.id SET v.quotation_no = m.new_no WHERE COALESCE(v.quotation_no, '''') <> COALESCE(m.new_no, '''')',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

COMMIT;

SELECT id, quotation_no, quotation_date, created_at
FROM quotations
ORDER BY id DESC
LIMIT 30;
