package com.fine.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Utils.ResponseResult;
import com.fine.modle.LogisticsUpdateDTO;
import com.fine.modle.SampleOrderDTO;
import com.fine.service.SampleOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 送样订单 Controller
 */
@RestController
@RequestMapping("/api/sales/samples")
@PreAuthorize("hasAnyAuthority('admin','sales','finance')")
@CrossOrigin
public class SampleController {

    @Autowired
    private SampleOrderService sampleOrderService;

    @GetMapping
    public ResponseResult<Page<SampleOrderDTO>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String trackingNumber) {
        Page<SampleOrderDTO> page = sampleOrderService.list(current, size, customerName, status, trackingNumber);
        return new ResponseResult<>(20000, "查询成功", page);
    }

    @GetMapping("/{sampleNo}")
    public ResponseResult<SampleOrderDTO> detail(@PathVariable String sampleNo) {
        SampleOrderDTO dto = sampleOrderService.getDetailBySampleNo(sampleNo);
        if (dto == null) {
            return new ResponseResult<>(40004, "送样订单不存在");
        }
        return new ResponseResult<>(20000, "查询成功", dto);
    }

    @PostMapping
    public ResponseResult<String> create(@RequestBody SampleOrderDTO dto) {
        try {
            String sampleNo = sampleOrderService.create(dto);
            return new ResponseResult<>(20000, "创建成功", sampleNo);
        } catch (Exception e) {
            return new ResponseResult<>(50000, "创建失败: " + e.getMessage());
        }
    }

    @PutMapping
    public ResponseResult<Void> update(@RequestBody SampleOrderDTO dto) {
        try {
            boolean success = sampleOrderService.update(dto);
            return success ? new ResponseResult<>(20000, "更新成功") : new ResponseResult<>(50000, "更新失败");
        } catch (Exception e) {
            return new ResponseResult<>(50000, "更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{sampleNo}")
    public ResponseResult<Void> delete(@PathVariable String sampleNo) {
        try {
            boolean success = sampleOrderService.delete(sampleNo);
            return success ? new ResponseResult<>(20000, "删除成功") : new ResponseResult<>(50000, "删除失败");
        } catch (Exception e) {
            return new ResponseResult<>(50000, "删除失败: " + e.getMessage());
        }
    }

    @PutMapping("/{sampleNo}/logistics")
    public ResponseResult<Void> updateLogistics(@PathVariable String sampleNo, @RequestBody LogisticsUpdateDTO dto) {
        try {
            dto.setSampleNo(sampleNo);
            boolean success = sampleOrderService.updateLogistics(dto);
            return success ? new ResponseResult<>(20000, "物流信息更新成功") : new ResponseResult<>(50000, "物流信息更新失败");
        } catch (Exception e) {
            return new ResponseResult<>(50000, "物流信息更新失败: " + e.getMessage());
        }
    }

    @GetMapping("/{sampleNo}/logistics")
    public ResponseResult<Map<String, Object>> queryLogistics(
            @PathVariable String sampleNo,
            @RequestParam(required = false) String trackingNumber,
            @RequestParam(required = false) String expressCompany) {
        try {
            Map<String, Object> result = sampleOrderService.queryLogistics(sampleNo, trackingNumber, expressCompany);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return new ResponseResult<>(20000, "物流查询成功", result);
            }
            String msg = String.valueOf(result.get("message"));
            if (msg.contains("查询无结果")) {
                result.put("success", false);
                result.put("status", result.getOrDefault("status", "暂无轨迹"));
                result.put("lastUpdate", result.getOrDefault("lastUpdate", "-"));
                result.put("traces", result.getOrDefault("traces", java.util.Collections.emptyList()));
                return new ResponseResult<>(20000, msg, result);
            }
            return new ResponseResult<>(50000, String.valueOf(result.get("message")));
        } catch (Exception e) {
            return new ResponseResult<>(50000, "物流查询失败: " + e.getMessage());
        }
    }

    @PutMapping("/{sampleNo}/status")
    public ResponseResult<Void> updateStatus(
            @PathVariable String sampleNo,
            @RequestParam String status,
            @RequestParam(required = false) String reason) {
        try {
            boolean success = sampleOrderService.updateStatus(sampleNo, status, reason);
            return success ? new ResponseResult<>(20000, "状态更新成功") : new ResponseResult<>(50000, "状态更新失败");
        } catch (Exception e) {
            return new ResponseResult<>(50000, "状态更新失败: " + e.getMessage());
        }
    }

    @PostMapping("/{sampleNo}/convert-to-order")
    public ResponseResult<String> convertToOrder(@PathVariable String sampleNo) {
        try {
            String orderNo = sampleOrderService.convertToOrder(sampleNo);
            return new ResponseResult<>(20000, "转订单成功", orderNo);
        } catch (Exception e) {
            return new ResponseResult<>(50000, "转订单失败: " + e.getMessage());
        }
    }

    @GetMapping("/generate-no")
    public ResponseResult<String> generateSampleNo() {
        String sampleNo = sampleOrderService.generateSampleNo();
        return new ResponseResult<>(20000, "生成成功", sampleNo);
    }

    @PostMapping("/import")
    public ResponseResult<Map<String, Object>> importSamples(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return new ResponseResult<>(40000, "请选择文件");
            }
            Map<String, Object> result = sampleOrderService.importFromExcel(file);
            return new ResponseResult<>(20000, "导入完成", result);
        } catch (Exception e) {
            return new ResponseResult<>(50000, "导入失败: " + e.getMessage());
        }
    }

    @GetMapping("/export")
    public ResponseResult<?> exportSamples(
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String status) {
        try {
            return sampleOrderService.exportToExcel(customerName, status);
        } catch (Exception e) {
            return new ResponseResult<>(50000, "导出失败: " + e.getMessage());
        }
    }
}
