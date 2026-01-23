package com.fine.mapper.schedule;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.schedule.OrderCustomerPriority;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单客户优先级Mapper
 */
@Mapper
public interface OrderCustomerPriorityMapper extends BaseMapper<OrderCustomerPriority> {
    
    /**
     * 根据订单ID列表查询优先级，按总分降序、下单时间升序排列
     */
    @Select("<script>" +
            "SELECT * FROM order_customer_priority " +
            "WHERE order_id IN " +
            "<foreach item='item' index='index' collection='orderIds' open='(' separator=',' close=')'>" +
            "#{item}" +
            "</foreach>" +
            " ORDER BY total_score DESC, order_time ASC" +
            "</script>")
    List<OrderCustomerPriority> selectByOrderIdsOrdered(List<Long> orderIds);
}
