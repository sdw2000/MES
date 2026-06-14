-- 增加客户表企业微信Webhook地址字段
ALTER TABLE customers ADD COLUMN wecom_webhook_url VARCHAR(512) DEFAULT NULL COMMENT '企业微信群机器人Webhook地址';
