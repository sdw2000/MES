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
 * 送样订单Controller
 * @author AI Assistant
 * @date 2026-01-05
 */
@RestController
@RequestMapping("/api/sales/samples")
@PreAuthorize("hasAuthority('admin')")
@CrossOrigin
public class SampleController {
      @Autowired
    private SampleOrderService sampleOrderService;
    
    /**
     * 分页查询送样订单列表
     * @param current 当前页
     * @param size 每页大小
     * @param customerName 客户名称（可选）
     * @param status 状态（可选）
     * @param trackingNumber 快递单号（可选）
     */    @GetMapping
    public ResponseResult<Page<SampleOrderDTO>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String trackingNumber) {
        
        Page<SampleOrderDTO> page = sampleOrderService.list(current, size, customerName, status, trackingNumber);
        return new ResponseResult<>(20000, "查询成功", page);
    }
    
    /**
     * 查询送样订单详情
     */
    @GetMapping("/{sampleNo}")
    public ResponseResult<SampleOrderDTO> detail(@PathVariable String sampleNo) {
        SampleOrderDTO dto = sampleOrderService.getDetailBySampleNo(sampleNo);
        if (dto == null) {
            return new ResponseResult<>(40004, "送样订单不存在");
        }
        return new ResponseResult<>(20000, "查询成功", dto);
    }
    
    /**
     * 创建送样订单
     */
    @PostMapping
    public ResponseResult<String> create(@RequestBody SampleOrderDTO dto) {
        try {
            String sampleNo = sampleOrderService.create(dto);
            return new ResponseResult<>(20000, "创建成功", sampleNo);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(50000, "创建失败：" + e.getMessage());
        }
    }
    
    /**
     * 更新送样订单
     */
    @PutMapping
    public ResponseResult<Void> update(@RequestBody SampleOrderDTO dto) {
        try {
            boolean success = sampleOrderService.update(dto);
            if (success) {
                return new ResponseResult<>(20000, "更新成功");
            } else {
                return new ResponseResult<>(50000, "更新失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(50000, "更新失败：" + e.getMessage());
        }
    }
    
    /**
     * 删除送样订单（逻辑删除）
     */
    @DeleteMapping("/{sampleNo}")
    public ResponseResult<Void> delete(@PathVariable String sampleNo) {
        try {
            boolean success = sampleOrderService.delete(sampleNo);
            if (success) {
                return new ResponseResult<>(20000, "删除成功");
            } else {
                return new ResponseResult<>(50000, "删除失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(50000, "删除失败：" + e.getMessage());
        }
    }
    
    /**
     * 更新物流信息
     */
    @PutMapping("/{sampleNo}/logistics")
    public ResponseResult<Void> updateLogistics(
            @PathVariable String sampleNo,
            @RequestBody LogisticsUpdateDTO dto) {
        try {
            dto.setSampleNo(sampleNo);
            boolean success = sampleOrderService.updateLogistics(dto);
            if (success) {
                return new ResponseResult<>(20000, "物流信息更新成功");
            } else {
                return new ResponseResult<>(50000, "物流信息更新失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(50000, "物流信息更新失败：" + e.getMessage());
        }
    }
    
    /**
     * 查询物流信息（调用快递100 API）
     */
    @GetMapping("/{sampleNo}/logistics")
    public ResponseResult<Map<String, Object>> queryLogistics(@PathVariable String sampleNo) {
        try {
            Map<String, Object> result = sampleOrderService.queryLogistics(sampleNo);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return new ResponseResult<>(20000, "物流查询成功", result);
            } else {
                return new ResponseResult<>(50000, result.get("message").toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(50000, "物流查询失败：" + e.getMessage());
        }
    }
    
    /**
     * 更新状态
     */
    @PutMapping("/{sampleNo}/status")
    public ResponseResult<Void> updateStatus(
            @PathVariable String sampleNo,
            @RequestParam String status,
            @RequestParam(required = false) String reason) {
        try {
            boolean success = sampleOrderService.updateStatus(sampleNo, status, reason);
            if (success) {
                return new ResponseResult<>(20000, "状态更新成功");
            } else {
                return new ResponseResult<>(50000, "状态更新失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(50000, "状态更新失败：" + e.getMessage());
        }
    }
    
    /**
     * 转为订单
     */
    @PostMapping("/{sampleNo}/convert-to-order")
    public ResponseResult<String> convertToOrder(@PathVariable String sampleNo) {
        try {
            String orderNo = sampleOrderService.convertToOrder(sampleNo);
            return new ResponseResult<>(20000, "转订单成功", orderNo);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(50000, "转订单失败：" + e.getMessage());
        }
    }
      /**
     * 生成送样编号（前端可以预览）
     */
    @GetMapping("/generate-no")
    public ResponseResult<String> generateSampleNo() {
        String sampleNo = sampleOrderService.generateSampleNo();
        return new ResponseResult<>(20000, "生成成功", sampleNo);
    }
    
    /**
     * 导入送样单
     */
    @PostMapping("/import")
    public ResponseResult<Map<String, Object>> importSamples(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return new ResponseResult<>(40000, "请选择文件");
            }
            Map<String, Object> result = sampleOrderService.importFromExcel(file);
            return new ResponseResult<>(20000, "导入完成", result);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(50000, "导入失败：" + e.getMessage());
        }
    }
    
    /**
     * 导出送样单
     */
    @GetMapping("/export")
    public ResponseResult<?> exportSamples(
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String status) {
        try {
            return sampleOrderService.exportToExcel(customerName, status);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(50000, "导出失败：" + e.getMessage());
        }
    }
}
