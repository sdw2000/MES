package com.fine.Dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.DeliveryNoticeItem;

@Mapper
public interface DeliveryNoticeItemMapper extends BaseMapper<DeliveryNoticeItem> {
    
        @Select("SELECT dni.id, dni.notice_id, dni.order_item_id, dni.material_code, " +
            "COALESCE(ts.product_name, '') AS material_name, " +
            "dni.spec, dni.quantity, dni.area_size, dni.box_count, dni.gross_weight, dni.total_weight, dni.remark " +
            "FROM delivery_notice_items dni " +
            "LEFT JOIN tape_spec ts ON ts.material_code = dni.material_code " +
            "WHERE dni.notice_id = #{noticeId}")
    List<DeliveryNoticeItem> selectByNoticeId(@Param("noticeId") Long noticeId);

    @Select("SELECT IFNULL(SUM(quantity), 0) FROM delivery_notice_items WHERE order_item_id = #{orderItemId} AND EXISTS (SELECT 1 FROM delivery_notices WHERE id = delivery_notice_items.notice_id AND is_deleted = 0)")
    Integer getShippedQuantityByOrderItemId(@Param("orderItemId") Long orderItemId);

    @Select("SELECT IFNULL(SUM(quantity), 0) FROM delivery_notice_items WHERE notice_id = #{noticeId} AND order_item_id = #{orderItemId}")
    Integer getNoticeItemQuantity(@Param("noticeId") Long noticeId, @Param("orderItemId") Long orderItemId);
}
