package com.fine.Dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.DeliveryNotice;

@Mapper
public interface DeliveryNoticeMapper extends BaseMapper<DeliveryNotice> {
    
    @Select("SELECT * FROM delivery_notices WHERE order_id = #{orderId} AND is_deleted = 0")
    List<DeliveryNotice> selectByOrderId(@Param("orderId") Long orderId);
}
