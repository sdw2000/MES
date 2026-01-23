package com.fine.controller.purchase;

import com.fine.Utils.ResponseResult;
import com.fine.modle.purchase.PurchaseSample;
import com.fine.service.purchase.PurchaseSampleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/purchase/samples")
@PreAuthorize("hasAuthority('admin')")
public class PurchaseSampleController {

    @Autowired
    private PurchaseSampleService sampleService;

    @GetMapping
    public ResponseResult<?> list(@RequestParam(defaultValue = "1") Integer page,
                                  @RequestParam(defaultValue = "20") Integer size,
                                  @RequestParam(required = false) String supplier,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(required = false) String trackingNumber) {
        return sampleService.list(page, size, supplier, status, trackingNumber);
    }

    @GetMapping("/{id}")
    public ResponseResult<?> detail(@PathVariable("id") String sampleNo) {
        return sampleService.detail(sampleNo);
    }

    @PostMapping
    public ResponseResult<?> create(@RequestBody PurchaseSample sample) {
        return sampleService.create(sample);
    }

    @PutMapping
    public ResponseResult<?> update(@RequestBody PurchaseSample sample) {
        return sampleService.updateSample(sample);
    }

    @DeleteMapping("/{id}")
    public ResponseResult<?> delete(@PathVariable("id") String sampleNo) {
        return sampleService.deleteSample(sampleNo);
    }
}
