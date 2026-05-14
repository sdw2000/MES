SELECT 'tape_stock_exact' AS src, COUNT(*) AS cnt FROM tape_stock WHERE material_code='YKLJ0805';
SELECT 'tape_stock_like' AS src, COUNT(*) AS cnt FROM tape_stock WHERE material_code LIKE '%YKLJ0805%';
SELECT 'tape_rolls_exact' AS src, COUNT(*) AS cnt FROM tape_stock_rolls WHERE material_code='YKLJ0805' AND COALESCE(available_area,0)>0;
SELECT 'film_detail_exact' AS src, COUNT(*) AS cnt FROM film_stock_detail WHERE material_code='YKLJ0805' AND status='available' AND is_deleted=0;
SELECT 'chemical_detail_exact' AS src, COUNT(*) AS cnt FROM chemical_stock_detail WHERE material_code='YKLJ0805' AND status='available' AND is_deleted=0;
SELECT 'film_stock_exact' AS src, COUNT(*) AS cnt FROM film_stock WHERE material_code='YKLJ0805' AND is_deleted=0;
SELECT 'chemical_stock_exact' AS src, COUNT(*) AS cnt FROM chemical_stock WHERE material_code='YKLJ0805' AND is_deleted=0;
SELECT id,material_code,batch_no,total_rolls,available_area,status,location,stock_type,roll_type,reel_type FROM tape_stock WHERE material_code LIKE '%YKLJ0%' ORDER BY id DESC LIMIT 20;
