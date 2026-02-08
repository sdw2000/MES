package com.fine.controller.schedule;

import com.fine.Utils.ResponseResult;
import com.fine.modle.schedule.ManualSchedule;
import com.fine.service.schedule.ManualScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 手动排程控制器
 */
@RestController
@RequestMapping("/schedule/manual")
public class ManualScheduleController {
    
    @Autowired
    private ManualScheduleService manualScheduleService;
    
    /**
     * 获取待排程订单列表
     */
    @GetMapping("/pending-orders")
    public ResponseResult<List<Map<String, Object>>> getPendingOrders() {
        List<Map<String, Object>> orders = manualScheduleService.getPendingOrders();
        return ResponseResult.success(orders);
    }
    
    /**
     * 获取已完成涂布待复卷的订单列表
     */
    @GetMapping("/coating-completed-orders")
    public ResponseResult<List<Map<String, Object>>> getCoatingCompletedOrders() {
        List<Map<String, Object>> orders = manualScheduleService.getCoatingCompletedOrders();
        return ResponseResult.success(orders);
    }
    
    /**
     * 匹配库存（先进先出）
     */
    @PostMapping("/match-stock")
    public ResponseResult<Map<String, Object>> matchStock(@RequestBody Map<String, Object> params) {
        String materialCode = (String) params.get("materialCode");
        Integer width = ((Number) params.get("width")).intValue();
        Integer thickness = ((Number) params.get("thickness")).intValue();
        Integer requiredQty = ((Number) params.get("requiredQty")).intValue();
        
        Map<String, Object> result = manualScheduleService.matchStock(materialCode, width, thickness, requiredQty);
        return ResponseResult.success(result);
    }
    
    /**
     * 计算涂布需求
     */
    @PostMapping("/calculate-coating")
    public ResponseResult<Map<String, Object>> calculateCoating(@RequestBody Map<String, Object> params) {
        String orderNo = (String) params.get("orderNo");
        String materialCode = (String) params.get("materialCode");
        
        Map<String, Object> result = manualScheduleService.calculateCoatingRequirement(orderNo, materialCode);
        return ResponseResult.success(result);
    }

    /**
     * 获取涂布排程列表
     */
    @GetMapping("/coating-schedules")
    public ResponseResult<List<Map<String, Object>>> getCoatingSchedules() {
        List<Map<String, Object>> list = manualScheduleService.getCoatingSchedules();
        return ResponseResult.success(list);
    }
    
    /**
     * 创建手动排程
     */
    @PostMapping("/create")
    public ResponseResult<Boolean> createSchedule(@RequestBody ManualSchedule schedule) {
        boolean success = manualScheduleService.createSchedule(schedule);
        return ResponseResult.success(success);
    }
    
    /**
     * 创建复卷排程
     */
    @PostMapping("/create-rewinding")
    public ResponseResult<Long> createRewindingSchedule(@RequestBody Map<String, Object> params) {
        Long scheduleId = ((Number) params.get("scheduleId")).longValue();
        List<Map<String, Object>> stockAllocations = (List<Map<String, Object>>) params.get("stockAllocations");
        
        Long rewindingId = manualScheduleService.createRewindingSchedule(scheduleId, stockAllocations);
        return ResponseResult.success(rewindingId);
    }
    
    /**
     * 创建涂布排程
     */
    @PostMapping("/create-coating")
    public ResponseResult<Long> createCoatingSchedule(@RequestBody Map<String, Object> params) {
        try {
            Object scheduleIdObj = params.get("scheduleId");
            Object coatingAreaObj = params.get("coatingArea");
            Object equipmentIdObj = params.get("equipmentId");
            
            Long scheduleId = null;
            if (scheduleIdObj instanceof Number) {
                scheduleId = ((Number) scheduleIdObj).longValue();
            } else if (scheduleIdObj instanceof String) {
                scheduleId = Long.parseLong((String) scheduleIdObj);
            }
            
            Double coatingArea = null;
            if (coatingAreaObj instanceof Number) {
                coatingArea = ((Number) coatingAreaObj).doubleValue();
            } else if (coatingAreaObj instanceof String) {
                coatingArea = Double.parseDouble((String) coatingAreaObj);
            }
            
            String coatingDate = (String) params.get("coatingDate");
            String equipmentId = null;
            if (equipmentIdObj instanceof Number) {
                equipmentId = ((Number) equipmentIdObj).toString();
            } else if (equipmentIdObj instanceof String) {
                equipmentId = (String) equipmentIdObj;
            }
            
            Long coatingId = manualScheduleService.createCoatingSchedule(
                    scheduleId,
                    coatingArea,
                    coatingDate,
                    null,
                    null,
                    equipmentId
            );
            return ResponseResult.success(coatingId);
        } catch (Exception e) {
            return ResponseResult.error("创建涂布排程失败: " + e.getMessage());
        }
    }
    
    /**
     * 确认排程
     */
    @PostMapping("/confirm")
    public ResponseResult<Boolean> confirmSchedule(@RequestBody Map<String, Object> params) {
        String orderNo = (String) params.get("orderNo");
        String materialCode = (String) params.get("materialCode");
        Integer scheduleQty = ((Number) params.get("scheduleQty")).intValue();
        
        boolean success = manualScheduleService.confirmSchedule(orderNo, materialCode, scheduleQty);
        return ResponseResult.success(success);
    }
}
