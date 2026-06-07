package com.fine.controller.quality;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Utils.ResponseResult;
import com.fine.model.quality.QualityDisposition;
import com.fine.service.quality.QualityDispositionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quality/disposition")
@PreAuthorize("hasAnyAuthority('admin','quality','production')")
public class QualityDispositionController {

    @Autowired
    private QualityDispositionService dispositionService;

    @GetMapping
    public ResponseResult<?> list(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                  @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                  @RequestParam(value = "dispositionNo", required = false) String dispositionNo,
                                  @RequestParam(value = "inspectionNo", required = false) String inspectionNo,
                                  @RequestParam(value = "dispositionMethod", required = false) String dispositionMethod,
                                  @RequestParam(value = "startDate", required = false) String startDate,
                                  @RequestParam(value = "endDate", required = false) String endDate,
                                  @RequestParam(value = "status", required = false) String status) {
        Page<QualityDisposition> page = new Page<>(pageNum, pageSize);
        IPage<QualityDisposition> data = dispositionService.list(page, dispositionNo, inspectionNo, dispositionMethod, status, startDate, endDate);
        return ResponseResult.success(data);
    }

    @GetMapping("/{id}")
    public ResponseResult<?> detail(@PathVariable("id") Long id) {
        return ResponseResult.success(dispositionService.detail(id));
    }

    @PostMapping
    public ResponseResult<?> create(@RequestBody QualityDisposition disposition) {
        return ResponseResult.success(dispositionService.create(disposition));
    }

    @PutMapping
    public ResponseResult<?> update(@RequestBody QualityDisposition disposition) {
        return ResponseResult.success(dispositionService.updateDisposition(disposition));
    }

    @DeleteMapping("/{id}")
    public ResponseResult<?> delete(@PathVariable("id") Long id) {
        dispositionService.delete(id);
        return ResponseResult.success();
    }

    @PostMapping("/{id}/approve")
    public ResponseResult<?> approve(@PathVariable("id") Long id,
                                     @RequestParam("status") String status,
                                     @RequestParam(value = "remark", required = false) String remark) {
        QualityDisposition result = dispositionService.approve(id, status, remark);
        return ResponseResult.success(result);
    }
}
