package com.fine.Dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.SalesOrderItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface SalesOrderItemMapper extends BaseMapper<SalesOrderItem> {
    /**
     * 减少卷数（用于排程时更新pending_qty）
     * @param id 订单明细ID
     * @param quantity 减少的卷数
     */
    @Update("UPDATE sales_order_items SET rolls = GREATEST(0, rolls - #{quantity}) WHERE id = #{id}")
    int decreaseRolls(@Param("id") Long id, @Param("quantity") Integer quantity);
    
    /**
     * 更新已排程数量
     * @param id 订单明细ID
     * @param quantity 新增的已排程数量
     */
    @Update("UPDATE sales_order_items SET scheduled_qty = COALESCE(scheduled_qty, 0) + #{quantity} WHERE id = #{id}")
    int updateScheduledQty(@Param("id") Long id, @Param("quantity") Integer quantity);

    /**
     * 更新已排程面积与待排面积
     * @param id 订单明细ID
     * @param area 新增排程面积
     */
    @Update("UPDATE sales_order_items SET "
        + "scheduled_area = COALESCE(scheduled_area, 0) + #{area}, "
        + "pending_area = GREATEST(COALESCE(sqm, 0) - (COALESCE(scheduled_area, 0) + #{area}) - COALESCE(delivered_area, 0), 0) "
        + "WHERE id = #{id}")
    int updateScheduledArea(@Param("id") Long id, @Param("area") java.math.BigDecimal area);
    
    /**
     * 查询订单明细完整信息（含订单和客户信息）
     */
    @Select("SELECT soi.*, so.order_no, so.customer, so.delivery_date, " +
            "c.customer_level, c.short_name as customer_name " +
            "FROM sales_order_items soi " +
            "LEFT JOIN sales_orders so ON soi.order_id = so.id " +
            "LEFT JOIN customers c ON so.customer COLLATE utf8mb4_unicode_ci = c.customer_code COLLATE utf8mb4_unicode_ci " +
            "WHERE soi.id = #{id}")
    Map<String, Object> selectFullItemById(@Param("id") Long id);

        /**
         * 分页查询未完成的订单明细（按面积口径），动态过滤。
         * 口径：
         *  scheduled_area = (SELECT SUM(schedule_qty) FROM schedule_order_item WHERE order_item_id=soi.id AND status!='cancelled')
         *                   * (soi.sqm / NULLIF(soi.rolls,0))
         *  delivered_area = (SELECT SUM(delivery_sqm) FROM order_delivery_record WHERE order_item_id=soi.id)
         *  pending_area   = GREATEST(soi.sqm - scheduled_area - delivered_area, 0)
         */
        @Select("<script>\n"
            + "SELECT * FROM (\n"
            + "  SELECT "
            + "    soi.id, soi.order_id, soi.material_code, soi.material_name, soi.color_code, "
            + "    soi.thickness, soi.width, soi.length, soi.rolls, soi.scheduled_qty, "
            + "    CASE WHEN soi.rolls IS NULL OR soi.rolls = 0 THEN 0"
            + "         ELSE COALESCE((SELECT SUM(sch.schedule_qty)"
            + "                           FROM schedule_order_item sch"
            + "                          WHERE sch.order_item_id = soi.id"
            + "                            AND sch.status != 'cancelled'), 0) * (soi.sqm / soi.rolls) END AS scheduled_area, "
            + "    soi.produced_area, "
            + "    COALESCE((SELECT SUM(od.delivery_sqm)"
            + "               FROM order_delivery_record od"
            + "              WHERE od.order_item_id = soi.id), 0) AS delivered_area, "
            + "    soi.sqm, soi.unit_price, soi.amount, soi.remark, soi.created_by, soi.updated_by, soi.created_at, soi.updated_at, soi.is_deleted, "
            + "    so.order_no AS order_no, so.delivery_date AS delivery_date, "
            + "    GREATEST(soi.sqm - "
            + "             (CASE WHEN soi.rolls IS NULL OR soi.rolls = 0 THEN 0"
            + "                   ELSE COALESCE((SELECT SUM(sch2.schedule_qty)"
            + "                                         FROM schedule_order_item sch2"
            + "                                        WHERE sch2.order_item_id = soi.id"
            + "                                          AND sch2.status != 'cancelled'), 0) * (soi.sqm / soi.rolls) END) - "
            + "             COALESCE((SELECT SUM(od2.delivery_sqm)"
            + "                        FROM order_delivery_record od2"
            + "                       WHERE od2.order_item_id = soi.id), 0), 0) AS pending_area "
            + "  FROM sales_order_items soi "
            + "  LEFT JOIN sales_orders so ON so.id = soi.order_id "
            + "  WHERE soi.is_deleted = 0 "
            + "    <if test='orderNo != null and orderNo != \"\"'>AND so.order_no LIKE CONCAT('%', #{orderNo}, '%')</if> "
            + "    <if test='materialCode != null and materialCode != \"\"'>AND soi.material_code = #{materialCode}</if> "
            + " ) t "
            + "WHERE t.pending_area &gt; 0 "
            + "ORDER BY t.created_at DESC\n"
            + "</script>")
        com.baomidou.mybatisplus.core.metadata.IPage<SalesOrderItem> selectPendingItems(
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<SalesOrderItem> page,
            @Param("orderNo") String orderNo,
            @Param("materialCode") String materialCode);

        /**
         * 查询客户在某料号下历史下单规格（去重后按最近下单时间倒序）
         */
        @Select("<script>" +
            "SELECT " +
            "  soi.thickness AS thickness, " +
            "  soi.width AS width, " +
            "  soi.length AS length, " +
            "  MAX(so.order_date) AS lastOrderDate, " +
            "  COUNT(1) AS useCount " +
            "FROM sales_order_items soi " +
            "INNER JOIN sales_orders so ON so.id = soi.order_id " +
            "WHERE so.is_deleted = 0 " +
            "  AND soi.is_deleted = 0 " +
            "  AND (so.customer = #{customerCode} " +
            "       OR REGEXP_REPLACE(so.customer, '[^0-9A-Za-z\\\\u4e00-\\\\u9fa5_-]', '') = #{customerCode}) " +
            "  AND REGEXP_REPLACE(REPLACE(TRIM(soi.material_code), '　', ' '), '\\s+', '') = " +
            "      REGEXP_REPLACE(REPLACE(TRIM(#{materialCode}), '　', ' '), '\\s+', '') " +
            "  AND soi.thickness IS NOT NULL " +
            "  AND soi.width IS NOT NULL " +
            "  AND soi.length IS NOT NULL " +
            "GROUP BY soi.thickness, soi.width, soi.length " +
            "ORDER BY MAX(so.order_date) DESC, COUNT(1) DESC " +
            "LIMIT 10" +
            "</script>")
        List<Map<String, Object>> selectCustomerMaterialHistorySpecs(@Param("customerCode") String customerCode,
                                      @Param("materialCode") String materialCode);

        /**
         * 当订单历史为空时，从报价基线中回退查询规格
         */
        @Select("<script>" +
            "SELECT " +
            "  qi.thickness AS thickness, " +
            "  qi.width AS width, " +
            "  qi.length AS length, " +
            "  MAX(q.quotation_date) AS lastOrderDate, " +
            "  COUNT(1) AS useCount " +
            "FROM quotation_items qi " +
            "INNER JOIN quotations q ON q.id = qi.quotation_id " +
            "WHERE q.is_deleted = 0 " +
            "  AND qi.is_deleted = 0 " +
            "  AND q.source_sample_no = 'INIT_FROM_SALES_ORDER_LATEST_PRICE' " +
            "  AND (q.customer = #{customerCode} " +
            "       OR REGEXP_REPLACE(q.customer, '[^0-9A-Za-z\\\\u4e00-\\\\u9fa5_-]', '') = #{customerCode}) " +
            "  AND REGEXP_REPLACE(REPLACE(TRIM(qi.material_code), '　', ' '), '\\s+', '') = " +
            "      REGEXP_REPLACE(REPLACE(TRIM(#{materialCode}), '　', ' '), '\\s+', '') " +
            "  AND qi.thickness IS NOT NULL " +
            "  AND qi.width IS NOT NULL " +
            "  AND qi.length IS NOT NULL " +
            "GROUP BY qi.thickness, qi.width, qi.length " +
            "ORDER BY MAX(q.quotation_date) DESC, COUNT(1) DESC " +
            "LIMIT 10" +
            "</script>")
        List<Map<String, Object>> selectCustomerMaterialHistorySpecsFromQuotationBaseline(@Param("customerCode") String customerCode,
                                                                                           @Param("materialCode") String materialCode);

        @Delete("DELETE FROM sales_order_items")
        int deleteAllPhysical();

        @Update("ALTER TABLE sales_order_items AUTO_INCREMENT = 1")
        int resetAutoIncrement();

        @Select("SELECT " +
            "IFNULL(SUM(IFNULL(delivered_qty, 0)), 0) AS completed_rolls, " +
            "IFNULL(SUM(IFNULL(remaining_qty, GREATEST(rolls - IFNULL(delivered_qty, 0), 0))), 0) AS remaining_rolls " +
            "FROM sales_order_items WHERE order_id = #{orderId} AND is_deleted = 0")
        Map<String, Object> selectOrderRollProgress(@Param("orderId") Long orderId);
}
