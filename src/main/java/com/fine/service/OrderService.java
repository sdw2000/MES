package com.fine.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.Utils.ResponseResult;
import com.fine.modle.Order;
import com.fine.modle.OrderDetailDTO;


public interface OrderService extends IService<Order> {

	IPage<OrderDetailDTO> getOrderDetails(Page<OrderDetailDTO> page, QueryWrapper<OrderDetailDTO> queryWrapper);
	
	ResponseResult<?> getOrderNumble();

	ResponseResult<?> getQuotationNumble();

	
}
