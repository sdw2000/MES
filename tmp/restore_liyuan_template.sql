INSERT INTO label_print_template_config (
    biz_type, scene_name, template_key, customer_code, sort_no, is_active, remark, create_by, create_time, update_by, update_time
)
SELECT
    'delivery_notice_template',
    '力源发货通知模板',
    'liyuan_delivery',
    NULL,
    5,
    1,
    '{"compact":false,"showCarrierPhone":true,"showCustomerOrderNo":true,"showItemArea":true,"showItemBox":true,"showItemRemark":true,"showFooterNotes":true}',
    'system',
    NOW(),
    'system',
    NOW()
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM label_print_template_config
    WHERE biz_type='delivery_notice_template' AND template_key='liyuan_delivery'
);

UPDATE label_print_template_config
SET template_key='liyuan_delivery',
    is_active=1,
    update_by='system',
    update_time=NOW()
WHERE biz_type='delivery_notice_default'
  AND customer_code='LY6001';

SELECT id, template_key, scene_name, sort_no, is_active
FROM label_print_template_config
WHERE biz_type='delivery_notice_template'
ORDER BY sort_no, id;

SELECT id, customer_code, template_key, is_active
FROM label_print_template_config
WHERE biz_type='delivery_notice_default' AND customer_code='LY6001';
