-- Procurement multi-UOM and reconciliation migration

CREATE TABLE IF NOT EXISTS material_uom_dict (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    uom_code VARCHAR(32) NOT NULL COMMENT '单位编码',
    uom_name VARCHAR(64) NOT NULL COMMENT '单位名称',
    uom_type VARCHAR(32) NOT NULL COMMENT '单位类型: stock/purchase/price',
    is_base TINYINT NOT NULL DEFAULT 0 COMMENT '是否基础单位',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    remark VARCHAR(255) NULL,
    created_by VARCHAR(64) NULL,
    updated_by VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_uom_code (uom_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料单位字典';

CREATE TABLE IF NOT EXISTS material_uom_conversion (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    material_code VARCHAR(64) NOT NULL COMMENT '物料编码',
    from_uom_code VARCHAR(32) NOT NULL COMMENT '来源单位',
    to_uom_code VARCHAR(32) NOT NULL COMMENT '目标单位',
    conversion_rate DECIMAL(18,8) NOT NULL COMMENT '换算比率',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    remark VARCHAR(255) NULL,
    created_by VARCHAR(64) NULL,
    updated_by VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_material_from_to (material_code, from_uom_code, to_uom_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料单位换算表';

ALTER TABLE purchase_orders
    ADD COLUMN reconciliation_status VARCHAR(32) NULL COMMENT '对账状态';

ALTER TABLE purchase_order_items
    ADD COLUMN purchase_qty DECIMAL(18,4) NULL COMMENT '采购数量',
    ADD COLUMN purchase_uom_code VARCHAR(32) NULL COMMENT '采购单位',
    ADD COLUMN price_qty DECIMAL(18,4) NULL COMMENT '计价数量',
    ADD COLUMN price_uom_code VARCHAR(32) NULL COMMENT '计价单位',
    ADD COLUMN stock_qty DECIMAL(18,4) NULL COMMENT '库存数量',
    ADD COLUMN stock_uom_code VARCHAR(32) NULL COMMENT '库存单位',
    ADD COLUMN conversion_rate DECIMAL(18,8) NULL COMMENT '换算率',
    ADD COLUMN reconciliation_status VARCHAR(32) NULL COMMENT '对账状态';

ALTER TABLE purchase_receipts
    ADD COLUMN purchase_order_no VARCHAR(64) NULL COMMENT '采购订单号',
    ADD COLUMN reconciliation_status VARCHAR(32) NULL COMMENT '对账状态';

ALTER TABLE purchase_receipt_items
    ADD COLUMN purchase_order_no VARCHAR(64) NULL COMMENT '采购订单号',
    ADD COLUMN purchase_qty DECIMAL(18,4) NULL COMMENT '采购数量',
    ADD COLUMN purchase_uom_code VARCHAR(32) NULL COMMENT '采购单位',
    ADD COLUMN price_qty DECIMAL(18,4) NULL COMMENT '计价数量',
    ADD COLUMN price_uom_code VARCHAR(32) NULL COMMENT '计价单位',
    ADD COLUMN stock_qty DECIMAL(18,4) NULL COMMENT '库存数量',
    ADD COLUMN stock_uom_code VARCHAR(32) NULL COMMENT '库存单位',
    ADD COLUMN conversion_rate DECIMAL(18,8) NULL COMMENT '换算率';
