package com.fine.service;

import com.fine.Utils.ResponseResult;
import com.fine.modle.SalesReconciliationConfirmRequest;
import com.fine.modle.SalesStatementHistory;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;

public interface SalesReconciliationService {
    ResponseResult<?> getStatement(String customerCode, String month);

    ResponseResult<?> getStatementOverview(String month, String customerCode, String reconciledStatus, Integer current, Integer size, String sortProp, String sortOrder);

    ResponseResult<?> getHistory(String customerCode);

    ResponseResult<?> saveHistory(SalesStatementHistory history);

    ResponseResult<?> deleteHistory(Long id);

    ResponseResult<?> confirmStatementDetails(SalesReconciliationConfirmRequest request);

    ResponseResult<?> queryUnreconciledCandidates(String customerCode, String month, String orderNo);

    ResponseResult<?> appendUnreconciledDetails(String customerCode, String month);

    ResponseResult<?> removeStatementDetail(String customerCode, String month, Long detailId, String bizType);

    ResponseResult<?> migrateLegacyReceiptStatus(String cutoffDate);

    ResponseResult<?> importHistory(String customerCode, MultipartFile file);

    ResponseResult<?> initializeHistory(SalesStatementHistory history);

    void exportStatement(String customerCode, String month, HttpServletResponse response);

    // 管理接口：清理总览缓存（用于在直接修改 DB 后强制刷新）
    ResponseResult<?> adminClearOverviewCache();

    // 管理接口：诊断 sales_statement_delivery_confirm 中被标记为已删除但仍可能影响显示的记录
    ResponseResult<?> adminDiagnoseDeletedConfirms(String noticeItemIdsCsv);
}
