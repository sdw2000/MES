package com.fine.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fine.Utils.ResponseResult;
import com.fine.modle.SalesOrder;
import com.fine.service.SalesOrderService;

import javax.servlet.http.HttpServletResponse;

/**
 * 销售订单控制器
 */
@RestController
@RequestMapping("/sales/orders")
@PreAuthorize("hasAuthority('admin')")
public class SalesOrderController {

    @Autowired
    private SalesOrderService salesOrderService;

    /**
     * 获取所有订单列表
     * GET /sales/orders
     */
    @GetMapping
    public ResponseResult<?> getAllOrders(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "orderNo", required = false) String orderNo,
            @RequestParam(value = "customer", required = false) String customer,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate) {
        System.out.println("=== 获取订单列表 ===");
        return salesOrderService.getAllOrders(pageNum, pageSize, orderNo, customer, startDate, endDate);
    }

    /**
     * 创建订单
     * POST /sales/orders
     */
    @PostMapping
    public ResponseResult<?> createOrder(@RequestBody SalesOrder salesOrder) {
        System.out.println("=== 创建订单 ===");
        System.out.println("客户: " + salesOrder.getCustomer());
        System.out.println("明细数量: " + (salesOrder.getItems() != null ? salesOrder.getItems().size() : 0));
        return salesOrderService.createOrder(salesOrder);
    }

    /**
     * 更新订单
     * PUT /sales/orders
     */
    @PutMapping
    public ResponseResult<?> updateOrder(@RequestBody SalesOrder salesOrder) {
        System.out.println("=== 更新订单 ===");
        System.out.println("订单号: " + salesOrder.getOrderNo());
        return salesOrderService.updateOrder(salesOrder);
    }

    /**
     * 删除订单（逻辑删除）
     * DELETE /sales/orders?orderNo=xxx
     */
    @DeleteMapping
    public ResponseResult<?> deleteOrder(@RequestParam String orderNo) {
        System.out.println("=== 删除订单 ===");
        System.out.println("订单号: " + orderNo);
        return salesOrderService.deleteOrder(orderNo);
    }    /**
     * 根据订单号获取订单详情
     * GET /sales/orders/{orderNo}
     */
    @GetMapping("/{orderNo}")
    public ResponseResult<?> getOrderDetail(@PathVariable String orderNo) {
        System.out.println("=== 获取订单详情 ===");
        System.out.println("订单号: " + orderNo);
        return salesOrderService.getOrderByOrderNo(orderNo);
    }

    /**
     * 搜索订单（用于发货通知选择订单）
     * GET /sales/orders/search?keyword=xxx&status=pending
     */
    @GetMapping("/search")
    public ResponseResult<?> searchOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        System.out.println("=== 搜索订单 ===");
        System.out.println("关键词: " + keyword);
        System.out.println("状态: " + status);
        return salesOrderService.searchOrders(keyword, status);
    }

    /**
     * 下载导入模板
     * GET /sales/orders/template
     */
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) {
        salesOrderService.downloadTemplate(response);
    }

    /**
     * 导入订单Excel
     * POST /sales/orders/import
     */
    @PostMapping("/import")
    public ResponseResult<?> importOrders(@RequestParam("file") MultipartFile file) {
        return salesOrderService.importOrders(file, "admin");
    }

    /**
     * 导出所有订单
     * GET /sales/orders/export
     */
    @GetMapping("/export")
    public void exportOrders(HttpServletResponse response) {
        salesOrderService.exportOrders(response);
    }
}
