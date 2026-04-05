package com.fine.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fine.Utils.ResponseResult;
import com.fine.service.SalesDashboardService;

@RestController
@RequestMapping("/api/sales/dashboard")
@PreAuthorize("hasAnyAuthority('admin', 'sales', 'documentation', 'finance')")
public class SalesDashboardController {

    @Autowired
    private SalesDashboardService salesDashboardService;

    @GetMapping("/summary")
    public ResponseResult<Map<String, Object>> summary(
            @RequestParam(value = "salesUserId", required = false) Long salesUserId,
            @RequestParam(value = "documentationUserId", required = false) Long documentationUserId) {
        return ResponseResult.success(salesDashboardService.getSummary(salesUserId, documentationUserId));
    }

    @GetMapping("/top-customers")
    public ResponseResult<List<Map<String, Object>>> topCustomers(
            @RequestParam(value = "salesUserId", required = false) Long salesUserId,
            @RequestParam(value = "documentationUserId", required = false) Long documentationUserId) {
        return ResponseResult.success(salesDashboardService.getTopCustomers(salesUserId, documentationUserId));
    }

    @GetMapping("/year-trend")
    public ResponseResult<Map<String, Object>> yearTrend(
            @RequestParam(value = "salesUserId", required = false) Long salesUserId,
            @RequestParam(value = "documentationUserId", required = false) Long documentationUserId) {
        return ResponseResult.success(salesDashboardService.getYearTrend(salesUserId, documentationUserId));
    }

    @GetMapping("/shipment-stats")
    public ResponseResult<Map<String, Object>> shipmentStats(
            @RequestParam(value = "salesUserId", required = false) Long salesUserId,
            @RequestParam(value = "documentationUserId", required = false) Long documentationUserId) {
        return ResponseResult.success(salesDashboardService.getShipmentStats(salesUserId, documentationUserId));
    }

    @GetMapping("/today-orders")
    public ResponseResult<List<Map<String, Object>>> todayOrders(
            @RequestParam(value = "salesUserId", required = false) Long salesUserId,
            @RequestParam(value = "documentationUserId", required = false) Long documentationUserId) {
        return ResponseResult.success(salesDashboardService.getTodayOrderItems(salesUserId, documentationUserId));
    }
}
