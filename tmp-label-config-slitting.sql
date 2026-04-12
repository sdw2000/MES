START TRANSACTION;
SET @TEMPLATE_VER = 'v1';

INSERT INTO label_print_template_config
(biz_type, scene_name, template_key, customer_code, sort_no, is_active, remark, create_by, create_time, update_by, update_time)
SELECT 'SLITTING_CORE_LABEL','分切卷芯标签',CONCAT('SLITTING_CORE_LABEL__', c.customer_code, '__', @TEMPLATE_VER),c.customer_code,1,1,'客户专用自动初始化','system',NOW(),'system',NOW()
FROM (
  SELECT 'DGFN001' AS customer_code
  UNION ALL SELECT 'ZZWB1001'
  UNION ALL SELECT 'ZZ6001'
  UNION ALL SELECT 'ZYCJ01'
  UNION ALL SELECT 'ZX1001'
) c
WHERE NOT EXISTS (
  SELECT 1 FROM label_print_template_config t
  WHERE t.biz_type='SLITTING_CORE_LABEL' AND t.customer_code=c.customer_code AND t.is_active=1
);

INSERT INTO label_print_template_config
(biz_type, scene_name, template_key, customer_code, sort_no, is_active, remark, create_by, create_time, update_by, update_time)
SELECT 'SLITTING_PALLET_LABEL','分切栈板标签',CONCAT('SLITTING_PALLET_LABEL__', c.customer_code, '__', @TEMPLATE_VER),c.customer_code,1,1,'客户专用自动初始化','system',NOW(),'system',NOW()
FROM (
  SELECT 'DGFN001' AS customer_code
  UNION ALL SELECT 'ZZWB1001'
  UNION ALL SELECT 'ZZ6001'
  UNION ALL SELECT 'ZYCJ01'
  UNION ALL SELECT 'ZX1001'
) c
WHERE NOT EXISTS (
  SELECT 1 FROM label_print_template_config t
  WHERE t.biz_type='SLITTING_PALLET_LABEL' AND t.customer_code=c.customer_code AND t.is_active=1
);

COMMIT;

SELECT biz_type, customer_code, template_key, is_active, sort_no, update_time
FROM label_print_template_config
WHERE customer_code IN ('DGFN001','ZZWB1001','ZZ6001','ZYCJ01','ZX1001')
  AND biz_type IN ('SLITTING_CORE_LABEL','SLITTING_INNER_LABEL','SLITTING_OUTER_LABEL','SLITTING_PALLET_LABEL')
ORDER BY customer_code, biz_type;
