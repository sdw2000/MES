-- 基材材质字典：英文化并扩展新选项
-- 目标：新增 PEFOAM / OPS / TISSUE，且原有中文标识统一为英文代码

-- 1) 统一已有字典名称为英文
UPDATE tape_material_dict
SET material_name = material_code
WHERE material_type = 'base'
  AND status = 1
  AND material_code IN ('PET', 'PI', 'PP', 'PVC', 'BOPP', 'OPP', 'CPP', 'TPU', 'OPS', 'PEFOAM', 'TISSUE', 'FIBERGLASS');

-- 2) 新增缺失的基材字典项
INSERT INTO tape_material_dict(material_type, material_code, material_name, sort_order, status)
SELECT 'base', 'PEFOAM', 'PEFOAM', 50, 1
WHERE NOT EXISTS (
  SELECT 1 FROM tape_material_dict t
  WHERE t.material_type = 'base' AND t.material_code = 'PEFOAM'
);

INSERT INTO tape_material_dict(material_type, material_code, material_name, sort_order, status)
SELECT 'base', 'OPS', 'OPS', 51, 1
WHERE NOT EXISTS (
  SELECT 1 FROM tape_material_dict t
  WHERE t.material_type = 'base' AND t.material_code = 'OPS'
);

INSERT INTO tape_material_dict(material_type, material_code, material_name, sort_order, status)
SELECT 'base', 'TISSUE', 'TISSUE', 52, 1
WHERE NOT EXISTS (
  SELECT 1 FROM tape_material_dict t
  WHERE t.material_type = 'base' AND t.material_code = 'TISSUE'
);

-- 3) 历史规格数据英文化（仅改 base_material）
UPDATE tape_spec
SET base_material = CASE
    WHEN base_material IN ('PI聚酰亚胺', '聚酰亚胺') THEN 'PI'
    WHEN base_material IN ('PP聚丙烯', '聚丙烯') THEN 'PP'
    WHEN base_material IN ('美纹纸', '离型纸', '纸基') THEN 'TISSUE'
    WHEN base_material IN ('玻纤布', '玻璃纤维布') THEN 'FIBERGLASS'
    WHEN base_material IN ('PET膜', 'PET薄膜') THEN 'PET'
    WHEN base_material IN ('BOPP膜', 'BOPP薄膜') THEN 'BOPP'
    WHEN base_material IN ('OPP膜', 'OPP薄膜') THEN 'OPP'
    WHEN base_material IN ('CPP膜', 'CPP薄膜') THEN 'CPP'
    WHEN base_material IN ('OPS膜', 'OPS薄膜') THEN 'OPS'
    WHEN base_material IN ('PVC膜', 'PVC薄膜') THEN 'PVC'
    WHEN base_material IN ('TPU膜', 'TPU薄膜') THEN 'TPU'
    WHEN base_material LIKE '%泡棉%' THEN 'PEFOAM'
    ELSE UPPER(base_material)
END
WHERE base_material IS NOT NULL AND TRIM(base_material) <> '';
