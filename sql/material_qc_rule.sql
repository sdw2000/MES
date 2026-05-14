CREATE TABLE material_qc_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    material_code VARCHAR(50) NOT NULL,
    item_code VARCHAR(50) NOT NULL,
    item_name VARCHAR(100) NOT NULL,
    unit VARCHAR(20),
    judge_mode VARCHAR(20),
    min DECIMAL(18,3),
    max DECIMAL(18,3),
    standard_value VARCHAR(50),
    sort INT DEFAULT 0,
    remark VARCHAR(255),
    is_active TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX idx_material_item ON material_qc_rule(material_code, item_code);