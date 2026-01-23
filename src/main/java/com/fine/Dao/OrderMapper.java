package com.fine.Dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.modle.Order;
import com.fine.modle.OrderDetailDTO;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {
	
	@Select("SELECT o.id, o.order_number, o.is_deleted,o.customer_order_number, o.order_date, o.delivery_date, o.notes, o.customer_id, c.customer_code, " +
	        "od.material_code, od.material_name, od.length, od.width, od.thickness, od.roll_count, price, od.amount " +
	        "FROM orders o " +
	        "JOIN order_details od ON o.id = od.order_id " +
	        "JOIN customer c ON o.customer_id = c.customer_id "+
	        "${ew.customSqlSegment}")
	IPage<OrderDetailDTO> getOrderDetails(Page<OrderDetailDTO> page, @Param(Constants.WRAPPER) QueryWrapper<OrderDetailDTO> queryWrapper);
	
	@Select("SELECT o.*, od.* " +
	        "FROM orders o " +
	        "LEFT JOIN order_details od ON o.id = od.order_id " +
	        "WHERE o.id = #{id}")
	Order getOrderByOrderNumber(@Param("id") String id);
	
	@Select("SELECT o.*,c.short_name " +
	        "FROM orders o " +
			"LEFT JOIN customer c ON o.customer_id=c.customer_id "+
	        "WHERE o.id = #{id}")
	Order getOrderBycustomerOrder(@Param("id") String id);
	
	@Select("SELECT order_number FROM orders ORDER BY id DESC LIMIT 1")
    String getLastOrderNumber();


}
