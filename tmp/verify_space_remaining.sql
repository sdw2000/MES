SELECT 'tape_spec.material_code' AS col_name, COUNT(*) AS cnt
FROM tape_spec
WHERE material_code LIKE CONCAT('%', 0xC2A0, '%') OR material_code REGEXP '[[:space:]]'
UNION ALL
SELECT 'quotation_item_versions.spec_key', COUNT(*)
FROM quotation_item_versions
WHERE spec_key LIKE CONCAT('%', 0xC2A0, '%') OR spec_key REGEXP '[[:space:]]';
