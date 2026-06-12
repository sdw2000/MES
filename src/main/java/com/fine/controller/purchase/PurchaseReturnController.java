package com.fine.controller.purchase;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Utils.ResponseResult;
import com.fine.modle.purchase.PurchaseReturnOrder;
import com.fine.service.purchase.PurchaseReturnService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/purchase/returns")
public class PurchaseReturnController {

    @Autowired
    private PurchaseReturnService returnService;

    @GetMapping
    public ResponseResult<?> list(@RequestParam(defaultValue = "1") Integer current,
                                 @RequestParam(defaultValue = "10") Integer size,
                                 String supplier,
                                 String returnNo) {
        Page<PurchaseReturnOrder> page = new Page<>(current, size);
        LambdaQueryWrapper<PurchaseReturnOrder> query = new LambdaQueryWrapper<>();
        if (supplier != null && !supplier.isEmpty()) {
            query.like(PurchaseReturnOrder::getSupplier, supplier);
        }
        if (returnNo != null && !returnNo.isEmpty()) {
            query.like(PurchaseReturnOrder::getReturnNo, returnNo);
        }
        query.orderByDesc(PurchaseReturnOrder::getReturnDate);
        return ResponseResult.success(returnService.page(page, query));
    }

    @GetMapping("/{id}")
    public ResponseResult<?> getById(@PathVariable Long id) {
        return ResponseResult.success(returnService.getById(id));
    }

    @PostMapping
    public ResponseResult<?> save(@RequestBody PurchaseReturnOrder returnOrder) {
        returnService.saveReturn(returnOrder);
        return ResponseResult.success("保存成功");
    }

    @PutMapping
    public ResponseResult<?> update(@RequestBody PurchaseReturnOrder returnOrder) {
        returnService.saveReturn(returnOrder);
        return ResponseResult.success("修改成功");
    }

    @DeleteMapping("/{id}")
    public ResponseResult<?> delete(@PathVariable Long id) {
        returnService.removeById(id);
        return ResponseResult.success("删除成功");
    }
}
