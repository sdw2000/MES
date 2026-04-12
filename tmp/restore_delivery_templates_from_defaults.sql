INSERT INTO label_print_template_config (
    biz_type, scene_name, template_key, customer_code, sort_no, is_active, remark, create_by, create_time, update_by, update_time
)
SELECT
    'delivery_notice_template' AS biz_type,
    CASE
        WHEN LOWER(d.template_key) LIKE '%wanbao%' THEN 'wanbao_delivery_template'
        WHEN LOWER(d.template_key) LIKE '%liyuan%' THEN 'liyuan_delivery_template'
        WHEN LOWER(d.template_key) LIKE '%yiwei%' THEN 'yiwei_delivery_template'
        ELSE d.template_key
    END AS scene_name,
    d.template_key,
    NULL AS customer_code,
    999 AS sort_no,
    1 AS is_active,
    '{"compact":false,"showCarrierPhone":true,"showCustomerOrderNo":true,"showItemArea":true,"showItemBox":true,"showItemRemark":true,"showFooterNotes":true}' AS remark,
    'system' AS create_by,
    NOW() AS create_time,
    'system' AS update_by,
    NOW() AS update_time
FROM (
    SELECT DISTINCT TRIM(template_key) AS template_key
    FROM label_print_template_config
    WHERE biz_type = 'delivery_notice_default'
      AND is_active = 1
      AND template_key IS NOT NULL
      AND TRIM(template_key) <> ''
) d
LEFT JOIN label_print_template_config t
       ON t.biz_type = 'delivery_notice_template'
      AND t.template_key = d.template_key
WHERE t.id IS NULL;

SELECT id, template_key, scene_name, sort_no, is_active
FROM label_print_template_config
WHERE biz_type = 'delivery_notice_template'
ORDER BY sort_no, id;
