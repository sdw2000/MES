package com.fine.Dao.purchase;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.modle.PurchaseOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
            + "    poi.thickness, poi.width, poi.length, poi.rolls, poi.sqm, poi.unit_price, poi.amount, poi.remark, "
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
}
