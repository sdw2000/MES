package com.fine.Dao.purchase;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.modle.PurchaseOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface PurchaseOrderItemMapper extends BaseMapper<PurchaseOrderItem> {

    @Update("UPDATE purchase_order_items SET rolls = GREATEST(0, rolls - #{quantity}) WHERE id = #{id}")
    int decreaseRolls(@Param("id") Long id, @Param("quantity") Integer quantity);

    @Update("UPDATE purchase_order_items SET sqm = #{sqm}, amount = #{amount} WHERE id = #{id}")
    int updateAmounts(@Param("id") Long id, @Param("sqm") java.math.BigDecimal sqm, @Param("amount") java.math.BigDecimal amount);

    @Select("SELECT poi.*, po.order_no, po.delivery_date FROM purchase_order_items poi "
            + "LEFT JOIN purchase_orders po ON poi.order_id = po.id "
            + "WHERE poi.id = #{id}")
    Map<String, Object> selectFullItemById(@Param("id") Long id);

    @Select("<script>"
            + "SELECT * FROM ("
            + "  SELECT "
            + "    poi.id, poi.order_id, poi.material_code, poi.material_name, poi.color_code, "
            + "    poi.thickness, poi.width, poi.length, poi.rolls, poi.sqm, poi.unit_price, poi.amount, poi.raw_spec, poi.film_spec_raw, poi.remark, "
            + "    poi.created_by, poi.updated_by, poi.created_at, poi.updated_at, poi.is_deleted, "
            + "    po.order_no AS order_no, po.delivery_date AS delivery_date "
            + "  FROM purchase_order_items poi "
            + "  LEFT JOIN purchase_orders po ON po.id = poi.order_id "
            + "  WHERE poi.is_deleted = 0 "
            + "    <if test='orderNo != null and orderNo != \"\"'>AND po.order_no LIKE CONCAT('%', #{orderNo}, '%')</if> "
            + "    <if test='materialCode != null and materialCode != \"\"'>AND poi.material_code = #{materialCode}</if> "
            + " ) t "
            + "ORDER BY t.created_at DESC"
            + "</script>")
    IPage<PurchaseOrderItem> selectItems(
            Page<PurchaseOrderItem> page,
            @Param("orderNo") String orderNo,
            @Param("materialCode") String materialCode);

    @Update("<script>"
            + "UPDATE purchase_order_items "
            + "SET is_deleted = 1, updated_by = #{updatedBy}, updated_at = NOW() "
            + "WHERE order_id = #{orderId} AND is_deleted = 0 "
            + "<if test='keepIds != null and keepIds.size() > 0'>"
            + "  AND id NOT IN "
            + "  <foreach collection='keepIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</if>"
            + "</script>")
    int logicDeleteMissingItems(@Param("orderId") Long orderId,
                                @Param("keepIds") java.util.List<Long> keepIds,
                                @Param("updatedBy") String updatedBy);

    @Update("UPDATE purchase_order_items poi "
            + "INNER JOIN purchase_orders po ON po.id = poi.order_id "
            + "SET poi.is_deleted = 1, poi.updated_by = #{updatedBy}, poi.updated_at = NOW() "
            + "WHERE po.order_no = #{orderNo} AND poi.is_deleted = 0")
    int logicDeleteByOrderNo(@Param("orderNo") String orderNo,
                             @Param("updatedBy") String updatedBy);

    @Select("<script>"
            + "SELECT t.raw_spec FROM ("
            + "  SELECT poi.raw_spec AS raw_spec, MAX(COALESCE(poi.updated_at, poi.created_at)) AS latest_time "
            + "  FROM purchase_order_items poi "
            + "  INNER JOIN purchase_orders po ON po.id = poi.order_id AND po.is_deleted = 0 "
            + "  LEFT JOIN customers c ON po.supplier COLLATE utf8mb4_unicode_ci = c.customer_code COLLATE utf8mb4_unicode_ci "
            + "  WHERE poi.is_deleted = 0 "
            + "    AND poi.material_code = #{materialCode} "
            + "    AND poi.raw_spec IS NOT NULL AND poi.raw_spec != '' "
            + "    <if test='supplierKeyword != null and supplierKeyword != \"\"'> "
            + "      AND (po.supplier LIKE CONCAT('%', #{supplierKeyword}, '%') "
            + "           OR c.customer_name LIKE CONCAT('%', #{supplierKeyword}, '%') "
            + "           OR c.short_name LIKE CONCAT('%', #{supplierKeyword}, '%')) "
            + "    </if> "
            + "  GROUP BY poi.raw_spec"
            + ") t "
            + "ORDER BY t.latest_time DESC "
            + "LIMIT 100"
            + "</script>")
    List<String> selectRawSpecHistory(@Param("supplierKeyword") String supplierKeyword,
                                      @Param("materialCode") String materialCode);
}
