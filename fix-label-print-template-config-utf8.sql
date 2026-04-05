SET NAMES utf8mb4;

UPDATE label_print_template_config
SET scene_name = '母卷标签',
    remark = '涂布母卷标签默认模板',
    update_by = 'system'
WHERE biz_type = 'COATING_ROLL_LABEL';

UPDATE label_print_template_config
SET scene_name = '涂布入库标签单',
    remark = '如该场景走标签模板时可在此配置；A4/B5单据不使用本表',
    update_by = 'system'
WHERE biz_type = 'COATING_INBOUND_SHEET';
