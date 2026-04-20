package com.fine.controller.purchase;

import com.fine.Utils.ResponseResult;
import com.fine.modle.purchase.PurchaseReceipt;
import com.fine.service.purchase.PurchaseReceiptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/purchase/receipts")
@PreAuthorize("hasAnyAuthority('admin','purchase')")
public class PurchaseReceiptController {

    @Autowired
    private PurchaseReceiptService receiptService;

    @GetMapping
    public ResponseResult<?> list(@RequestParam(defaultValue = "1") Integer page,
                                  @RequestParam(defaultValue = "20") Integer size,
                                  @RequestParam(required = false) String supplier,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(required = false) String reconciliationStatus) {
        return receiptService.list(page, size, supplier, status, reconciliationStatus);
    }

    @GetMapping("/{id}")
    public ResponseResult<?> detail(@PathVariable Long id) {
        return receiptService.detail(id);
    }

    @PostMapping
    public ResponseResult<?> create(@RequestBody PurchaseReceipt receipt) {
        return receiptService.create(receipt);
    }

    @PutMapping
    public ResponseResult<?> update(@RequestBody PurchaseReceipt receipt) {
        return receiptService.updateReceipt(receipt);
    }

    @DeleteMapping("/{id}")
    public ResponseResult<?> delete(@PathVariable Long id) {
        return receiptService.deleteReceipt(id);
    }

    @PostMapping("/test-data/seed")
    public ResponseResult<?> seedTestData(@RequestParam(value = "count", required = false, defaultValue = "3") Integer count) {
        return receiptService.seedTestData(count);
    }

    @DeleteMapping("/test-data/cleanup")
    public ResponseResult<?> cleanupTestData() {
        return receiptService.cleanupTestData();
    }
}
