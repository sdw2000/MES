-- 先处理tape_spec唯一键冲突：若去除NBSP后与现有料号重复，删除带NBSP的重复行
DELETE t1
FROM tape_spec t1
JOIN tape_spec t2
  ON REPLACE(t1.material_code, CONVERT(0xC2A0 USING utf8mb4), '') = t2.material_code
 AND t1.id <> t2.id
WHERE t1.material_code LIKE CONCAT('%', CONVERT(0xC2A0 USING utf8mb4), '%');

-- 再执行去NBSP
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
