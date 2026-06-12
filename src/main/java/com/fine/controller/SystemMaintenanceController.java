package com.fine.controller;

import com.fine.Utils.ResponseResult;
import com.fine.service.stock.TapeStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 系统维护与日常纠偏工具
 */
@RestController
@RequestMapping("/system/maintenance")
public class SystemMaintenanceController {

    @Autowired
    private TapeStockService tapeStockService;

    /**
     * 纠偏：将误入胶带仓的采购来料（如离型纸、离型膜、化工料、包材等）迁移到正确的原料仓
     */
    @PostMapping("/migrate-misrouted-purchase-inbound")
    public ResponseResult<Map<String, Object>> migrateMisroutedPurchaseInbound(@RequestParam(required = false) String auditor) {
        String op = StringUtils.hasText(auditor) ? auditor : "system-migration";
        return ResponseResult.success(tapeStockService.migrateMisroutedPurchaseInboundToRawWarehouse(op));
    }
}
