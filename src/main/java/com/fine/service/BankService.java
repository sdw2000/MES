package com.fine.service;

import com.fine.Utils.ResponseResult;
import java.util.Map;

public interface BankService {
    ResponseResult<?> listAccounts(Map<String, Object> params);
    ResponseResult<?> listTransactions(Long accountId, Map<String, Object> params);
    ResponseResult<?> applyPayment(Map<String, Object> payload);
}
