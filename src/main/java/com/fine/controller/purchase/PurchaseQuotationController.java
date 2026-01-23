package com.fine.controller.purchase;

import com.fine.Utils.ResponseResult;
import com.fine.modle.purchase.PurchaseQuotation;
import com.fine.service.purchase.PurchaseQuotationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/purchase/quotations")
@PreAuthorize("hasAuthority('admin')")
public class PurchaseQuotationController {

    @Autowired
    private PurchaseQuotationService quotationService;

    @GetMapping
    public ResponseResult<?> list(@RequestParam(defaultValue = "1") Integer page,
                                  @RequestParam(defaultValue = "20") Integer size,
                                  @RequestParam(required = false) String supplier,
                                  @RequestParam(required = false) String status) {
        return quotationService.list(page, size, supplier, status);
    }

    @GetMapping("/{id}")
    public ResponseResult<?> detail(@PathVariable Long id) {
        return quotationService.detail(id);
    }

    @PostMapping
    public ResponseResult<?> create(@RequestBody PurchaseQuotation quotation) {
        return quotationService.create(quotation);
    }

    @PutMapping
    public ResponseResult<?> update(@RequestBody PurchaseQuotation quotation) {
        return quotationService.updateQuotation(quotation);
    }

    @DeleteMapping("/{id}")
    public ResponseResult<?> delete(@PathVariable Long id) {
        return quotationService.deleteQuotation(id);
    }
}
