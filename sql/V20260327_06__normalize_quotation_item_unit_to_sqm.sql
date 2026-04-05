-- 统一报价单单位：平米/m2/m²/? -> ㎡

START TRANSACTION;

SET NAMES utf8mb4;

UPDATE quotation_items
SET unit = CONVERT(0xE38EA1 USING utf8mb4)
WHERE HEX(unit) IN ('E5B9B3E7B1B3', '6D32', '6DC2', '3F');

UPDATE quotation_item_versions
SET unit = CONVERT(0xE38EA1 USING utf8mb4)
WHERE HEX(unit) IN ('E5B9B3E7B1B3', '6D32', '6DC2', '3F');

COMMIT;

SELECT HEX(unit) AS unit_hex, COUNT(*) AS cnt FROM quotation_items GROUP BY HEX(unit);
SELECT HEX(unit) AS unit_hex, COUNT(*) AS cnt FROM quotation_item_versions GROUP BY HEX(unit);
