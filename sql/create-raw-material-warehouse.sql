-- 原料仓库表结构
-- 用于管理涂布生产所需的原料（薄膜和化工原料）
-- 采用总量表+明细表的设计

-- ==================== 薄膜仓 ====================

-- 1. 薄膜库存总量表（汇总数据）
CREATE TABLE IF NOT EXISTS film_stock (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    material_code VARCHAR(50) NOT NULL COMMENT '物料编号',
    material_name VARCHAR(100) NOT NULL COMMENT '物料名称',
    
    -- 规格参数（从尺寸解析）
    thickness DECIMAL(10,3) COMMENT '厚度(μm)',
    width INT COMMENT '宽度(mm)',
    spec_desc VARCHAR(50) COMMENT '规格描述（如25*1040）',
    
    -- 库存汇总
    total_area DECIMAL(12,2) DEFAULT 0 COMMENT '总面积(㎡)',
    total_rolls INT DEFAULT 0 COMMENT '总卷数',
    available_area DECIMAL(12,2) DEFAULT 0 COMMENT '可用面积(㎡)',
    available_rolls INT DEFAULT 0 COMMENT '可用卷数',
    locked_area DECIMAL(12,2) DEFAULT 0 COMMENT '锁定面积(㎡)',
    locked_rolls INT DEFAULT 0 COMMENT '锁定卷数',
    
    -- 统计信息
    last_in_date DATE COMMENT '最后入库日期',
    last_out_date DATE COMMENT '最后出库日期',
    
    remark VARCHAR(500) COMMENT '备注',
    
    -- 审计字段
    create_by VARCHAR(50) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(50) COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    
    UNIQUE KEY uk_material_code (material_code),
    INDEX idx_spec (thickness, width)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薄膜库存总量表';

-- 2. 薄膜库存明细表（每一卷的详细信息）
CREATE TABLE IF NOT EXISTS film_stock_detail (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    stock_id BIGINT NOT NULL COMMENT '库存总量表ID',
    material_code VARCHAR(50) NOT NULL COMMENT '物料编号',
    
    -- 批次信息
    batch_no VARCHAR(50) NOT NULL COMMENT '批次号',
    roll_no VARCHAR(50) COMMENT '卷号/序列号',
    
    -- 规格参数
    thickness DECIMAL(10,3) NOT NULL COMMENT '厚度(μm)',
    width INT NOT NULL COMMENT '宽度(mm)',
    length INT COMMENT '长度(m)',
    diameter INT COMMENT '外径(mm)',
    core_diameter INT DEFAULT 76 COMMENT '纸管内径(mm)',
    weight DECIMAL(10,2) COMMENT '重量(kg)',
    area DECIMAL(10,2) COMMENT '面积(㎡)',
    
    -- 质量信息
    quality_level VARCHAR(20) DEFAULT 'A' COMMENT '质量等级(A/B/C)',
    quality_status VARCHAR(20) DEFAULT 'qualified' COMMENT '质检状态',
    defect_desc VARCHAR(200) COMMENT '缺陷描述',
    
    -- 仓储信息
    warehouse VARCHAR(50) DEFAULT '原料仓' COMMENT '仓库',
    location VARCHAR(50) COMMENT '库位',
    supplier VARCHAR(100) COMMENT '供应商',
    
    -- 入库信息
    purchase_order_no VARCHAR(50) COMMENT '采购单号',
    arrival_date DATE COMMENT '到货日期',
    storage_date DATE COMMENT '入库日期',
    unit_price DECIMAL(10,4) COMMENT '单价(元/㎡)',
    
    -- 状态
    status VARCHAR(20) DEFAULT 'available' COMMENT '状态(available-可用/reserved-预留/locked-锁定/used-已用/scrap-报废)',
    locked_by VARCHAR(50) COMMENT '锁定人/锁定单号',
    locked_time DATETIME COMMENT '锁定时间',
    
    remark VARCHAR(500) COMMENT '备注',
    
    -- 审计字段
    create_by VARCHAR(50) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(50) COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    
    INDEX idx_stock_id (stock_id),
    INDEX idx_batch_no (batch_no),
    INDEX idx_material_code (material_code),
    INDEX idx_status (status),
    UNIQUE KEY uk_roll_no (roll_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薄膜库存明细表';

-- ==================== 化工仓 ====================

-- 3. 化工原料库存总量表
CREATE TABLE IF NOT EXISTS chemical_stock (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    material_code VARCHAR(50) NOT NULL COMMENT '物料编号',
    material_name VARCHAR(100) NOT NULL COMMENT '物料名称',
    chemical_type VARCHAR(50) COMMENT '化工类型(adhesive-胶水/solvent-溶剂/additive-助剂/pigment-色浆)',
    
    -- 包装规格
    unit VARCHAR(20) DEFAULT '桶' COMMENT '单位(桶/瓶/袋/kg)',
    unit_weight DECIMAL(10,2) COMMENT '单桶重量(kg)',
    
    -- 库存汇总
    total_quantity INT DEFAULT 0 COMMENT '总数量(桶数)',
    total_weight DECIMAL(12,2) DEFAULT 0 COMMENT '总重量(kg)',
    available_quantity INT DEFAULT 0 COMMENT '可用数量',
    available_weight DECIMAL(12,2) DEFAULT 0 COMMENT '可用重量(kg)',
    locked_quantity INT DEFAULT 0 COMMENT '锁定数量',
    locked_weight DECIMAL(12,2) DEFAULT 0 COMMENT '锁定重量(kg)',
    
    -- 统计信息
    last_in_date DATE COMMENT '最后入库日期',
    last_out_date DATE COMMENT '最后出库日期',
    
    remark VARCHAR(500) COMMENT '备注',
    
    -- 审计字段
    create_by VARCHAR(50) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(50) COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    
    UNIQUE KEY uk_material_code (material_code),
    INDEX idx_chemical_type (chemical_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='化工原料库存总量表';

-- 4. 化工原料库存明细表（每一桶的详细信息）
CREATE TABLE IF NOT EXISTS chemical_stock_detail (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    stock_id BIGINT NOT NULL COMMENT '库存总量表ID',
    material_code VARCHAR(50) NOT NULL COMMENT '物料编号',
    
    -- 批次信息
    batch_no VARCHAR(50) NOT NULL COMMENT '批次号',
    barrel_no VARCHAR(50) COMMENT '桶号/序列号',
    
    -- 包装信息
    unit VARCHAR(20) DEFAULT '桶' COMMENT '单位',
    weight DECIMAL(10,2) COMMENT '重量(kg)',
    net_weight DECIMAL(10,2) COMMENT '净重(kg)',
    remaining_weight DECIMAL(10,2) COMMENT '剩余重量(kg)',
    
    -- 质量信息
    quality_level VARCHAR(20) DEFAULT 'A' COMMENT '质量等级',
    quality_status VARCHAR(20) DEFAULT 'qualified' COMMENT '质检状态',
    production_date DATE COMMENT '生产日期',
    expiry_date DATE COMMENT '有效期',
    
    -- 仓储信息
    warehouse VARCHAR(50) DEFAULT '化工仓' COMMENT '仓库',
    location VARCHAR(50) COMMENT '库位',
    supplier VARCHAR(100) COMMENT '供应商',
    
    -- 入库信息
    purchase_order_no VARCHAR(50) COMMENT '采购单号',
    arrival_date DATE COMMENT '到货日期',
    storage_date DATE COMMENT '入库日期',
    unit_price DECIMAL(10,2) COMMENT '单价(元/kg)',
    
    -- 状态
    status VARCHAR(20) DEFAULT 'available' COMMENT '状态(available/reserved/locked/used/empty-空桶/scrap)',
    is_opened TINYINT DEFAULT 0 COMMENT '是否已开封(0-未开封/1-已开封)',
    locked_by VARCHAR(50) COMMENT '锁定人/锁定单号',
    locked_time DATETIME COMMENT '锁定时间',
    
    -- 安全信息
    danger_level VARCHAR(20) COMMENT '危险等级',
    msds_file VARCHAR(200) COMMENT 'MSDS文件',
    
    remark VARCHAR(500) COMMENT '备注',
    
    -- 审计字段
    create_by VARCHAR(50) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_by VARCHAR(50) COMMENT '更新人',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT DEFAULT 0 COMMENT '删除标记',
    
    INDEX idx_stock_id (stock_id),
    INDEX idx_batch_no (batch_no),
    INDEX idx_material_code (material_code),
    INDEX idx_status (status),
    INDEX idx_expiry_date (expiry_date),
    UNIQUE KEY uk_barrel_no (barrel_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='化工原料库存明细表';

-- ==================== 关联表和记录表 ====================

-- 5. 修改涂布任务表，添加薄膜库存关联
ALTER TABLE schedule_coating 
ADD COLUMN film_stock_id BIGINT COMMENT '薄膜库存ID(总量表)' AFTER equipment_code,
ADD COLUMN film_detail_ids VARCHAR(500) COMMENT '使用的薄膜明细ID列表(逗号分隔)' AFTER film_stock_id,
ADD COLUMN film_batch_no VARCHAR(50) COMMENT '薄膜批次号' AFTER film_detail_ids,
ADD COLUMN film_width INT COMMENT '薄膜宽度(mm)' AFTER film_batch_no,
ADD COLUMN film_thickness DECIMAL(10,3) COMMENT '薄膜厚度(μm)' AFTER film_width,
ADD COLUMN base_film_rolls INT COMMENT '使用薄膜卷数' AFTER film_thickness,
ADD COLUMN base_film_area DECIMAL(10,2) COMMENT '使用薄膜面积(㎡)' AFTER base_film_rolls,
ADD INDEX idx_film_stock_id (film_stock_id);

-- 6. 薄膜出库记录表
CREATE TABLE IF NOT EXISTS film_stock_out (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    out_no VARCHAR(50) NOT NULL COMMENT '出库单号',
    stock_id BIGINT NOT NULL COMMENT '薄膜库存ID(总量表)',
    detail_id BIGINT NOT NULL COMMENT '薄膜明细ID',
    material_code VARCHAR(50) NOT NULL COMMENT '物料编号',
    batch_no VARCHAR(50) NOT NULL COMMENT '批次号',
    roll_no VARCHAR(50) COMMENT '卷号',
    
    -- 出库信息
    out_area DECIMAL(10,2) NOT NULL COMMENT '出库面积(㎡)',
    out_date DATETIME NOT NULL COMMENT '出库时间',
    out_type VARCHAR(20) DEFAULT 'production' COMMENT '出库类型(production-生产领用/transfer-调拨/scrap-报废)',
    
    -- 关联信息
    schedule_id BIGINT COMMENT '排程ID',
    coating_task_id BIGINT COMMENT '涂布任务ID',
    
    -- 操作信息
    operator VARCHAR(50) COMMENT '操作员',
    receiver VARCHAR(50) COMMENT '领料人',
    department VARCHAR(50) COMMENT '领料部门',
    
    remark VARCHAR(500) COMMENT '备注',
    
    create_by VARCHAR(50) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    INDEX idx_out_no (out_no),
    INDEX idx_stock_id (stock_id),
    INDEX idx_detail_id (detail_id),
    INDEX idx_schedule_id (schedule_id),
    INDEX idx_coating_task_id (coating_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='薄膜出库记录表';

-- 7. 化工原料出库记录表
CREATE TABLE IF NOT EXISTS chemical_stock_out (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    out_no VARCHAR(50) NOT NULL COMMENT '出库单号',
    stock_id BIGINT NOT NULL COMMENT '化工库存ID(总量表)',
    detail_id BIGINT NOT NULL COMMENT '化工明细ID',
    material_code VARCHAR(50) NOT NULL COMMENT '物料编号',
    batch_no VARCHAR(50) NOT NULL COMMENT '批次号',
    barrel_no VARCHAR(50) COMMENT '桶号',
    
    -- 出库信息
    out_weight DECIMAL(10,2) NOT NULL COMMENT '出库重量(kg)',
    out_date DATETIME NOT NULL COMMENT '出库时间',
    out_type VARCHAR(20) DEFAULT 'production' COMMENT '出库类型',
    
    -- 关联信息
    schedule_id BIGINT COMMENT '排程ID',
    coating_task_id BIGINT COMMENT '涂布任务ID',
    
    -- 操作信息
    operator VARCHAR(50) COMMENT '操作员',
    receiver VARCHAR(50) COMMENT '领料人',
    department VARCHAR(50) COMMENT '领料部门',
    
    remark VARCHAR(500) COMMENT '备注',
    
    create_by VARCHAR(50) COMMENT '创建人',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    INDEX idx_out_no (out_no),
    INDEX idx_stock_id (stock_id),
    INDEX idx_detail_id (detail_id),
    INDEX idx_schedule_id (schedule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='化工原料出库记录表';

-- ==================== 测试数据 ====================

-- 插入薄膜库存总量数据（根据实际表格）
INSERT INTO film_stock (material_code, material_name, thickness, width, spec_desc, total_area, total_rolls, available_area, available_rolls) VALUES
('BOPPM-T25', 'BOPP原膜', 25, 1040, '25*1040', 64220.00, 100, 64220.00, 100),
('BOPPM-T28', 'BOPP原膜', 28, 1040, '28*1040', 0, 0, 0, 0),
('BOPPM-T37', 'BOPP原膜', 37, 1040, '37*1040', 37284.00, 60, 37284.00, 60),
('BOPPM-T50', 'BOPP原膜,厚度50u,无打', 50, NULL, '50u', 22256.00, 35, 22256.00, 35),
('BOPPM-T60', 'BOPP原膜', 60, NULL, '60u', 8320.00, 15, 8320.00, 15),
('BOPPM-T65', 'BOPP原膜,厚度65u,无打', 65, NULL, '65u', 0, 0, 0, 0),
('GLXZ-S90Y', '双硅接拉丝纸（1:2）', 90, 1040, '90g*1040', 29211.00, 50, 29211.00, 50),
('GLXZ-S90Y14', '双硅接拉丝纸（1:4）', 90, 1040, '90g*1040（1:4）', 4387.00, 8, 4387.00, 8),
('NPZ-D105', '单硅牛皮纸', 105, 1040, '105g*1040', 3120.00, 5, 3120.00, 5),
('OPSM-40um', 'OPS收缩膜', 40, NULL, '40um', 46900.00, 75, 46900.00, 75),
('OPSM-50um', 'OPS透明膜', 50, NULL, '50um', 31960.00, 50, 31960.00, 50),
('PELXM-100B', 'PE离型膜', 100, 1070, '100*1070', 1177.00, 2, 1177.00, 2),
('PELXM-60B', 'PE离型膜', 60, 1070, '60*1070', 1926.00, 3, 1926.00, 3);

-- 插入薄膜库存明细数据（示例：为BOPPM-T25创建明细）
INSERT INTO film_stock_detail (stock_id, material_code, batch_no, roll_no, thickness, width, length, area, quality_level, warehouse, location, storage_date, unit_price, status) VALUES
(1, 'BOPPM-T25', 'BATCH-20260101-001', 'ROLL-001', 25, 1040, 3000, 3120.00, 'A', '原料仓', 'A-01-01', '2026-01-05', 15.50, 'available'),
(1, 'BOPPM-T25', 'BATCH-20260101-001', 'ROLL-002', 25, 1040, 3000, 3120.00, 'A', '原料仓', 'A-01-02', '2026-01-05', 15.50, 'available'),
(1, 'BOPPM-T25', 'BATCH-20260101-002', 'ROLL-003', 25, 1040, 2800, 2912.00, 'A', '原料仓', 'A-01-03', '2026-01-08', 15.50, 'available');

-- 插入化工库存总量数据
INSERT INTO chemical_stock (material_code, material_name, chemical_type, unit, unit_weight, total_quantity, total_weight, available_quantity, available_weight) VALUES
('FN8558', '胶水', 'adhesive', '桶', 150, 150, 22500, 150, 22500),
('FN8851', '胶水', 'adhesive', '桶', 150, 150, 22500, 150, 22500),
('FN-C102', '胶水', 'adhesive', '桶', NULL, 20, NULL, 20, NULL),
('ZRJ6000F', '阻燃剂-6000F', 'additive', '包', 20, 100, 2000, 100, 2000),
('ZRJ601', '阻燃剂-601', 'additive', '桶', 250, 50, 12500, 50, 12500),
('2089', '绿色色浆2089', 'pigment', '桶', 20, 17, 340, 17, 340),
('6030UJ', '无机蓝色色浆6030UJ', 'pigment', '桶', 25, 25, 625, 25, 625),
('9002', '固化剂9C02', 'additive', '瓶', 1, 20, 20, 20, 20),
('ATSZ0101', '离基树脂（S82-2）', 'additive', '桶', 17, 170, 2890, 170, 2890),
('B6028-U', '群青B6028-U/蓝色茶色色粉,颜色同', 'pigment', '桶', 20, 20, 400, 20, 400),
('CSF1015FY20', '标样', 'other', '桶', 25, 25, 625, 25, 625),
('DC', '丙烯酸涂布稀释剂', 'solvent', '桶', 160, 160, 25600, 160, 25600),
('DJBHS0301', '对甲苯磺酸', 'additive', '瓶', 0.5, 20, 10, 20, 10);

-- 插入化工库存明细数据（示例）
INSERT INTO chemical_stock_detail (stock_id, material_code, batch_no, barrel_no, unit, weight, net_weight, remaining_weight, quality_level, warehouse, location, storage_date, unit_price, status) VALUES
(1, 'FN8558', 'CHEM-20260101-001', 'BARREL-001', '桶', 150, 150, 150, 'A', '化工仓', 'B-01-01', '2026-01-05', 85.00, 'available'),
(1, 'FN8558', 'CHEM-20260101-001', 'BARREL-002', '桶', 150, 150, 150, 'A', '化工仓', 'B-01-02', '2026-01-05', 85.00, 'available'),
(2, 'FN8851', 'CHEM-20260102-001', 'BARREL-003', '桶', 150, 150, 150, 'A', '化工仓', 'B-02-01', '2026-01-08', 95.00, 'available');
