package com.fine.controller.finance;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.Utils.ResponseResult;
import com.fine.service.finance.FinanceAccountingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/finance")
@PreAuthorize("hasAnyAuthority('admin','finance')")
public class FinanceAccountingController {

    @Autowired
    private FinanceAccountingService financeAccountingService;

    @GetMapping("/cost-accounting/coating")
    public ResponseResult<?> getCoatingCostAccounting(@RequestParam String month,
                                                      @RequestParam(defaultValue = "1") Integer pageNum,
                                                      @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            return new ResponseResult<>(200, "查询成功", financeAccountingService.getCoatingCostAccounting(month, pageNum, pageSize));
        } catch (Exception e) {
            return new ResponseResult<>(500, "查询失败: " + e.getMessage(), null);
        }
    }

    @GetMapping("/cost-accounting/coating-summary")
    public ResponseResult<?> getCoatingCostSummary(@RequestParam String month) {
        try {
            return new ResponseResult<>(200, "查询成功", financeAccountingService.getCoatingCostSummary(month));
        } catch (Exception e) {
            return new ResponseResult<>(500, "查询失败: " + e.getMessage(), null);
        }
    }

    @GetMapping("/cost-accounting/material-config")
    public ResponseResult<?> getMaterialCostConfig(@RequestParam(required = false) String month,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(defaultValue = "1") Integer pageNum,
                                                   @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            IPage<Map<String, Object>> page = financeAccountingService.getMaterialCostConfigPage(month, keyword, pageNum, pageSize);
            return new ResponseResult<>(200, "查询成功", page);
        } catch (Exception e) {
            return new ResponseResult<>(500, "查询失败: " + e.getMessage(), null);
        }
    }

    @PostMapping("/cost-accounting/material-config")
    public ResponseResult<?> saveMaterialCostConfig(@RequestBody Map<String, Object> payload) {
        try {
            return new ResponseResult<>(200, "保存成功", financeAccountingService.saveMaterialCostConfig(payload));
        } catch (Exception e) {
            return new ResponseResult<>(500, "保存失败: " + e.getMessage(), null);
        }
    }

    @GetMapping("/basic-config/monthly")
    public ResponseResult<?> getMonthlyBasicConfig(@RequestParam String month) {
        try {
            return new ResponseResult<>(200, "查询成功", financeAccountingService.getMonthlyBasicConfig(month));
        } catch (Exception e) {
            return new ResponseResult<>(500, "查询失败: " + e.getMessage(), null);
        }
    }

    @PostMapping("/basic-config/monthly")
    public ResponseResult<?> saveMonthlyBasicConfig(@RequestBody Map<String, Object> payload) {
        try {
            return new ResponseResult<>(200, "保存成功", financeAccountingService.saveMonthlyBasicConfig(payload));
        } catch (Exception e) {
            return new ResponseResult<>(500, "保存失败: " + e.getMessage(), null);
        }
    }

    @GetMapping("/salary/page")
    public ResponseResult<?> getSalaryPage(@RequestParam String month,
                                           @RequestParam(required = false) String employeeName,
                                           @RequestParam(defaultValue = "1") Integer pageNum,
                                           @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            return new ResponseResult<>(200, "查询成功", financeAccountingService.getSalaryPage(month, employeeName, pageNum, pageSize));
        } catch (Exception e) {
            return new ResponseResult<>(500, "查询失败: " + e.getMessage(), null);
        }
    }

    @PostMapping("/salary")
    public ResponseResult<?> saveSalaryRecord(@RequestBody Map<String, Object> payload) {
        try {
            return new ResponseResult<>(200, "保存成功", financeAccountingService.saveSalaryRecord(payload));
        } catch (Exception e) {
            return new ResponseResult<>(500, "保存失败: " + e.getMessage(), null);
        }
    }

    @DeleteMapping("/salary/{id}")
    public ResponseResult<?> deleteSalaryRecord(@PathVariable Long id) {
        try {
            financeAccountingService.deleteSalaryRecord(id);
            return new ResponseResult<>(200, "删除成功", null);
        } catch (Exception e) {
            return new ResponseResult<>(500, "删除失败: " + e.getMessage(), null);
        }
    }

    @GetMapping("/bank-ledger/page")
    public ResponseResult<?> getBankLedgerPage(@RequestParam String month,
                                               @RequestParam(required = false) String bankCode,
                                               @RequestParam(defaultValue = "1") Integer pageNum,
                                               @RequestParam(defaultValue = "20") Integer pageSize) {
        try {
            return new ResponseResult<>(200, "查询成功", financeAccountingService.getBankLedgerPage(month, bankCode, pageNum, pageSize));
        } catch (Exception e) {
            return new ResponseResult<>(500, "查询失败: " + e.getMessage(), null);
        }
    }

    @PostMapping("/bank-ledger")
    public ResponseResult<?> saveBankLedger(@RequestBody Map<String, Object> payload) {
        try {
            return new ResponseResult<>(200, "保存成功", financeAccountingService.saveBankLedger(payload));
        } catch (Exception e) {
            return new ResponseResult<>(500, "保存失败: " + e.getMessage(), null);
        }
    }

    @PostMapping("/bank-ledger/kingdee/push/{id}")
    public ResponseResult<?> pushBankLedgerToKingdee(@PathVariable Long id) {
        try {
            return new ResponseResult<>(200, "推送成功", financeAccountingService.pushBankLedgerToKingdee(id));
        } catch (Exception e) {
            return new ResponseResult<>(500, "推送失败: " + e.getMessage(), null);
        }
    }

    @GetMapping("/bank-ledger/kingdee/template")
    public ResponseResult<?> getKingdeeTemplate() {
        try {
            return new ResponseResult<>(200, "查询成功", financeAccountingService.getKingdeeTemplate());
        } catch (Exception e) {
            return new ResponseResult<>(500, "查询失败: " + e.getMessage(), null);
        }
    }
}
