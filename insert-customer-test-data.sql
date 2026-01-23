-- ========================================
-- 客户管理功能 - 测试数据
-- 创建日期: 2026-01-06
-- ========================================

USE erp;

-- 清理旧数据
DELETE FROM customer_contacts;
DELETE FROM customers;
DELETE FROM customer_code_sequence;

-- 重置自增ID
ALTER TABLE customers AUTO_INCREMENT = 1;
ALTER TABLE customer_contacts AUTO_INCREMENT = 1;
ALTER TABLE customer_code_sequence AUTO_INCREMENT = 1;

-- ========================================
-- 插入客户主表数据
-- ========================================

-- 客户1: 阿里巴巴集团
INSERT INTO `customers` (
  `customer_code`, `customer_name`, `short_name`, `customer_type`, `customer_level`, `industry`,
  `tax_number`, `legal_person`, `registered_capital`, `registered_address`, `business_address`, `contact_address`,
  `company_phone`, `company_email`, `website`, `business_scope`, `credit_code`,
  `credit_limit`, `payment_terms`, `tax_rate`, `bank_name`, `bank_account`,
  `source`, `sales_person`, `sales_department`, `status`, `remark`, `create_by`
) VALUES (
  'ALB001', '阿里巴巴集团控股有限公司', '阿里巴巴', '企业客户', 'A级客户', '互联网',
  '91330000MA27XA8G9B', '张勇', '50000.00', '杭州市余杭区文一西路969号', '杭州市西湖区文三路90号', '杭州市滨江区网商路699号',
  '0571-85022088', 'contact@alibaba.com', 'www.alibaba.com', '互联网信息服务、电子商务、云计算', '91330000MA27XA8G9B',
  '10000000.00', '月结60天', '13.00', '中国工商银行杭州分行', '6222021001234567890',
  '老客户介绍', '张三', '销售一部', '正常', '重要客户，VIP待遇', 'admin'
);

-- 客户2: 腾讯科技
INSERT INTO `customers` (
  `customer_code`, `customer_name`, `short_name`, `customer_type`, `customer_level`, `industry`,
  `tax_number`, `legal_person`, `registered_capital`, `registered_address`, `contact_address`,
  `company_phone`, `company_email`, `website`,
  `credit_limit`, `payment_terms`, `tax_rate`, `bank_name`, `bank_account`,
  `source`, `sales_person`, `sales_department`, `status`, `create_by`
) VALUES (
  'TX001', '深圳市腾讯计算机系统有限公司', '腾讯', '企业客户', 'A级客户', '互联网',
  '91440300708461136T', '马化腾', '100000.00', '深圳市南山区粤海街道麻岭社区科技中一路腾讯大厦', '深圳市南山区科技园',
  '0755-86013388', 'service@tencent.com', 'www.tencent.com',
  '5000000.00', '月结30天', '13.00', '中国建设银行深圳分行', '6217001234567890123',
  '网络推广', '李四', '销售一部', '正常', 'admin'
);

-- 客户3: 华为技术
INSERT INTO `customers` (
  `customer_code`, `customer_name`, `short_name`, `customer_type`, `customer_level`, `industry`,
  `tax_number`, `legal_person`, `registered_capital`, `contact_address`,
  `company_phone`, `company_email`, `website`,
  `credit_limit`, `payment_terms`, `tax_rate`,
  `source`, `sales_person`, `sales_department`, `status`, `create_by`
) VALUES (
  'HW001', '华为技术有限公司', '华为', '企业客户', 'A级客户', '通信设备',
  '91440300192174123Q', '任正非', '403.09', '深圳市龙岗区坂田华为基地',
  '0755-28780808', 'info@huawei.com', 'www.huawei.com',
  '8000000.00', '月结60天', '13.00',
  '展会', '王五', '销售二部', '正常', 'admin'
);

-- 客户4: 小米科技
INSERT INTO `customers` (
  `customer_code`, `customer_name`, `short_name`, `customer_type`, `customer_level`, `industry`,
  `contact_address`, `company_phone`, `company_email`, `website`,
  `credit_limit`, `payment_terms`, `tax_rate`,
  `source`, `sales_person`, `sales_department`, `status`, `create_by`
) VALUES (
  'XM001', '小米科技有限责任公司', '小米', '企业客户', 'B级客户', '消费电子',
  '北京市海淀区清河中街68号华润五彩城', '010-59592888', 'service@xiaomi.com', 'www.mi.com',
  '2000000.00', '月结30天', '13.00',
  '电话营销', '赵六', '销售二部', '正常', 'admin'
);

-- 客户5: 京东集团
INSERT INTO `customers` (
  `customer_code`, `customer_name`, `short_name`, `customer_type`, `customer_level`, `industry`,
  `contact_address`, `company_phone`, `company_email`, `website`,
  `credit_limit`, `payment_terms`, `tax_rate`,
  `source`, `sales_person`, `sales_department`, `status`, `create_by`
) VALUES (
  'JD001', '北京京东世纪贸易有限公司', '京东', '企业客户', 'B级客户', '电子商务',
  '北京市大兴区亦庄经济开发区科创十一街18号', '400-606-5500', 'service@jd.com', 'www.jd.com',
  '3000000.00', '货到付款', '13.00',
  '网络推广', '张三', '销售一部', '正常', 'admin'
);

-- 客户6: 字节跳动
INSERT INTO `customers` (
  `customer_code`, `customer_name`, `short_name`, `customer_type`, `customer_level`, `industry`,
  `contact_address`, `company_phone`, `company_email`,
  `credit_limit`, `payment_terms`, `tax_rate`,
  `source`, `sales_person`, `sales_department`, `status`, `create_by`
) VALUES (
  'ZJ001', '北京字节跳动科技有限公司', '字节跳动', '企业客户', 'C级客户', '互联网',
  '北京市海淀区北三环西路甲18号', '010-82765555', 'service@bytedance.com',
  '500000.00', '现款现货', '13.00',
  '老客户介绍', '李四', '销售三部', '正常', 'admin'
);

-- 客户7: 潜在客户
INSERT INTO `customers` (
  `customer_code`, `customer_name`, `short_name`, `customer_type`, `customer_level`,
  `contact_address`, `company_phone`, `company_email`,
  `payment_terms`, `source`, `sales_person`, `sales_department`, `status`, `create_by`
) VALUES (
  'QZ001', '深圳市创新科技有限公司', '创新科技', '企业客户', '潜在客户',
  '深圳市南山区科技园', '0755-88889999', 'info@cxkj.com',
  '现款现货', '展会', '王五', '销售三部', '正常', 'admin'
);

-- ========================================
-- 插入客户联系人数据
-- ========================================

-- 阿里巴巴的联系人
INSERT INTO `customer_contacts` (
  `customer_id`, `contact_name`, `contact_gender`, `contact_position`, `contact_department`,
  `contact_phone`, `contact_mobile`, `contact_email`, `contact_wechat`,
  `is_primary`, `is_decision_maker`, `birthday`, `hobby`, `remark`, `sort_order`
) VALUES 
(1, '马经理', '男', '采购总监', '采购部', '0571-85022001', '13800138001', 'ma@alibaba.com', 'ma_wechat', 1, 1, '1975-03-15', '高尔夫、阅读', '主要联系人，决策人', 1),
(1, '李助理', '女', '采购助理', '采购部', '0571-85022002', '13800138002', 'li@alibaba.com', 'li_wechat', 0, 0, '1990-06-20', '瑜伽、旅游', '协助处理日常事务', 2),
(1, '王主管', '男', '技术主管', '技术部', '0571-85022003', '13800138003', 'wang@alibaba.com', NULL, 0, 0, '1985-09-10', '篮球、编程', '技术对接人', 3);

-- 腾讯的联系人
INSERT INTO `customer_contacts` (
  `customer_id`, `contact_name`, `contact_gender`, `contact_position`, `contact_department`,
  `contact_phone`, `contact_mobile`, `contact_email`,
  `is_primary`, `is_decision_maker`, `sort_order`
) VALUES 
(2, '张总', '男', '采购总经理', '采购部', '0755-86013001', '13900139001', 'zhang@tencent.com', 1, 1, 1),
(2, '刘经理', '女', '采购经理', '采购部', '0755-86013002', '13900139002', 'liu@tencent.com', 0, 0, 2);

-- 华为的联系人
INSERT INTO `customer_contacts` (
  `customer_id`, `contact_name`, `contact_gender`, `contact_position`,
  `contact_phone`, `contact_mobile`, `contact_email`,
  `is_primary`, `is_decision_maker`, `sort_order`
) VALUES 
(3, '陈总监', '男', '供应链总监', '0755-28780001', '13700137001', 'chen@huawei.com', 1, 1, 1),
(3, '周采购', '女', '采购专员', '0755-28780002', '13700137002', 'zhou@huawei.com', 0, 0, 2),
(3, '吴工程师', '男', '质量工程师', '0755-28780003', '13700137003', 'wu@huawei.com', 0, 0, 3);

-- 小米的联系人
INSERT INTO `customer_contacts` (
  `customer_id`, `contact_name`, `contact_position`,
  `contact_phone`, `contact_mobile`, `contact_email`,
  `is_primary`, `is_decision_maker`, `sort_order`
) VALUES 
(4, '郑经理', '采购经理', '010-59592001', '13600136001', 'zheng@xiaomi.com', 1, 1, 1),
(4, '孙助理', '采购助理', '010-59592002', '13600136002', 'sun@xiaomi.com', 0, 0, 2);

-- 京东的联系人
INSERT INTO `customer_contacts` (
  `customer_id`, `contact_name`, `contact_position`,
  `contact_phone`, `contact_mobile`, `contact_email`,
  `is_primary`, `is_decision_maker`, `sort_order`
) VALUES 
(5, '林总', '采购总监', '400-606-5501', '13500135001', 'lin@jd.com', 1, 1, 1);

-- 字节跳动的联系人
INSERT INTO `customer_contacts` (
  `customer_id`, `contact_name`, `contact_position`,
  `contact_phone`, `contact_mobile`,
  `is_primary`, `is_decision_maker`, `sort_order`
) VALUES 
(6, '何经理', '采购经理', '010-82765001', '13400134001', 1, 0, 1);

-- 潜在客户的联系人
INSERT INTO `customer_contacts` (
  `customer_id`, `contact_name`, `contact_position`,
  `contact_phone`, `contact_mobile`, `contact_email`,
  `is_primary`, `is_decision_maker`, `sort_order`
) VALUES 
(7, '谢总', '总经理', '0755-88889001', '13300133001', 'xie@cxkj.com', 1, 1, 1);

-- ========================================
-- 插入编号序列数据
-- ========================================
INSERT INTO `customer_code_sequence` (`prefix`, `current_number`) VALUES
('ALB', 1),
('TX', 1),
('HW', 1),
('XM', 1),
('JD', 1),
('ZJ', 1),
('QZ', 1);

-- ========================================
-- 查询验证
-- ========================================
SELECT '✅ 测试数据插入完成！' AS message;

SELECT '--- 客户列表 ---' AS section;
SELECT 
  customer_code AS 编号,
  customer_name AS 客户名称,
  short_name AS 简称,
  customer_level AS 等级,
  sales_person AS 负责人,
  status AS 状态
FROM customers 
WHERE is_deleted = 0
ORDER BY id;

SELECT '--- 联系人统计 ---' AS section;
SELECT 
  c.customer_code AS 客户编号,
  c.customer_name AS 客户名称,
  COUNT(cc.id) AS 联系人数量,
  GROUP_CONCAT(cc.contact_name ORDER BY cc.sort_order SEPARATOR ', ') AS 联系人列表
FROM customers c
LEFT JOIN customer_contacts cc ON c.id = cc.customer_id
WHERE c.is_deleted = 0
GROUP BY c.id
ORDER BY c.id;

SELECT '--- 主联系人 ---' AS section;
SELECT 
  c.customer_code AS 客户编号,
  c.customer_name AS 客户名称,
  cc.contact_name AS 主联系人,
  cc.contact_mobile AS 手机,
  cc.contact_email AS 邮箱
FROM customers c
LEFT JOIN customer_contacts cc ON c.id = cc.customer_id AND cc.is_primary = 1
WHERE c.is_deleted = 0
ORDER BY c.id;
