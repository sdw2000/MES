        START TRANSACTION;

UPDATE film_stock_detail d
JOIN (
    SELECT DISTINCT detail_id
    FROM film_stock_out
    WHERE id BETWEEN 440 AND 451
      AND detail_id IS NOT NULL
      AND detail_id > 0
) t ON d.id = t.detail_id
SET d.status = 'available',
    d.locked_by = NULL,
    d.locked_time = NULL,
    d.update_by = 'rollback-test',
    d.update_time = NOW();

DELETE FROM film_stock_out
WHERE id BETWEEN 440 AND 451;

UPDATE film_stock fs
JOIN (
    SELECT
        d.stock_id AS stock_id,
        IFNULL(SUM(d.area), 0) AS total_area,
        IFNULL(SUM(CASE WHEN LOWER(IFNULL(d.status, '')) = 'available' THEN d.area ELSE 0 END), 0) AS available_area,
        IFNULL(SUM(CASE WHEN LOWER(IFNULL(d.status, '')) = 'locked' THEN d.area ELSE 0 END), 0) AS locked_area,
        IFNULL(SUM(CASE WHEN d.pack_count IS NULL OR d.pack_count <= 0 THEN 1 ELSE d.pack_count END), 0) AS total_rolls,
        IFNULL(SUM(CASE WHEN LOWER(IFNULL(d.status, '')) = 'available'
                        THEN (CASE WHEN d.pack_count IS NULL OR d.pack_count <= 0 THEN 1 ELSE d.pack_count END)
                        ELSE 0 END), 0) AS available_rolls,
        IFNULL(SUM(CASE WHEN LOWER(IFNULL(d.status, '')) = 'locked'
                        THEN (CASE WHEN d.pack_count IS NULL OR d.pack_count <= 0 THEN 1 ELSE d.pack_count END)
                        ELSE 0 END), 0) AS locked_rolls
    FROM film_stock_detail d
    WHERE d.is_deleted = 0
      AND d.stock_id = 425
    GROUP BY d.stock_id
) s ON fs.id = s.stock_id
SET fs.total_area = s.total_area,
    fs.available_area = s.available_area,
    fs.locked_area = s.locked_area,
    fs.total_rolls = s.total_rolls,
    fs.available_rolls = s.available_rolls,
    fs.locked_rolls = s.locked_rolls,
    fs.total_pack_count = s.total_rolls,
    fs.available_pack_count = s.available_rolls,
    fs.locked_pack_count = s.locked_rolls,
    fs.status = CASE
        WHEN s.available_area <= 0 THEN 'out_of_stock'
        WHEN fs.safety_stock IS NOT NULL AND s.available_area < fs.safety_stock THEN 'low_stock'
        ELSE 'active'
    END,
    fs.update_by = 'rollback-test',
    fs.update_time = NOW()
WHERE fs.id = 425;

COMMIT;
