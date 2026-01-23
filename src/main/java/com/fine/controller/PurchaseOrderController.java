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
import com.fine.modle.PurchaseOrder;
import com.fine.service.PurchaseOrderService;

import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/purchase/orders")
@PreAuthorize("hasAuthority('admin')")
public class PurchaseOrderController {

    @Autowired
    private PurchaseOrderService purchaseOrderService;

    @GetMapping
    public ResponseResult<?> getAllOrders(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "orderNo", required = false) String orderNo,
            @RequestParam(value = "supplier", required = false) String supplier,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate) {
        return purchaseOrderService.getAllOrders(pageNum, pageSize, orderNo, supplier, startDate, endDate);
    }

    @PostMapping
    public ResponseResult<?> createOrder(@RequestBody PurchaseOrder purchaseOrder) {
        return purchaseOrderService.createOrder(purchaseOrder);
    }

    @PutMapping
    public ResponseResult<?> updateOrder(@RequestBody PurchaseOrder purchaseOrder) {
        return purchaseOrderService.updateOrder(purchaseOrder);
    }

    @DeleteMapping
    public ResponseResult<?> deleteOrder(@RequestParam String orderNo) {
        return purchaseOrderService.deleteOrder(orderNo);
    }

    @GetMapping("/{orderNo}")
    public ResponseResult<?> getOrderDetail(@PathVariable String orderNo) {
        return purchaseOrderService.getOrderByOrderNo(orderNo);
    }

    @GetMapping("/search")
    public ResponseResult<?> searchOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return purchaseOrderService.searchOrders(keyword, status);
    }

    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) {
        purchaseOrderService.downloadTemplate(response);
    }

    @PostMapping("/import")
    public ResponseResult<?> importOrders(@RequestParam("file") MultipartFile file) {
        return purchaseOrderService.importOrders(file, "admin");
    }

    @GetMapping("/export")
    public void exportOrders(HttpServletResponse response) {
        purchaseOrderService.exportOrders(response);
    }
}
