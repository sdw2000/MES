package com.fine.controller;

import com.fine.Utils.ResponseResult;
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
}
