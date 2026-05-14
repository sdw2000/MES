package com.fine.controller.purchase;

import com.fine.Utils.ResponseResult;
import com.fine.modle.purchase.PurchaseQuotation;
import com.fine.serviceIMPL.purchase.PurchaseQuotationPriceSheetInitService;
import com.fine.service.purchase.PurchaseQuotationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/purchase/quotations")
@PreAuthorize("hasAnyAuthority('admin','purchase')")
public class PurchaseQuotationController {

    @Autowired
    private PurchaseQuotationService quotationService;

    @Autowired
    private PurchaseQuotationPriceSheetInitService priceSheetInitService;

    @GetMapping
    public ResponseResult<?> list(@RequestParam(defaultValue = "1") Integer page,
                                  @RequestParam(defaultValue = "20") Integer size,
                                  @RequestParam(required = false) String supplier,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(required = false) String materialCode) {
        return quotationService.list(page, size, supplier, status, materialCode);
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

    @PostMapping("/{id}/requote")
    public ResponseResult<?> reQuote(@PathVariable Long id) {
        return quotationService.reQuote(id);
    }

    @DeleteMapping("/{id}")
    public ResponseResult<?> delete(@PathVariable Long id) {
        return quotationService.deleteQuotation(id);
    }

    @PostMapping("/initialize-from-price-sheet")
    @PreAuthorize("hasAnyAuthority('admin','purchase')")
    public ResponseResult<?> initializeFromPriceSheet(@RequestParam("file") MultipartFile file,
                                                      @RequestParam(value = "operator", required = false) String operator) {
        return priceSheetInitService.initializeFromPriceSheet(file, operator);
    }
}
