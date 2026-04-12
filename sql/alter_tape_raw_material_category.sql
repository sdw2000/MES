ALTER TABLE tape_raw_material
    ADD COLUMN material_category VARCHAR(20) NOT NULL DEFAULT 'chemical' COMMENT '物料类别: film/chemical' AFTER material_name;

UPDATE tape_raw_material
SET material_category = CASE
    WHEN unit = 'm²' OR spec LIKE '%卷%' THEN 'film'
    ELSE 'chemical'
END
WHERE material_category IS NULL OR material_category = '';

ALTER TABLE tape_raw_material
    ADD INDEX idx_tape_raw_material_category (material_category);
