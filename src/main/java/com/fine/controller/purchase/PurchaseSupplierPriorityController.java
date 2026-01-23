package com.fine.controller.purchase;

import com.fine.Utils.ResponseResult;
import com.fine.modle.purchase.PurchaseSupplierPriority;
import com.fine.service.purchase.PurchaseSupplierPriorityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/purchase/supplier-priority")
@PreAuthorize("hasAuthority('admin')")
public class PurchaseSupplierPriorityController {

    @Autowired
    private PurchaseSupplierPriorityService priorityService;

    @GetMapping
    public ResponseResult<?> list(@RequestParam(required = false) String keyword,
                                  @RequestParam(defaultValue = "1") Integer page,
                                  @RequestParam(defaultValue = "20") Integer size) {
        return priorityService.list(keyword, page, size);
    }

    @PostMapping
    public ResponseResult<?> create(@RequestBody PurchaseSupplierPriority priority) {
        return priorityService.upsert(priority);
    }

    @PutMapping
    public ResponseResult<?> update(@RequestBody PurchaseSupplierPriority priority) {
        return priorityService.upsert(priority);
    }

    @DeleteMapping("/{id}")
    public ResponseResult<?> delete(@PathVariable Long id) {
        return priorityService.deleteById(id);
    }
}
