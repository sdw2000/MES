package com.fine.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.Utils.ResponseResult;
import com.fine.entity.PendingScheduleOrder;
import com.fine.model.production.ScheduleCoating;
import com.fine.service.production.ProductionScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 待排程订单管理Controller
 */
@RestController
@RequestMapping("/api/production/pending-orders")
@CrossOrigin
public class PendingScheduleOrderController {

    @Autowired
    private ProductionScheduleService scheduleService;

    /**
     * 分页查询待排程订单
     */
    @GetMapping("/list")
    public ResponseResult getPendingOrders(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String materialCode
    ) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("pageNum", pageNum);
            params.put("pageSize", pageSize);
            if (materialCode != null && !materialCode.isEmpty()) {
                params.put("materialCode", materialCode);
            }
            
            IPage<PendingScheduleOrder> page = scheduleService.getPendingOrders(params);
            return ResponseResult.success(page);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("查询待排程订单失败: " + e.getMessage());
        }
    }

    /**
     * 按物料编号分组查询待排程订单汇总
     */
    @GetMapping("/group-by-material")
    public ResponseResult getPendingOrdersGroupByMaterial() {
        try {
            List<PendingScheduleOrder> list = scheduleService.getPendingOrdersGroupByMaterial();
            return ResponseResult.success(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("查询待排程订单汇总失败: " + e.getMessage());
        }
    }

    /**
     * 根据物料编号查询待排程订单
     */
    @GetMapping("/by-material/{materialCode}")
    public ResponseResult getPendingOrdersByMaterial(@PathVariable String materialCode) {
        try {
            List<PendingScheduleOrder> list = scheduleService.getPendingOrdersByMaterial(materialCode);
            return ResponseResult.success(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("查询物料待排程订单失败: " + e.getMessage());
        }
    }

    /**
     * 自动涂布排程 - 按物料编号自动生成涂布任务
     */
    @PostMapping("/auto-schedule-coating")
    public ResponseResult autoScheduleCoating(@RequestBody Map<String, Object> params) {
        try {
            String materialCode = (String) params.get("materialCode");
            Integer filmWidth = Integer.parseInt(params.get("filmWidth").toString());
            String operator = (String) params.get("operator");
            
            if (materialCode == null || materialCode.isEmpty()) {
                return ResponseResult.error("物料编号不能为空");
            }
            if (filmWidth == null || filmWidth <= 0) {
                return ResponseResult.error("薄膜宽度必须大于0");
            }
            
            List<ScheduleCoating> tasks = scheduleService.autoScheduleCoating(materialCode, filmWidth, operator);
            
            if (tasks.isEmpty()) {
                return ResponseResult.error("没有找到待排程的订单");
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("taskCount", tasks.size());
            result.put("tasks", tasks);
            result.put("message", "自动排程成功，共创建" + tasks.size() + "个涂布任务");
            
            return ResponseResult.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("自动排程失败: " + e.getMessage());
        }
    }

    /**
     * 批量涂布排程 - 选择多个订单进行排程
     */
    @PostMapping("/batch-schedule-coating")
    public ResponseResult batchScheduleCoating(@RequestBody Map<String, Object> params) {
        try {
            @SuppressWarnings("unchecked")
            List<Long> orderItemIds = (List<Long>) params.get("orderItemIds");
            Integer filmWidth = Integer.parseInt(params.get("filmWidth").toString());
            String planDate = (String) params.get("planDate");
            String operator = (String) params.get("operator");
            
            if (orderItemIds == null || orderItemIds.isEmpty()) {
                return ResponseResult.error("订单明细ID列表不能为空");
            }
            if (filmWidth == null || filmWidth <= 0) {
                return ResponseResult.error("薄膜宽度必须大于0");
            }
            
            List<ScheduleCoating> tasks = scheduleService.batchScheduleCoating(orderItemIds, filmWidth, planDate, operator);
            
            if (tasks.isEmpty()) {
                return ResponseResult.error("没有可排程的订单");
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("taskCount", tasks.size());
            result.put("tasks", tasks);
            result.put("message", "批量排程成功，共创建" + tasks.size() + "个涂布任务");
            
            return ResponseResult.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("批量排程失败: " + e.getMessage());
        }
    }
}
