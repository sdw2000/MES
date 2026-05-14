-- 来料检测主表
CREATE TABLE incoming_inspection (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    inspection_no VARCHAR(50) NOT NULL COMMENT '检验单号',
    supplier_code VARCHAR(50) COMMENT '供应商代码',
    supplier_name VARCHAR(100) COMMENT '供应商名称',
    material_code VARCHAR(50) COMMENT '物料代码',
    material_name VARCHAR(100) COMMENT '物料名称',
    material_spec VARCHAR(100) COMMENT '来料规格',
    batch_no VARCHAR(50) COMMENT '批次号',
    roll_code VARCHAR(50) COMMENT '卷码',
    quantity DECIMAL(18,3) COMMENT '来料数量',
    sample_qty INT COMMENT '取样个数',
    inspection_date DATETIME COMMENT '检验日期',
    pass_qty INT COMMENT '合格数',
    fail_qty INT COMMENT '不合格数',
    overall_result VARCHAR(20) COMMENT '检验结果',
    inspector_name VARCHAR(50) COMMENT '检验员',
    defect_type VARCHAR(50) COMMENT '缺陷类型',
    remark VARCHAR(255) COMMENT '备注',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 检验样本表
CREATE TABLE incoming_inspection_sample (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    inspection_id BIGINT NOT NULL COMMENT '检验单ID',
    sample_no INT COMMENT '样本序号',
    remark VARCHAR(255) COMMENT '备注',
    FOREIGN KEY (inspection_id) REFERENCES incoming_inspection(id)
);

-- 检验项目表
CREATE TABLE incoming_inspection_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sample_id BIGINT NOT NULL COMMENT '样本ID',
    item_code VARCHAR(50) COMMENT '检测项目编码',
    item_name VARCHAR(50) COMMENT '检测项目名称',
    value1 DECIMAL(18,3) COMMENT '测试值1',
    value2 DECIMAL(18,3) COMMENT '测试值2',
    value3 DECIMAL(18,3) COMMENT '测试值3',
    value4 DECIMAL(18,3) COMMENT '测试值4',
    value5 DECIMAL(18,3) COMMENT '测试值5',
    result VARCHAR(20) COMMENT '项目结果',
    remark VARCHAR(255) COMMENT '备注',
    FOREIGN KEY (sample_id) REFERENCES incoming_inspection_sample(id)
);

-- 说明：
-- 检测项目(item_code, item_name)需由原材料表(raw_material)的配置动态生成。
-- 可根据物料代码在原材料表查找对应检测项目清单，前端/后端动态渲染。