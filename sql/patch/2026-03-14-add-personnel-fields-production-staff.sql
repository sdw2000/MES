-- Personnel fields extension (MySQL 5.7/8.0 compatible)
-- Target database: erp

ALTER TABLE production_staff
  ADD COLUMN department VARCHAR(50) NULL COMMENT 'department' AFTER phone,
  ADD COLUMN position_name VARCHAR(50) NULL COMMENT 'position' AFTER department,
  ADD COLUMN education VARCHAR(30) NULL COMMENT 'education' AFTER position_name,
  ADD COLUMN age INT NULL COMMENT 'age' AFTER education,
  ADD COLUMN native_place VARCHAR(100) NULL COMMENT 'native place' AFTER age,
  ADD COLUMN id_card_no VARCHAR(30) NULL COMMENT 'id card no' AFTER native_place,
  ADD COLUMN household_address VARCHAR(255) NULL COMMENT 'household address' AFTER id_card_no,
  ADD COLUMN current_address VARCHAR(255) NULL COMMENT 'current address' AFTER household_address,
  ADD COLUMN emergency_contact VARCHAR(50) NULL COMMENT 'emergency contact' AFTER current_address,
  ADD COLUMN emergency_relation VARCHAR(50) NULL COMMENT 'emergency relation' AFTER emergency_contact,
  ADD COLUMN contract_sign_date DATE NULL COMMENT 'contract sign date' AFTER emergency_relation,
  ADD COLUMN medical_exam_date DATE NULL COMMENT 'medical exam date' AFTER contract_sign_date,
  ADD COLUMN medical_exam_result VARCHAR(50) NULL COMMENT 'medical exam result' AFTER medical_exam_date;

-- Optional indexes
CREATE INDEX idx_production_staff_department ON production_staff(department);
CREATE INDEX idx_production_staff_position_name ON production_staff(position_name);
CREATE INDEX idx_production_staff_id_card_no ON production_staff(id_card_no);
