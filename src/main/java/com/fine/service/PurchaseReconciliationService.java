package com.fine.service;

import com.fine.Utils.ResponseResult;
import com.fine.modle.purchase.PurchaseStatementHistory;
import com.fine.modle.purchase.PurchaseReconciliationConfirmRequest;

public interface PurchaseReconciliationService {
    ResponseResult<?> getStatement(String supplierCode, String month, Integer current, Integer size, String sortProp, String sortOrder);
    ResponseResult<?> getStatementOverview(String month, String supplierCode, String reconciledStatus, Integer current, Integer size, String sortProp, String sortOrder);
    ResponseResult<?> getHistory(String supplierCode);
    ResponseResult<?> saveHistory(PurchaseStatementHistory history);
    ResponseResult<?> deleteHistory(Long id);
    ResponseResult<?> confirmStatementDetails(PurchaseReconciliationConfirmRequest request);
}
