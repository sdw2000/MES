-- 全量迁移送样编号到新规则：SPyyMMdd-001
-- 覆盖所有 sample_no / source_sample_no 字段

START TRANSACTION;

-- 统一转换表达式说明：
-- 1) SPYYYYMMDDNNN  -> SPYYMMDD-NNN
-- 2) SPYYYYMMDD-NNN -> SPYYMMDD-NNN
-- 3) SPYYMMDDNNN    -> SPYYMMDD-NNN

UPDATE sample_orders
SET sample_no = CASE
    WHEN sample_no REGEXP '^SP[0-9]{8}[0-9]{3}$' THEN CONCAT('SP', SUBSTRING(sample_no, 5, 2), SUBSTRING(sample_no, 7, 2), SUBSTRING(sample_no, 9, 2), '-', SUBSTRING(sample_no, 11, 3))
    WHEN sample_no REGEXP '^SP[0-9]{8}-[0-9]{3}$' THEN CONCAT('SP', SUBSTRING(sample_no, 5, 2), SUBSTRING(sample_no, 7, 2), SUBSTRING(sample_no, 9, 2), '-', SUBSTRING(sample_no, 12, 3))
    WHEN sample_no REGEXP '^SP[0-9]{6}[0-9]{3}$' THEN CONCAT(SUBSTRING(sample_no, 1, 8), '-', SUBSTRING(sample_no, 9, 3))
    ELSE sample_no
END
WHERE sample_no REGEXP '^SP([0-9]{8}[0-9]{3}|[0-9]{8}-[0-9]{3}|[0-9]{6}[0-9]{3})$';

UPDATE sample_items
SET sample_no = CASE
    WHEN sample_no REGEXP '^SP[0-9]{8}[0-9]{3}$' THEN CONCAT('SP', SUBSTRING(sample_no, 5, 2), SUBSTRING(sample_no, 7, 2), SUBSTRING(sample_no, 9, 2), '-', SUBSTRING(sample_no, 11, 3))
    WHEN sample_no REGEXP '^SP[0-9]{8}-[0-9]{3}$' THEN CONCAT('SP', SUBSTRING(sample_no, 5, 2), SUBSTRING(sample_no, 7, 2), SUBSTRING(sample_no, 9, 2), '-', SUBSTRING(sample_no, 12, 3))
    WHEN sample_no REGEXP '^SP[0-9]{6}[0-9]{3}$' THEN CONCAT(SUBSTRING(sample_no, 1, 8), '-', SUBSTRING(sample_no, 9, 3))
    ELSE sample_no
END
WHERE sample_no REGEXP '^SP([0-9]{8}[0-9]{3}|[0-9]{8}-[0-9]{3}|[0-9]{6}[0-9]{3})$';

UPDATE sample_logistics_records
SET sample_no = CASE
    WHEN sample_no REGEXP '^SP[0-9]{8}[0-9]{3}$' THEN CONCAT('SP', SUBSTRING(sample_no, 5, 2), SUBSTRING(sample_no, 7, 2), SUBSTRING(sample_no, 9, 2), '-', SUBSTRING(sample_no, 11, 3))
    WHEN sample_no REGEXP '^SP[0-9]{8}-[0-9]{3}$' THEN CONCAT('SP', SUBSTRING(sample_no, 5, 2), SUBSTRING(sample_no, 7, 2), SUBSTRING(sample_no, 9, 2), '-', SUBSTRING(sample_no, 12, 3))
    WHEN sample_no REGEXP '^SP[0-9]{6}[0-9]{3}$' THEN CONCAT(SUBSTRING(sample_no, 1, 8), '-', SUBSTRING(sample_no, 9, 3))
    ELSE sample_no
END
WHERE sample_no REGEXP '^SP([0-9]{8}[0-9]{3}|[0-9]{8}-[0-9]{3}|[0-9]{6}[0-9]{3})$';

UPDATE sample_status_history
SET sample_no = CASE
    WHEN sample_no REGEXP '^SP[0-9]{8}[0-9]{3}$' THEN CONCAT('SP', SUBSTRING(sample_no, 5, 2), SUBSTRING(sample_no, 7, 2), SUBSTRING(sample_no, 9, 2), '-', SUBSTRING(sample_no, 11, 3))
    WHEN sample_no REGEXP '^SP[0-9]{8}-[0-9]{3}$' THEN CONCAT('SP', SUBSTRING(sample_no, 5, 2), SUBSTRING(sample_no, 7, 2), SUBSTRING(sample_no, 9, 2), '-', SUBSTRING(sample_no, 12, 3))
    WHEN sample_no REGEXP '^SP[0-9]{6}[0-9]{3}$' THEN CONCAT(SUBSTRING(sample_no, 1, 8), '-', SUBSTRING(sample_no, 9, 3))
    ELSE sample_no
END
WHERE sample_no REGEXP '^SP([0-9]{8}[0-9]{3}|[0-9]{8}-[0-9]{3}|[0-9]{6}[0-9]{3})$';

UPDATE quotations
SET source_sample_no = CASE
    WHEN source_sample_no REGEXP '^SP[0-9]{8}[0-9]{3}$' THEN CONCAT('SP', SUBSTRING(source_sample_no, 5, 2), SUBSTRING(source_sample_no, 7, 2), SUBSTRING(source_sample_no, 9, 2), '-', SUBSTRING(source_sample_no, 11, 3))
    WHEN source_sample_no REGEXP '^SP[0-9]{8}-[0-9]{3}$' THEN CONCAT('SP', SUBSTRING(source_sample_no, 5, 2), SUBSTRING(source_sample_no, 7, 2), SUBSTRING(source_sample_no, 9, 2), '-', SUBSTRING(source_sample_no, 12, 3))
    WHEN source_sample_no REGEXP '^SP[0-9]{6}[0-9]{3}$' THEN CONCAT(SUBSTRING(source_sample_no, 1, 8), '-', SUBSTRING(source_sample_no, 9, 3))
    ELSE source_sample_no
END
WHERE source_sample_no REGEXP '^SP([0-9]{8}[0-9]{3}|[0-9]{8}-[0-9]{3}|[0-9]{6}[0-9]{3})$';

UPDATE quotation_items
SET sample_no = CASE
    WHEN sample_no REGEXP '^SP[0-9]{8}[0-9]{3}$' THEN CONCAT('SP', SUBSTRING(sample_no, 5, 2), SUBSTRING(sample_no, 7, 2), SUBSTRING(sample_no, 9, 2), '-', SUBSTRING(sample_no, 11, 3))
    WHEN sample_no REGEXP '^SP[0-9]{8}-[0-9]{3}$' THEN CONCAT('SP', SUBSTRING(sample_no, 5, 2), SUBSTRING(sample_no, 7, 2), SUBSTRING(sample_no, 9, 2), '-', SUBSTRING(sample_no, 12, 3))
    WHEN sample_no REGEXP '^SP[0-9]{6}[0-9]{3}$' THEN CONCAT(SUBSTRING(sample_no, 1, 8), '-', SUBSTRING(sample_no, 9, 3))
    ELSE sample_no
END
WHERE sample_no REGEXP '^SP([0-9]{8}[0-9]{3}|[0-9]{8}-[0-9]{3}|[0-9]{6}[0-9]{3})$';

UPDATE quotation_item_versions
SET source_sample_no = CASE
    WHEN source_sample_no REGEXP '^SP[0-9]{8}[0-9]{3}$' THEN CONCAT('SP', SUBSTRING(source_sample_no, 5, 2), SUBSTRING(source_sample_no, 7, 2), SUBSTRING(source_sample_no, 9, 2), '-', SUBSTRING(source_sample_no, 11, 3))
    WHEN source_sample_no REGEXP '^SP[0-9]{8}-[0-9]{3}$' THEN CONCAT('SP', SUBSTRING(source_sample_no, 5, 2), SUBSTRING(source_sample_no, 7, 2), SUBSTRING(source_sample_no, 9, 2), '-', SUBSTRING(source_sample_no, 12, 3))
    WHEN source_sample_no REGEXP '^SP[0-9]{6}[0-9]{3}$' THEN CONCAT(SUBSTRING(source_sample_no, 1, 8), '-', SUBSTRING(source_sample_no, 9, 3))
    ELSE source_sample_no
END
WHERE source_sample_no REGEXP '^SP([0-9]{8}[0-9]{3}|[0-9]{8}-[0-9]{3}|[0-9]{6}[0-9]{3})$';

COMMIT;

-- 迁移后校验
SELECT COUNT(*) AS old_count_sample_orders FROM sample_orders WHERE sample_no REGEXP '^SP([0-9]{8}[0-9]{3}|[0-9]{8}-[0-9]{3}|[0-9]{6}[0-9]{3})$';
SELECT COUNT(*) AS old_count_sample_items FROM sample_items WHERE sample_no REGEXP '^SP([0-9]{8}[0-9]{3}|[0-9]{8}-[0-9]{3}|[0-9]{6}[0-9]{3})$';
SELECT COUNT(*) AS old_count_sample_logistics FROM sample_logistics_records WHERE sample_no REGEXP '^SP([0-9]{8}[0-9]{3}|[0-9]{8}-[0-9]{3}|[0-9]{6}[0-9]{3})$';
SELECT COUNT(*) AS old_count_sample_status FROM sample_status_history WHERE sample_no REGEXP '^SP([0-9]{8}[0-9]{3}|[0-9]{8}-[0-9]{3}|[0-9]{6}[0-9]{3})$';
SELECT COUNT(*) AS old_count_quotations FROM quotations WHERE source_sample_no REGEXP '^SP([0-9]{8}[0-9]{3}|[0-9]{8}-[0-9]{3}|[0-9]{6}[0-9]{3})$';
SELECT COUNT(*) AS old_count_quotation_items FROM quotation_items WHERE sample_no REGEXP '^SP([0-9]{8}[0-9]{3}|[0-9]{8}-[0-9]{3}|[0-9]{6}[0-9]{3})$';
SELECT COUNT(*) AS old_count_quotation_versions FROM quotation_item_versions WHERE source_sample_no REGEXP '^SP([0-9]{8}[0-9]{3}|[0-9]{8}-[0-9]{3}|[0-9]{6}[0-9]{3})$';

SELECT sample_no, send_date, create_time FROM sample_orders ORDER BY id DESC LIMIT 30;
