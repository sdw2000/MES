-- 清理发货明细冗余字段：物料名称/规格（可由料号+订单规格动态映射）
-- 执行策略：
-- 1) material_name 全量清空（保留空串，兼容历史 NOT NULL 约束）
-- 2) spec 仅在可由 order_item_id 回溯订单规格的记录上清空
--    （无 order_item_id 的历史记录保留 spec，避免无法回溯）

UPDATE delivery_notice_items
SET material_name = ''
WHERE material_name IS NOT NULL
  AND material_name <> '';

UPDATE delivery_notice_items dni
INNER JOIN sales_order_items soi ON soi.id = dni.order_item_id
SET dni.spec = ''
WHERE dni.spec IS NOT NULL
  AND dni.spec <> '';
