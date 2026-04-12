package com.fine.controller.purchase;

import com.fine.Utils.ResponseResult;
import com.fine.modle.purchase.PurchaseSupplier;
import com.fine.service.purchase.PurchaseSupplierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/purchase/suppliers")
@PreAuthorize("hasAnyAuthority('admin','purchase')")
public class PurchaseSupplierController {

    @Autowired
    private PurchaseSupplierService supplierService;

    @GetMapping
    public ResponseResult<?> list(@RequestParam(required = false) String keyword,
                                  @RequestParam(defaultValue = "1") Integer page,
                                  @RequestParam(defaultValue = "20") Integer size) {
        return supplierService.listSuppliers(keyword, page, size);
    }

    @PostMapping
    public ResponseResult<?> create(@RequestBody PurchaseSupplier supplier) {
        return supplierService.saveSupplier(supplier);
    }

    @PutMapping
    public ResponseResult<?> update(@RequestBody PurchaseSupplier supplier) {
        return supplierService.saveSupplier(supplier);
    }

    @GetMapping("/{id}")
    public ResponseResult<?> detail(@PathVariable Long id) {
        return supplierService.getSupplierDetail(id);
    }

    @DeleteMapping("/{id}")
    public ResponseResult<?> delete(@PathVariable Long id) {
        return supplierService.deleteSupplier(id);
    }

    @GetMapping("/export")
    public void export(HttpServletResponse response,
                       @RequestParam(required = false) String keyword) {
        supplierService.exportSuppliers(response, keyword);
    }

    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) {
        supplierService.downloadTemplate(response);
    }

    @PostMapping("/import")
    @PreAuthorize("hasAnyAuthority('admin','purchase')")
    public ResponseResult<?> importSuppliers(@RequestParam("file") MultipartFile file) {
        return supplierService.importSuppliers(file);
    }
}
