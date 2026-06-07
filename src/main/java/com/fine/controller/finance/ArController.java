package com.fine.controller.finance;

import com.fine.Utils.ResponseResult;
import com.fine.service.ArService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/finance/ar")
public class ArController {

    @Autowired
    private ArService arService;

    @GetMapping("/invoices")
    public ResponseResult<?> list(@RequestParam(required = false) Map<String, Object> params) {
        return arService.listInvoices(params);
    }

    @GetMapping("/unpaid-details")
    public ResponseResult<?> listUnpaidDetails(@RequestParam(required = false) Map<String, Object> params) {
        return arService.listUnpaidDetails(params);
    }

    @GetMapping("/invoice/{id}")
    public ResponseResult<?> get(@PathVariable Long id) {
        return arService.getInvoice(id);
    }

    @PostMapping("/invoice")
    public ResponseResult<?> create(@RequestBody Map<String, Object> payload) {
        return arService.createInvoice(payload);
    }

    @PostMapping("/invoice/post")
    public ResponseResult<?> createAndPost(@RequestBody Map<String, Object> payload) {
        return arService.createAndPostInvoice(payload);
    }

    @GetMapping("/receipts")
    public ResponseResult<?> listReceipts(@RequestParam(required = false) Map<String, Object> params) {
        return arService.listReceipts(params);
    }

    @PostMapping("/receipt")
    public ResponseResult<?> createReceipt(@RequestBody Map<String, Object> payload) {
        return arService.createReceipt(payload);
    }

    @PostMapping("/receipt/{id}/reconcile")
    public ResponseResult<?> reconcileReceipt(@PathVariable Long id) {
        return arService.reconcileReceipt(id);
    }

    @PostMapping("/receipt/{id}/reconcile-history")
    public ResponseResult<?> reconcileReceiptHistory(@PathVariable Long id,
                                                     @RequestBody(required = false) Map<String, Object> payload) {
        return arService.reconcileReceiptHistory(id, payload);
    }

    @PostMapping("/receipt/{id}/reverse")
    public ResponseResult<?> reverseReceipt(@PathVariable Long id) {
        return arService.reverseReceipt(id);
    }

    @PostMapping("/receipts/reconcile-batch")
    public ResponseResult<?> batchReconcileReceipts(@RequestBody(required = false) Map<String, Object> payload) {
        return arService.batchReconcileReceipts(payload);
    }

    @PostMapping("/receipts/reconcile-history-batch")
    public ResponseResult<?> batchReconcileReceiptHistory(@RequestBody(required = false) Map<String, Object> payload) {
        return arService.batchReconcileReceiptHistory(payload);
    }

    @PutMapping("/receipt")
    public ResponseResult<?> updateReceipt(@RequestBody Map<String, Object> payload) {
        return arService.updateReceipt(payload);
    }

    @DeleteMapping("/receipt/{id}")
    public ResponseResult<?> deleteReceipt(@PathVariable Long id) {
        return arService.deleteReceipt(id);
    }

    @GetMapping("/customers")
    public ResponseResult<?> searchCustomers(@RequestParam(required = false) Map<String, Object> params) {
        return arService.searchCustomers(params);
    }

    @PostMapping("/order-payments/import")
    public ResponseResult<?> importOrderPaymentStatuses(@RequestBody Map<String, Object> payload) {
        return arService.importOrderPaymentStatuses(payload);
    }

    @GetMapping("/order-payments/item-candidates")
    public ResponseResult<?> listOrderItemCandidates(@RequestParam(required = false) Map<String, Object> params) {
        return arService.listOrderItemCandidates(params);
    }

    @PutMapping("/order-payments/{id}")
    public ResponseResult<?> updateOrderPaymentRow(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return arService.updateOrderPaymentRow(id, payload);
    }

    @PutMapping("/order-payments/batch/customer")
    public ResponseResult<?> batchUpdateOrderPaymentCustomer(@RequestBody Map<String, Object> payload) {
        return arService.batchUpdateOrderPaymentCustomer(payload);
    }

    @PutMapping("/order-payments/{id}/match")
    public ResponseResult<?> updateOrderPaymentMatch(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return arService.updateOrderPaymentMatch(id, payload);
    }

    @PostMapping("/order-payments/supplement-missing")
    public ResponseResult<?> supplementOrderMissingItems(@RequestBody Map<String, Object> payload) {
        return arService.supplementOrderMissingItems(payload);
    }
}
