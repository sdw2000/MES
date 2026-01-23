package com.fine.service.purchase;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.Utils.ResponseResult;
import com.fine.modle.purchase.PurchaseReceipt;

public interface PurchaseReceiptService extends IService<PurchaseReceipt> {
    ResponseResult<?> list(Integer pageNum, Integer pageSize, String supplier, String status);
    ResponseResult<?> detail(Long id);
    ResponseResult<?> create(PurchaseReceipt receipt);
    ResponseResult<?> updateReceipt(PurchaseReceipt receipt);
    ResponseResult<?> deleteReceipt(Long id);
}
