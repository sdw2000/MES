ALTER TABLE customers
    ADD COLUMN receive_address VARCHAR(500) NULL COMMENT '收货地址(优先发货地址)' AFTER contact_address;

ALTER TABLE customer_contacts
    ADD COLUMN is_receiver TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否收货人:0否 1是' AFTER is_decision_maker;

UPDATE customers
SET receive_address = contact_address
WHERE (receive_address IS NULL OR TRIM(receive_address) = '')
  AND contact_address IS NOT NULL
  AND TRIM(contact_address) <> '';

UPDATE customer_contacts cc
LEFT JOIN (
    SELECT customer_id, SUM(CASE WHEN IFNULL(is_receiver, 0) = 1 THEN 1 ELSE 0 END) AS receiver_cnt
    FROM customer_contacts
    GROUP BY customer_id
) rc ON rc.customer_id = cc.customer_id
JOIN (
    SELECT customer_id, MIN(id) AS first_contact_id
    FROM customer_contacts
    GROUP BY customer_id
) fc ON fc.first_contact_id = cc.id
SET cc.is_receiver = 1
WHERE IFNULL(rc.receiver_cnt, 0) = 0;