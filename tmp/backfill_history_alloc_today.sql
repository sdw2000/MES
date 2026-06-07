DROP PROCEDURE IF EXISTS sp_backfill_history_alloc_today;
DELIMITER $$
CREATE PROCEDURE sp_backfill_history_alloc_today(IN p_day DATE, IN p_operator VARCHAR(64))
BEGIN
    DECLARE done_receipt INT DEFAULT 0;
    DECLARE v_receipt_id BIGINT;
    DECLARE v_customer_code VARCHAR(100);
    DECLARE v_allocated DECIMAL(18,2);
    DECLARE v_remain DECIMAL(18,2);
    DECLARE v_allocated_hist DECIMAL(18,2);

    DECLARE cur_receipt CURSOR FOR
        SELECT r.id, r.customer_code, IFNULL(r.allocated_amount,0) AS allocated_amount
        FROM finance_ar_receipt r
        WHERE r.is_deleted = 0
          AND DATE(r.updated_at) = p_day
          AND IFNULL(r.allocated_amount,0) > 0
          AND IFNULL(r.reconcile_status,'UNRECONCILED') IN ('FULL','PARTIAL')
          AND NOT EXISTS (
              SELECT 1 FROM finance_ar_history_allocation ha WHERE ha.receipt_id = r.id
          )
        ORDER BY r.updated_at ASC, r.id ASC;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done_receipt = 1;

    CREATE TABLE IF NOT EXISTS finance_ar_history_allocation (
        id BIGINT PRIMARY KEY AUTO_INCREMENT,
        receipt_id BIGINT NOT NULL,
        history_id BIGINT NOT NULL,
        customer_code VARCHAR(100) NOT NULL,
        statement_month VARCHAR(7) NOT NULL,
        allocation_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
        created_by VARCHAR(64) NULL,
        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
        KEY idx_receipt_id (receipt_id),
        KEY idx_history_id (history_id),
        KEY idx_customer_month (customer_code, statement_month)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

    CREATE TEMPORARY TABLE IF NOT EXISTS tmp_backfill_log (
        receipt_id BIGINT,
        customer_code VARCHAR(100),
        receipt_allocated DECIMAL(18,2),
        history_allocated DECIMAL(18,2),
        left_unallocated DECIMAL(18,2)
    );
    TRUNCATE TABLE tmp_backfill_log;

    OPEN cur_receipt;
    receipt_loop: LOOP
        FETCH cur_receipt INTO v_receipt_id, v_customer_code, v_allocated;
        IF done_receipt = 1 THEN
            LEAVE receipt_loop;
        END IF;

        SET v_remain = IFNULL(v_allocated,0);
        SET v_allocated_hist = 0;

        BEGIN
            DECLARE done_hist INT DEFAULT 0;
            DECLARE h_id BIGINT;
            DECLARE h_month VARCHAR(7);
            DECLARE h_unpaid DECIMAL(18,2);
            DECLARE v_take DECIMAL(18,2);

            DECLARE cur_hist CURSOR FOR
                SELECT h.id, h.statement_month, IFNULL(h.unpaid_amount,0) AS unpaid_amount
                FROM sales_statement_history h
                WHERE h.is_deleted = 0
                  AND h.customer_code = v_customer_code
                  AND IFNULL(h.unpaid_amount,0) > 0
                ORDER BY h.statement_month ASC, h.id ASC;

            DECLARE CONTINUE HANDLER FOR NOT FOUND SET done_hist = 1;

            OPEN cur_hist;
            hist_loop: LOOP
                FETCH cur_hist INTO h_id, h_month, h_unpaid;
                IF done_hist = 1 OR v_remain <= 0 THEN
                    LEAVE hist_loop;
                END IF;

                SET v_take = LEAST(v_remain, IFNULL(h_unpaid,0));
                IF v_take > 0 THEN
                    UPDATE sales_statement_history
                    SET unpaid_amount = unpaid_amount - v_take,
                        updated_by = p_operator,
                        updated_at = NOW()
                    WHERE id = h_id AND is_deleted = 0;

                    INSERT INTO finance_ar_history_allocation(
                        receipt_id, history_id, customer_code, statement_month, allocation_amount, created_by, created_at
                    ) VALUES (
                        v_receipt_id, h_id, v_customer_code, h_month, v_take, p_operator, NOW()
                    );

                    SET v_remain = v_remain - v_take;
                    SET v_allocated_hist = v_allocated_hist + v_take;
                END IF;
            END LOOP;
            CLOSE cur_hist;
        END;

        INSERT INTO tmp_backfill_log(receipt_id, customer_code, receipt_allocated, history_allocated, left_unallocated)
        VALUES (v_receipt_id, v_customer_code, v_allocated, v_allocated_hist, v_remain);
    END LOOP;

    CLOSE cur_receipt;

    SELECT * FROM tmp_backfill_log ORDER BY receipt_id;
END$$
DELIMITER ;

CALL sp_backfill_history_alloc_today('2026-05-30', 'manual-ar-history-deduct-batch');
DROP PROCEDURE IF EXISTS sp_backfill_history_alloc_today;

SELECT COUNT(*) AS receipts_left_without_history_alloc
FROM finance_ar_receipt r
WHERE r.is_deleted = 0
  AND DATE(r.updated_at) = '2026-05-30'
  AND IFNULL(r.allocated_amount,0) > 0
  AND IFNULL(r.reconcile_status,'UNRECONCILED') IN ('FULL','PARTIAL')
  AND NOT EXISTS (SELECT 1 FROM finance_ar_history_allocation ha WHERE ha.receipt_id = r.id);
