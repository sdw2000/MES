package com.fine.service.purchase;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.Utils.ResponseResult;
import com.fine.modle.purchase.PurchaseSupplierPriority;

public interface PurchaseSupplierPriorityService extends IService<PurchaseSupplierPriority> {
    ResponseResult<?> listAll();
    ResponseResult<?> list(String keyword, Integer page, Integer size);
    ResponseResult<?> upsert(PurchaseSupplierPriority priority);
    ResponseResult<?> deleteById(Long id);
}
