-- 1) 客户物料映射增加客户订单号维度
SET @db = DATABASE();

SET @sql = IF(
  EXISTS(
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'customer_material_mapping' AND COLUMN_NAME = 'customer_order_no'
  ),
  'SELECT 1',
  'ALTER TABLE `customer_material_mapping` ADD COLUMN `customer_order_no` VARCHAR(128) NOT NULL DEFAULT '''' COMMENT ''客户订单号（可选）'' AFTER `customer_material_code`'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE customer_material_mapping
SET customer_order_no = ''
WHERE customer_order_no IS NULL OR TRIM(customer_order_no) = '';

ALTER TABLE `customer_material_mapping`
  MODIFY COLUMN `customer_order_no` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '客户订单号（可选）';

SET @sql = IF(
  EXISTS(
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'customer_material_mapping' AND INDEX_NAME = 'uk_customer_material_spec'
  ),
  'DROP INDEX `uk_customer_material_spec` ON `customer_material_mapping`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE UNIQUE INDEX `uk_customer_material_spec`
ON `customer_material_mapping` (`customer_code`, `material_code`, `customer_order_no`, `thickness`, `width`, `length`);

-- 2) 标签打印记录表（保存各种标签打印数据，便于后续查阅）
CREATE TABLE IF NOT EXISTS `label_print_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `scene_name` VARCHAR(64) DEFAULT NULL COMMENT '打印场景名',
  `biz_type` VARCHAR(64) DEFAULT NULL COMMENT '业务类型',
  `template_key` VARCHAR(255) DEFAULT NULL COMMENT '模板键/路径',
  `job_name` VARCHAR(255) DEFAULT NULL COMMENT '打印任务名',
  `copies` INT DEFAULT NULL COMMENT '份数',
  `customer_code` VARCHAR(64) DEFAULT NULL COMMENT '客户代码',
  `customer_order_no` VARCHAR(128) DEFAULT NULL COMMENT '客户订单号',
  `order_no` VARCHAR(128) DEFAULT NULL COMMENT '内部订单号',
  `material_code` VARCHAR(128) DEFAULT NULL COMMENT '物料代码',
  `material_name` VARCHAR(255) DEFAULT NULL COMMENT '物料名称',
  `batch_no` VARCHAR(128) DEFAULT NULL COMMENT '批次号',
  `printer_name` VARCHAR(255) DEFAULT NULL COMMENT '打印机',
  `print_status` VARCHAR(16) DEFAULT NULL COMMENT 'SUCCESS/FAIL',
  `result_message` VARCHAR(1000) DEFAULT NULL COMMENT '结果说明',
  `print_data_json` LONGTEXT DEFAULT NULL COMMENT '标签数据JSON',
  `print_payload_json` LONGTEXT DEFAULT NULL COMMENT '发送网关payload JSON',
  `print_result_json` LONGTEXT DEFAULT NULL COMMENT '网关返回JSON',
  `operator` VARCHAR(64) DEFAULT NULL COMMENT '操作人',
  `print_time` DATETIME DEFAULT NULL COMMENT '打印时间',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_lpr_order_no` (`order_no`),
  KEY `idx_lpr_customer_order_no` (`customer_order_no`),
  KEY `idx_lpr_material_code` (`material_code`),
  KEY `idx_lpr_template_key` (`template_key`),
  KEY `idx_lpr_print_status` (`print_status`),
  KEY `idx_lpr_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签打印记录';
