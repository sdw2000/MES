package com.fine.controller.finance;

import com.fine.Utils.ResponseResult;
import com.fine.service.BankService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/finance/bank")
public class BankController {

    @Autowired
    private BankService bankService;

    @GetMapping("/accounts")
    public ResponseResult<?> accounts(@RequestParam(required = false) Map<String, Object> params) {
        return bankService.listAccounts(params);
    }

    @GetMapping("/transactions/{accountId}")
    public ResponseResult<?> transactions(@PathVariable Long accountId, @RequestParam(required = false) Map<String, Object> params) {
        return bankService.listTransactions(accountId, params);
    }

    @PostMapping("/payment/apply")
    public ResponseResult<?> applyPayment(@RequestBody Map<String, Object> payload) {
        return bankService.applyPayment(payload);
    }
}
