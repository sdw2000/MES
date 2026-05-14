package com.fine.service.purchase;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.Utils.ResponseResult;
import com.fine.modle.purchase.PurchaseReceipt;

import java.util.List;

public interface PurchaseReceiptService extends IService<PurchaseReceipt> {
    ResponseResult<?> list(Integer pageNum, Integer pageSize, String supplier, String status, String reconciliationStatus,
                           Boolean includeReceived, String sortField, String sortOrder);
    ResponseResult<?> detail(Long id);
    ResponseResult<?> create(PurchaseReceipt receipt);
    ResponseResult<?> updateReceipt(PurchaseReceipt receipt);
    ResponseResult<?> deleteReceipt(Long id);

    ResponseResult<?> seedTestData(Integer count);

    ResponseResult<?> cleanupTestData();

    ResponseResult<?> syncInboundRequestsForAllActiveReceipts();

    ResponseResult<?> listScanInboundDocuments(Integer pageNum, Integer pageSize, String keyword);

    ResponseResult<?> getScanInboundDocument(String receiptNo, Long receiptId);

    ResponseResult<?> submitScanInbound(String receiptNo, Long receiptId, List<String> scanCodes, String scannedLocation, String operator);
}
