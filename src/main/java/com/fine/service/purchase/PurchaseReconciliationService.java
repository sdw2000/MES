package com.fine.service.purchase;

import com.fine.Utils.ResponseResult;
import com.fine.modle.purchase.PurchaseReconciliationConfirmRequest;
import com.fine.modle.purchase.PurchaseReconciliationHistoryRequest;

public interface PurchaseReconciliationService {

    ResponseResult<?> getStatement(String supplier, String month);

    ResponseResult<?> getOverview(String month,
                                  String supplierKeyword,
                                  String reconciledStatus,
                                  Integer current,
                                  Integer size,
                                  String sortProp,
                                  String sortOrder);

    ResponseResult<?> confirmDetails(PurchaseReconciliationConfirmRequest request);

    ResponseResult<?> carryToNextMonth(PurchaseReconciliationConfirmRequest request);

    ResponseResult<?> getCarryInCandidates(String supplier, String month, String keyword);

    ResponseResult<?> moveToMonth(PurchaseReconciliationConfirmRequest request);

    ResponseResult<?> getHistory(String supplier);

    ResponseResult<?> saveHistory(PurchaseReconciliationHistoryRequest request);

    ResponseResult<?> deleteHistory(Long id);

    ResponseResult<?> getSupplierOptions(String keyword);
}
