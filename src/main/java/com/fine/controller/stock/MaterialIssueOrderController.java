package com.fine.controller.stock;

import com.fine.Utils.ResponseResult;
import com.fine.modle.stock.MaterialIssueOrder;
import com.fine.service.stock.MaterialIssueOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 领料单(物料发放)管理
 */
@RestController
@RequestMapping("/api/stock/issue-order")
@PreAuthorize("hasAnyAuthority('warehouse','admin','production')")
public class MaterialIssueOrderController {

    @Autowired
    private MaterialIssueOrderService materialIssueOrderService;

    @PostMapping("/create")
    public ResponseResult<MaterialIssueOrder> create(
            @RequestBody Map<String, Object> params) {
        try {
            List<Long> lockIds = (List<Long>) params.get("lockIds");
            String planDateStr = (String) params.get("planDate");
            LocalDate planDate = planDateStr != null ? LocalDate.parse(planDateStr) : LocalDate.now();
            String materialCode = (String) params.get("materialCode");
            String orderNo = (String) params.get("orderNo");
            String remark = (String) params.get("remark");

            return ResponseResult.success(materialIssueOrderService.createIssueOrder(
                    lockIds, planDate, materialCode, orderNo, remark));
        } catch (Exception e) {
            return ResponseResult.error("创建领料单失败: " + e.getMessage());
        }
    }

    @PostMapping("/create-by-schedule")
    public ResponseResult<MaterialIssueOrder> createBySchedule(
            @RequestBody Map<String, Object> params) {
        try {
            Long scheduleId = Long.parseLong(String.valueOf(params.get("scheduleId")));
            String processType = (String) params.get("processType");
            String planDateStr = (String) params.get("planDate");
            LocalDate planDate = planDateStr != null ? LocalDate.parse(planDateStr) : LocalDate.now();
            String materialCode = (String) params.get("materialCode");
            String orderNo = (String) params.get("orderNo");
            String remark = (String) params.get("remark");

            return ResponseResult.success(materialIssueOrderService.createIssueOrderBySchedule(
                    scheduleId, processType, planDate, materialCode, orderNo, remark));
        } catch (Exception e) {
            return ResponseResult.error("生成领料单失败: " + e.getMessage());
        }
    }

    @GetMapping("/list")
    public ResponseResult<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate planDate,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String materialCode) {
        return ResponseResult.success(materialIssueOrderService.getIssueOrderPage(
                current, size, planDate, orderNo, materialCode));
    }

    @GetMapping("/detail")
    public ResponseResult<MaterialIssueOrder> getDetail(@RequestParam String issueNo) {
        MaterialIssueOrder order = materialIssueOrderService.getIssueOrderDetail(issueNo);
        if (order == null) {
            return ResponseResult.error("未找到领料单");
        }
        return ResponseResult.success(order);
    }

    @PostMapping("/receive")
    public ResponseResult<String> receive(@RequestParam String issueNo, @RequestParam String operator) {
        try {
            materialIssueOrderService.receiveIssueOrder(issueNo, operator);
            return ResponseResult.success("接收成功");
        } catch (Exception e) {
            return ResponseResult.error("接收失败: " + e.getMessage());
        }
    }

    @PostMapping("/cancel")
    public ResponseResult<String> cancel(@RequestParam Long id, @RequestParam String operator) {
        try {
            materialIssueOrderService.cancelIssueOrder(id, operator);
            return ResponseResult.success("取消成功");
        } catch (Exception e) {
            return ResponseResult.error("取消失败: " + e.getMessage());
        }
    }

    @GetMapping("/workshop-stock")
    public ResponseResult<List<Map<String, Object>>> workshopStock(@RequestParam(required = false) String workshop) {
        return ResponseResult.success(materialIssueOrderService.getWorkshopStock(workshop));
    }
}
