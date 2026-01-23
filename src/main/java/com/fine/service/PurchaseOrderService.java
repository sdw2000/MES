package com.fine.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.Utils.ResponseResult;
import com.fine.modle.PurchaseOrder;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;

public interface PurchaseOrderService extends IService<PurchaseOrder> {

    ResponseResult<?> getAllOrders(Integer pageNum, Integer pageSize, String orderNo, String supplier, String startDate, String endDate);

    ResponseResult<?> createOrder(PurchaseOrder purchaseOrder);

    ResponseResult<?> updateOrder(PurchaseOrder purchaseOrder);

    ResponseResult<?> deleteOrder(String orderNo);

    ResponseResult<?> getOrderByOrderNo(String orderNo);

    ResponseResult<?> searchOrders(String keyword, String status);

    void exportOrders(HttpServletResponse response);

    ResponseResult<?> importOrders(MultipartFile file, String username);

    void downloadTemplate(HttpServletResponse response);

    String generateOrderNo();
}
