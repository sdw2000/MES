package com.fine.controller.stock;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.Utils.ResponseResult;
import com.fine.model.stock.MaterialScanTxn;
import com.fine.service.stock.MaterialScanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 小程序扫码领退料
 */
@RestController
@RequestMapping("/api/stock/scan")
@PreAuthorize("hasAnyAuthority('warehouse','admin','production')")
public class MaterialScanController {

    @Autowired
    private MaterialScanService materialScanService;

    @GetMapping("/resolve")
    public ResponseResult<?> resolve(
            @RequestParam("code") String code,
            @RequestParam(value = "stockType", required = false) String stockType) {
        try {
            return ResponseResult.success("查询成功", materialScanService.resolveByCode(code, stockType));
        } catch (Exception e) {
            return ResponseResult.error("扫码解析失败: " + e.getMessage());
        }
    }

    @PostMapping("/issue")
    public ResponseResult<?> issue(@RequestBody Map<String, Object> payload) {
        try {
            return ResponseResult.success("领料成功", materialScanService.issueByScan(payload));
        } catch (Exception e) {
            return ResponseResult.error("领料失败: " + e.getMessage());
        }
    }

    @PostMapping("/return")
    public ResponseResult<?> returnMaterial(@RequestBody Map<String, Object> payload) {
        try {
            return ResponseResult.success("退料成功", materialScanService.returnByScan(payload));
        } catch (Exception e) {
            return ResponseResult.error("退料失败: " + e.getMessage());
        }
    }

    @GetMapping("/txn/list")
    public ResponseResult<?> txnList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String txnType,
            @RequestParam(required = false) String stockType,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Long scheduleId,
            @RequestParam(required = false) String qrCode) {
        IPage<MaterialScanTxn> result = materialScanService.getTxnPage(page, size, txnType, stockType, orderNo, scheduleId, qrCode);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        data.put("current", result.getCurrent());
        data.put("size", result.getSize());
        data.put("pages", result.getPages());
        return ResponseResult.success("查询成功", data);
    }
}
