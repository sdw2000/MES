package com.fine.serviceIMPL;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.OrderMapper;
import com.fine.Dao.QuotationMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.Order;
import com.fine.modle.OrderDetailDTO;
import com.fine.modle.Quotation;
import com.fine.service.OrderService;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {
	
	@Autowired
	private OrderMapper orderMapper;
	
	@Autowired
	private QuotationMapper qutationMapper;
	
	
	@Override
	public IPage<OrderDetailDTO> getOrderDetails(Page<OrderDetailDTO> page, QueryWrapper<OrderDetailDTO> queryWrapper) {
	    return this.baseMapper.getOrderDetails(page, queryWrapper);
	}

	@Override
	public ResponseResult<?> getOrderNumble() {
		LocalDate today = LocalDate.now();
        String todayStr = DateTimeFormatter.ofPattern("yyMMdd").format(today);
        String prefix = "DH" + todayStr;

        // Query the latest order number with the current date
        QueryWrapper<Order> queryWrapper = new QueryWrapper<>();
        queryWrapper.likeRight("order_number", prefix).orderByDesc("order_number").last("LIMIT 1");
        Order lastOrder = orderMapper.selectOne(queryWrapper);

        String newOrderNumber;
        if (lastOrder != null && lastOrder.getOrderNumber().startsWith(prefix)) {
            // Extract the last sequence number and increment it
            int lastSequence = Integer.parseInt(lastOrder.getOrderNumber().substring(8));
            newOrderNumber = prefix + String.format("%03d", lastSequence + 1);
        } else {
            // Start a new sequence
            newOrderNumber = prefix + "001";
        }
        Map<String, Object> map = new HashMap<>();
        map.put("newOrderNumber",newOrderNumber);
         return new ResponseResult<>(20000, "获取成功", map);
	}
	
	@Override
	public ResponseResult<?> getQuotationNumble() {
//		LocalDate today = LocalDate.now();
//        String todayStr = DateTimeFormatter.ofPattern("yyMMdd").format(today);
//        String prefix = "DH" + todayStr;        // Query the latest order number with the current date
        QueryWrapper<Quotation> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByDesc("id").last("LIMIT 1");
        Quotation lastquQuotation = qutationMapper.selectOne(queryWrapper);

//        if (lastquQuotation != null && String.valueOf(lastquQuotation.getId()).startsWith(prefix)) {
//            // Extract the last sequence number and increment it
//            int lastSequence = Integer.parseInt(String.valueOf(lastquQuotation.getId()).substring(8));
//            newQuotationNumble = prefix + String.format("%03d", lastSequence + 1);
//        } else {
//            // Start a new sequence
//        	newQuotationNumble = prefix + "001";
//        }
        Map<String, Object> map = new HashMap<>();
        map.put("newQuotationNumble",lastquQuotation.getId());
         return new ResponseResult<>(20000, "获取成功", map);
	}

	
}
