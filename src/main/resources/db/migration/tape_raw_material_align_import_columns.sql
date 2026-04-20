-- 研发原材料表与导入模板字段对齐
ALTER TABLE tape_raw_material
    ADD COLUMN IF NOT EXISTS supplier_code VARCHAR(64) NULL COMMENT '供应商代码',
    ADD COLUMN IF NOT EXISTS material_major VARCHAR(100) NULL COMMENT '物料大类',
    ADD COLUMN IF NOT EXISTS material_category_raw VARCHAR(100) NULL COMMENT '物料类别(原始值)',
    ADD COLUMN IF NOT EXISTS material_category VARCHAR(50) NULL COMMENT '物料类别(系统值:film/chemical)',
    ADD COLUMN IF NOT EXISTS performance_params TEXT NULL COMMENT '性能参数(JSON/范围)',
    ADD COLUMN IF NOT EXISTS remark VARCHAR(255) NULL COMMENT '备注';

-- 规格说明字段放宽，避免导入截断
ALTER TABLE tape_raw_material
    MODIFY COLUMN spec VARCHAR(500) NULL COMMENT '规格说明';
