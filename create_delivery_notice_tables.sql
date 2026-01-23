-- 创建发货通知单主表
CREATE TABLE IF NOT EXISTS delivery_notices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    notice_no VARCHAR(50) NOT NULL COMMENT '发货单号',
    order_id BIGINT NOT NULL COMMENT '关联销售订单ID',
    order_no VARCHAR(50) NOT NULL COMMENT '关联销售订单号',
    customer VARCHAR(100) NOT NULL COMMENT '客户名称',
    delivery_date DATE NOT NULL COMMENT '发货日期',
    delivery_address TEXT COMMENT '收货地址',
    contact_person VARCHAR(50) COMMENT '联系人',
    contact_phone VARCHAR(50) COMMENT '联系电话',
    shipping_method VARCHAR(50) COMMENT '运输方式',
    logistics_company VARCHAR(100) COMMENT '物流公司',
    tracking_no VARCHAR(50) COMMENT '物流单号',
    status VARCHAR(20) DEFAULT 'pending' COMMENT '状态: pending, shipping, shipped, cancelled',
    remarks TEXT COMMENT '备注',
    created_by VARCHAR(50) COMMENT '创建人',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by VARCHAR(50) COMMENT '更新人',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_order_id (order_id),
    INDEX idx_notice_no (notice_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发货通知单';

-- 创建发货通知单明细表
CREATE TABLE IF NOT EXISTS delivery_notice_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    notice_id BIGINT NOT NULL COMMENT '关联发货通知单ID',
    order_item_id BIGINT NOT NULL COMMENT '关联订单明细ID',
    material_code VARCHAR(50) NOT NULL COMMENT '物料编码',
    material_name VARCHAR(100) NOT NULL COMMENT '物料名称',
    spec VARCHAR(100) COMMENT '规格',
    quantity INT NOT NULL COMMENT '发货卷数',
    detail_remarks VARCHAR(255) COMMENT '明细备注',
    created_by VARCHAR(50) COMMENT '创建人',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by VARCHAR(50) COMMENT '更新人',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_notice_id (notice_id),
    INDEX idx_order_item_id (order_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发货通知单明细';
