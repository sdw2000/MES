package com.fine.controller.production;

import com.fine.Utils.ResponseResult;
import com.fine.service.production.MaterialReadinessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 排程前物料齐套监控
 */
@RestController
@RequestMapping("/api/production/readiness")
@PreAuthorize("hasAnyAuthority('admin','production','warehouse')")
public class MaterialReadinessController {

    @Autowired
    private MaterialReadinessService materialReadinessService;

    /**
     * 化工原料齐套汇总
     */
    @GetMapping("/chemical-summary")
    public ResponseResult<Map<String, Object>> chemicalSummary(@RequestParam(required = false) String requiredByDate,
                                                               @RequestParam(required = false) String orderNo,
                                                               @RequestParam(required = false) String materialCode) {
        LocalDate date = (requiredByDate == null || requiredByDate.trim().isEmpty())
                ? LocalDate.now() : LocalDate.parse(requiredByDate);
        return ResponseResult.success(materialReadinessService.getChemicalReadinessSummary(date, orderNo, materialCode));
    }

    /**
     * 单个订单明细齐套评估
     */
    @GetMapping("/order-item/{orderItemId}")
    public ResponseResult<Map<String, Object>> orderItemReadiness(@PathVariable Long orderItemId) {
        return ResponseResult.success(materialReadinessService.getOrderItemReadiness(orderItemId));
    }

    /**
     * 批量订单明细齐套评估
     */
    @PostMapping("/order-items/batch")
    public ResponseResult<Map<Long, Map<String, Object>>> batchOrderItemReadiness(@RequestBody BatchReadinessRequest request) {
        List<Long> ids = request == null ? null : request.getOrderItemIds();
        if (ids == null || ids.isEmpty()) {
            return ResponseResult.success(Collections.emptyMap());
        }
        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            try {
                result.put(id, materialReadinessService.getOrderItemReadiness(id));
            } catch (Exception ex) {
                Map<String, Object> fallback = new LinkedHashMap<>();
                fallback.put("orderItemId", id);
                fallback.put("statusCode", "RISK");
                fallback.put("statusText", "评估失败");
                fallback.put("error", ex.getMessage());
                result.put(id, fallback);
            }
        }
        return ResponseResult.success(result);
    }

    @lombok.Data
    public static class BatchReadinessRequest {
        private List<Long> orderItemIds;
    }
}
