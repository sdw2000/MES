package com.fine.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.Utils.ResponseResult;
import com.fine.modle.SalesOrder;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;

public interface SalesOrderService extends IService<SalesOrder> {
    
    /**
     * 获取所有订单列表
     */
    ResponseResult<?> getAllOrders(Integer pageNum, Integer pageSize, String orderNo, String customer, String startDate, String endDate);
    
    /**
     * 创建订单
     */
    ResponseResult<?> createOrder(SalesOrder salesOrder);
    
    /**
     * 更新订单
     */
    ResponseResult<?> updateOrder(SalesOrder salesOrder);
    
    /**
     * 删除订单（逻辑删除）
     */
    ResponseResult<?> deleteOrder(String orderNo);
    
    /**
     * 根据订单号获取订单详情
     */
    ResponseResult<?> getOrderByOrderNo(String orderNo);
    
    /**
     * 搜索订单（用于发货通知选择）
     */
    ResponseResult<?> searchOrders(String keyword, String status);
    
    /**
     * 生成订单号
     */
    String generateOrderNo();

    /**
     * 下载导入模板
     */
    void downloadTemplate(HttpServletResponse response);

    /**
     * 导入订单Excel
     */
    ResponseResult<?> importOrders(MultipartFile file, String operator);

    /**
     * 导出所有订单
     */
    void exportOrders(HttpServletResponse response);
}
