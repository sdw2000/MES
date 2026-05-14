SELECT COUNT(*) AS c1 FROM tape_stock WHERE material_code='"'"'YKLJ0805'"'"';
SELECT COUNT(*) AS c2 FROM tape_stock WHERE code='"'"'YKLJ0805'"'"';
SELECT id,material_code,code,batch_no,total_rolls,available_area,reserved_area,consumed_area,status,location FROM tape_stock WHERE material_code LIKE '"'"'%YKLJ0805%'"'"' OR code LIKE '"'"'%YKLJ0805%'"'"' ORDER BY id DESC LIMIT 20;
