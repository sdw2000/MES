package com.fine.Dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.DeliveryNotice;

@Mapper
public interface DeliveryNoticeMapper extends BaseMapper<DeliveryNotice> {
    
    @Select("SELECT * FROM delivery_notices WHERE order_id = #{orderId} AND is_deleted = 0")
    List<DeliveryNotice> selectByOrderId(@Param("orderId") Long orderId);

    @Update("UPDATE delivery_notices " +
            "SET customer_order_no = #{customerOrderNo}, updated_at = NOW() " +
            "WHERE is_deleted = 0 AND order_no = #{orderNo} " +
            "  AND (customer_order_no IS NULL OR customer_order_no <> #{customerOrderNo})")
    int syncCustomerOrderNoByOrderNo(@Param("orderNo") String orderNo,
                                     @Param("customerOrderNo") String customerOrderNo);
}
