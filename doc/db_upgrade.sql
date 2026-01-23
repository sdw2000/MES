-- 销售交付记录表（用于计算客户月销量）
CREATE TABLE IF NOT EXISTS sales_delivery_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    order_id BIGINT NOT NULL COMMENT '订单ID',
    order_no VARCHAR(50) NOT NULL COMMENT '订单号',
    order_item_id BIGINT NOT NULL COMMENT '订单明细ID',
    customer_id BIGINT COMMENT '客户ID',
    customer_name VARCHAR(100) COMMENT '客户名称',
    material_code VARCHAR(50) NOT NULL COMMENT '料号',
    material_name VARCHAR(200) COMMENT '产品名称',
    color_code VARCHAR(20) COMMENT '颜色编码',
    thickness INT COMMENT '厚度(μm)',
    delivered_qty INT NOT NULL DEFAULT 0 COMMENT '交付数量(卷)',
    delivered_sqm DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '交付面积(平方米)',
    delivery_date DATE NOT NULL COMMENT '交付日期',
    delivery_month VARCHAR(7) COMMENT '交付月份(YYYY-MM)',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    KEY idx_material_date (material_code, delivery_date),
    KEY idx_customer_month (customer_name, delivery_month),
    KEY idx_delivery_month (delivery_month)
) COMMENT='销售交付记录表';

-- 扩展tape_stock表字段
ALTER TABLE tape_stock ADD COLUMN IF NOT EXISTS stock_type VARCHAR(20) COMMENT '库存类型：jumbo(母卷)/rewound(复卷)/finished(成品)';
ALTER TABLE tape_stock ADD COLUMN IF NOT EXISTS material_code_ext VARCHAR(50) COMMENT '扩展料号字段';
ALTER TABLE tape_stock ADD KEY IF NOT EXISTS idx_material_stock (material_code, stock_type, status);

-- 扩展schedule_coating表字段
ALTER TABLE schedule_coating ADD COLUMN IF NOT EXISTS order_no VARCHAR(50) COMMENT '订单号';
ALTER TABLE schedule_coating ADD COLUMN IF NOT EXISTS order_item_id BIGINT COMMENT '订单明细ID';
ALTER TABLE schedule_coating ADD COLUMN IF NOT EXISTS base_material_code VARCHAR(50) COMMENT '基材料号';
ALTER TABLE schedule_coating ADD COLUMN IF NOT EXISTS base_thickness INT COMMENT '基材厚度(μm)';
ALTER TABLE schedule_coating ADD COLUMN IF NOT EXISTS adhesive_code VARCHAR(50) COMMENT '胶水编码';
ALTER TABLE schedule_coating ADD COLUMN IF NOT EXISTS adhesive_solid_content DECIMAL(5,2) COMMENT '胶水固含量(%)';
ALTER TABLE schedule_coating ADD COLUMN IF NOT EXISTS coating_thickness DECIMAL(8,2) COMMENT '涂胶厚度(g/m²)';
ALTER TABLE schedule_coating ADD COLUMN IF NOT EXISTS release_layer VARCHAR(100) COMMENT '离型层';
ALTER TABLE schedule_coating ADD COLUMN IF NOT EXISTS film_width INT COMMENT '膜宽(mm)';
ALTER TABLE schedule_coating ADD COLUMN IF NOT EXISTS work_hours INT COMMENT '工时(分钟)';
ALTER TABLE schedule_coating ADD COLUMN IF NOT EXISTS order_status VARCHAR(20) COMMENT '订单状态';
ALTER TABLE schedule_coating ADD KEY IF NOT EXISTS idx_order_no (order_no);
