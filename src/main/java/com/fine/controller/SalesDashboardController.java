package com.fine.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fine.Utils.ResponseResult;
import com.fine.service.SalesDashboardService;

@RestController
@RequestMapping("/api/sales/dashboard")
@PreAuthorize("hasAnyAuthority('admin', 'sales')")
public class SalesDashboardController {

    @Autowired
    private SalesDashboardService salesDashboardService;

    @GetMapping("/summary")
    public ResponseResult<Map<String, Object>> summary() {
        return ResponseResult.success(salesDashboardService.getSummary());
    }

    @GetMapping("/top-customers")
    public ResponseResult<List<Map<String, Object>>> topCustomers() {
        return ResponseResult.success(salesDashboardService.getTopCustomers());
    }

    @GetMapping("/year-trend")
    public ResponseResult<Map<String, Object>> yearTrend() {
        return ResponseResult.success(salesDashboardService.getYearTrend());
    }

    @GetMapping("/shipment-stats")
    public ResponseResult<Map<String, Object>> shipmentStats() {
        return ResponseResult.success(salesDashboardService.getShipmentStats());
    }
}
