package com.fine.controller.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.model.production.*;
import com.fine.service.production.ProductionScheduleService;
import com.fine.Utils.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Date;

/**
 * 生产排程Controller
 */
@RestController
@RequestMapping("/api/production/schedule")
@CrossOrigin
public class ProductionScheduleController {
    
    @Autowired
    private ProductionScheduleService scheduleService;
    
    // ========== 排程主表接口 ==========
    
    /**
     * 获取排程列表
     */
    @GetMapping("/list")
    public ResponseResult<Map<String, Object>> getScheduleList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String scheduleNo,
            @RequestParam(required = false) String scheduleType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        
        Map<String, Object> params = new HashMap<>();
        params.put("pageNum", pageNum);
        params.put("pageSize", pageSize);
        params.put("scheduleNo", scheduleNo);
        params.put("scheduleType", scheduleType);
        params.put("status", status);
        params.put("startDate", startDate);
        params.put("endDate", endDate);
        
        IPage<ProductionSchedule> pageResult = scheduleService.getScheduleList(params);
        List<ProductionSchedule> list = pageResult.getRecords();
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", pageResult.getTotal());
        result.put("pageNum", pageResult.getCurrent());
        result.put("pageSize", pageResult.getSize());
        
        return ResponseResult.success(result);
    }
    
    /**
     * 获取排程详情
     */
    @GetMapping("/{id}")
    public ResponseResult<ProductionSchedule> getScheduleById(@PathVariable Long id) {
        ProductionSchedule schedule = scheduleService.getScheduleById(id);
        if (schedule == null) {
            return ResponseResult.error("排程不存在");
        }
        return ResponseResult.success(schedule);
    }
      /**
     * 获取排程明细列表
     */
    @GetMapping("/{scheduleId}/items")
    public ResponseResult<Map<String, Object>> getScheduleOrderItems(
            @PathVariable Long scheduleId,
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String materialCode) {
        
        Map<String, Object> params = new HashMap<>();
        params.put("pageNum", pageNum);
        params.put("pageSize", pageSize);
        params.put("scheduleId", scheduleId);
        params.put("status", status);
        params.put("materialCode", materialCode);
        
        IPage<ScheduleOrderItem> page = scheduleService.getScheduleOrderItems(params);
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", page.getRecords());
        result.put("total", page.getTotal());
        result.put("pages", page.getPages());
        result.put("pageNum", page.getCurrent());
        result.put("pageSize", page.getSize());
        result.put("hasNextPage", page.getCurrent() < page.getPages());
        
        return ResponseResult.success(result);
    }
    
    /**
     * 创建排程
     */
    @PostMapping
    public ResponseResult<ProductionSchedule> createSchedule(@RequestBody ProductionSchedule schedule) {
        try {
            ProductionSchedule created = scheduleService.createSchedule(schedule);
            return ResponseResult.success(created);
        } catch (Exception e) {
            return ResponseResult.error("创建排程失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新排程
     */
    @PutMapping("/{id}")
    public ResponseResult<String> updateSchedule(@PathVariable Long id, @RequestBody ProductionSchedule schedule) {
        schedule.setId(id);
        int rows = scheduleService.updateSchedule(schedule);
        if (rows > 0) {
            return ResponseResult.success("更新成功");
        }
        return ResponseResult.error("更新失败");
    }
    
    /**
     * 删除排程
     */
    @DeleteMapping("/{id}")
    public ResponseResult<String> deleteSchedule(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "admin") String operator) {
        try {
            int rows = scheduleService.deleteSchedule(id, operator);
            if (rows > 0) {
                return ResponseResult.success("删除成功");
            }
            return ResponseResult.error("删除失败");
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }
    
    /**
     * 确认排程
     */
    @PostMapping("/{id}/confirm")
    public ResponseResult<String> confirmSchedule(@PathVariable Long id,
                                                   @RequestParam(defaultValue = "admin") String operator) {
        try {
            int rows = scheduleService.confirmSchedule(id, operator);
            if (rows > 0) {
                return ResponseResult.success("排程已确认");
            }
            return ResponseResult.error("确认失败");
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }
    
    /**
     * 自动排程/批量排程
     */
    @PostMapping("/auto")
    public ResponseResult<ProductionSchedule> autoSchedule(@RequestBody Map<String, Object> params) {
        try {
            ProductionSchedule schedule = scheduleService.autoSchedule(params);
            return ResponseResult.success(schedule);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("自动排程失败: " + e.getMessage());
        }
    }
    
    /**
     * 取消排程
     */
    @PostMapping("/{id}/cancel")
    public ResponseResult<String> cancelSchedule(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "admin") String operator) {
        try {
            int rows = scheduleService.cancelSchedule(id, operator);
            if (rows > 0) {
                return ResponseResult.success("取消成功");
            }
            return ResponseResult.error("取消失败");
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }
    
    /**
     * 获取待排程订单明细
     */
    @GetMapping("/pending-orders")
    public ResponseResult<Map<String, Object>> getPendingOrderItems(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String customerLevel,
            @RequestParam(required = false) String materialCode) {
        
        Map<String, Object> params = new HashMap<>();
        params.put("pageNum", pageNum);
        params.put("pageSize", pageSize);
        params.put("customerLevel", customerLevel);
        params.put("materialCode", materialCode);
        
        IPage<Map<String, Object>> page = scheduleService.getPendingOrderItems(params);
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", page.getRecords());
        result.put("total", page.getTotal());
        result.put("pageNum", page.getCurrent());
        result.put("pageSize", page.getSize());
        result.put("pages", page.getPages());
        result.put("hasNextPage", page.getCurrent() < page.getPages());
        
        return ResponseResult.success(result);
    }
    
    /**
     * 获取排程统计
     */
    @GetMapping("/statistics")
    public ResponseResult<Map<String, Object>> getScheduleStatistics() {
        Map<String, Object> stats = scheduleService.getScheduleStatistics();
        return ResponseResult.success(stats);    }
    
    // ========== 涂布任务接口 ==========
    
    /**
     * 获取涂布任务列表
     */
    @GetMapping("/coating/list")
    public ResponseResult<Map<String, Object>> getCoatingTasks(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long scheduleId,
            @RequestParam(required = false) String planDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) String materialCode) {
        
        Map<String, Object> params = new HashMap<>();
        params.put("pageNum", pageNum);
        params.put("pageSize", pageSize);
        params.put("scheduleId", scheduleId);
        params.put("planDate", planDate);
        params.put("status", status);
        params.put("equipmentId", equipmentId);
        params.put("materialCode", materialCode);
        
        IPage<ScheduleCoating> page = scheduleService.getCoatingTasks(params);
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", page.getRecords());
        result.put("total", page.getTotal());
        result.put("pages", page.getPages());
        result.put("pageNum", page.getCurrent());
        result.put("pageSize", page.getSize());
        result.put("hasNextPage", page.getCurrent() < page.getPages());
        
        return ResponseResult.success(result);
    }
    
    /**
     * 添加涂布任务
     */
    @PostMapping("/coating")
    public ResponseResult<ScheduleCoating> addCoatingTask(@RequestBody ScheduleCoating coating) {
        try {
            ScheduleCoating created = scheduleService.addCoatingTask(coating);
            return ResponseResult.success(created);
        } catch (Exception e) {
            return ResponseResult.error("添加涂布任务失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新涂布任务
     */
    @PutMapping("/coating/{id}")
    public ResponseResult<String> updateCoatingTask(@PathVariable Long id, @RequestBody ScheduleCoating coating) {
        coating.setId(id);
        int rows = scheduleService.updateCoatingTask(coating);
        if (rows > 0) {
            return ResponseResult.success("更新成功");
        }
        return ResponseResult.error("更新失败");
    }
    
    /**
     * 开始涂布任务
     */
    @PostMapping("/coating/{id}/start")
    public ResponseResult<String> startCoatingTask(@PathVariable Long id,
                                                    @RequestParam(defaultValue = "admin") String operator) {
        int rows = scheduleService.startCoatingTask(id, operator);
        if (rows > 0) {
            return ResponseResult.success("任务已开始");
        }
        return ResponseResult.error("操作失败");
    }
    
    /**
     * 完成涂布任务
     */
    @PostMapping("/coating/{id}/complete")
    public ResponseResult<String> completeCoatingTask(@PathVariable Long id,
                                                       @RequestParam String outputBatchNo,
                                                       @RequestParam(defaultValue = "admin") String operator) {        int rows = scheduleService.completeCoatingTask(id, outputBatchNo, operator);
        if (rows > 0) {
            return ResponseResult.success("任务已完成");
        }
        return ResponseResult.error("操作失败");
    }
    
    // ========== 复卷任务接口 ==========
    
    /**
     * 获取复卷任务列表
     */
    @GetMapping("/rewinding/list")
    public ResponseResult<Map<String, Object>> getRewindingTasks(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long scheduleId,
            @RequestParam(required = false) String planDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) String materialCode) {
        
        Map<String, Object> params = new HashMap<>();
        params.put("pageNum", pageNum);
        params.put("pageSize", pageSize);
        params.put("scheduleId", scheduleId);
        params.put("planDate", planDate);
        params.put("status", status);
        params.put("equipmentId", equipmentId);
        params.put("materialCode", materialCode);
        
        IPage<ScheduleRewinding> page = scheduleService.getRewindingTasks(params);
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", page.getRecords());
        result.put("total", page.getTotal());
        result.put("pages", page.getPages());
        result.put("pageNum", page.getCurrent());
        result.put("pageSize", page.getSize());
        result.put("hasNextPage", page.getCurrent() < page.getPages());
        
        return ResponseResult.success(result);
    }
      /**
     * 添加复卷任务
     */
    @PostMapping("/rewinding")
    public ResponseResult<ScheduleRewinding> addRewindingTask(@RequestBody ScheduleRewinding rewinding) {
        try {
            ScheduleRewinding created = scheduleService.addRewindingTask(rewinding);
            return ResponseResult.success(created);
        } catch (Exception e) {
            return ResponseResult.error("添加复卷任务失败: " + e.getMessage());
        }
    }

    /**
     * 更新复卷任务设备
     */
    @PostMapping("/rewinding/equipment")
    public ResponseResult<Boolean> updateRewindingEquipment(@RequestBody Map<String, Object> body) {
        try {
            Long taskId = body.get("taskId") == null ? null : Long.valueOf(body.get("taskId").toString());
            Long equipmentId = body.get("equipmentId") == null ? null : Long.valueOf(body.get("equipmentId").toString());
            boolean ok = scheduleService.updateRewindingEquipment(taskId, equipmentId);
            return ok ? ResponseResult.success(true) : ResponseResult.error("更新设备失败");
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }

    /**
     * 调整复卷任务时间
     */
    @PostMapping("/rewinding/adjust-time/{taskId}")
    public ResponseResult<String> adjustRewindingTaskTime(@PathVariable Long taskId,
                                                          @RequestBody Map<String, Object> data) {
        try {
            boolean ok = scheduleService.adjustRewindingTaskTime(taskId, data);
            return ok ? ResponseResult.success("复卷时间已调整") : ResponseResult.error("调整失败");
        } catch (Exception e) {
            return ResponseResult.error("调整复卷时间失败: " + e.getMessage());
        }
    }
    
    // ========== 分切任务接口 ==========
    
    /**
     * 获取分切任务列表
     */
    @GetMapping("/slitting/list")
    public ResponseResult<Map<String, Object>> getSlittingTasks(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long scheduleId,
            @RequestParam(required = false) String planDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) String materialCode) {

        Map<String, Object> params = new HashMap<>();
        params.put("pageNum", pageNum);
        params.put("pageSize", pageSize);
        params.put("scheduleId", scheduleId);
        params.put("planDate", planDate);
        params.put("status", status);
        params.put("equipmentId", equipmentId);
        params.put("materialCode", materialCode);

        IPage<ScheduleSlitting> page = scheduleService.getSlittingTasks(params);
        Map<String, Object> result = new HashMap<>();
        result.put("list", page.getRecords());
        result.put("total", page.getTotal());
        result.put("pages", page.getPages());
        result.put("pageNum", page.getCurrent());
        result.put("pageSize", page.getSize());
        result.put("hasNextPage", page.getCurrent() < page.getPages());
        return ResponseResult.success(result);
    }

    /**
     * 添加分切任务
     */
    @PostMapping("/slitting")
    public ResponseResult<ScheduleSlitting> addSlittingTask(@RequestBody ScheduleSlitting slitting) {
        try {
            ScheduleSlitting created = scheduleService.addSlittingTask(slitting);
            return ResponseResult.success(created);
        } catch (Exception e) {
            return ResponseResult.error("添加分切任务失败: " + e.getMessage());
        }
    }

    /**
     * 更新分切任务
     */
    @PutMapping("/slitting/{id}")
    public ResponseResult<String> updateSlittingTask(@PathVariable Long id, @RequestBody ScheduleSlitting slitting) {
        slitting.setId(id);
        int rows = scheduleService.updateSlittingTask(slitting);
        return rows > 0 ? ResponseResult.success("更新成功") : ResponseResult.error("更新失败");
    }

    /**
     * 删除分切任务
     */
    @DeleteMapping("/slitting/{id}")
    public ResponseResult<String> deleteSlittingTask(@PathVariable Long id,
                                                     @RequestParam(defaultValue = "admin") String operator) {
        try {
            int rows = scheduleService.deleteSlittingTask(id);
            return rows > 0 ? ResponseResult.success("删除成功") : ResponseResult.error("删除失败");
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }

    /**
     * 启动分切任务
     */
    @PostMapping("/slitting/{id}/start")
    public ResponseResult<String> startSlittingTask(@PathVariable Long id,
                                                    @RequestParam(defaultValue = "admin") String operator) {
        try {
            int rows = scheduleService.startSlittingTask(id, operator);
            return rows > 0 ? ResponseResult.success("任务已开始") : ResponseResult.error("操作失败");
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }

    /**
     * 完成分切任务
     */
    @PostMapping("/slitting/{id}/complete")
    public ResponseResult<String> completeSlittingTask(@PathVariable Long id,
                                                       @RequestParam(required = false) Integer actualRolls,
                                                       @RequestParam(defaultValue = "admin") String operator) {
        try {
            int rows = scheduleService.completeSlittingTask(id, actualRolls, operator);
            return rows > 0 ? ResponseResult.success("任务已完成") : ResponseResult.error("操作失败");
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }
    // ========== 分条任务接口 ==========
    
    /**
     * 获取分条任务列表
     */
    @GetMapping("/stripping/list")
    public ResponseResult<Map<String, Object>> getStrippingTasks(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long scheduleId,
            @RequestParam(required = false) String planDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) String materialCode) {
        
        Map<String, Object> params = new HashMap<>();
        params.put("pageNum", pageNum);
        params.put("pageSize", pageSize);
        params.put("scheduleId", scheduleId);
        params.put("planDate", planDate);
        params.put("status", status);
        params.put("equipmentId", equipmentId);
        params.put("materialCode", materialCode);
        
        IPage<ScheduleStripping> page = scheduleService.getStrippingTasks(params);
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", page.getRecords());
        result.put("total", page.getTotal());
        result.put("pages", page.getPages());
        result.put("pageNum", page.getCurrent());
        result.put("pageSize", page.getSize());
        result.put("hasNextPage", page.getCurrent() < page.getPages());
        
        return ResponseResult.success(result);
    }
    
    /**
     * 添加分条任务
     */
    @PostMapping("/stripping")
    public ResponseResult<ScheduleStripping> addStrippingTask(@RequestBody ScheduleStripping stripping) {
        try {
            ScheduleStripping created = scheduleService.addStrippingTask(stripping);
            return ResponseResult.success(created);
        } catch (Exception e) {
            return ResponseResult.error("添加分条任务失败: " + e.getMessage());
        }
    }
    
    // ========== 生产报工接口 ==========
    
    /**
     * 获取报工记录列表
     */
    @GetMapping("/report/list")
    public ResponseResult<Map<String, Object>> getReportList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String reportNo,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String taskNo,
            @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) String reportDate) {
        
        Map<String, Object> params = new HashMap<>();
        params.put("pageNum", pageNum);
        params.put("pageSize", pageSize);
        params.put("reportNo", reportNo);
        params.put("taskType", taskType);
        params.put("taskNo", taskNo);
        params.put("staffId", staffId);
        params.put("reportDate", reportDate);
        
        IPage<ProductionReport> page = scheduleService.getReportList(params);
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", page.getRecords());
        result.put("total", page.getTotal());
        result.put("pages", page.getPages());
        result.put("pageNum", page.getCurrent());
        result.put("pageSize", page.getSize());
        result.put("hasNextPage", page.getCurrent() < page.getPages());
        
        return ResponseResult.success(result);
    }
    
    /**
     * 提交报工
     */
    @PostMapping("/report")
    public ResponseResult<ProductionReport> submitReport(@RequestBody ProductionReport report) {
        try {
            ProductionReport created = scheduleService.submitReport(report);
            return ResponseResult.success(created);
        } catch (Exception e) {
            return ResponseResult.error("提交报工失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取今日产量
     */
    @GetMapping("/report/today-output")
    public ResponseResult<List<Map<String, Object>>> getTodayOutput() {
        List<Map<String, Object>> output = scheduleService.getTodayOutput();
        return ResponseResult.success(output);
    }

    /**
     * 获取当班当月/当年生产报工总平米数
     */
    @GetMapping("/report/shift-summary")
    public ResponseResult<Map<String, Object>> getShiftSummary(
            @RequestParam(required = false) String shiftCode) {
        Map<String, Object> summary = scheduleService.getShiftProductionAreaSummary(shiftCode);
        return ResponseResult.success(summary);
    }
    
    // ========== 生产看板接口 ==========
    
    /**
     * 获取设备看板数据
     */
    @GetMapping("/board/equipment")
    public ResponseResult<List<Map<String, Object>>> getEquipmentBoard(
            @RequestParam(required = false) String planDate) {
        if (planDate == null || planDate.isEmpty()) {
            planDate = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        }
        List<Map<String, Object>> board = scheduleService.getEquipmentBoard(planDate);
        return ResponseResult.success(board);
    }
    
    /**
     * 获取进度看板数据
     */
    @GetMapping("/board/progress")
    public ResponseResult<Map<String, Object>> getProgressBoard(
            @RequestParam(required = false) String planDate) {
        if (planDate == null || planDate.isEmpty()) {
            planDate = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        }
        Map<String, Object> board = scheduleService.getProgressBoard(planDate);
        return ResponseResult.success(board);
    }
    
    // ========== 印刷任务接口 ==========
    
    /**
     * 获取印刷任务列表
     */
    @GetMapping("/printing/list")
    public ResponseResult<Map<String, Object>> getPrintingTasks(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String planDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long equipmentId) {
        
        Map<String, Object> params = new HashMap<>();
        params.put("pageNum", pageNum);
        params.put("pageSize", pageSize);
        params.put("planDate", planDate);
        params.put("status", status);
        params.put("equipmentId", equipmentId);
        
        IPage<SchedulePrinting> page = scheduleService.getPrintingTasks(params);
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", page.getRecords());
        result.put("total", page.getTotal());
        result.put("pageNum", page.getCurrent());
        result.put("pageSize", page.getSize());
        result.put("pages", page.getPages());
        result.put("hasNextPage", page.getCurrent() < page.getPages());
        
        return ResponseResult.success(result);
    }
    
    /**
     * 添加印刷任务
     */
    @PostMapping("/printing")
    public ResponseResult<SchedulePrinting> addPrintingTask(@RequestBody SchedulePrinting printing) {
        try {
            SchedulePrinting created = scheduleService.addPrintingTask(printing);
            return ResponseResult.success(created);
        } catch (Exception e) {
            return ResponseResult.error("添加印刷任务失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新印刷任务
     */
    @PutMapping("/printing/{id}")
    public ResponseResult<String> updatePrintingTask(@PathVariable Long id, @RequestBody SchedulePrinting printing) {
        printing.setId(id);
        int rows = scheduleService.updatePrintingTask(printing);
        if (rows > 0) {
            return ResponseResult.success("更新成功");
        }
        return ResponseResult.error("更新失败");
    }
    
    /**
     * 开始印刷任务
     */
    @PostMapping("/printing/{id}/start")
    public ResponseResult<String> startPrintingTask(@PathVariable Long id,
                                                     @RequestParam(defaultValue = "admin") String operator) {
        int rows = scheduleService.startPrintingTask(id, operator);
        if (rows > 0) {
            return ResponseResult.success("任务已开始");
        }
        return ResponseResult.error("操作失败");
    }
    
    /**
     * 完成印刷任务
     */
    @PostMapping("/printing/{id}/complete")
    public ResponseResult<String> completePrintingTask(@PathVariable Long id,
                                                        @RequestParam String outputBatchNo,
                                                        @RequestParam(defaultValue = "admin") String operator) {
        int rows = scheduleService.completePrintingTask(id, outputBatchNo, operator);
        if (rows > 0) {
            return ResponseResult.success("任务已完成");
        }
        return ResponseResult.error("操作失败");
    }
    
    // ========== 审批流程接口 ==========
    
    /**
     * 提交排程审批
     */
    @PostMapping("/{id}/submit-approval")
    public ResponseResult<String> submitApproval(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "admin") String operator) {
        try {
            int rows = scheduleService.submitApproval(id, operator);
            if (rows > 0) {
                return ResponseResult.success("已提交审批");
            }
            return ResponseResult.error("提交失败");
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }
    
    /**
     * 审批排程
     */
    @PostMapping("/{id}/approve")
    public ResponseResult<String> approveSchedule(@PathVariable Long id,
                                                   @RequestParam boolean approved,
                                                   @RequestParam(required = false) String remark,
                                                   @RequestParam(defaultValue = "admin") String operator) {
        try {
            int rows = scheduleService.approveSchedule(id, approved, remark, operator);
            if (rows > 0) {
                return ResponseResult.success(approved ? "审批通过" : "已驳回");
            }
            return ResponseResult.error("审批失败");
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }
    
    /**
     * 获取审批记录
     */
    @GetMapping("/{id}/approval-logs")
    public ResponseResult<List<ScheduleApprovalLog>> getApprovalLogs(@PathVariable Long id) {
        List<ScheduleApprovalLog> logs = scheduleService.getApprovalLogs(id);
        return ResponseResult.success(logs);
    }
    
    /**
     * 获取待审批排程列表
     */
    @GetMapping("/pending-approval")
    public ResponseResult<Map<String, Object>> getPendingApprovalList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        
        Map<String, Object> params = new HashMap<>();
        params.put("pageNum", pageNum);
        params.put("pageSize", pageSize);
        params.put("approvalStatus", "pending");
        
        IPage<ProductionSchedule> pageResult = scheduleService.getScheduleList(params);
        List<ProductionSchedule> list = pageResult.getRecords();
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", pageResult.getTotal());
        result.put("pageNum", pageResult.getCurrent());
        result.put("pageSize", pageResult.getSize());
        
        return ResponseResult.success(result);
    }
    
    // ========== 质检反馈接口 ==========
    
    /**
     * 获取质检记录列表
     */
    @GetMapping("/inspection/list")
    public ResponseResult<Map<String, Object>> getInspectionList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String inspectionNo,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String batchNo,
            @RequestParam(required = false) String overallResult,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        
        Map<String, Object> params = new HashMap<>();
        params.put("pageNum", pageNum);
        params.put("pageSize", pageSize);
        params.put("inspectionNo", inspectionNo);
        params.put("taskType", taskType);
        params.put("batchNo", batchNo);
        params.put("overallResult", overallResult);
        params.put("startDate", startDate);
        params.put("endDate", endDate);
        
        IPage<QualityInspection> page = scheduleService.getInspectionList(params);
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", page.getRecords());
        result.put("total", page.getTotal());
        result.put("pages", page.getPages());
        result.put("pageNum", page.getCurrent());
        result.put("pageSize", page.getSize());
        result.put("hasNextPage", page.getCurrent() < page.getPages());
        
        return ResponseResult.success(result);
    }
    
    /**
     * 获取质检详情
     */
    @GetMapping("/inspection/{id}")
    public ResponseResult<QualityInspection> getInspectionById(@PathVariable Long id) {
        QualityInspection inspection = scheduleService.getInspectionById(id);
        if (inspection == null) {
            return ResponseResult.error("质检记录不存在");
        }
        return ResponseResult.success(inspection);
    }
    
    /**
     * 提交质检记录
     */
    @PostMapping("/inspection")
    public ResponseResult<QualityInspection> submitInspection(@RequestBody QualityInspection inspection) {
        try {
            QualityInspection created = scheduleService.submitInspection(inspection);
            return ResponseResult.success(created);
        } catch (Exception e) {
            return ResponseResult.error("提交质检失败: " + e.getMessage());
        }
    }
    
    /**
     * 更新质检记录
     */
    @PutMapping("/inspection/{id}")
    public ResponseResult<String> updateInspection(@PathVariable Long id, @RequestBody QualityInspection inspection) {
        inspection.setId(id);
        int rows = scheduleService.updateInspection(inspection);
        if (rows > 0) {
            return ResponseResult.success("更新成功");
        }
        return ResponseResult.error("更新失败");
    }
    
    /**
     * 根据任务获取质检记录
     */
    @GetMapping("/inspection/by-task")
    public ResponseResult<List<QualityInspection>> getInspectionByTask(
            @RequestParam String taskType,
            @RequestParam Long taskId) {
        List<QualityInspection> list = scheduleService.getInspectionByTask(taskType, taskId);
        return ResponseResult.success(list);
    }
    
    /**
     * 获取质检统计
     */
    @GetMapping("/inspection/statistics")
    public ResponseResult<Map<String, Object>> getInspectionStatistics() {
        Map<String, Object> stats = scheduleService.getInspectionStatistics();
        return ResponseResult.success(stats);
    }
    
    // ========== 紧急插单接口 ==========
    
    /**
     * 创建紧急插单
     */
    @PostMapping("/urgent")
    public ResponseResult<UrgentOrderLog> createUrgentOrder(@RequestBody UrgentOrderLog urgentOrder) {
        try {
            UrgentOrderLog created = scheduleService.createUrgentOrder(urgentOrder);
            return ResponseResult.success(created);
        } catch (Exception e) {
            return ResponseResult.error("创建紧急插单失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取紧急插单列表
     */
    @GetMapping("/urgent/list")
    public ResponseResult<Map<String, Object>> getUrgentOrderList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String urgentLevel) {
        
        Map<String, Object> params = new HashMap<>();
        params.put("pageNum", pageNum);
        params.put("pageSize", pageSize);
        params.put("status", status);
        params.put("urgentLevel", urgentLevel);
        
        IPage<UrgentOrderLog> page = scheduleService.getUrgentOrderList(params);
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", page.getRecords());
        result.put("total", page.getTotal());
        result.put("pages", page.getPages());
        result.put("pageNum", page.getCurrent());
        result.put("pageSize", page.getSize());
        result.put("hasNextPage", page.getCurrent() < page.getPages());
        
        return ResponseResult.success(result);
    }
    
    /**
     * 审批紧急插单
     */
    @PostMapping("/urgent/{id}/approve")
    public ResponseResult<String> approveUrgentOrder(@PathVariable Long id,
                                                      @RequestParam boolean approved,
                                                      @RequestParam(required = false) String remark,
                                                      @RequestParam(defaultValue = "admin") String operator) {
        try {
            boolean result = scheduleService.approveUrgentOrder(id, approved, remark, operator);
            if (result) {
                return ResponseResult.success(approved ? "已批准，正在调整排程" : "已驳回");
            }
            return ResponseResult.error("审批失败");
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }
    
    /**
     * 执行紧急插单（调整排程）
     */
    @PostMapping("/urgent/{id}/execute")
    public ResponseResult<String> executeUrgentOrder(@PathVariable Long id,
                                                      @RequestParam(defaultValue = "admin") String operator) {
        try {
            boolean result = scheduleService.executeUrgentOrder(id, operator);
            if (result) {
                return ResponseResult.success("紧急插单已执行，相关排程已调整");
            }
            return ResponseResult.error("执行失败");
        } catch (Exception e) {
            return ResponseResult.error(e.getMessage());
        }
    }
    
    /**
     * 根据料号获取物料信息（颜色、厚度等）
     */
    @GetMapping("/material-info/{materialCode}")
    public ResponseResult<Map<String, Object>> getMaterialInfo(@PathVariable String materialCode) {
        try {
            Map<String, Object> materialInfo = scheduleService.getMaterialInfoByCode(materialCode);
            if (materialInfo != null && !materialInfo.isEmpty()) {
                return ResponseResult.success(materialInfo);
            }
            return ResponseResult.error("未找到该料号的信息");
        } catch (Exception e) {
            return ResponseResult.error("查询失败: " + e.getMessage());
        }
    }
    
    // ========== 甘特图数据接口 ==========
    
    /**
     * 获取甘特图数据
     */
    @GetMapping("/gantt")
    public ResponseResult<Object> getGanttData(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long scheduleId) {
        
        Map<String, Object> params = new HashMap<>();
        params.put("startDate", startDate);
        params.put("endDate", endDate);
        params.put("scheduleId", scheduleId);
        
        List<Map<String, Object>> ganttData = scheduleService.getGanttData(params);
        Map<String, Object> result = new HashMap<>();
        result.put("tasks", ganttData);
        return ResponseResult.success(result);
    }
    
    // ========== 涂布排程看板接口 ==========
    
    /**
     * 获取涂布任务队列（看板用）
     * @param planDate 计划日期 (yyyy-MM-dd)
     */
    @GetMapping("/coating-schedule/queue")
    public ResponseResult<List<Map<String, Object>>> getCoatingQueue(
            @RequestParam(required = false) String planDate) {
        try {
            List<Map<String, Object>> queue = scheduleService.getCoatingQueue(planDate);
            return ResponseResult.success(queue);
        } catch (Exception e) {
            return ResponseResult.error("获取涂布队列失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取涂布时间轴数据（看板用）
     * @param planDate 计划日期 (yyyy-MM-dd)
     */
    @GetMapping("/coating-schedule/timeline")
    public ResponseResult<List<Map<String, Object>>> getCoatingTimeline(
            @RequestParam(required = false) String planDate) {
        try {
            List<Map<String, Object>> timeline = scheduleService.getCoatingTimeline(planDate);
            return ResponseResult.success(timeline);
        } catch (Exception e) {
            return ResponseResult.error("获取涂布时间轴失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取涂布合并记录（看板用）
     * @param planDate 计划日期 (yyyy-MM-dd)
     */
    @GetMapping("/coating-schedule/merge-records")
    public ResponseResult<List<Map<String, Object>>> getCoatingMergeRecords(
            @RequestParam(required = false) String planDate) {
        try {
            List<Map<String, Object>> records = scheduleService.getCoatingMergeRecords(planDate);
            return ResponseResult.success(records);
        } catch (Exception e) {
            return ResponseResult.error("获取涂布合并记录失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取涂布统计数据（看板用）
     */
    @GetMapping("/coating-schedule/stats")
    public ResponseResult<Map<String, Object>> getCoatingStats() {
        try {
            Map<String, Object> stats = scheduleService.getCoatingStats();
            return ResponseResult.success(stats);
        } catch (Exception e) {
            return ResponseResult.error("获取涂布统计失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取涂布原材料锁定情况（看板用）
     */
    @GetMapping("/coating-schedule/material-locks")
    public ResponseResult<List<Map<String, Object>>> getCoatingMaterialLocks() {
        try {
            List<Map<String, Object>> locks = scheduleService.getCoatingMaterialLocks();
            return ResponseResult.success(locks);
        } catch (Exception e) {
            return ResponseResult.error("获取原材料锁定情况失败: " + e.getMessage());
        }
    }
    
    /**
     * 调整涂布任务时间
     * @param taskId 涂布任务ID
     * @param data 包含 planStartTime 和 estimatedDuration
     */
    @PostMapping("/coating-schedule/adjust-time/{taskId}")
    public ResponseResult<String> adjustCoatingTaskTime(
            @PathVariable Long taskId,
            @RequestBody Map<String, Object> data) {
        try {
            boolean result = scheduleService.adjustCoatingTaskTime(taskId, data);
            if (result) {
                return ResponseResult.success("涂布任务时间已调整");
            }
            return ResponseResult.error("调整失败");
        } catch (Exception e) {
            return ResponseResult.error("调整时间失败: " + e.getMessage());
        }
    }

    /**
     * 调整涂布任务涂布量
     */
    @PostMapping("/coating-schedule/adjust-quantity/{taskId}")
    public ResponseResult<String> adjustCoatingTaskQuantity(
            @PathVariable Long taskId,
            @RequestBody Map<String, Object> data) {
        try {
            boolean result = scheduleService.adjustCoatingTaskQuantity(taskId, data);
            if (result) {
                return ResponseResult.success("涂布量已调整");
            }
            return ResponseResult.error("调整失败");
        } catch (Exception e) {
            return ResponseResult.error("调整涂布量失败: " + e.getMessage());
        }
    }
    
    /**
     * 查看涂布任务详情（看板用）
     * @param taskId 涂布任务ID
     */
    @GetMapping("/coating-schedule/task-detail/{taskId}")
    public ResponseResult<Map<String, Object>> getCoatingTaskDetail(@PathVariable Long taskId) {
        try {
            Map<String, Object> detail = scheduleService.getCoatingTaskDetail(taskId);
            if (detail != null && !detail.isEmpty()) {
                return ResponseResult.success(detail);
            }
            return ResponseResult.error("涂布任务不存在");
        } catch (Exception e) {
            return ResponseResult.error("获取任务详情失败: " + e.getMessage());
        }
    }
    
    // ========== 待涂布订单池接口 ==========
    
    /**
     * 获取待涂布订单池列表
     */
    @GetMapping("/coating-schedule/pending-pool")
    public ResponseResult<Map<String, Object>> getPendingCoatingPool(
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String status) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("materialCode", materialCode);
            params.put("status", status);
            Map<String, Object> pool = scheduleService.getPendingCoatingPool(params);
            return ResponseResult.success(pool);
        } catch (Exception e) {
            return ResponseResult.error("获取待涂布池失败: " + e.getMessage());
        }
    }
    
    /**
     * 按料号分组获取待涂布订单
     */
    @GetMapping("/coating-schedule/pending-by-material")
    public ResponseResult<List<Map<String, Object>>> getPendingCoatingByMaterial() {
        try {
            List<Map<String, Object>> grouped = scheduleService.getPendingCoatingByMaterial();
            return ResponseResult.success(grouped);
        } catch (Exception e) {
            return ResponseResult.error("获取分组数据失败: " + e.getMessage());
        }
    }
    
    /**
     * 添加订单到待涂布池
     */
    @PostMapping("/coating-schedule/add-to-pool")
    public ResponseResult<String> addToPendingCoatingPool(@RequestBody Map<String, Object> data) {
        try {
            scheduleService.addToPendingCoatingPool(data);
            return ResponseResult.success("添加成功");
        } catch (Exception e) {
            return ResponseResult.error("添加失败: " + e.getMessage());
        }
    }
    
    /**
     * 从待涂布池移除订单
     */
    @PostMapping("/coating-schedule/remove-from-pool/{poolId}")
    public ResponseResult<String> removeFromPendingCoatingPool(
            @PathVariable Long poolId,
            @RequestParam(defaultValue = "admin") String operator) {
        try {
            scheduleService.removeFromPendingCoatingPool(poolId, operator);
            return ResponseResult.success("移除成功");
        } catch (Exception e) {
            return ResponseResult.error("移除失败: " + e.getMessage());
        }
    }
    
    /**
     * 生成涂布排程任务
     */
    @PostMapping("/coating-schedule/generate-tasks")
    public ResponseResult<Map<String, Object>> generateCoatingTasks(@RequestBody Map<String, Object> data) {
        try {
            Map<String, Object> result = scheduleService.generateCoatingTasks(data);
            return ResponseResult.success(result);
        } catch (Exception e) {
            return ResponseResult.error("生成任务失败: " + e.getMessage());
        }
    }
    
}
