package com.fine.controller;

import com.fine.Utils.ResponseResult;
import com.fine.service.ProductionDashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/production/dashboard")
public class ProductionDashboardController {

    @Autowired
    private ProductionDashboardService productionDashboardService;

    @GetMapping("/summary")
    public ResponseResult<Map<String, Object>> summary(
            @RequestParam(value = "shiftCode", required = false) String shiftCode) {
        return ResponseResult.success(productionDashboardService.getSummary(shiftCode));
    }

    @GetMapping("/top-processes")
    public ResponseResult<List<Map<String, Object>>> topProcesses(
            @RequestParam(value = "shiftCode", required = false) String shiftCode) {
        return ResponseResult.success(productionDashboardService.getTopProcesses(shiftCode));
    }

    @GetMapping("/year-trend")
    public ResponseResult<Map<String, Object>> yearTrend(
            @RequestParam(value = "shiftCode", required = false) String shiftCode) {
        return ResponseResult.success(productionDashboardService.getYearTrend(shiftCode));
    }

    @GetMapping("/today-reports")
    public ResponseResult<List<Map<String, Object>>> todayReports(
            @RequestParam(value = "shiftCode", required = false) String shiftCode) {
        return ResponseResult.success(productionDashboardService.getTodayReports(shiftCode));
    }
}
