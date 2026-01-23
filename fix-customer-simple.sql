-- Quick Fix: Create customer_code_sequence table if not exists
-- 快速修复：创建客户编号序列表（如果不存在）

USE mes;

-- Create the sequence table
CREATE TABLE IF NOT EXISTS `customer_code_sequence` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `prefix` VARCHAR(10) NOT NULL COMMENT '客户编号前缀（如ALB、TX等）',
  `current_number` INT(11) NOT NULL DEFAULT 0 COMMENT '当前序号',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_prefix` (`prefix`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户编号序列表';

-- Verify
SELECT '✅ customer_code_sequence table created!' AS message;
SHOW CREATE TABLE customer_code_sequence;
