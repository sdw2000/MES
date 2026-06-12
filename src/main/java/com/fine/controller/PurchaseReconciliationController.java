package com.fine.controller;

import com.fine.Utils.ResponseResult;
import com.fine.modle.purchase.PurchaseStatementHistory;
import com.fine.service.PurchaseReconciliationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/purchase/reconciliation")
@PreAuthorize("hasAnyAuthority('admin', 'purchase', 'finance')")
public class PurchaseReconciliationController {

    @Autowired
    private PurchaseReconciliationService purchaseReconciliationService;

    @GetMapping("/statement")
    public ResponseResult<?> getStatement(@RequestParam String supplierCode, 
                                         @RequestParam String month,
                                         @RequestParam(value = "current", defaultValue = "1") Integer current,
                                         @RequestParam(value = "size", defaultValue = "20") Integer size,
                                         @RequestParam(value = "sortProp", required = false) String sortProp,
                                         @RequestParam(value = "sortOrder", required = false) String sortOrder) {
        return purchaseReconciliationService.getStatement(supplierCode, month, current, size, sortProp, sortOrder);
    }

    @GetMapping("/overview")
    public ResponseResult<?> getOverview(@RequestParam String month,
                                         @RequestParam(value = "supplierCode", required = false) String supplierCode,
                                         @RequestParam(value = "reconciledStatus", required = false) String reconciledStatus,
                                         @RequestParam(value = "current", required = false) Integer current,
                                         @RequestParam(value = "size", required = false) Integer size,
                                         @RequestParam(value = "sortProp", required = false) String sortProp,
                                         @RequestParam(value = "sortOrder", required = false) String sortOrder) {
        return purchaseReconciliationService.getStatementOverview(month, supplierCode, reconciledStatus, current, size, sortProp, sortOrder);
    }

    @GetMapping("/history")
    public ResponseResult<?> getHistory(@RequestParam String supplierCode) {
        return purchaseReconciliationService.getHistory(supplierCode);
    }

    @PostMapping("/history")
    public ResponseResult<?> saveHistory(@RequestBody PurchaseStatementHistory history) {
        return purchaseReconciliationService.saveHistory(history);
    }

    @DeleteMapping("/history/{id}")
    public ResponseResult<?> deleteHistory(@PathVariable Long id) {
        return purchaseReconciliationService.deleteHistory(id);
    }

    @PostMapping("/confirm-details")
    public ResponseResult<?> confirmDetails(@RequestBody com.fine.modle.purchase.PurchaseReconciliationConfirmRequest request) {
        return purchaseReconciliationService.confirmStatementDetails(request);
    }
}
