package com.fine.service;

import com.fine.Utils.ResponseResult;
import com.fine.modle.SalesStatementHistory;

public interface SalesReconciliationService {
    ResponseResult<?> getStatement(String customerCode, String month);

    ResponseResult<?> getHistory(String customerCode);

    ResponseResult<?> saveHistory(SalesStatementHistory history);

    ResponseResult<?> deleteHistory(Long id);
}
