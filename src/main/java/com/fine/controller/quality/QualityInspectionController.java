package com.fine.controller.quality;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Utils.ResponseResult;
import com.fine.model.quality.QualityInspectionRecord;
import com.fine.service.quality.QualityInspectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quality")
public class QualityInspectionController {

    @Autowired
    private QualityInspectionService inspectionService;

    @GetMapping({"/incoming", "/process", "/outbound"})
    public ResponseResult<?> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                  @RequestParam(defaultValue = "10") Integer pageSize,
                                  @RequestParam(required = false) String inspectionNo,
                                  @RequestParam(required = false) String batchNo,
                                  @RequestParam(required = false) String rollCode,
                                  @RequestParam(required = false) String result,
                                  @RequestParam(required = false) String startDate,
                                  @RequestParam(required = false) String endDate) {
        String inspectionType = resolveType();
        Page<QualityInspectionRecord> page = new Page<>(pageNum, pageSize);
        IPage<QualityInspectionRecord> data = inspectionService.list(page, inspectionType, inspectionNo, batchNo, rollCode, result, startDate, endDate);
        return ResponseResult.success(data);
    }

    @GetMapping({"/incoming/{id}", "/process/{id}", "/outbound/{id}"})
    public ResponseResult<?> detail(@PathVariable Long id) {
        QualityInspectionRecord record = inspectionService.detail(id);
        return ResponseResult.success(record);
    }

    @PostMapping({"/incoming", "/process", "/outbound"})
    public ResponseResult<?> create(@RequestBody QualityInspectionRecord record) {
        record.setInspectionType(resolveType());
        return ResponseResult.success(inspectionService.create(record));
    }

    @PutMapping({"/incoming", "/process", "/outbound"})
    public ResponseResult<?> update(@RequestBody QualityInspectionRecord record) {
        record.setInspectionType(resolveType());
        return ResponseResult.success(inspectionService.updateRecord(record));
    }

    @DeleteMapping({"/incoming/{id}", "/process/{id}", "/outbound/{id}"})
    public ResponseResult<?> delete(@PathVariable Long id) {
        inspectionService.deleteRecord(id);
        return ResponseResult.success();
    }

    // 兼容旧版接口：/api/quality/inspection/**
    @GetMapping("/inspection/list")
    public ResponseResult<?> listLegacy(@RequestParam(defaultValue = "1") Integer pageNum,
                                        @RequestParam(defaultValue = "10") Integer pageSize,
                                        @RequestParam(required = false) String inspectionType,
                                        @RequestParam(required = false) String inspectionNo,
                                        @RequestParam(required = false) String batchNo,
                                        @RequestParam(required = false) String rollCode,
                                        @RequestParam(required = false) String result,
                                        @RequestParam(required = false) String startDate,
                                        @RequestParam(required = false) String endDate) {
        String type = (inspectionType == null || inspectionType.isEmpty()) ? "incoming" : inspectionType;
        Page<QualityInspectionRecord> page = new Page<>(pageNum, pageSize);
        IPage<QualityInspectionRecord> data = inspectionService.list(page, type, inspectionNo, batchNo, rollCode, result, startDate, endDate);
        return ResponseResult.success(data);
    }

    @GetMapping("/inspection/{id}")
    public ResponseResult<?> detailLegacy(@PathVariable Long id) {
        return ResponseResult.success(inspectionService.detail(id));
    }

    @PostMapping("/inspection")
    public ResponseResult<?> createLegacy(@RequestBody QualityInspectionRecord record) {
        String type = record.getInspectionType();
        record.setInspectionType(type == null || type.isEmpty() ? "incoming" : type);
        return ResponseResult.success(inspectionService.create(record));
    }

    @PutMapping("/inspection")
    public ResponseResult<?> updateLegacy(@RequestBody QualityInspectionRecord record) {
        String type = record.getInspectionType();
        record.setInspectionType(type == null || type.isEmpty() ? "incoming" : type);
        return ResponseResult.success(inspectionService.updateRecord(record));
    }

    @DeleteMapping("/inspection/{id}")
    public ResponseResult<?> deleteLegacy(@PathVariable Long id) {
        inspectionService.deleteRecord(id);
        return ResponseResult.success();
    }

    private String resolveType() {
        String path = getCurrentPath();
        if (path.contains("/process")) return "process";
        if (path.contains("/outbound")) return "outbound";
        return "incoming";
    }

    private String getCurrentPath() {
        // Simple helper using RequestContextHolder to detect path
        return org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() instanceof org.springframework.web.context.request.ServletRequestAttributes
                ? ((org.springframework.web.context.request.ServletRequestAttributes) org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()).getRequest().getRequestURI()
                : "";
    }
}
