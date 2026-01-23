-- ========================================
-- 清理旧数据并插入测试数据
-- ========================================

USE erp;

-- 清空现有数据（如果有）
TRUNCATE TABLE sample_items;
TRUNCATE TABLE sample_status_history;
TRUNCATE TABLE sample_logistics_records;
TRUNCATE TABLE sample_orders;

-- 插入测试数据 - 送样订单
INSERT INTO `sample_orders` (
  `sample_no`,
  `customer_id`,
  `customer_name`,
  `contact_name`,
  `contact_phone`,
  `contact_address`,
  `send_date`,
  `status`,
  `remark`,
  `create_by`,
  `create_time`
) VALUES 
(
  'SP20260105001',
  1,
  '广州胶带有限公司',
  '张经理',
  '13800138000',
  '广东省广州市天河区科技园路88号',
  '2026-01-05',
  '待发货',
  '首次送样',
  'admin',
  NOW()
),
(
  'SP20260105002',
  2,
  '深圳包装材料公司',
  '李总',
  '13900139000',
  '广东省深圳市南山区高新科技园',
  '2026-01-04',
  '已发货',
  '重要客户',
  'admin',
  NOW()
);

-- 插入测试数据 - 送样明细
INSERT INTO `sample_items` (
  `sample_no`,
  `material_code`,
  `material_name`,
  `specification`,
  `model`,
  `batch_no`,
  `quantity`,
  `unit`,
  `remark`
) VALUES 
-- SP20260105001的明细
(
  'SP20260105001',
  'M001',
  '透明胶带',
  '48mm*50m',
  'A型',
  '20260105-001',
  10,
  '卷',
  '普通包装'
),
(
  'SP20260105001',
  'M002',
  '封箱胶带',
  '60mm*100m',
  'B型',
  '20260105-002',
  5,
  '卷',
  '加强型'
),
-- SP20260105002的明细
(
  'SP20260105002',
  'M003',
  '双面胶带',
  '12mm*10m',
  'C型',
  '20260104-001',
  20,
  '卷',
  '特殊规格'
);

-- 更新第二个订单的物流信息
UPDATE `sample_orders` 
SET 
  `express_company` = '顺丰速运',
  `tracking_number` = 'SF1234567890',
  `ship_date` = '2026-01-04',
  `logistics_status` = '运输中'
WHERE `sample_no` = 'SP20260105002';

-- 插入状态历史记录
INSERT INTO `sample_status_history` (
  `sample_no`,
  `old_status`,
  `new_status`,
  `change_reason`,
  `change_source`,
  `operator`,
  `change_time`
) VALUES 
(
  'SP20260105002',
  '待发货',
  '已发货',
  '已安排发货',
  'MANUAL',
  'admin',
  NOW()
);

-- 查看插入的数据
SELECT 
  sample_no AS '送样编号',
  customer_name AS '客户名称',
  contact_name AS '联系人',
  contact_phone AS '联系电话',
  send_date AS '送样日期',
  status AS '状态',
  tracking_number AS '快递单号'
FROM sample_orders
ORDER BY create_time DESC;

SELECT 
  sample_no AS '送样编号',
  material_name AS '物料名称',
  batch_no AS '批次号',
  quantity AS '数量',
  unit AS '单位'
FROM sample_items
ORDER BY sample_no, id;

COMMIT;
