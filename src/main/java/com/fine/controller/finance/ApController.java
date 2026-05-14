package com.fine.controller.finance;

import com.fine.Utils.ResponseResult;
import com.fine.service.ApService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/finance/ap")
public class ApController {

    @Autowired
    private ApService apService;

    @GetMapping("/bills")
    public ResponseResult<?> list(@RequestParam(required = false) Map<String, Object> params) {
        return apService.listBills(params);
    }

    @GetMapping("/bill/{id}")
    public ResponseResult<?> get(@PathVariable Long id) {
        return apService.getBill(id);
    }

    @PostMapping("/bill")
    public ResponseResult<?> create(@RequestBody Map<String, Object> payload) {
        return apService.createBill(payload);
    }
}
