package com.fine.controller;

import com.fine.Utils.ResponseResult;
import com.fine.modle.SalesReturn;
import com.fine.service.SalesReturnService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sales/returns")
@PreAuthorize("hasAnyAuthority('admin','sales','finance')")
public class SalesReturnController {

    @Autowired
    private SalesReturnService salesReturnService;

    @GetMapping
    public ResponseResult<?> getAllReturns(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "returnNo", required = false) String returnNo,
            @RequestParam(value = "customer", required = false) String customer,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate,
            @RequestParam(value = "status", required = false) String status
    ) {
        return salesReturnService.getAllReturns(pageNum, pageSize, returnNo, customer, startDate, endDate, status);
    }

    @PostMapping
    public ResponseResult<?> createReturn(@RequestBody SalesReturn salesReturn) {
        return salesReturnService.createReturn(salesReturn);
    }

    @PutMapping
    public ResponseResult<?> updateReturn(@RequestBody SalesReturn salesReturn) {
        return salesReturnService.updateReturn(salesReturn);
    }

    @DeleteMapping
    public ResponseResult<?> deleteReturn(@RequestParam("returnNo") String returnNo) {
        return salesReturnService.deleteReturn(returnNo);
    }

    @GetMapping("/{returnNo}")
    public ResponseResult<?> getReturnDetail(@PathVariable("returnNo") String returnNo) {
        return salesReturnService.getReturnDetail(returnNo);
    }

    @GetMapping("/generate-no")
    public ResponseResult<?> generateReturnNo(
            @RequestParam(value = "customerCode", required = false) String customerCode,
            @RequestParam(value = "returnDate", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate returnDate
    ) {
        return salesReturnService.generateReturnNo(customerCode, returnDate);
    }

    @GetMapping("/reconciliation")
    public ResponseResult<?> reconciliationSummary(@RequestParam("month") String month) {
        return salesReturnService.reconciliationSummary(month);
    }

    @GetMapping("/order-items")
    public ResponseResult<?> getReturnableOrderItems(
            @RequestParam("orderNo") String orderNo,
            @RequestParam(value = "excludeReturnNo", required = false) String excludeReturnNo
    ) {
        return salesReturnService.getReturnableOrderItems(orderNo, excludeReturnNo);
    }

    @GetMapping("/audit-logs")
    public ResponseResult<?> getReturnAuditLogs(
            @RequestParam(value = "returnNo", required = false) String returnNo,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize
    ) {
        return salesReturnService.getReturnAuditLogs(returnNo, pageNum, pageSize);
    }
}
