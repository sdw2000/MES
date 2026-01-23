package com.fine.service.purchase;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.Utils.ResponseResult;
import com.fine.modle.purchase.PurchaseQuotation;

public interface PurchaseQuotationService extends IService<PurchaseQuotation> {
    ResponseResult<?> list(Integer pageNum, Integer pageSize, String supplier, String status);
    ResponseResult<?> detail(Long id);
    ResponseResult<?> create(PurchaseQuotation quotation);
    ResponseResult<?> updateQuotation(PurchaseQuotation quotation);
    ResponseResult<?> deleteQuotation(Long id);
    String generateQuotationNo();
}
