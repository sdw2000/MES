package com.fine.service;

import com.fine.Utils.ResponseResult;
import com.fine.modle.SalesReconciliationConfirmRequest;
import com.fine.modle.SalesStatementHistory;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;

public interface SalesReconciliationService {
    ResponseResult<?> getStatement(String customerCode, String month);

    ResponseResult<?> getHistory(String customerCode);

    ResponseResult<?> saveHistory(SalesStatementHistory history);

    ResponseResult<?> deleteHistory(Long id);

    ResponseResult<?> confirmStatementDetails(SalesReconciliationConfirmRequest request);

    ResponseResult<?> migrateLegacyReceiptStatus(String cutoffDate);

    ResponseResult<?> importHistory(String customerCode, MultipartFile file);

    ResponseResult<?> initializeHistory(SalesStatementHistory history);

    void exportStatement(String customerCode, String month, HttpServletResponse response);
}
