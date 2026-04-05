-- 涂布报工母卷 + 工序领料明细 持久化表

CREATE TABLE IF NOT EXISTS manual_schedule_coating_roll (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    schedule_id BIGINT NOT NULL,
    report_id BIGINT NOT NULL,
    roll_code VARCHAR(64) NOT NULL,
    batch_no VARCHAR(64) NULL,
    width_mm DECIMAL(10,2) NULL,
    length_m DECIMAL(12,2) NULL,
    area DECIMAL(12,2) NOT NULL,
    weight_kg DECIMAL(12,3) NULL,
    remark VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    KEY idx_mscr_schedule (schedule_id),
    KEY idx_mscr_report (report_id),
    KEY idx_mscr_roll_code (roll_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS manual_schedule_coating_order_lock (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    schedule_id BIGINT NOT NULL,
    report_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    material_code VARCHAR(100) NULL,
    roll_code VARCHAR(64) NOT NULL,
    locked_area DECIMAL(12,2) NOT NULL,
    lock_status VARCHAR(32) NOT NULL DEFAULT 'LOCKED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    KEY idx_mscol_order_no (order_no),
    KEY idx_mscol_schedule (schedule_id),
    KEY idx_mscol_report (report_id),
    KEY idx_mscol_roll_code (roll_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS manual_schedule_process_material_issue (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    schedule_id BIGINT NOT NULL,
    report_id BIGINT NOT NULL,
    process_type VARCHAR(32) NOT NULL,
    material_type VARCHAR(32) NULL,
    material_code VARCHAR(100) NULL,
    stock_id BIGINT NULL,
    roll_code VARCHAR(64) NULL,
    plan_area DECIMAL(12,2) NULL,
    actual_area DECIMAL(12,2) NULL,
    loss_area DECIMAL(12,2) NULL,
    operator_name VARCHAR(64) NULL,
    issue_time DATETIME NULL,
    remark VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    KEY idx_mspmi_schedule_process (schedule_id, process_type),
    KEY idx_mspmi_report (report_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
