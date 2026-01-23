package com.fine.controller.production;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String priorityRange
    ) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("pageNum", pageNum);
            params.put("pageSize", pageSize);
            params.put("orderNo", orderNo);
            params.put("customerName", customerName);
            params.put("priorityRange", priorityRange);

            IPage<OrderCustomerPriority> page = customerPriorityService.getCustomerPriorityPage(params);
            
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
    public ResponseResult<OrderCustomerPriority> getDetail(@PathVariable Long orderId) {
        try {
            OrderCustomerPriority priority = customerPriorityService.getById(orderId);
            if (priority == null) {
                return ResponseResult.error("订单优先级不存在");
            }
            return ResponseResult.success(priority);
        } catch (Exception e) {
            return ResponseResult.error("获取优先级详情失败：" + e.getMessage());
        }
    }

    /**
     * 批量计算订单客户优先级
     */
    @PostMapping("/calculate")
    public ResponseResult<String> calculate(@RequestBody List<Long> orderIds) {
        try {
            customerPriorityService.calculatePriorities(orderIds);
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
}
