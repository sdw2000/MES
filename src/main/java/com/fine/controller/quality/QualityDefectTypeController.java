package com.fine.controller.quality;

import com.fine.Utils.ResponseResult;
import com.fine.model.quality.QualityDefectType;
import com.fine.service.quality.QualityDefectTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quality/defect-type")
@PreAuthorize("hasAnyAuthority('admin','quality','production')")
public class QualityDefectTypeController {

    @Autowired
    private QualityDefectTypeService defectTypeService;

    @GetMapping("/list")
    public ResponseResult<?> list() {
        return ResponseResult.success(defectTypeService.list());
    }

    @PostMapping
    public ResponseResult<?> create(@RequestBody QualityDefectType type) {
        return ResponseResult.success(defectTypeService.create(type));
    }

    @DeleteMapping("/{id}")
    public ResponseResult<?> delete(@PathVariable Long id) {
        defectTypeService.delete(id);
        return ResponseResult.success();
    }
}
