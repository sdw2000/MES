-- 物流公司编码标准化（快递100）
-- 用途：
-- 1) 自动补齐 logistics_companies.company_code
-- 2) 自动插入常用承运公司（不存在时）
-- 3) 兼容别名（如“顺丰速运/顺丰快递/SF”）

START TRANSACTION;

-- 1) 新增常用公司（如不存在）
INSERT INTO logistics_companies
(company_name, company_code, status, remark, created_at, updated_at, is_deleted)
SELECT m.company_name, m.company_code, 'active', '系统初始化-快递100编码', NOW(), NOW(), 0
FROM (
    SELECT '顺丰速运' AS company_name, 'shunfeng' AS company_code
    UNION ALL SELECT '圆通速递', 'yuantong'
    UNION ALL SELECT '中通快递', 'zhongtong'
    UNION ALL SELECT '申通快递', 'shentong'
    UNION ALL SELECT '韵达快递', 'yunda'
    UNION ALL SELECT '邮政快递', 'youzhengguonei'
    UNION ALL SELECT 'EMS', 'ems'
    UNION ALL SELECT '京东快递', 'jd'
    UNION ALL SELECT '德邦快递', 'debangwuliu'
    UNION ALL SELECT '安能物流', 'annengwuliu'
    UNION ALL SELECT '极兔速递', 'jtexpress'
    UNION ALL SELECT '百世快递', 'huitongkuaidi'
    UNION ALL SELECT '天天快递', 'tiantian'
    UNION ALL SELECT '宅急送', 'zhaijisong'
    UNION ALL SELECT '丰网速运', 'fengwang'
    UNION ALL SELECT '顺心捷达', 'shunxinjieda'
    UNION ALL SELECT '中铁快运', 'ztky'
    UNION ALL SELECT '跨越速运', 'kuayue'
) m
WHERE NOT EXISTS (
    SELECT 1
    FROM logistics_companies lc
    WHERE lc.is_deleted = 0
      AND lc.company_name = m.company_name
);

-- 2) 补齐已有公司的 company_code（仅更新空值）
-- 说明：通过名称标准化匹配，去掉“快递/速运/物流/空格”后比对
UPDATE logistics_companies lc
JOIN (
    SELECT '顺丰速运' AS alias_name, 'shunfeng' AS company_code
    UNION ALL SELECT '顺丰', 'shunfeng'
    UNION ALL SELECT '顺丰快递', 'shunfeng'
    UNION ALL SELECT 'SF', 'shunfeng'
    UNION ALL SELECT 'SF EXPRESS', 'shunfeng'

    UNION ALL SELECT '圆通速递', 'yuantong'
    UNION ALL SELECT '圆通', 'yuantong'

    UNION ALL SELECT '中通快递', 'zhongtong'
    UNION ALL SELECT '中通', 'zhongtong'

    UNION ALL SELECT '申通快递', 'shentong'
    UNION ALL SELECT '申通', 'shentong'

    UNION ALL SELECT '韵达快递', 'yunda'
    UNION ALL SELECT '韵达', 'yunda'

    UNION ALL SELECT '邮政快递', 'youzhengguonei'
    UNION ALL SELECT '邮政', 'youzhengguonei'
    UNION ALL SELECT '中国邮政', 'youzhengguonei'
    UNION ALL SELECT 'EMS', 'ems'
    UNION ALL SELECT 'EMS快递', 'ems'

    UNION ALL SELECT '京东快递', 'jd'
    UNION ALL SELECT '京东', 'jd'

    UNION ALL SELECT '德邦快递', 'debangwuliu'
    UNION ALL SELECT '德邦', 'debangwuliu'

    UNION ALL SELECT '安能物流', 'annengwuliu'
    UNION ALL SELECT '安能', 'annengwuliu'

    UNION ALL SELECT '极兔速递', 'jtexpress'
    UNION ALL SELECT '极兔', 'jtexpress'

    UNION ALL SELECT '百世快递', 'huitongkuaidi'
    UNION ALL SELECT '百世', 'huitongkuaidi'

    UNION ALL SELECT '天天快递', 'tiantian'
    UNION ALL SELECT '宅急送', 'zhaijisong'

    UNION ALL SELECT '丰网速运', 'fengwang'
    UNION ALL SELECT '丰网', 'fengwang'

    UNION ALL SELECT '顺心捷达', 'shunxinjieda'

    UNION ALL SELECT '中铁快运', 'ztky'
    UNION ALL SELECT '中铁', 'ztky'

    UNION ALL SELECT '跨越速运', 'kuayue'
    UNION ALL SELECT '跨越快递', 'kuayue'
    UNION ALL SELECT '跨越', 'kuayue'
) m
  ON UPPER(REPLACE(REPLACE(REPLACE(REPLACE(IFNULL(lc.company_name, ''), ' ', ''), '快递', ''), '速运', ''), '物流', ''))
   = UPPER(REPLACE(REPLACE(REPLACE(REPLACE(IFNULL(m.alias_name, ''), ' ', ''), '快递', ''), '速运', ''), '物流', ''))
SET lc.company_code = m.company_code,
    lc.updated_at = NOW()
WHERE lc.is_deleted = 0
  AND (
      lc.company_code IS NULL
      OR lc.company_code = ''
      OR LOWER(lc.company_code) <> LOWER(m.company_code)
  );

COMMIT;

-- 校验输出
SELECT company_name, company_code, status, updated_at
FROM logistics_companies
WHERE is_deleted = 0
  AND company_name IN (
      '顺丰速运','圆通速递','中通快递','申通快递','韵达快递','邮政快递','EMS','京东快递',
      '德邦快递','安能物流','极兔速递','百世快递','天天快递','宅急送','丰网速运','顺心捷达','中铁快运','跨越速运'
  )
ORDER BY company_name;
