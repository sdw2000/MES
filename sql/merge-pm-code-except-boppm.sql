SET @old_sql_safe_updates := @@SQL_SAFE_UPDATES;
SET SQL_SAFE_UPDATES=0;

DROP PROCEDURE IF EXISTS sp_merge_pm_code;
DELIMITER $$
CREATE PROCEDURE sp_merge_pm_code()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE v_table VARCHAR(128);
  DECLARE v_col VARCHAR(128);

  DECLARE cur CURSOR FOR
    SELECT TABLE_NAME, COLUMN_NAME
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND DATA_TYPE IN ('char','varchar','tinytext','text','mediumtext','longtext')
    ORDER BY TABLE_NAME, ORDINAL_POSITION;

  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  DROP TEMPORARY TABLE IF EXISTS tmp_pm_merge_log;
  CREATE TEMPORARY TABLE tmp_pm_merge_log (
    table_name VARCHAR(128),
    column_name VARCHAR(128),
    affected_rows BIGINT
  );

  OPEN cur;
  read_loop: LOOP
    FETCH cur INTO v_table, v_col;
    IF done = 1 THEN
      LEAVE read_loop;
    END IF;

    SET @sql = CONCAT(
      'UPDATE `', v_table, '` ',
      'SET `', v_col, '` = REPLACE(REPLACE(REPLACE(`', v_col, '`, ''BOPPM-'', ''__KEEP_BOPPM__''), ''PM-'', ''PM''), ''__KEEP_BOPPM__'', ''BOPPM-'') ',
      'WHERE `', v_col, '` IS NOT NULL AND `', v_col, '` <> REPLACE(REPLACE(REPLACE(`', v_col, '`, ''BOPPM-'', ''__KEEP_BOPPM__''), ''PM-'', ''PM''), ''__KEEP_BOPPM__'', ''BOPPM-'')'
    );

    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    SET @rc = ROW_COUNT();
    DEALLOCATE PREPARE stmt;

    IF @rc > 0 THEN
      INSERT INTO tmp_pm_merge_log(table_name, column_name, affected_rows)
      VALUES (v_table, v_col, @rc);
    END IF;
  END LOOP;
  CLOSE cur;

  SELECT * FROM tmp_pm_merge_log ORDER BY affected_rows DESC, table_name, column_name;
  SELECT COUNT(*) AS updated_columns, IFNULL(SUM(affected_rows),0) AS total_affected_rows FROM tmp_pm_merge_log;
END $$
DELIMITER ;

CALL sp_merge_pm_code();
DROP PROCEDURE IF EXISTS sp_merge_pm_code;

SET SQL_SAFE_UPDATES=@old_sql_safe_updates;
