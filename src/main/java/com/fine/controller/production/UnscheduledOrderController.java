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
    public ResponseResult<java.util.Map<String, Object>> page(
            @RequestParam(value = "current", required = false) Integer current,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "materialCode", required = false) String materialCode,
            @RequestParam(value = "orderNo", required = false) String orderNo,
            @RequestParam(value = "customerName", required = false) String customerName,
            @RequestParam(value = "statusBadge", required = false) String statusBadge) {

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

        // 兼容前端字段命名与过滤
        java.util.List<java.util.Map<String, Object>> records = new java.util.ArrayList<>();
        if (page.getRecords() != null) {
            for (PendingScheduleOrder po : page.getRecords()) {
                // 过滤：订单号、客户、状态（状态仅本地计算，可选）
                if (orderNo != null && !orderNo.isEmpty() && (po.getOrderNo() == null || !po.getOrderNo().contains(orderNo))) {
                    continue;
                }
                if (customerName != null && !customerName.isEmpty() && (po.getCustomer() == null || !po.getCustomer().contains(customerName))) {
                    continue;
                }

                java.util.Map<String, Object> m = new java.util.HashMap<>();
                m.put("orderId", po.getOrderId());
                m.put("orderItemId", po.getOrderItemId());
                m.put("orderNo", po.getOrderNo());
                m.put("customerName", po.getCustomer());
                m.put("materialCode", po.getMaterialCode());
                m.put("materialName", po.getMaterialName());

                // 数量映射：pendingQty 作为需求量/缺口；已入池量暂为0；已锁定量暂无数据，置0
                m.put("totalQty", po.getPendingQty());
                m.put("lockedQty", 0);
                m.put("shortageQty", po.getPendingQty());
                m.put("inPoolQty", 0);

                // 日期：added_at 列映射为计划日期/交期显示
                m.put("planDate", po.getDeliveryDate());
                m.put("deliveryDate", po.getDeliveryDate());

                // 状态徽标：pending pool 均标记为“已入池待排程”
                String badge = "🔵 已入池待排程";
                if (statusBadge != null && !statusBadge.isEmpty() && !badge.contains(statusBadge.replace("🔵", "").trim())) {
                    // 若前端传入期望状态且不匹配则跳过
                    continue;
                }
                m.put("statusBadge", badge);

                // 优先级占位（后续可改为真实分数）
                m.put("priority", 0);

                records.add(m);
            }
        }

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("records", records);
        result.put("total", page.getTotal());
        result.put("size", resolvedPageSize);
        result.put("current", resolvedPageNum);

        return ResponseResult.success(result);
    }
}
