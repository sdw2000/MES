UPDATE tape_spec
SET material_code = REPLACE(material_code, CONVERT(0xC2A0 USING utf8mb4), '')
WHERE material_code LIKE CONCAT('%', CONVERT(0xC2A0 USING utf8mb4), '%');

UPDATE quotation_item_versions
SET spec_key = REPLACE(spec_key, CONVERT(0xC2A0 USING utf8mb4), ''),
    material_code = REPLACE(material_code, CONVERT(0xC2A0 USING utf8mb4), '')
WHERE spec_key LIKE CONCAT('%', CONVERT(0xC2A0 USING utf8mb4), '%')
   OR material_code LIKE CONCAT('%', CONVERT(0xC2A0 USING utf8mb4), '%');

SELECT 'tape_spec.material_code' AS col_name, COUNT(*) AS cnt
FROM tape_spec
WHERE material_code LIKE CONCAT('%', CONVERT(0xC2A0 USING utf8mb4), '%') OR material_code REGEXP '[[:space:]]'
UNION ALL
SELECT 'quotation_item_versions.spec_key', COUNT(*)
FROM quotation_item_versions
WHERE spec_key LIKE CONCAT('%', CONVERT(0xC2A0 USING utf8mb4), '%') OR spec_key REGEXP '[[:space:]]';
