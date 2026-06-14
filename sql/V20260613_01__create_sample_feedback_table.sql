CREATE TABLE IF NOT EXISTS sample_feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sample_order_id BIGINT NOT NULL COMMENT '关联送样单ID',
    feedback_content TEXT COMMENT '反馈内容',
    feedback_person VARCHAR(100) COMMENT '反馈人',
    feedback_date DATETIME COMMENT '反馈日期',
    satisfaction_level VARCHAR(20) COMMENT '满意度',
    attachments TEXT COMMENT '附件(JSON列表或逗号分隔)',
    create_by VARCHAR(64),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT(1) DEFAULT 0,
    INDEX idx_sample_order_id (sample_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='送样反馈记录表';
