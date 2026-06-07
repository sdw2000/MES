package com.fine.controller.purchase;

import com.fine.Utils.ResponseResult;
import com.fine.modle.purchase.PurchaseReconciliationConfirmRequest;
import com.fine.modle.purchase.PurchaseReconciliationHistoryRequest;
import com.fine.service.purchase.PurchaseReconciliationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/purchase/reconciliation")
@PreAuthorize("hasAnyAuthority('admin','purchase','finance')")
public class PurchaseReconciliationController {

    @Autowired
    private PurchaseReconciliationService purchaseReconciliationService;

    @GetMapping("/statement")
    public ResponseResult<?> getStatement(@RequestParam String supplier,
                                          @RequestParam(required = false) String month) {
        return purchaseReconciliationService.getStatement(supplier, month);
    }

    @GetMapping("/overview")
    public ResponseResult<?> getOverview(@RequestParam(required = false) String month,
                                         @RequestParam(required = false) String supplierKeyword,
                                         @RequestParam(required = false) String reconciledStatus,
                                         @RequestParam(defaultValue = "1") Integer current,
                                         @RequestParam(defaultValue = "20") Integer size,
                                         @RequestParam(required = false) String sortProp,
                                         @RequestParam(required = false) String sortOrder) {
        return purchaseReconciliationService.getOverview(month, supplierKeyword, reconciledStatus, current, size, sortProp, sortOrder);
    }

    @PostMapping("/confirm-details")
    public ResponseResult<?> confirmDetails(@RequestBody PurchaseReconciliationConfirmRequest request) {
        return purchaseReconciliationService.confirmDetails(request);
    }

    @PostMapping("/carry-next-month")
    public ResponseResult<?> carryNextMonth(@RequestBody PurchaseReconciliationConfirmRequest request) {
        return purchaseReconciliationService.carryToNextMonth(request);
    }

    @GetMapping("/carry-in-candidates")
    public ResponseResult<?> carryInCandidates(@RequestParam String supplier,
                                               @RequestParam(required = false) String month,
                                               @RequestParam(required = false) String keyword) {
        return purchaseReconciliationService.getCarryInCandidates(supplier, month, keyword);
    }

    @PostMapping("/move-to-month")
    public ResponseResult<?> moveToMonth(@RequestBody PurchaseReconciliationConfirmRequest request) {
        return purchaseReconciliationService.moveToMonth(request);
    }

    @GetMapping("/history")
    public ResponseResult<?> getHistory(@RequestParam String supplier) {
        return purchaseReconciliationService.getHistory(supplier);
    }

    @PostMapping("/history")
    public ResponseResult<?> saveHistory(@RequestBody PurchaseReconciliationHistoryRequest request) {
        return purchaseReconciliationService.saveHistory(request);
    }

    @DeleteMapping("/history/{id}")
    public ResponseResult<?> deleteHistory(@PathVariable Long id) {
        return purchaseReconciliationService.deleteHistory(id);
    }

    @GetMapping("/suppliers")
    public ResponseResult<?> suppliers(@RequestParam(required = false) String keyword) {
        return purchaseReconciliationService.getSupplierOptions(keyword);
    }
}
