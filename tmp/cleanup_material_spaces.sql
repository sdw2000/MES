UPDATE sales_order_items
SET material_code = REPLACE(REPLACE(REPLACE(REPLACE(material_code,' ',''), CHAR(9),''), CHAR(10),''), CHAR(13),'')
WHERE material_code IS NOT NULL;

UPDATE quotation_items
SET material_code = REPLACE(REPLACE(REPLACE(REPLACE(material_code,' ',''), CHAR(9),''), CHAR(10),''), CHAR(13),'')
WHERE material_code IS NOT NULL;

UPDATE quotation_item_versions
SET material_code = REPLACE(REPLACE(REPLACE(REPLACE(material_code,' ',''), CHAR(9),''), CHAR(10),''), CHAR(13),''),
    spec_key = REPLACE(REPLACE(REPLACE(REPLACE(spec_key,' ',''), CHAR(9),''), CHAR(10),''), CHAR(13),'')
WHERE material_code IS NOT NULL OR spec_key IS NOT NULL;

UPDATE price_rule
SET material_code = REPLACE(REPLACE(REPLACE(REPLACE(material_code,' ',''), CHAR(9),''), CHAR(10),''), CHAR(13),'')
WHERE material_code IS NOT NULL;

UPDATE customer_material_price_stats
SET material_code = REPLACE(REPLACE(REPLACE(REPLACE(material_code,' ',''), CHAR(9),''), CHAR(10),''), CHAR(13),'')
WHERE material_code IS NOT NULL;

UPDATE tape_spec
SET material_code = REPLACE(REPLACE(REPLACE(REPLACE(material_code,' ',''), CHAR(9),''), CHAR(10),''), CHAR(13),'')
WHERE material_code IS NOT NULL;

UPDATE tape_stock
SET material_code = REPLACE(REPLACE(REPLACE(REPLACE(material_code,' ',''), CHAR(9),''), CHAR(10),''), CHAR(13),'')
WHERE material_code IS NOT NULL;

UPDATE customer_material_mapping
SET customer_material_code = REPLACE(REPLACE(REPLACE(REPLACE(customer_material_code,' ',''), CHAR(9),''), CHAR(10),''), CHAR(13),''),
    material_code = REPLACE(REPLACE(REPLACE(REPLACE(material_code,' ',''), CHAR(9),''), CHAR(10),''), CHAR(13),'')
WHERE customer_material_code IS NOT NULL OR material_code IS NOT NULL;

UPDATE order_preprocessing
SET material_code = REPLACE(REPLACE(REPLACE(REPLACE(material_code,' ',''), CHAR(9),''), CHAR(10),''), CHAR(13),'')
WHERE material_code IS NOT NULL;

SELECT 'sales_order_items.material_code' AS col_name, COUNT(*) AS cnt FROM sales_order_items WHERE material_code REGEXP '[[:space:]]'
UNION ALL
SELECT 'quotation_items.material_code', COUNT(*) FROM quotation_items WHERE material_code REGEXP '[[:space:]]'
UNION ALL
SELECT 'quotation_item_versions.material_code', COUNT(*) FROM quotation_item_versions WHERE material_code REGEXP '[[:space:]]'
UNION ALL
SELECT 'quotation_item_versions.spec_key', COUNT(*) FROM quotation_item_versions WHERE spec_key REGEXP '[[:space:]]'
UNION ALL
SELECT 'price_rule.material_code', COUNT(*) FROM price_rule WHERE material_code REGEXP '[[:space:]]'
UNION ALL
SELECT 'customer_material_price_stats.material_code', COUNT(*) FROM customer_material_price_stats WHERE material_code REGEXP '[[:space:]]'
UNION ALL
SELECT 'tape_spec.material_code', COUNT(*) FROM tape_spec WHERE material_code REGEXP '[[:space:]]'
UNION ALL
SELECT 'tape_stock.material_code', COUNT(*) FROM tape_stock WHERE material_code REGEXP '[[:space:]]'
UNION ALL
SELECT 'customer_material_mapping.customer_material_code', COUNT(*) FROM customer_material_mapping WHERE customer_material_code REGEXP '[[:space:]]'
UNION ALL
SELECT 'customer_material_mapping.material_code', COUNT(*) FROM customer_material_mapping WHERE material_code REGEXP '[[:space:]]'
UNION ALL
SELECT 'order_preprocessing.material_code', COUNT(*) FROM order_preprocessing WHERE material_code REGEXP '[[:space:]]';
