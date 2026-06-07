package com.fine.controller;

import com.fine.Utils.ResponseResult;
import com.fine.modle.SalesReconciliationConfirmRequest;
import com.fine.modle.SalesStatementHistory;
import com.fine.service.SalesReconciliationService;
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
import org.springframework.web.multipart.MultipartFile;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/sales/reconciliation")
@PreAuthorize("hasAnyAuthority('admin', 'sales', 'finance')")
public class SalesReconciliationController {

    @Autowired
    private SalesReconciliationService salesReconciliationService;

    @GetMapping("/statement")
    public ResponseResult<?> getStatement(@RequestParam String customerCode, @RequestParam String month) {
        return salesReconciliationService.getStatement(customerCode, month);
    }

    @GetMapping("/overview")
    public ResponseResult<?> getOverview(@RequestParam String month,
                                         @RequestParam(value = "customerCode", required = false) String customerCode,
                                         @RequestParam(value = "reconciledStatus", required = false) String reconciledStatus,
                                         @RequestParam(value = "current", required = false) Integer current,
                                         @RequestParam(value = "size", required = false) Integer size,
                                         @RequestParam(value = "sortProp", required = false) String sortProp,
                                         @RequestParam(value = "sortOrder", required = false) String sortOrder) {
        return salesReconciliationService.getStatementOverview(month, customerCode, reconciledStatus, current, size, sortProp, sortOrder);
    }

    @GetMapping("/history")
    public ResponseResult<?> getHistory(@RequestParam String customerCode) {
        return salesReconciliationService.getHistory(customerCode);
    }

    @PostMapping("/history")
    public ResponseResult<?> saveHistory(@RequestBody SalesStatementHistory history) {
        return salesReconciliationService.saveHistory(history);
    }

    @DeleteMapping("/history/{id}")
    public ResponseResult<?> deleteHistory(@PathVariable Long id) {
        return salesReconciliationService.deleteHistory(id);
    }

    @PostMapping("/confirm-details")
    public ResponseResult<?> confirmDetails(@RequestBody SalesReconciliationConfirmRequest request) {
        return salesReconciliationService.confirmStatementDetails(request);
    }

    @PostMapping("/sync-to-ar")
    public ResponseResult<?> syncToAr() {
        return salesReconciliationService.batchSyncConfirmedStatementsToAr();
    }

    @GetMapping("/unreconciled-candidates")
    public ResponseResult<?> queryUnreconciledCandidates(@RequestParam String customerCode,
                                                         @RequestParam String month,
                                                         @RequestParam(required = false) String orderNo) {
        return salesReconciliationService.queryUnreconciledCandidates(customerCode, month, orderNo);
    }

    @PostMapping("/append-unreconciled")
    public ResponseResult<?> appendUnreconciled(@RequestParam String customerCode,
                                                @RequestParam String month) {
        return salesReconciliationService.appendUnreconciledDetails(customerCode, month);
    }

    @DeleteMapping("/statement/detail/{detailId}")
    public ResponseResult<?> removeStatementDetail(@PathVariable Long detailId,
                                                   @RequestParam String customerCode,
                                                   @RequestParam String month,
                                                   @RequestParam(required = false) String bizType) {
        return salesReconciliationService.removeStatementDetail(customerCode, month, detailId, bizType);
    }

    @PostMapping("/migrate-legacy-receipt-status")
    @PreAuthorize("hasAnyAuthority('admin', 'finance')")
    public ResponseResult<?> migrateLegacyReceiptStatus(@RequestParam(value = "cutoffDate", required = false) String cutoffDate) {
        return salesReconciliationService.migrateLegacyReceiptStatus(cutoffDate);
    }

    @PostMapping("/admin/clear-overview-cache")
    @PreAuthorize("hasAuthority('admin')")
    public ResponseResult<?> clearOverviewCache() {
        return salesReconciliationService.adminClearOverviewCache();
    }

    @PostMapping("/admin/rollback-finance-confirm")
    @PreAuthorize("hasAuthority('admin')")
    public ResponseResult<?> rollbackFinanceConfirm(@RequestParam String customerCode,
                                                    @RequestParam String month) {
        return salesReconciliationService.adminRollbackFinanceConfirm(customerCode, month);
    }

    @PostMapping("/rollback-finance-confirm")
    @PreAuthorize("hasAuthority('admin')")
    public ResponseResult<?> rollbackFinanceConfirmPublic(@RequestParam String customerCode,
                                                          @RequestParam String month) {
        return salesReconciliationService.adminRollbackFinanceConfirm(customerCode, month);
    }

    @GetMapping("/admin/diagnose-deleted-confirms")
    @PreAuthorize("hasAuthority('admin')")
    public ResponseResult<?> diagnoseDeletedConfirms(@RequestParam(value = "ids", required = false) String idsCsv) {
        return salesReconciliationService.adminDiagnoseDeletedConfirms(idsCsv);
    }

    @PostMapping("/history/import")
    public ResponseResult<?> importHistory(@RequestParam(required = false) String customerCode,
                                           @RequestParam("file") MultipartFile file) {
        return salesReconciliationService.importHistory(customerCode, file);
    }

    @PostMapping("/history/initialize")
    public ResponseResult<?> initializeHistory(@RequestBody SalesStatementHistory history) {
        return salesReconciliationService.initializeHistory(history);
    }

    @GetMapping("/export")
    public void exportStatement(@RequestParam String customerCode,
                                @RequestParam String month,
                                HttpServletResponse response) {
        salesReconciliationService.exportStatement(customerCode, month, response);
    }
}
