package com.fine.serviceIMPL;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.OrderDetailMapper;
import com.fine.modle.OrderDetail;
import com.fine.service.OrderDetailService;

@Service
public class OrderDetailServiceImpl extends ServiceImpl<OrderDetailMapper, OrderDetail> implements OrderDetailService {
	
	
}
