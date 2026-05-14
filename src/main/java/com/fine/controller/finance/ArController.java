package com.fine.controller.finance;

import com.fine.Utils.ResponseResult;
import com.fine.service.ArService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/finance/ar")
public class ArController {

    @Autowired
    private ArService arService;

    @GetMapping("/invoices")
    public ResponseResult<?> list(@RequestParam(required = false) Map<String, Object> params) {
        return arService.listInvoices(params);
    }

    @GetMapping("/invoice/{id}")
    public ResponseResult<?> get(@PathVariable Long id) {
        return arService.getInvoice(id);
    }

    @PostMapping("/invoice")
    public ResponseResult<?> create(@RequestBody Map<String, Object> payload) {
        return arService.createInvoice(payload);
    }

    @PostMapping("/invoice/post")
    public ResponseResult<?> createAndPost(@RequestBody Map<String, Object> payload) {
        return arService.createAndPostInvoice(payload);
    }
}
