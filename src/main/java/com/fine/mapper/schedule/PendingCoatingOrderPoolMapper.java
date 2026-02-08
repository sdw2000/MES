package com.fine.mapper.schedule;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.schedule.PendingCoatingOrderPool;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;
import java.util.Map;

/**
 * 待涂布订单池Mapper
 */
@Mapper
public interface PendingCoatingOrderPoolMapper extends BaseMapper<PendingCoatingOrderPool> {
    
    /**
     * 查询料号的所有待涂布订单（按优先级降序）
     */
    @Select("SELECT * FROM pending_coating_order_pool " +
            "WHERE material_code = #{materialCode} AND pool_status = 'WAITING' AND shortage_area > 0 " +
            "ORDER BY customer_priority DESC, added_at ASC")
    List<PendingCoatingOrderPool> selectWaitingByMaterialCode(@Param("materialCode") String materialCode);
    
    /**
     * 查询所有待涂布料号（去重）
     */
    @Select("SELECT DISTINCT material_code, material_name FROM pending_coating_order_pool " +
            "WHERE pool_status = 'WAITING' AND shortage_area > 0")
    List<PendingCoatingOrderPool> selectDistinctWaitingMaterials();

        /**
         * 按池记录ID删除，提交排程后用来移除列表数据。
         */
        int deleteById(@Param("id") Long id);

        /**
         * 按订单明细ID删除（用于自动排产后清理已被排产且已锁定的订单池记录）
         */
        @Delete("DELETE FROM pending_coating_order_pool WHERE order_item_id = #{orderItemId}")
        int deleteByOrderItemId(@Param("orderItemId") Long orderItemId);

        /**
         * 按料号+长度聚合待复卷汇总（联表 sales_order_items 获取长度/宽度信息）
         */
        @Select({
                "<script>",
                "SELECT",
                "  t.materialCode AS materialCode,",
                "  t.materialName AS materialName,",
                "  COALESCE(soi.length, 0) AS length,",
                "  lw.lockedWidth AS width,",
                "  COALESCE(soi.thickness, 0) AS thickness,",
                "  COALESCE(soi.rolls, 0) AS rolls,",
                "  SUM(COALESCE(lk.lockedArea, 0)) AS totalArea,",
                "  lw.lockedWidth AS defaultWidth,",
                "  COALESCE(CEIL(SUM(t.shortageArea) / NULLIF((COALESCE(soi.length,0) * COALESCE(lw.lockedWidth, 0) / 1000.0), 0)), 0) AS requiredRolls,",
                "  COALESCE(CEIL(SUM(t.shortageArea) / NULLIF((COALESCE(soi.length,0) * COALESCE(lw.lockedWidth, 0) / 1000.0), 0)), 0) AS totalShortage,",
                "  COUNT(DISTINCT t.orderItemId) AS orderCount,",
                "  GROUP_CONCAT(DISTINCT t.orderNo) AS orderNosConcat,",
                "  GROUP_CONCAT(t.poolId) AS poolIdsConcat",
                "FROM (",
                "  SELECT",
                "    x.orderItemId,",
                "    MAX(x.orderNo) AS orderNo,",
                "    MAX(x.materialCode) AS materialCode,",
                "    MAX(x.materialName) AS materialName,",
                "    MAX(x.shortageQty) AS shortageQty,",
                "    MAX(x.shortageArea) AS shortageArea,",
                "    GROUP_CONCAT(DISTINCT x.poolId) AS poolId",
                "  FROM (",
                "    SELECT p.id AS poolId, p.order_item_id AS orderItemId, p.order_no AS orderNo,",
                "           p.material_code AS materialCode, p.material_name AS materialName,",
                "           COALESCE(p.shortage_qty, 0) AS shortageQty, COALESCE(p.shortage_area, 0) AS shortageArea",
                "    FROM pending_coating_order_pool p",
                "    WHERE p.pool_status = 'WAITING' AND COALESCE(p.shortage_area,0) > 0",
                "    UNION ALL",
                "    SELECT p.id AS poolId, p.order_item_id AS orderItemId, p.order_no AS orderNo,",
                "           p.material_code AS materialCode, p.material_name AS materialName,",
                "           COALESCE(p.shortage_qty, 0) AS shortageQty, COALESCE(p.shortage_area, 0) AS shortageArea",
                "    FROM pending_rewinding_order_pool p",
                "    WHERE p.pool_status = 'WAITING' AND COALESCE(p.shortage_area,0) > 0",
                "    UNION ALL",
                "    SELECT p.id AS poolId, p.order_item_id AS orderItemId, p.order_no AS orderNo,",
                "           p.material_code AS materialCode, p.material_name AS materialName,",
                "           COALESCE(p.shortage_qty, 0) AS shortageQty, COALESCE(p.shortage_area, 0) AS shortageArea",
                "    FROM pending_slitting_order_pool p",
                "    WHERE p.pool_status = 'WAITING' AND COALESCE(p.shortage_area,0) > 0",
                "  ) x",
                "  GROUP BY x.orderItemId",
                ") t",
                "LEFT JOIN sales_order_items soi ON soi.id = t.orderItemId",
                "LEFT JOIN (",
                "  SELECT l.order_item_id AS orderItemId,",
                "         COALESCE(SUM(l.locked_area), 0) AS lockedArea",
                "  FROM order_material_lock l",
                "  WHERE l.lock_status = 'locked'",
                "  GROUP BY l.order_item_id",
                ") lk ON lk.orderItemId = t.orderItemId",
                "LEFT JOIN (",
                "  SELECT l.order_item_id AS orderItemId,",
                "         MAX(CAST(REGEXP_SUBSTR(l.material_spec, '[0-9]+(\\.[0-9]+)?(?=mm)') AS DECIMAL(18,2))) AS lockedWidth",
                "  FROM order_material_lock l",
                "  WHERE l.lock_status = 'locked'",
                "    AND (l.stock_type = 'jumbo' OR l.stock_type = '母卷')",
                "  GROUP BY l.order_item_id",
                ") lw ON lw.orderItemId = t.orderItemId",
                "GROUP BY t.materialCode, t.materialName, COALESCE(soi.length,0), lw.lockedWidth, COALESCE(soi.thickness,0), COALESCE(soi.rolls,0)",
                "</script>"
        })
        List<Map<String, Object>> selectRewindSummary();
}
