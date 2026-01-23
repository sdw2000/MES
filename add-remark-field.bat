@echo off
mysql -h ssdw8127.mysql.rds.aliyuncs.com -u mesuser -pu7qH^^$Y9eo erp -e "ALTER TABLE delivery_notice_items ADD COLUMN IF NOT EXISTS remark VARCHAR(500) DEFAULT '' COMMENT '备注';"
echo.
mysql -h ssdw8127.mysql.rds.aliyuncs.com -u mesuser -pu7qH^^$Y9eo erp -e "SHOW COLUMNS FROM delivery_notice_items LIKE 'remark';"
pause
