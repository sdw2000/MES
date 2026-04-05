package com.fine.controller;

import com.fine.Utils.ResponseResult;
import com.fine.modle.SalesOrder;
import com.fine.service.SalesOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;

/**
 * 销售订单控制器
 */
@RestController
@RequestMapping("/sales/orders")
@PreAuthorize("hasAnyAuthority('admin','sales','finance')")
public class SalesOrderController {

    private static final Logger log = LoggerFactory.getLogger(SalesOrderController.class);

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
            @RequestParam(value = "completionStatus", required = false) String completionStatus,
            @RequestParam(value = "showCompleted", required = false, defaultValue = "false") Boolean showCompleted,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "sortProp", required = false) String sortProp,
            @RequestParam(value = "sortOrder", required = false) String sortOrder) {
        log.debug("获取订单列表, pageNum={}, pageSize={}, orderNo={}, customer={}, completionStatus={}, showCompleted={}, sortProp={}, sortOrder={}",
                pageNum, pageSize, orderNo, customer, completionStatus, showCompleted, sortProp, sortOrder);
        return salesOrderService.getAllOrders(pageNum, pageSize, orderNo, customer, completionStatus, showCompleted, startDate, endDate, sortProp, sortOrder);
    }

    /**
     * 创建订单
     * POST /sales/orders
     */
    @PostMapping
    public ResponseResult<?> createOrder(@RequestBody SalesOrder salesOrder) {
        log.info("创建订单, customer={}, itemCount={}", salesOrder.getCustomer(),
                salesOrder.getItems() != null ? salesOrder.getItems().size() : 0);
        return salesOrderService.createOrder(salesOrder);
    }

    /**
     * 生成订单号
     * GET /sales/orders/generate-no?customerCode=xxx&orderDate=yyyy-MM-dd
     */
    @GetMapping("/generate-no")
    public ResponseResult<?> generateOrderNo(
            @RequestParam("customerCode") String customerCode,
            @RequestParam(value = "orderDate", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate orderDate) {
        return salesOrderService.generateOrderNo(customerCode, orderDate);
    }

    /**
     * 查询客户在指定料号下的历史下单规格
     * GET /sales/orders/history-specs?customerCode=xxx&materialCode=yyy
     */
    @GetMapping("/history-specs")
    public ResponseResult<?> getCustomerMaterialHistorySpecs(
            @RequestParam("customerCode") String customerCode,
            @RequestParam("materialCode") String materialCode) {
        return salesOrderService.getCustomerMaterialHistorySpecs(customerCode, materialCode);
    }

    /**
     * 查询客户历史订单备注
     * GET /sales/orders/remark-history?customerCode=xxx&limit=20
     */
    @GetMapping("/remark-history")
    public ResponseResult<?> getCustomerOrderRemarkHistory(
            @RequestParam("customerCode") String customerCode,
            @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit) {
        return salesOrderService.getCustomerOrderRemarkHistory(customerCode, limit);
    }

    /**
     * 更新订单
     * PUT /sales/orders
     */
    @PutMapping
    public ResponseResult<?> updateOrder(@RequestBody SalesOrder salesOrder) {
        log.info("更新订单, orderNo={}", salesOrder.getOrderNo());
        return salesOrderService.updateOrder(salesOrder);
    }

    /**
     * 删除订单（逻辑删除）
     * DELETE /sales/orders?orderNo=xxx
     */
    @DeleteMapping
    public ResponseResult<?> deleteOrder(@RequestParam("orderNo") String orderNo) {
        log.info("删除订单, orderNo={}", orderNo);
        return salesOrderService.deleteOrder(orderNo);
    }

    /**
     * 取消订单（必须填写取消原因）
     * POST /sales/orders/cancel
     */
    @PostMapping("/cancel")
    public ResponseResult<?> cancelOrder(@RequestBody SalesOrder salesOrder) {
        String orderNo = salesOrder == null ? null : salesOrder.getOrderNo();
        String cancelReason = salesOrder == null ? null : salesOrder.getCancelReason();
        log.info("取消订单, orderNo={}", orderNo);
        return salesOrderService.cancelOrder(orderNo, cancelReason);
    }

    /**
     * 根据订单号获取订单详情
     * GET /sales/orders/{orderNo}
     */
    @GetMapping("/{orderNo}")
    @PreAuthorize("hasAnyAuthority('admin','sales','finance','production','packaging','packing')")
    public ResponseResult<?> getOrderDetail(@PathVariable("orderNo") String orderNo) {
        log.debug("获取订单详情, orderNo={}", orderNo);
        return salesOrderService.getOrderByOrderNo(orderNo);
    }

    /**
     * 搜索订单（用于发货通知选择订单）
     * GET /sales/orders/search?keyword=xxx&status=pending
     */
    @GetMapping("/search")
    public ResponseResult<?> searchOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String customer) {
        log.debug("搜索订单, keyword={}, status={}, customer={}", keyword, status, customer);
        return salesOrderService.searchOrders(keyword, status, customer);
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
     * 历史初始化状态
     * GET /sales/orders/history-init/status
     */
    @GetMapping("/history-init/status")
    public ResponseResult<?> getHistoryInitStatus() {
        return salesOrderService.getHistoryInitStatus();
    }

    /**
     * 历史订单初始化导入（一次性）
     * POST /sales/orders/history-init/import
     */
    @PostMapping("/history-init/import")
    public ResponseResult<?> importHistoryInit(@RequestParam("file") MultipartFile file) {
        return salesOrderService.importHistoryInit(file, "admin");
    }

    /**
     * 初始化后增量同步订单
     * POST /sales/orders/history-init/sync
     */
    @PostMapping("/history-init/sync")
    public ResponseResult<?> syncIncrementalOrders(@RequestParam("file") MultipartFile file) {
        return salesOrderService.syncIncrementalOrders(file, "admin");
    }

    /**
     * 清空历史初始化订单数据并重置初始化状态
     * POST /sales/orders/history-init/reset
     */
    @PostMapping("/history-init/reset")
    public ResponseResult<?> resetHistoryInitialization() {
        return salesOrderService.resetHistoryInitialization("admin");
    }

    /**
     * 基于现有订单数据重建历史初始化状态（不清理订单）
     * POST /sales/orders/history-init/rebuild-state
     */
    @PostMapping("/history-init/rebuild-state")
    public ResponseResult<?> rebuildHistoryInitializationState() {
        return salesOrderService.rebuildHistoryInitializationState("admin");
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
