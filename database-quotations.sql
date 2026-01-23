-- ================================================================
-- 报价单管理系统数据库
-- ================================================================

-- 1. 创建报价单主表
CREATE TABLE IF NOT EXISTS `quotations` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `quotation_no` VARCHAR(50) UNIQUE COMMENT '报价单号（格式：QT-YYYYMMDD-XXX）',
  `customer` VARCHAR(200) NOT NULL COMMENT '客户名称',
  `contact_person` VARCHAR(100) COMMENT '联系人',
  `contact_phone` VARCHAR(50) COMMENT '联系电话',
  `total_amount` DECIMAL(15,2) DEFAULT 0.00 COMMENT '总金额',
  `total_area` DECIMAL(15,2) DEFAULT 0.00 COMMENT '总面积（平方米）',
  `quotation_date` DATE COMMENT '报价日期',
  `valid_until` DATE COMMENT '有效期截止日期',
  `status` VARCHAR(20) DEFAULT 'draft' COMMENT '报价状态（draft-草稿，submitted-已提交，accepted-已接受，rejected-已拒绝，expired-已过期）',
  `remark` TEXT COMMENT '备注',
  `created_by` VARCHAR(100) COMMENT '创建人',
  `updated_by` VARCHAR(100) COMMENT '更新人',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT(1) DEFAULT 0 COMMENT '逻辑删除标记（0-未删除，1-已删除）',
  PRIMARY KEY (`id`),
  INDEX `idx_quotation_no` (`quotation_no`),
  INDEX `idx_customer` (`customer`),
  INDEX `idx_created_at` (`created_at`),
  INDEX `idx_is_deleted` (`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报价单主表';

-- 2. 创建报价单明细表
CREATE TABLE IF NOT EXISTS `quotation_items` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `quotation_id` BIGINT NOT NULL COMMENT '关联的报价单ID',
  `material_code` VARCHAR(50) COMMENT '物料代码',
  `material_name` VARCHAR(200) COMMENT '物料名称',
  `specifications` VARCHAR(200) COMMENT '规格型号',
  `length` DECIMAL(10,2) COMMENT '长度（毫米）',
  `width` DECIMAL(10,2) COMMENT '宽度（毫米）',
  `thickness` DECIMAL(10,6) COMMENT '厚度（微米）',
  `quantity` INT COMMENT '数量（卷数）',
  `unit` VARCHAR(20) DEFAULT '卷' COMMENT '单位',
  `sqm` DECIMAL(15,2) COMMENT '平方米数（计算得出）',
  `unit_price` DECIMAL(15,2) COMMENT '单价（每平方米）',
  `amount` DECIMAL(15,2) COMMENT '金额（计算得出）',
  `remark` VARCHAR(500) COMMENT '备注',
  `created_by` VARCHAR(100) COMMENT '创建人',
  `updated_by` VARCHAR(100) COMMENT '更新人',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT(1) DEFAULT 0 COMMENT '逻辑删除标记（0-未删除，1-已删除）',
  PRIMARY KEY (`id`),
  INDEX `idx_quotation_id` (`quotation_id`),
  INDEX `idx_material_code` (`material_code`),
  INDEX `idx_is_deleted` (`is_deleted`),
  FOREIGN KEY (`quotation_id`) REFERENCES `quotations`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报价单明细表';

-- 3. 插入测试数据

-- 插入测试报价单1
INSERT INTO `quotations` (
  `quotation_no`, `customer`, `contact_person`, `contact_phone`, 
  `total_amount`, `total_area`, `quotation_date`, `valid_until`, 
  `status`, `remark`, `created_by`, `updated_by`, `is_deleted`
) VALUES (
  'QT-20260105-001', '广州包装材料有限公司', '张经理', '13800138001',
  15000.00, 300.00, '2026-01-05', '2026-02-05',
  'submitted', '首次报价，价格优惠', 'admin', 'admin', 0
);

-- 获取刚插入的报价单ID
SET @quotation1_id = LAST_INSERT_ID();

-- 插入报价单1的明细
INSERT INTO `quotation_items` (
  `quotation_id`, `material_code`, `material_name`, `specifications`,
  `length`, `width`, `thickness`, `quantity`, `unit`,
  `sqm`, `unit_price`, `amount`, `remark`,
  `created_by`, `updated_by`, `is_deleted`
) VALUES 
(
  @quotation1_id, 'MT-BOPP-001', 'BOPP薄膜', '透明',
  1000, 500, 50.000000, 20, '卷',
  10.00, 50.00, 500.00, '常规产品',
  'admin', 'admin', 0
),
(
  @quotation1_id, 'MT-PET-002', 'PET薄膜', '磨砂',
  1200, 600, 75.000000, 25, '卷',
  18.00, 65.00, 1170.00, '高品质产品',
  'admin', 'admin', 0
);

-- 插入测试报价单2
INSERT INTO `quotations` (
  `quotation_no`, `customer`, `contact_person`, `contact_phone`,
  `total_amount`, `total_area`, `quotation_date`, `valid_until`,
  `status`, `remark`, `created_by`, `updated_by`, `is_deleted`
) VALUES (
  'QT-20260105-002', '深圳印刷厂', '李总', '13900139002',
  28000.00, 500.00, '2026-01-05', '2026-01-20',
  'draft', '大客户特价', 'admin', 'admin', 0
);

-- 获取刚插入的报价单ID
SET @quotation2_id = LAST_INSERT_ID();

-- 插入报价单2的明细
INSERT INTO `quotation_items` (
  `quotation_id`, `material_code`, `material_name`, `specifications`,
  `length`, `width`, `thickness`, `quantity`, `unit`,
  `sqm`, `unit_price`, `amount`, `remark`,
  `created_by`, `updated_by`, `is_deleted`
) VALUES 
(
  @quotation2_id, 'MT-CPP-003', 'CPP薄膜', '印刷级',
  1500, 800, 60.000000, 30, '卷',
  36.00, 55.00, 1980.00, '批量订单',
  'admin', 'admin', 0
),
(
  @quotation2_id, 'MT-BOPET-004', 'BOPET薄膜', '高透',
  1000, 600, 12.000000, 40, '卷',
  24.00, 70.00, 1680.00, '特殊规格',
  'admin', 'admin', 0
),
(
  @quotation2_id, 'MT-ALU-005', '铝箔', '食品级',
  800, 400, 9.000000, 50, '卷',
  16.00, 80.00, 1280.00, '食品包装专用',
  'admin', 'admin', 0
);

-- 4. 查询验证
SELECT 
  q.id,
  q.quotation_no,
  q.customer,
  q.contact_person,
  q.total_amount,
  q.total_area,
  q.quotation_date,
  q.valid_until,
  q.status,
  COUNT(qi.id) as item_count
FROM quotations q
LEFT JOIN quotation_items qi ON q.id = qi.quotation_id AND qi.is_deleted = 0
WHERE q.is_deleted = 0
GROUP BY q.id
ORDER BY q.created_at DESC;

-- 5. 查看明细
SELECT 
  qi.id,
  qi.quotation_id,
  qi.material_code,
  qi.material_name,
  qi.specifications,
  qi.length,
  qi.width,
  qi.thickness,
  qi.quantity,
  qi.unit,
  qi.sqm,
  qi.unit_price,
  qi.amount
FROM quotation_items qi
WHERE qi.quotation_id IN (SELECT id FROM quotations WHERE is_deleted = 0)
  AND qi.is_deleted = 0
ORDER BY qi.quotation_id, qi.id;

-- 完成
SELECT '报价单数据库创建完成！' AS status;
SELECT CONCAT('已插入 ', COUNT(*), ' 条报价单') AS quotations_count FROM quotations WHERE is_deleted = 0;
SELECT CONCAT('已插入 ', COUNT(*), ' 条报价明细') AS quotation_items_count FROM quotation_items WHERE is_deleted = 0;
