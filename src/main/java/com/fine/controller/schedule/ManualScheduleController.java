package com.fine.controller.schedule;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Utils.ResponseResult;
import com.fine.modle.schedule.ManualSchedule;
import com.fine.model.production.readiness.ReadinessStatus;
import com.fine.service.production.MaterialReadinessService;
import com.fine.service.schedule.ManualScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 手动排程控制器
 */
@RestController
@RequestMapping("/schedule/manual")
public class ManualScheduleController {
    
    @Autowired
    private ManualScheduleService manualScheduleService;

    @Autowired
    private MaterialReadinessService materialReadinessService;

    @Value("${mes.readiness.allow-ready-by-eta:false}")
    private boolean allowReadyByEta;
    
    /**
     * 获取待排程订单列表
     */
    @GetMapping("/pending-orders")
    public ResponseResult<List<Map<String, Object>>> getPendingOrders(
            @RequestParam(defaultValue = "false") boolean includeCompleted) {
        List<Map<String, Object>> orders = manualScheduleService.getPendingOrders(includeCompleted);
        return ResponseResult.success(orders);
    }

    /**
     * 分页获取待排程订单列表（MyBatis-Plus）
     */
    @GetMapping("/pending-orders/page")
    public ResponseResult<IPage<Map<String, Object>>> getPendingOrdersPage(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(defaultValue = "false") boolean includeCompleted,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String sortProp,
            @RequestParam(required = false) String sortOrder) {
        // 固定走后端分页，排序以SQL为准（优先级降序）
        IPage<Map<String, Object>> page = manualScheduleService.getPendingOrdersPage(current, size, includeCompleted, orderNo);
        return ResponseResult.success(page);
    }

    private Comparator<Map<String, Object>> buildPendingComparator(String sortProp, String sortOrder) {
        final boolean asc = "ascending".equalsIgnoreCase(sortOrder);
        final int factor = asc ? 1 : -1;

        return (a, b) -> {
            Comparable av = pendingSortValue(a, sortProp);
            Comparable bv = pendingSortValue(b, sortProp);
            if (av == null && bv == null) return 0;
            if (av == null) return -1 * factor;
            if (bv == null) return 1 * factor;
            int cmp = av.compareTo(bv);
            return cmp * factor;
        };
    }

    private Comparable pendingSortValue(Map<String, Object> row, String key) {
        if (row == null || key == null) {
            return "";
        }

        if ("spec".equals(key)) {
            double t = toDouble(row.get("thickness"));
            double w = toDouble(row.get("width"));
            double l = toDouble(row.get("length"));
            return t * 100000000 + w * 10000 + l;
        }
        if ("coating_date".equals(key) || "rewinding_date".equals(key) || "packaging_date".equals(key)) {
            return toTime(row.get(key));
        }
        if ("is_completed".equals(key)) {
            double orderQty = toDouble(row.get("order_qty"));
            double coating = toDouble(row.get("coating_report_qty"));
            double rewinding = toDouble(row.get("rewinding_report_qty"));
            double slitting = toDouble(row.get("slitting_report_qty"));
            int coatingStop = (int) toDouble(row.get("coating_stop_next"));
            int rewindingStop = (int) toDouble(row.get("rewinding_stop_next"));
            int slittingStop = (int) toDouble(row.get("slitting_stop_next"));
            double completed;
            if (slittingStop == 1) {
                completed = slitting;
            } else if (rewindingStop == 1) {
                completed = rewinding;
            } else if (coatingStop == 1) {
                completed = coating;
            } else if (slitting > 0) {
                completed = slitting;
            } else {
                completed = 0;
            }
            return (orderQty > 0 && completed >= orderQty) ? 1 : 0;
        }
        if ("schedule_qty".equals(key)) {
            return toDouble(row.get("order_qty"));
        }
        if ("owe_area".equals(key)) {
            double w = toDouble(row.get("width"));
            double l = toDouble(row.get("length"));
            double r = toDouble(row.get("remaining_qty"));
            return (w / 1000.0) * l * r;
        }
        if ("completed_qty".equals(key)) {
            if (row.get("completed_qty") != null) {
                return toDouble(row.get("completed_qty"));
            }
            double coating = toDouble(row.get("coating_report_qty"));
            double rewinding = toDouble(row.get("rewinding_report_qty"));
            double slitting = toDouble(row.get("slitting_report_qty"));
            int coatingStop = (int) toDouble(row.get("coating_stop_next"));
            int rewindingStop = (int) toDouble(row.get("rewinding_stop_next"));
            int slittingStop = (int) toDouble(row.get("slitting_stop_next"));
            if (slittingStop == 1) return slitting;
            if (rewindingStop == 1) return rewinding;
            if (coatingStop == 1) return coating;
            if (slitting > 0) return slitting;
            return 0d;
        }
        if ("priority_score".equals(key) || "order_qty".equals(key) || "remaining_qty".equals(key)) {
            return toDouble(row.get(key));
        }

        Object v = row.get(key);
        return v == null ? "" : String.valueOf(v).toUpperCase();
    }

    private double toDouble(Object value) {
        if (value == null) return 0D;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return 0D;
        }
    }

    private long toTime(Object value) {
        if (value == null) return 0L;
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return 0L;
        try {
            if (text.length() >= 19) {
                return LocalDateTime.parse(text.substring(0, 19), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            }
            if (text.length() >= 16) {
                return LocalDateTime.parse(text.substring(0, 16), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            }
            return LocalDate.parse(text.substring(0, 10), DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            return 0L;
        }
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
     * 分页获取已完成涂布待复卷订单列表
     */
    @GetMapping("/coating-completed-orders/page")
    public ResponseResult<IPage<Map<String, Object>>> getCoatingCompletedOrdersPage(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        IPage<Map<String, Object>> page = manualScheduleService.getCoatingCompletedOrdersPage(current, size);
        return ResponseResult.success(page);
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
        java.math.BigDecimal plannedArea = null;
        Object plannedAreaObj = params.get("plannedArea");
        if (plannedAreaObj instanceof Number) {
            plannedArea = java.math.BigDecimal.valueOf(((Number) plannedAreaObj).doubleValue());
        } else if (plannedAreaObj instanceof String && !((String) plannedAreaObj).trim().isEmpty()) {
            plannedArea = new java.math.BigDecimal((String) plannedAreaObj);
        }
        
        Map<String, Object> result = manualScheduleService.calculateCoatingRequirement(orderNo, materialCode, plannedArea);
        return ResponseResult.success(result);
    }

    /**
     * 保存涂布分配明细
     */
    @PostMapping("/coating-allocation/save")
    public ResponseResult<Boolean> saveCoatingAllocation(@RequestBody Map<String, Object> params) {
        Object scheduleIdObj = params.get("scheduleId");
        Long scheduleId = null;
        if (scheduleIdObj instanceof Number) {
            scheduleId = ((Number) scheduleIdObj).longValue();
        } else if (scheduleIdObj instanceof String && !((String) scheduleIdObj).trim().isEmpty()) {
            scheduleId = Long.parseLong((String) scheduleIdObj);
        }
        if (scheduleId == null) {
            return ResponseResult.error("scheduleId 不能为空");
        }
        List<Map<String, Object>> details = (List<Map<String, Object>>) params.get("details");
        boolean ok = manualScheduleService.saveCoatingAllocationDetails(scheduleId, details);
        return ResponseResult.success(ok);
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
     * 分页获取涂布排程列表
     */
    @GetMapping("/coating-schedules/page")
    public ResponseResult<IPage<Map<String, Object>>> getCoatingSchedulesPage(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String planDateStart,
            @RequestParam(required = false) String planDateEnd,
            @RequestParam(required = false) String status) {

        boolean noFilter = (planDateStart == null || planDateStart.trim().isEmpty())
                && (planDateEnd == null || planDateEnd.trim().isEmpty())
                && (status == null || status.trim().isEmpty());

        IPage<Map<String, Object>> page;
        if (noFilter) {
            page = manualScheduleService.getCoatingSchedulesPage(current, size);
        } else {
            LocalDate startDate = parseDate(planDateStart);
            LocalDate endDate = parseDate(planDateEnd);
            String statusFilter = status == null ? null : status.trim();

            List<Map<String, Object>> all = manualScheduleService.getCoatingSchedules();
            List<Map<String, Object>> filtered = all.stream().filter(row -> {
                if (statusFilter != null && !statusFilter.isEmpty()) {
                    Object st = row.get("status");
                    if (!matchCoatingStatusFilter(st == null ? null : String.valueOf(st), statusFilter)) {
                        return false;
                    }
                }

                LocalDate planDate = parsePlanDate(row);
                if (startDate != null && (planDate == null || planDate.isBefore(startDate))) {
                    return false;
                }
                if (endDate != null && (planDate == null || planDate.isAfter(endDate))) {
                    return false;
                }
                return true;
            }).collect(Collectors.toList());

            long total = filtered.size();
            long from = Math.max(0, (current - 1) * size);
            long to = Math.min(total, from + size);
            List<Map<String, Object>> records = from >= total
                    ? java.util.Collections.emptyList()
                    : filtered.subList((int) from, (int) to);

            Page<Map<String, Object>> p = new Page<>(current, size);
            p.setTotal(total);
            p.setRecords(records);
            page = p;
        }
        return ResponseResult.success(page);
    }

    private boolean matchCoatingStatusFilter(String rowStatus, String statusFilter) {
        if (statusFilter == null || statusFilter.trim().isEmpty()) {
            return true;
        }
        String filter = statusFilter.trim().toUpperCase();
        String status = rowStatus == null ? "" : rowStatus.trim().toUpperCase();

        if ("SCHEDULED".equals(filter) || "UNSCHEDULED".equals(filter)) {
            return "PENDING".equals(status)
                    || "COATING_SCHEDULED".equals(status)
                    || "REWINDING_SCHEDULED".equals(status)
                    || "CONFIRMED".equals(status);
        }
        if ("IN_PROGRESS".equals(filter)) {
            return "RUNNING".equals(status) || "IN_PROGRESS".equals(status);
        }
        if ("COMPLETED".equals(filter)) {
            return "DONE".equals(status) || "COMPLETED".equals(status);
        }
        if ("CANCELLED".equals(filter)) {
            return "TERMINATED".equals(status) || "CANCELLED".equals(status);
        }

        return filter.equals(status);
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate parsePlanDate(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        Object planObj = row.get("coating_schedule_date");
        if (planObj == null) {
            planObj = row.get("coating_date");
        }
        if (planObj == null) {
            return null;
        }
        String text = String.valueOf(planObj).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            if (text.length() >= 19) {
                return LocalDateTime.parse(text.substring(0, 19), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).toLocalDate();
            }
            if (text.length() >= 16) {
                return LocalDateTime.parse(text.substring(0, 16), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")).toLocalDate();
            }
            return LocalDate.parse(text.substring(0, 10), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取分切已排列表
     */
    @GetMapping("/slitting-schedules")
    public ResponseResult<List<Map<String, Object>>> getSlittingSchedules() {
        List<Map<String, Object>> list = manualScheduleService.getSlittingSchedules();
        return ResponseResult.success(list);
    }

    /**
     * 分页获取分切已排列表
     */
    @GetMapping("/slitting-schedules/page")
    public ResponseResult<IPage<Map<String, Object>>> getSlittingSchedulesPage(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String orderNo) {
        IPage<Map<String, Object>> page = manualScheduleService.getSlittingSchedulesPage(current, size, orderNo);
        return ResponseResult.success(page);
    }

    /**
     * 更新分切/包装日期
     */
    @PostMapping("/update-slitting")
    public ResponseResult<Boolean> updateSlitting(@RequestBody Map<String, Object> params) {
        try {
            Object scheduleIdObj = params.get("scheduleId");
            Long scheduleId = null;
            if (scheduleIdObj instanceof Number) {
                scheduleId = ((Number) scheduleIdObj).longValue();
            } else if (scheduleIdObj instanceof String) {
                scheduleId = Long.parseLong((String) scheduleIdObj);
            }
            if (scheduleId == null) {
                return ResponseResult.error("scheduleId 不能为空");
            }
            String packagingDate = (String) params.get("packagingDate");
            String slittingEquipment = params.get("slittingEquipment") == null ? null : String.valueOf(params.get("slittingEquipment"));
            boolean ok = manualScheduleService.updateSlittingInfo(scheduleId, packagingDate, slittingEquipment);
            return ResponseResult.success(ok);
        } catch (Exception e) {
            return ResponseResult.error("更新分切/包装日期失败: " + e.getMessage());
        }
    }

    /**
     * 新增工序报工
     */
    @PostMapping("/report-work")
    public ResponseResult<Map<String, Object>> reportWork(@RequestBody Map<String, Object> params) {
        try {
            Object scheduleIdObj = params.get("scheduleId");
            Long scheduleId = null;
            if (scheduleIdObj instanceof Number) {
                scheduleId = ((Number) scheduleIdObj).longValue();
            } else if (scheduleIdObj instanceof String) {
                scheduleId = Long.parseLong((String) scheduleIdObj);
            }

            Object orderDetailIdObj = params.get("orderDetailId");
            Long orderDetailId = null;
            if (orderDetailIdObj instanceof Number) {
                orderDetailId = ((Number) orderDetailIdObj).longValue();
            } else if (orderDetailIdObj instanceof String && !((String) orderDetailIdObj).trim().isEmpty()) {
                orderDetailId = Long.parseLong((String) orderDetailIdObj);
            }

            if (scheduleId == null && orderDetailId == null) {
                return ResponseResult.error("scheduleId 和 orderDetailId 不能同时为空");
            }

            String processType = params.get("processType") == null ? null : String.valueOf(params.get("processType"));
            String startTime = params.get("startTime") == null ? null : String.valueOf(params.get("startTime"));
            String endTime = params.get("endTime") == null ? null : String.valueOf(params.get("endTime"));
            String operator = params.get("operator") == null ? null : String.valueOf(params.get("operator"));
            String remark = params.get("remark") == null ? null : String.valueOf(params.get("remark"));
                Boolean proceedNextProcess = params.get("proceedNextProcess") == null
                    ? Boolean.TRUE
                    : Boolean.valueOf(String.valueOf(params.get("proceedNextProcess")));

            java.math.BigDecimal producedQty = null;
            Object producedQtyObj = params.get("producedQty");
            if (producedQtyObj instanceof Number) {
                producedQty = java.math.BigDecimal.valueOf(((Number) producedQtyObj).doubleValue());
            } else if (producedQtyObj instanceof String) {
                producedQty = new java.math.BigDecimal((String) producedQtyObj);
            }

            List<Map<String, Object>> producedRolls = null;
            Object producedRollsObj = params.get("producedRolls");
            if (producedRollsObj instanceof List) {
                producedRolls = (List<Map<String, Object>>) producedRollsObj;
            }

            List<Map<String, Object>> materialIssues = null;
            Object materialIssuesObj = params.get("materialIssues");
            if (materialIssuesObj instanceof List) {
                materialIssues = (List<Map<String, Object>>) materialIssuesObj;
            }

            boolean ok = manualScheduleService.reportProcessWork(
                    scheduleId,
                    orderDetailId,
                    processType,
                    startTime,
                    endTime,
                    producedQty,
                        proceedNextProcess,
                    producedRolls,
                    materialIssues,
                    operator,
                    remark
            );

            Long resolvedScheduleId = scheduleId;
            if (resolvedScheduleId == null && orderDetailId != null) {
                resolvedScheduleId = manualScheduleService.getLatestScheduleIdByOrderDetailId(orderDetailId);
            }

            java.util.Map<String, Object> result = new java.util.HashMap<>();
            result.put("success", ok);
            result.put("scheduleId", resolvedScheduleId);
            result.put("orderDetailId", orderDetailId);
            result.put("processType", processType);
            return ResponseResult.success(result);
        } catch (Exception e) {
            return ResponseResult.error("提交报工失败: " + e.getMessage());
        }
    }

    /**
     * 查询工序报工明细
     */
    @GetMapping("/report-work/list")
    public ResponseResult<List<Map<String, Object>>> getReportWorkList(@RequestParam Long scheduleId,
                                                                       @RequestParam String processType) {
        try {
            return ResponseResult.success(manualScheduleService.getProcessWorkReports(scheduleId, processType));
        } catch (Exception e) {
            return ResponseResult.error("查询报工明细失败: " + e.getMessage());
        }
    }

    /**
     * 更新工序报工记录
     */
    @PostMapping("/report-work/update")
    public ResponseResult<Boolean> updateReportWork(@RequestBody Map<String, Object> params) {
        try {
            Object reportIdObj = params.get("reportId");
            Long reportId = null;
            if (reportIdObj instanceof Number) {
                reportId = ((Number) reportIdObj).longValue();
            } else if (reportIdObj instanceof String && !((String) reportIdObj).trim().isEmpty()) {
                reportId = Long.parseLong((String) reportIdObj);
            }
            if (reportId == null) {
                return ResponseResult.error("reportId 不能为空");
            }

            String startTime = params.get("startTime") == null ? null : String.valueOf(params.get("startTime"));
            String endTime = params.get("endTime") == null ? null : String.valueOf(params.get("endTime"));
            String operator = params.get("operator") == null ? null : String.valueOf(params.get("operator"));
            String remark = params.get("remark") == null ? null : String.valueOf(params.get("remark"));
            Boolean proceedNextProcess = params.get("proceedNextProcess") == null
                    ? Boolean.TRUE
                    : Boolean.valueOf(String.valueOf(params.get("proceedNextProcess")));

            java.math.BigDecimal producedQty = null;
            Object producedQtyObj = params.get("producedQty");
            if (producedQtyObj instanceof Number) {
                producedQty = java.math.BigDecimal.valueOf(((Number) producedQtyObj).doubleValue());
            } else if (producedQtyObj instanceof String) {
                producedQty = new java.math.BigDecimal((String) producedQtyObj);
            }

            boolean ok = manualScheduleService.updateProcessWorkReport(
                    reportId,
                    startTime,
                    endTime,
                    producedQty,
                    proceedNextProcess,
                    operator,
                    remark
            );
            return ResponseResult.success(ok);
        } catch (Exception e) {
            return ResponseResult.error("更新报工失败: " + e.getMessage());
        }
    }

    /**
     * 删除工序报工记录
     */
    @PostMapping("/report-work/delete")
    public ResponseResult<Boolean> deleteReportWork(@RequestBody Map<String, Object> params) {
        try {
            Object reportIdObj = params.get("reportId");
            Long reportId = null;
            if (reportIdObj instanceof Number) {
                reportId = ((Number) reportIdObj).longValue();
            } else if (reportIdObj instanceof String && !((String) reportIdObj).trim().isEmpty()) {
                reportId = Long.parseLong((String) reportIdObj);
            }
            if (reportId == null) {
                return ResponseResult.error("reportId 不能为空");
            }

            boolean ok = manualScheduleService.deleteProcessWorkReport(reportId);
            return ResponseResult.success(ok);
        } catch (Exception e) {
            return ResponseResult.error("删除报工失败: " + e.getMessage());
        }
    }

    /**
     * 查询订单锁定的涂布母卷明细
     */
    @GetMapping("/report-work/coating-roll-locks")
    public ResponseResult<List<Map<String, Object>>> getCoatingRollLocks(@RequestParam String orderNo) {
        try {
            return ResponseResult.success(manualScheduleService.getCoatingRollLocks(orderNo));
        } catch (Exception e) {
            return ResponseResult.error("查询订单母卷锁定明细失败: " + e.getMessage());
        }
    }

    /**
     * 查询工序领料明细
     */
    @GetMapping("/report-work/material-issues")
    public ResponseResult<List<Map<String, Object>>> getProcessMaterialIssues(@RequestParam Long scheduleId,
                                                                               @RequestParam String processType) {
        try {
            return ResponseResult.success(manualScheduleService.getProcessMaterialIssues(scheduleId, processType));
        } catch (Exception e) {
            return ResponseResult.error("查询工序领料明细失败: " + e.getMessage());
        }
    }

    /**
     * 领料登记（可不依赖已有报工）
     */
    @PostMapping("/report-work/material-issue")
    public ResponseResult<Boolean> issueMaterial(@RequestBody Map<String, Object> params) {
        try {
            Object scheduleIdObj = params.get("scheduleId");
            Long scheduleId = null;
            if (scheduleIdObj instanceof Number) {
                scheduleId = ((Number) scheduleIdObj).longValue();
            } else if (scheduleIdObj instanceof String && !((String) scheduleIdObj).trim().isEmpty()) {
                scheduleId = Long.parseLong((String) scheduleIdObj);
            }

            Object orderDetailIdObj = params.get("orderDetailId");
            Long orderDetailId = null;
            if (orderDetailIdObj instanceof Number) {
                orderDetailId = ((Number) orderDetailIdObj).longValue();
            } else if (orderDetailIdObj instanceof String && !((String) orderDetailIdObj).trim().isEmpty()) {
                orderDetailId = Long.parseLong((String) orderDetailIdObj);
            }

            if (scheduleId == null && orderDetailId == null) {
                return ResponseResult.error("scheduleId 和 orderDetailId 不能同时为空");
            }

            String processType = params.get("processType") == null ? null : String.valueOf(params.get("processType"));
            String operator = params.get("operator") == null ? null : String.valueOf(params.get("operator"));
            String remark = params.get("remark") == null ? null : String.valueOf(params.get("remark"));

            List<Map<String, Object>> materialIssues = null;
            Object materialIssuesObj = params.get("materialIssues");
            if (materialIssuesObj instanceof List) {
                materialIssues = (List<Map<String, Object>>) materialIssuesObj;
            }

            boolean ok = manualScheduleService.issueProcessMaterial(
                    scheduleId,
                    orderDetailId,
                    processType,
                    materialIssues,
                    operator,
                    remark
            );
            return ResponseResult.success(ok);
        } catch (Exception e) {
            return ResponseResult.error("领料登记失败: " + e.getMessage());
        }
    }

    /**
     * 生成下一个涂布母卷号
     */
    @GetMapping("/report-work/next-coating-roll-code")
    public ResponseResult<String> getNextCoatingRollCode(@RequestParam Long scheduleId,
                                                         @RequestParam(required = false) String workGroup,
                                                         @RequestParam(required = false) String productionDateTime) {
        try {
            String code = manualScheduleService.generateNextCoatingRollCode(scheduleId, workGroup, productionDateTime);
            return ResponseResult.success(code);
        } catch (Exception e) {
            return ResponseResult.error("生成母卷号失败: " + e.getMessage());
        }
    }

    /**
     * 根据订单明细ID查询最新排程ID
     */
    @GetMapping("/latest-schedule-id")
    public ResponseResult<Long> getLatestScheduleId(@RequestParam Long orderDetailId) {
        try {
            return ResponseResult.success(manualScheduleService.getLatestScheduleIdByOrderDetailId(orderDetailId));
        } catch (Exception e) {
            return ResponseResult.error("查询最新排程ID失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取锁定库存列表
     */
    @GetMapping("/locked-stocks")
    public ResponseResult<List<Map<String, Object>>> getLockedStocks() {
        return ResponseResult.success(manualScheduleService.getLockedStocks());
    }

    /**
     * 获取复卷已排列表
     */
    @GetMapping("/rewinding-schedules")
    public ResponseResult<List<Map<String, Object>>> getRewindingSchedules() {
        List<Map<String, Object>> list = manualScheduleService.getRewindingSchedules();
        return ResponseResult.success(list);
    }

    /**
     * 分页获取复卷已排列表
     */
    @GetMapping("/rewinding-schedules/page")
    public ResponseResult<IPage<Map<String, Object>>> getRewindingSchedulesPage(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size) {
        IPage<Map<String, Object>> page = manualScheduleService.getRewindingSchedulesPage(current, size);
        return ResponseResult.success(page);
    }
    
    /**
     * 创建手动排程
     */
    @PostMapping("/create")
    public ResponseResult<Long> createSchedule(@RequestBody ManualSchedule schedule) {
        Long scheduleId = manualScheduleService.createSchedule(schedule);
        return ResponseResult.success(scheduleId);
    }
    
    /**
     * 创建复卷排程
     */
    @PostMapping("/create-rewinding")
    public ResponseResult<Long> createRewindingSchedule(@RequestBody Map<String, Object> params) {
        try {
            Object scheduleIdObj = params.get("scheduleId");
            Long scheduleId = null;
            if (scheduleIdObj instanceof Number) {
                scheduleId = ((Number) scheduleIdObj).longValue();
            } else if (scheduleIdObj instanceof String) {
                scheduleId = Long.parseLong((String) scheduleIdObj);
            }
            if (scheduleId == null) {
                return ResponseResult.<Long>error("scheduleId 不能为空");
            }
            List<Map<String, Object>> stockAllocations = (List<Map<String, Object>>) params.get("stockAllocations");
            if (stockAllocations != null) {
                Long rewindingId = manualScheduleService.createRewindingSchedule(scheduleId, stockAllocations);
                return ResponseResult.success(rewindingId);
            }

            Object rewindingAreaObj = params.get("rewindingArea");
            Double rewindingArea = null;
            if (rewindingAreaObj instanceof Number) {
                rewindingArea = ((Number) rewindingAreaObj).doubleValue();
            } else if (rewindingAreaObj instanceof String) {
                rewindingArea = Double.parseDouble((String) rewindingAreaObj);
            }
            String rewindingDate = (String) params.get("rewindingDate");
            String rewindingEquipment = (String) params.get("rewindingEquipment");
            Object rewindingWidthObj = params.get("rewindingWidth");
            Double rewindingWidth = null;
            if (rewindingWidthObj instanceof Number) {
                rewindingWidth = ((Number) rewindingWidthObj).doubleValue();
            } else if (rewindingWidthObj instanceof String && !((String) rewindingWidthObj).trim().isEmpty()) {
                rewindingWidth = Double.parseDouble((String) rewindingWidthObj);
            }

            manualScheduleService.updateRewindingInfo(scheduleId, rewindingArea, rewindingDate, rewindingEquipment, rewindingWidth);
            return ResponseResult.success(scheduleId);
        } catch (Exception e) {
            return ResponseResult.<Long>error("创建复卷排程失败: " + e.getMessage());
        }
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
            Object coatingWidthObj = params.get("coatingWidth");
            Object coatingLengthObj = params.get("coatingLength");
            
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

            Double coatingWidth = null;
            if (coatingWidthObj instanceof Number) {
                coatingWidth = ((Number) coatingWidthObj).doubleValue();
            } else if (coatingWidthObj instanceof String && !((String) coatingWidthObj).trim().isEmpty()) {
                coatingWidth = Double.parseDouble((String) coatingWidthObj);
            }

            Double coatingLength = null;
            if (coatingLengthObj instanceof Number) {
                coatingLength = ((Number) coatingLengthObj).doubleValue();
            } else if (coatingLengthObj instanceof String && !((String) coatingLengthObj).trim().isEmpty()) {
                coatingLength = Double.parseDouble((String) coatingLengthObj);
            }
            
            Long coatingId = manualScheduleService.createCoatingSchedule(
                    scheduleId,
                    coatingArea,
                    coatingDate,
                    null,
                    null,
                        equipmentId,
                        coatingWidth,
                        coatingLength
            );
            return ResponseResult.success(coatingId);
        } catch (Exception e) {
            return ResponseResult.error("创建涂布排程失败: " + e.getMessage());
        }
    }

    /**
     * 预估涂布机台可用时间与占用区间
     */
    @PostMapping("/coating-availability")
    public ResponseResult<Map<String, Object>> previewCoatingAvailability(@RequestBody Map<String, Object> params) {
        try {
            Object scheduleIdObj = params.get("scheduleId");
            Object equipmentIdObj = params.get("equipmentId");
            Object coatingLengthObj = params.get("coatingLength");

            Long scheduleId = null;
            if (scheduleIdObj instanceof Number) {
                scheduleId = ((Number) scheduleIdObj).longValue();
            } else if (scheduleIdObj instanceof String && !((String) scheduleIdObj).trim().isEmpty()) {
                scheduleId = Long.parseLong((String) scheduleIdObj);
            }
            String equipmentId = null;
            if (equipmentIdObj instanceof Number) {
                equipmentId = ((Number) equipmentIdObj).toString();
            } else if (equipmentIdObj instanceof String && !((String) equipmentIdObj).trim().isEmpty()) {
                equipmentId = (String) equipmentIdObj;
            }
            Double coatingLength = null;
            if (coatingLengthObj instanceof Number) {
                coatingLength = ((Number) coatingLengthObj).doubleValue();
            } else if (coatingLengthObj instanceof String && !((String) coatingLengthObj).trim().isEmpty()) {
                coatingLength = Double.parseDouble((String) coatingLengthObj);
            }
            String coatingDate = params.get("coatingDate") == null ? null : String.valueOf(params.get("coatingDate"));

            Map<String, Object> result = manualScheduleService.previewCoatingOccupation(scheduleId, equipmentId, coatingDate, coatingLength);
            return ResponseResult.success(result);
        } catch (Exception e) {
            return ResponseResult.error("获取涂布机台可用时间失败: " + e.getMessage());
        }
    }

    /**
     * 预估复卷机台可用时间与占用区间
     */
    @PostMapping("/rewinding-availability")
    public ResponseResult<Map<String, Object>> previewRewindingAvailability(@RequestBody Map<String, Object> params) {
        try {
            Object scheduleIdObj = params.get("scheduleId");
            Object rewindingEquipmentObj = params.get("rewindingEquipment");

            Long scheduleId = null;
            if (scheduleIdObj instanceof Number) {
                scheduleId = ((Number) scheduleIdObj).longValue();
            } else if (scheduleIdObj instanceof String && !((String) scheduleIdObj).trim().isEmpty()) {
                scheduleId = Long.parseLong((String) scheduleIdObj);
            }

            String rewindingEquipment = rewindingEquipmentObj == null ? null : String.valueOf(rewindingEquipmentObj);
            String rewindingDate = params.get("rewindingDate") == null ? null : String.valueOf(params.get("rewindingDate"));

            Map<String, Object> result = manualScheduleService.previewRewindingOccupation(scheduleId, rewindingEquipment, rewindingDate);
            return ResponseResult.success(result);
        } catch (Exception e) {
            return ResponseResult.error("获取复卷机台可用时间失败: " + e.getMessage());
        }
    }

    /**
     * 预估分切机台可用时间与占用区间
     */
    @PostMapping("/slitting-availability")
    public ResponseResult<Map<String, Object>> previewSlittingAvailability(@RequestBody Map<String, Object> params) {
        try {
            Object scheduleIdObj = params.get("scheduleId");
            Object slittingEquipmentObj = params.get("slittingEquipment");

            Long scheduleId = null;
            if (scheduleIdObj instanceof Number) {
                scheduleId = ((Number) scheduleIdObj).longValue();
            } else if (scheduleIdObj instanceof String && !((String) scheduleIdObj).trim().isEmpty()) {
                scheduleId = Long.parseLong((String) scheduleIdObj);
            }

            String slittingEquipment = slittingEquipmentObj == null ? null : String.valueOf(slittingEquipmentObj);
            String packagingDate = params.get("packagingDate") == null ? null : String.valueOf(params.get("packagingDate"));

            Map<String, Object> result = manualScheduleService.previewSlittingOccupation(scheduleId, slittingEquipment, packagingDate);
            return ResponseResult.success(result);
        } catch (Exception e) {
            return ResponseResult.error("获取分切机台可用时间失败: " + e.getMessage());
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

        Long scheduleId = null;
        Object scheduleIdObj = params.get("scheduleId");
        if (scheduleIdObj instanceof Number) {
            scheduleId = ((Number) scheduleIdObj).longValue();
        } else if (scheduleIdObj instanceof String) {
            scheduleId = Long.parseLong((String) scheduleIdObj);
        }
        Long orderDetailId = null;
        Object orderDetailIdObj = params.get("orderDetailId");
        if (orderDetailIdObj instanceof Number) {
            orderDetailId = ((Number) orderDetailIdObj).longValue();
        } else if (orderDetailIdObj instanceof String) {
            orderDetailId = Long.parseLong((String) orderDetailIdObj);
        }
        String coatingDate = (String) params.get("coatingDate");
        String rewindingDate = (String) params.get("rewindingDate");
        String packagingDate = (String) params.get("packagingDate");

        // 齐套门禁：缺料不可确认；预计齐套按策略控制
        if (orderDetailId != null) {
            Map<String, Object> readiness = materialReadinessService.getOrderItemReadiness(orderDetailId);
            String readinessCode = readiness == null ? null : String.valueOf(readiness.get("statusCode"));
            if (ReadinessStatus.SHORTAGE.equals(readinessCode)) {
                return new ResponseResult<>(40001, "当前订单明细存在原料缺口，请先完成采购/到料后再确认排程", false);
            }
            if (ReadinessStatus.READY_BY_ETA.equals(readinessCode) && !allowReadyByEta) {
                return new ResponseResult<>(40001, "当前订单明细为预计齐套，尚未达到放行策略。请确认到料或开启READY_BY_ETA放行", false);
            }
        }

        boolean success = manualScheduleService.confirmSchedule(orderNo, materialCode, scheduleQty, scheduleId, orderDetailId,
                coatingDate, rewindingDate, packagingDate);
        return ResponseResult.success(success);
    }

    /**
     * 清空排程并重排（按订单明细）
     */
    @PostMapping("/reset-by-order-detail")
    public ResponseResult<Boolean> resetByOrderDetail(@RequestBody Map<String, Object> params) {
        try {
            Object orderDetailIdObj = params.get("orderDetailId");
            Long orderDetailId = null;
            if (orderDetailIdObj instanceof Number) {
                orderDetailId = ((Number) orderDetailIdObj).longValue();
            } else if (orderDetailIdObj instanceof String) {
                orderDetailId = Long.parseLong((String) orderDetailIdObj);
            }
            if (orderDetailId == null) {
                return ResponseResult.error("orderDetailId 不能为空");
            }
            String reason = params.get("reason") == null ? null : String.valueOf(params.get("reason"));
            String operator = params.get("operator") == null ? null : String.valueOf(params.get("operator"));
            boolean ok = manualScheduleService.resetScheduleByOrderDetailId(orderDetailId, reason, operator);
            return ResponseResult.success(ok);
        } catch (Exception e) {
            return ResponseResult.error("清空重排失败: " + e.getMessage());
        }
    }

    /**
     * 急单抢料：优先使用未锁定库存，不足时释放低优先级订单锁定并转给急单
     */
    @PostMapping("/urgent-lock")
    public ResponseResult<Map<String, Object>> urgentLock(@RequestBody Map<String, Object> params) {
        try {
            String orderNo = params.get("orderNo") == null ? null : String.valueOf(params.get("orderNo")).trim();
            String materialCode = params.get("materialCode") == null ? null : String.valueOf(params.get("materialCode")).trim();
            Object requiredAreaObj = params.get("requiredArea");
            String operator = params.get("operator") == null ? "system" : String.valueOf(params.get("operator"));

            BigDecimal requiredArea = null;
            if (requiredAreaObj instanceof Number) {
                requiredArea = BigDecimal.valueOf(((Number) requiredAreaObj).doubleValue());
            } else if (requiredAreaObj instanceof String && !((String) requiredAreaObj).trim().isEmpty()) {
                requiredArea = new BigDecimal(((String) requiredAreaObj).trim());
            }

            Map<String, Object> result = manualScheduleService.allocateUrgentOrderMaterial(orderNo, materialCode, requiredArea, operator);
            return ResponseResult.success(result);
        } catch (Exception e) {
            return ResponseResult.error("急单抢料失败: " + e.getMessage());
        }
    }

    /**
     * 获取急单抢占保护参数
     */
    @GetMapping("/urgent-preempt-config")
    public ResponseResult<Map<String, Object>> getUrgentPreemptConfig() {
        try {
            return ResponseResult.success(manualScheduleService.getUrgentPreemptConfig());
        } catch (Exception e) {
            return ResponseResult.error("获取急单抢占参数失败: " + e.getMessage());
        }
    }

    /**
     * 保存急单抢占保护参数
     */
    @PostMapping("/urgent-preempt-config")
    public ResponseResult<Map<String, Object>> saveUrgentPreemptConfig(@RequestBody Map<String, Object> params) {
        try {
            String operator = params.get("operator") == null ? "system" : String.valueOf(params.get("operator"));
            return ResponseResult.success(manualScheduleService.saveUrgentPreemptConfig(params, operator));
        } catch (Exception e) {
            return ResponseResult.error("保存急单抢占参数失败: " + e.getMessage());
        }
    }

    /**
     * 终止排程（保留已开工，回滚未开工）
     */
    @PostMapping("/terminate")
    public ResponseResult<Boolean> terminateSchedule(@RequestBody Map<String, Object> params) {
        try {
            Object scheduleIdObj = params.get("scheduleId");
            Long scheduleId = null;
            if (scheduleIdObj instanceof Number) {
                scheduleId = ((Number) scheduleIdObj).longValue();
            } else if (scheduleIdObj instanceof String) {
                scheduleId = Long.parseLong((String) scheduleIdObj);
            }
            if (scheduleId == null) {
                return ResponseResult.error("scheduleId 不能为空");
            }
            String reason = params.get("reason") == null ? null : String.valueOf(params.get("reason"));
            String operator = params.get("operator") == null ? null : String.valueOf(params.get("operator"));
            boolean ok = manualScheduleService.terminateSchedule(scheduleId, reason, operator);
            return ResponseResult.success(ok);
        } catch (Exception e) {
            return ResponseResult.error("终止排程失败: " + e.getMessage());
        }
    }

    /**
     * 排程减量（只允许减少未开工部分）
     */
    @PostMapping("/reduce")
    public ResponseResult<Boolean> reduceSchedule(@RequestBody Map<String, Object> params) {
        try {
            Object scheduleIdObj = params.get("scheduleId");
            Long scheduleId = null;
            if (scheduleIdObj instanceof Number) {
                scheduleId = ((Number) scheduleIdObj).longValue();
            } else if (scheduleIdObj instanceof String) {
                scheduleId = Long.parseLong((String) scheduleIdObj);
            }
            if (scheduleId == null) {
                return ResponseResult.error("scheduleId 不能为空");
            }

            Object reduceQtyObj = params.get("reduceQty");
            Integer reduceQty = null;
            if (reduceQtyObj instanceof Number) {
                reduceQty = ((Number) reduceQtyObj).intValue();
            } else if (reduceQtyObj instanceof String) {
                reduceQty = Integer.parseInt((String) reduceQtyObj);
            }
            if (reduceQty == null || reduceQty <= 0) {
                return ResponseResult.error("reduceQty 必须大于0");
            }

            String reason = params.get("reason") == null ? null : String.valueOf(params.get("reason"));
            String operator = params.get("operator") == null ? null : String.valueOf(params.get("operator"));
            boolean ok = manualScheduleService.reduceSchedule(scheduleId, reduceQty, reason, operator);
            return ResponseResult.success(ok);
        } catch (Exception e) {
            return ResponseResult.error("排程减量失败: " + e.getMessage());
        }
    }
}
