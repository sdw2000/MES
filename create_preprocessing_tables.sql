-- 创建订单预处理表
CREATE TABLE IF NOT EXISTS order_preprocessing (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    
    -- 订单信息
    order_id BIGINT NOT NULL,
    order_no VARCHAR(50) NOT NULL,
    order_item_id BIGINT NOT NULL,
    order_item_code VARCHAR(50) NOT NULL,
    
    -- 物料需求信息
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(100),
    spec_desc VARCHAR(100),
    required_qty DECIMAL(12,2) NOT NULL,
    
    -- 库存选择
    selected_lock_id BIGINT,
    lock_status VARCHAR(20),
    locked_qty DECIMAL(12,2) DEFAULT 0,
    
    -- 排程路由
    schedule_type VARCHAR(20),
    target_pool VARCHAR(50),
    
    -- 状态管理
    `status` VARCHAR(20) DEFAULT 'preprocessing',
    remark VARCHAR(500),
    
    -- 操作日志
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    UNIQUE KEY uk_order_item (order_id, order_item_id),
    INDEX idx_order_no (order_no),
    INDEX idx_material_code (material_code),
    INDEX idx_status (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 创建订单物料锁定表
CREATE TABLE IF NOT EXISTS order_material_lock (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    
    -- 订单信息
    order_id BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    preprocessing_id BIGINT NOT NULL,
    
    -- 库存信息
    tape_stock_id BIGINT NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    batch_no VARCHAR(50),
    qr_code VARCHAR(100),
    
    -- 锁定数量
    lock_qty DECIMAL(12,2) NOT NULL,
    lock_area DECIMAL(12,2),
    
    -- 锁定状态
    lock_status VARCHAR(20) DEFAULT 'locked',
    
    -- 先进先出排序
    fifo_order INT,
    
    -- 操作信息
    locked_by BIGINT,
    locked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- 乐观锁
    version INT DEFAULT 0,
    
    INDEX idx_order_id (order_id),
    INDEX idx_tape_stock_id (tape_stock_id),
    INDEX idx_batch_no (batch_no),
    INDEX idx_fifo_order (fifo_order),
    CONSTRAINT fk_tape_stock FOREIGN KEY (tape_stock_id) REFERENCES tape_stock(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
