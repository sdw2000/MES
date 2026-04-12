SELECT material_code, HEX(material_code) AS hexv
FROM tape_spec
WHERE material_code REGEXP '[[:space:]]'
LIMIT 20;

SELECT spec_key, HEX(spec_key) AS hexv
FROM quotation_item_versions
WHERE spec_key REGEXP '[[:space:]]'
LIMIT 20;
