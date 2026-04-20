package com.fine.service.finance;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.Map;

public interface FinanceAccountingService {

    Map<String, Object> getCoatingCostAccounting(String month, Integer pageNum, Integer pageSize);

    Map<String, Object> getCoatingCostSummary(String month);

    IPage<Map<String, Object>> getMaterialCostConfigPage(String month, String keyword, Integer pageNum, Integer pageSize);

    Map<String, Object> saveMaterialCostConfig(Map<String, Object> payload);

    Map<String, Object> getMonthlyBasicConfig(String month);

    Map<String, Object> saveMonthlyBasicConfig(Map<String, Object> payload);

    IPage<Map<String, Object>> getSalaryPage(String month, String employeeName, Integer pageNum, Integer pageSize);

    Map<String, Object> saveSalaryRecord(Map<String, Object> payload);

    void deleteSalaryRecord(Long id);

    IPage<Map<String, Object>> getBankLedgerPage(String month, String bankCode, Integer pageNum, Integer pageSize);

    Map<String, Object> saveBankLedger(Map<String, Object> payload);

    Map<String, Object> pushBankLedgerToKingdee(Long id);

    Map<String, Object> getKingdeeTemplate();
}
