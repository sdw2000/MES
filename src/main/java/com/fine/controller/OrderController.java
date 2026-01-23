package com.fine.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Dao.OrderDetailMapper;
import com.fine.Dao.OrderMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.Order;
import com.fine.modle.OrderDetail;
import com.fine.modle.OrderDetailDTO;
import com.fine.service.OrderDetailService;
import com.fine.service.OrderService;

@PreAuthorize("hasAuthority('admin')")
@RestController
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;    @Autowired
    private OrderDetailService orderDetailService;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetail;
    
    /*use the orderNumber查询订单及详情
     * 参数 订单ID
     * 返回订单详情
     * 
     */
    
    @GetMapping("/getOrdersByOrderNumble")
    public ResponseResult<?> listOrdersSearch(@RequestParam String id) {
        Order order = orderMapper.getOrderBycustomerOrder(id);
                if (order != null) {
            QueryWrapper<OrderDetail> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("order_id", order.getId()); // Use the correct order ID
            List<OrderDetail> oDetail = orderDetail.selectList(queryWrapper); // Use the correct mapper instance
            System.out.println(oDetail);
            if (oDetail != null) {
                order.setOrderDetails(oDetail);
            }
        }

        return new ResponseResult<>(20000, "操作成功", order);
    }
    
    @GetMapping("/getOrderNumble")
    public ResponseResult<?> getOrderNumble() {
    	

        return orderService.getOrderNumble();
    }
    
    @GetMapping("/getQuotationNumble")
    public ResponseResult<?> getQuotationNumble() {
    	

        return orderService.getQuotationNumble();
    }

    @GetMapping
    public ResponseResult<?> listOrders(@RequestParam(required = false) String orderNumber,
                                     @RequestParam(required = false) String customerOrderNumber,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "10") int size) {
    	 Page<OrderDetailDTO> orderPage = new Page<>(page, size);
    	    QueryWrapper<OrderDetailDTO> queryWrapper = new QueryWrapper<>();

    	    if (orderNumber != null && !orderNumber.isEmpty()) {
    	        queryWrapper.like("o.order_number", orderNumber);
    	    }
    	    if (customerOrderNumber != null && !customerOrderNumber.isEmpty()) {
    	        queryWrapper.like("o.customer_order_number", customerOrderNumber);
    	    }
    	    queryWrapper.eq("o.is_deleted", 0);
    	    // Performing join operation and selecting required fields
    	    IPage<OrderDetailDTO> result = orderService.getOrderDetails(orderPage, queryWrapper);
    	    
    	    return new ResponseResult<>(20000, "操作成功", result);
    }

    @PostMapping
    public ResponseResult<?> createOrder(@RequestBody Order order) {
        boolean success = orderService.save(order);
        return new ResponseResult<>(20000, "操作成功", success);
    }

    @PutMapping("/updateOrder")
    public ResponseResult<?> updateOrder(@RequestBody Order order) {
    	orderMapper.updateById(order);
    	for (OrderDetail detail : order.getOrderDetails()) {
        	System.out.println("_____________________"+detail);
        	detail.setOrderId(order.getId());
            orderDetail.updateById(detail);  
        }
    	
       
        return new ResponseResult<>(20000, "操作成功");
    }

    @DeleteMapping("delete/{id}")
    @Transactional
    public ResponseResult<?> deleteOrder(@PathVariable String id) {
    	QueryWrapper<OrderDetail> queryWrapper = new QueryWrapper<>();
    	queryWrapper.eq("order_id", id);
    	QueryWrapper<Order> queryWrapper2 = new QueryWrapper<>();
    	queryWrapper2.eq("id", id);
    	orderMapper.delete(queryWrapper2);
    	orderDetail.delete(queryWrapper);
        
        return new ResponseResult<>(20000, "操作成功");
    }

    @GetMapping("/{orderId}/details")
    public ResponseResult<?> listOrderDetails(@PathVariable int orderId,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        Page<OrderDetail> orderDetailPage = new Page<>(page, size);
        QueryWrapper<OrderDetail> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("order_id", orderId);
        Page<OrderDetail> result = orderDetailService.page(orderDetailPage, queryWrapper);
        return new ResponseResult<>(20000, "操作成功", result);
    }

    @PostMapping("/{orderId}/details")
    public ResponseResult<?> createOrderDetail(@PathVariable Integer orderId, @RequestBody OrderDetail orderDetail) {
        orderDetail.setId(orderId);
        boolean success = orderDetailService.save(orderDetail);
        return new ResponseResult<>(20000, "操作成功", success);
    }

    @PostMapping("/with-details")
    @Transactional
    public ResponseResult<?> updateOrderDetail( @RequestBody Order order) {
    	System.out.println(order);
    	try {
    		orderMapper.insert(order); // Assuming this inserts the main order details
    		Integer insertedId = order.getId();
    		
            // Loop through each order detail and insert them
            for (OrderDetail detail : order.getOrderDetails()) {
            	
            	detail.setOrderId(insertedId);
                orderDetail.insert(detail); 
                
            }
            } catch (Exception e) {}

    	return new ResponseResult<>(20000, "操作成功");
    }

    @DeleteMapping("/details/{id}")
    public ResponseResult<?> deleteOrderDetail(@PathVariable int id) {
        boolean success = orderDetailService.removeById(id);
        orderService.removeById(id);
        return new ResponseResult<>(20000, "操作成功", success);
    }
}