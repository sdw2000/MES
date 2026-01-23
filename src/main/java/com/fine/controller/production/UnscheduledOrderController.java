package com.fine.controller.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.Utils.ResponseResult;
import com.fine.entity.PendingScheduleOrder;
import com.fine.service.production.ProductionScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 提供未排程订单分页查询的兼容入口（别名路径）。
 * 前端请求 /api/production/unscheduled-orders/page 对应待涂布池分页。
 */
@RestController
@RequestMapping("/api/production/unscheduled-orders")
@CrossOrigin
public class UnscheduledOrderController {

    @Autowired
    private ProductionScheduleService scheduleService;

    @GetMapping("/page")
    public ResponseResult<IPage<PendingScheduleOrder>> page(
            @RequestParam(value = "current", required = false) Integer current,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "materialCode", required = false) String materialCode) {

        // 兼容不同前端命名：current/size 或 pageNum/pageSize
        int resolvedPageNum = current != null ? current : (pageNum != null ? pageNum : 1);
        int resolvedPageSize = size != null ? size : (pageSize != null ? pageSize : 10);

        Map<String, Object> params = new HashMap<>();
        params.put("pageNum", resolvedPageNum);
        params.put("pageSize", resolvedPageSize);
        if (materialCode != null && !materialCode.isEmpty()) {
            params.put("materialCode", materialCode);
        }

        IPage<PendingScheduleOrder> page = scheduleService.getPendingOrders(params);
        return ResponseResult.success(page);
    }
}
