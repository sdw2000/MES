-- ----------------------------
-- Table structure for delivery_notices
-- ----------------------------
DROP TABLE IF EXISTS `delivery_notices`;
CREATE TABLE `delivery_notices` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `notice_no` varchar(50) NOT NULL COMMENT '发货单号',
  `order_id` bigint(20) NOT NULL COMMENT '关联销售订单ID',
  `order_no` varchar(50) DEFAULT NULL COMMENT '关联销售订单号',
  `customer` varchar(100) DEFAULT NULL COMMENT '客户名称',
  `delivery_date` date DEFAULT NULL COMMENT '发货日期',
  `delivery_address` varchar(255) DEFAULT NULL COMMENT '收货地址',
  `contact_person` varchar(50) DEFAULT NULL COMMENT '联系人',
  `contact_phone` varchar(50) DEFAULT NULL COMMENT '联系电话',
  `status` varchar(20) DEFAULT 'draft' COMMENT '状态：draft-草稿, shipped-已发货, cancelled-已作废',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_by` varchar(50) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_by` varchar(50) DEFAULT NULL,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` tinyint(1) DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_no` (`notice_no`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发货通知单主表';

-- ----------------------------
-- Table structure for delivery_notice_items
-- ----------------------------
DROP TABLE IF EXISTS `delivery_notice_items`;
CREATE TABLE `delivery_notice_items` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `notice_id` bigint(20) NOT NULL COMMENT '关联发货单ID',
  `order_item_id` bigint(20) NOT NULL COMMENT '关联订单明细ID',
  `material_code` varchar(50) DEFAULT NULL COMMENT '物料代码',
  `material_name` varchar(100) DEFAULT NULL COMMENT '物料名称',
  `spec` varchar(100) DEFAULT NULL COMMENT '规格',
  `batch_no` varchar(50) DEFAULT NULL COMMENT '批号',
  `quantity` int(11) NOT NULL DEFAULT 0 COMMENT '发货数量(卷)',
  `remark` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notice_id` (`notice_id`),
  KEY `idx_order_item_id` (`order_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发货通知单明细表';
