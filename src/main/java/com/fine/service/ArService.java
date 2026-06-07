package com.fine.service;

import com.fine.Utils.ResponseResult;
import java.util.Map;

public interface ArService {
    ResponseResult<?> listInvoices(Map<String, Object> params);
    ResponseResult<?> listUnpaidDetails(Map<String, Object> params);
    ResponseResult<?> getInvoice(Long id);
    ResponseResult<?> createInvoice(Map<String, Object> payload);
    ResponseResult<?> createAndPostInvoice(Map<String, Object> payload);
    ResponseResult<?> listReceipts(Map<String, Object> params);
    ResponseResult<?> createReceipt(Map<String, Object> payload);
    ResponseResult<?> reconcileReceipt(Long id);

    ResponseResult<?> reconcileReceiptHistory(Long id, Map<String, Object> payload);
    ResponseResult<?> batchReconcileReceiptHistory(Map<String, Object> payload);
    ResponseResult<?> reverseReceipt(Long id);
    ResponseResult<?> batchReconcileReceipts(Map<String, Object> payload);
    ResponseResult<?> updateReceipt(Map<String, Object> payload);
    ResponseResult<?> deleteReceipt(Long id);
    ResponseResult<?> searchCustomers(Map<String, Object> params);
    ResponseResult<?> importOrderPaymentStatuses(Map<String, Object> payload);
    ResponseResult<?> listOrderItemCandidates(Map<String, Object> params);
    ResponseResult<?> updateOrderPaymentRow(Long id, Map<String, Object> payload);
    ResponseResult<?> batchUpdateOrderPaymentCustomer(Map<String, Object> payload);
    ResponseResult<?> updateOrderPaymentMatch(Long id, Map<String, Object> payload);
    ResponseResult<?> supplementOrderMissingItems(Map<String, Object> payload);
}
