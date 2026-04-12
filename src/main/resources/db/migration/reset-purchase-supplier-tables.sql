-- 供应商测试数据清理脚本
-- 目的：清空供应商主表并重建，恢复成可重新导入的“新表”状态。
-- 执行顺序：先删子表，再重建主表。

SET FOREIGN_KEY_CHECKS = 0;

SET @has_contacts := (
	SELECT COUNT(*)
	FROM information_schema.TABLES
	WHERE TABLE_SCHEMA = DATABASE()
		AND TABLE_NAME = 'purchase_supplier_contacts'
);

SET @sql := IF(@has_contacts > 0, 'DELETE FROM purchase_supplier_contacts', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DROP TABLE IF EXISTS purchase_suppliers;

CREATE TABLE purchase_suppliers (
	id bigint NOT NULL AUTO_INCREMENT,
	supplier_code varchar(64) NOT NULL,
	supplier_name varchar(128) NOT NULL,
	short_name varchar(64) DEFAULT NULL,
	primary_contact_name varchar(64) DEFAULT NULL,
	primary_contact_mobile varchar(32) DEFAULT NULL,
	contact_email varchar(128) DEFAULT NULL,
	contact_address varchar(255) DEFAULT NULL,
	tax_no varchar(64) DEFAULT NULL,
	bank_name varchar(128) DEFAULT NULL,
	bank_account varchar(64) DEFAULT NULL,
	status varchar(32) DEFAULT 'active',
	remark varchar(255) DEFAULT NULL,
	created_by varchar(64) DEFAULT NULL,
	updated_by varchar(64) DEFAULT NULL,
	created_at datetime DEFAULT NULL,
	updated_at datetime DEFAULT NULL,
	is_deleted tinyint DEFAULT '0',
	PRIMARY KEY (id),
	UNIQUE KEY uk_supplier_code (supplier_code),
	KEY idx_supplier_name (supplier_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

SET FOREIGN_KEY_CHECKS = 1;
