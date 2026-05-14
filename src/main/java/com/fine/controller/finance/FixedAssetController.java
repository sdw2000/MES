package com.fine.controller.finance;

import com.fine.Utils.ResponseResult;
import com.fine.service.FixedAssetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;

import java.util.Map;

@RestController
@RequestMapping("/finance/fixed-assets")
public class FixedAssetController {

    @Autowired
    private FixedAssetService fixedAssetService;

    @GetMapping("/list")
    public ResponseResult<?> list(@RequestParam(required = false) Map<String, Object> params) {
        return fixedAssetService.listAssets(params);
    }

    @GetMapping("/{id}")
    public ResponseResult<?> get(@PathVariable Long id) {
        return fixedAssetService.getAsset(id);
    }

    @PostMapping
    public ResponseResult<?> create(@RequestBody Map<String, Object> payload) {
        return fixedAssetService.createAsset(payload);
    }

    @PutMapping("/{id}")
    public ResponseResult<?> update(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return fixedAssetService.updateAsset(id, payload);
    }

    @PostMapping("/depreciate")
    public ResponseResult<?> depreciate(@RequestBody Map<String, Object> payload) {
        return fixedAssetService.depreciate(payload);
    }

    @PostMapping("/depreciate/batch")
    public ResponseResult<?> batchDepreciate(@RequestBody Map<String, Object> payload) {
        return fixedAssetService.batchDepreciate(payload);
    }

    @GetMapping("/{id}/depreciations")
    public ResponseResult<?> depreciations(@PathVariable Long id) {
        return fixedAssetService.listDepreciations(id);
    }

    @PostMapping("/{id}/dispose")
    public ResponseResult<?> dispose(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> payload) {
        return fixedAssetService.disposeAsset(id, payload);
    }

    @GetMapping("/report/summary")
    public ResponseResult<?> reportSummary(@RequestParam(required = false) String month) {
        return fixedAssetService.reportSummary(month);
    }

    @GetMapping("/report/ledger")
    public ResponseResult<?> reportLedger(@RequestParam(required = false) Map<String, Object> params) {
        return fixedAssetService.reportLedger(params);
    }

    @GetMapping("/report/export")
    public void export(@RequestParam(required = false) Map<String, Object> params, HttpServletResponse response) {
        fixedAssetService.exportReport(params, response);
    }
}
