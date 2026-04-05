-- 化工请购：实收数量与操作日志

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chemical_purchase_request_item' AND COLUMN_NAME = 'received_qty') = 0,
  'ALTER TABLE chemical_purchase_request_item ADD COLUMN received_qty INT NOT NULL DEFAULT 0 COMMENT ''累计实收数量'' AFTER requested_qty',
  'SELECT ''received_qty exists'''
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS chemical_requisition_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_no VARCHAR(64) NOT NULL,
  request_id BIGINT NULL,
  action_type VARCHAR(32) NOT NULL,
  operator VARCHAR(64) NULL,
  content VARCHAR(500) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_crl_request_no(request_no),
  KEY idx_crl_action(action_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='化工请购操作日志';
