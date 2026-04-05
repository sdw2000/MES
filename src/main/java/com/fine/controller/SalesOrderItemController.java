package com.fine.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Utils.ResponseResult;
import com.fine.Dao.SalesOrderItemMapper;
import com.fine.modle.SalesOrderItem;
import com.fine.service.SalesOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 销售订单明细相关接口
 */
@RestController
@RequestMapping("/sales/order-items")
@PreAuthorize("hasAnyAuthority('admin','production','packaging','packing')")
public class SalesOrderItemController {

    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;

    @Autowired
    private SalesOrderService salesOrderService;

    /**
     * 分页查询未完成的订单明细（按面积口径）
     * GET /sales/order-items/pending?current=1&size=20&orderNo=...&materialCode=...
     */
    @GetMapping("/pending")
    public ResponseResult<IPage<SalesOrderItem>> getPendingItems(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String materialCode) {

        Page<SalesOrderItem> page = new Page<>(current, size);
        IPage<SalesOrderItem> result = salesOrderItemMapper.selectPendingItems(page, orderNo, materialCode);
        return ResponseResult.success(result);
    }

    /**
     * 生产侧只读：按订单号查询订单详情（用于生产任务打印）
     * GET /sales/order-items/order-detail?orderNo=xxx
     */
    @GetMapping("/order-detail")
    public ResponseResult<?> getOrderDetailForProduction(@RequestParam("orderNo") String orderNo) {
        return salesOrderService.getOrderByOrderNo(orderNo);
    }

    /**
     * 生产侧扫码解析：按订单明细ID反查订单关键信息
     * GET /sales/order-items/resolve-detail?detailId=10832
     */
    @GetMapping("/resolve-detail")
    public ResponseResult<?> resolveDetailForProduction(@RequestParam("detailId") Long detailId) {
        if (detailId == null || detailId <= 0) {
            return ResponseResult.error(400, "detailId无效");
        }
        Map<String, Object> raw = salesOrderItemMapper.selectFullItemById(detailId);
        if (raw == null || raw.isEmpty()) {
            return ResponseResult.error(404, "未找到对应订单明细");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("orderDetailId", detailId);
        result.put("orderNo", raw.get("order_no"));
        result.put("materialCode", raw.get("material_code"));
        result.put("materialName", raw.get("material_name"));
        result.put("thickness", raw.get("thickness"));
        result.put("width", raw.get("width"));
        result.put("length", raw.get("length"));
        result.put("rolls", raw.get("rolls"));
        result.put("sqm", raw.get("sqm"));
        return ResponseResult.success(result);
    }
}
