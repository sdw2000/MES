package com.fine.controller.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.model.schedule.OrderCustomerPriority;
import com.fine.service.schedule.CustomerPriorityService;
import com.fine.Utils.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户优先级Controller
 */
@RestController
@RequestMapping("/api/production/customer-priority")
public class CustomerPriorityController {

    @Autowired
    private CustomerPriorityService customerPriorityService;

    /**
     * 获取订单客户优先级列表
     */
    @GetMapping("/list")
    public ResponseResult<Map<String, Object>> list(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
            @RequestParam(value = "customerCode", required = false) String customerCode,
            @RequestParam(value = "customerName", required = false) String customerName,
            @RequestParam(value = "priorityRange", required = false) String priorityRange
    ) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("pageNum", pageNum);
            params.put("pageSize", pageSize);
            params.put("customerCode", customerCode);
            params.put("customerName", customerName);
            params.put("priorityRange", priorityRange);

            IPage<Map<String, Object>> page = customerPriorityService.getCustomerPriorityPageByCustomer(params);
            
            Map<String, Object> result = new HashMap<>();
            result.put("list", page.getRecords());
            result.put("total", page.getTotal());
            result.put("pageNum", pageNum);
            result.put("pageSize", pageSize);
            
            return ResponseResult.success(result);
        } catch (Exception e) {
            return ResponseResult.error("获取优先级列表失败：" + e.getMessage());
        }
    }

    /**
     * 获取单个订单优先级详情
     */
    @GetMapping("/{orderId}")
    public ResponseResult<Map<String, Object>> getDetail(@PathVariable Long orderId) {
        try {
            Map<String, Object> detail = customerPriorityService.getCustomerPriorityDetail(orderId);
            if (detail == null || detail.isEmpty()) {
                return ResponseResult.error("客户优先级不存在");
            }
            return ResponseResult.success(detail);
        } catch (Exception e) {
            return ResponseResult.error("获取优先级详情失败：" + e.getMessage());
        }
    }

    /**
     * 批量计算订单客户优先级
     */
    @PostMapping("/calculate")
    public ResponseResult<String> calculate(@RequestBody CalculateRequest request) {
        try {
            if (request == null || request.getOrderIds() == null || request.getOrderIds().isEmpty()) {
                return ResponseResult.error("缺少客户ID");
            }
            // 客户维度：逐个刷新详情（不落单据表）
            for (Long id : request.getOrderIds()) {
                customerPriorityService.getCustomerPriorityDetail(id);
            }
            return ResponseResult.success("优先级计算成功");
        } catch (Exception e) {
            return ResponseResult.error("优先级计算失败：" + e.getMessage());
        }
    }

    /**
     * 重新计算所有待排程订单的优先级
     */
    @PostMapping("/recalculate-all")
    public ResponseResult<String> recalculateAll() {
        try {
            customerPriorityService.recalculateAllPriorities();
            return ResponseResult.success("所有优先级已重新计算");
        } catch (Exception e) {
            return ResponseResult.error("重新计算失败：" + e.getMessage());
        }
    }

    /**
     * 获取客户交易统计
     */
    @GetMapping("/transaction-stats/{customerId}")
    public ResponseResult<Map<String, Object>> getTransactionStats(@PathVariable Long customerId) {
        try {
            Map<String, Object> stats = customerPriorityService.getCustomerTransactionStats(customerId);
            return ResponseResult.success(stats);
        } catch (Exception e) {
            return ResponseResult.error("获取交易统计失败：" + e.getMessage());
        }
    }

    /**
     * 获取客户料号单价统计
     */
    @GetMapping("/material-price-stats")
    public ResponseResult<Map<String, Object>> getMaterialPriceStats(
            @RequestParam Long customerId,
            @RequestParam String materialCode
    ) {
        try {
            Map<String, Object> stats = customerPriorityService.getCustomerMaterialPriceStats(customerId, materialCode);
            return ResponseResult.success(stats);
        } catch (Exception e) {
            return ResponseResult.error("获取料号单价统计失败：" + e.getMessage());
        }
    }

    public static class CalculateRequest {
        private List<Long> orderIds;

        public List<Long> getOrderIds() {
            return orderIds;
        }

        public void setOrderIds(List<Long> orderIds) {
            this.orderIds = orderIds;
        }
    }
}
