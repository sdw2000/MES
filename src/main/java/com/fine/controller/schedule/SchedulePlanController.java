package com.fine.controller.schedule;

import com.fine.Utils.ResponseResult;
import com.fine.Dao.SalesOrderItemMapper;
import com.fine.Dao.schedule.ManualScheduleMapper;
import com.fine.Dao.production.EquipmentOccupationMapper;
import com.fine.Dao.production.EquipmentMapper;
import com.fine.Dao.production.ScheduleCoatingMapper;
import com.fine.Dao.production.ScheduleRewindingMapper;
import com.fine.Dao.production.ScheduleSlittingMapper;
import com.fine.model.production.Equipment;
import com.fine.modle.SalesOrderItem;
import com.fine.modle.schedule.ManualSchedule;
import com.fine.modle.schedule.SchedulePlan;
import com.fine.service.schedule.SchedulePlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/schedule/plan")
public class SchedulePlanController {

    @Autowired
    private SchedulePlanService schedulePlanService;

    @Autowired
    private EquipmentMapper equipmentMapper;

    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;

    @Autowired
    private ManualScheduleMapper manualScheduleMapper;

    @Autowired
    private EquipmentOccupationMapper equipmentOccupationMapper;

    @Autowired
    private ScheduleCoatingMapper scheduleCoatingMapper;

    @Autowired
    private ScheduleRewindingMapper scheduleRewindingMapper;

    @Autowired
    private ScheduleSlittingMapper scheduleSlittingMapper;

    @PostMapping("/upsert")
    public ResponseResult<Boolean> upsert(@RequestBody SchedulePlan plan) {
        return ResponseResult.success(schedulePlanService.upsertPlan(plan));
    }

    @GetMapping("/daily")
    public ResponseResult<List<Map<String, Object>>> daily(@RequestParam(value = "date", required = false) String date) {
        return ResponseResult.success(schedulePlanService.getDailyPlan(date));
    }

    @GetMapping("/relation/order-material/page")
    public ResponseResult<Map<String, Object>> orderMaterialRelationPage(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
            @RequestParam(value = "orderNo", required = false) String orderNo,
            @RequestParam(value = "materialCode", required = false) String materialCode,
            @RequestParam(value = "rollCode", required = false) String rollCode) {
        Page<Map<String, Object>> page = new Page<>(pageNum, pageSize);
        List<Map<String, Object>> list = manualScheduleMapper.selectOrderMaterialRelationPage(page, orderNo, materialCode, rollCode);
        Long total = manualScheduleMapper.selectOrderMaterialRelationCount(orderNo, materialCode, rollCode);

        for (Map<String, Object> row : list) {
            double planned = toDouble(row.get("planned_area_total"));
            double locked = toDouble(row.get("locked_area_total"));
            double issue = toDouble(row.get("issue_area_total"));
            double loss = toDouble(row.get("loss_area_total"));
            row.put("lock_rate", planned > 0 ? round2(locked * 100.0 / planned) : 0D);
            row.put("issue_rate", planned > 0 ? round2(issue * 100.0 / planned) : 0D);
            row.put("loss_rate", issue > 0 ? round2(loss * 100.0 / issue) : 0D);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total == null ? 0 : total);
        data.put("pageNum", pageNum);
        data.put("pageSize", pageSize);
        return ResponseResult.success(data);
    }

    @GetMapping("/relation/material-order/page")
    public ResponseResult<Map<String, Object>> materialOrderRelationPage(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
            @RequestParam(value = "orderNo", required = false) String orderNo,
            @RequestParam(value = "materialCode", required = false) String materialCode,
            @RequestParam(value = "rollCode", required = false) String rollCode) {
        Page<Map<String, Object>> page = new Page<>(pageNum, pageSize);
        List<Map<String, Object>> list = manualScheduleMapper.selectMaterialOrderRelationPage(page, orderNo, materialCode, rollCode);
        Long total = manualScheduleMapper.selectMaterialOrderRelationCount(orderNo, materialCode, rollCode);

        for (Map<String, Object> row : list) {
            double rollArea = toDouble(row.get("roll_area"));
            double locked = toDouble(row.get("locked_area_total"));
            double issue = toDouble(row.get("issue_area_total"));
            double loss = toDouble(row.get("loss_area_total"));
            row.put("allocation_rate", rollArea > 0 ? round2(locked * 100.0 / rollArea) : 0D);
            row.put("issue_rate", locked > 0 ? round2(issue * 100.0 / locked) : 0D);
            row.put("loss_rate", issue > 0 ? round2(loss * 100.0 / issue) : 0D);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total == null ? 0 : total);
        data.put("pageNum", pageNum);
        data.put("pageSize", pageSize);
        return ResponseResult.success(data);
    }

    @GetMapping("/relation/summary")
    public ResponseResult<Map<String, Object>> relationSummary() {
        Map<String, Object> data = manualScheduleMapper.selectPlanRelationSummary();
        if (data == null) {
            data = new HashMap<>();
        }
        double planned = toDouble(data.get("planned_area_total"));
        double locked = toDouble(data.get("locked_area_total"));
        double issue = toDouble(data.get("issue_area_total"));
        double loss = toDouble(data.get("loss_area_total"));
        data.put("lock_rate", planned > 0 ? round2(locked * 100.0 / planned) : 0D);
        data.put("issue_rate", planned > 0 ? round2(issue * 100.0 / planned) : 0D);
        data.put("loss_rate", issue > 0 ? round2(loss * 100.0 / issue) : 0D);
        data.put("unlocked_area_total", round2(Math.max(planned - locked, 0D)));
        return ResponseResult.success(data);
    }

    @GetMapping("/coating/page")
    public ResponseResult<Map<String, Object>> coatingPage(
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "orderNo", required = false) String orderNo) {

        return stagePage("COATING", pageNum, pageSize, status, orderNo, null, null);
    }

    @GetMapping("/stage/page")
    public ResponseResult<Map<String, Object>> stagePage(
            @RequestParam("stage") String stage,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "orderNo", required = false) String orderNo,
            @RequestParam(value = "planDateStart", required = false) String planDateStart,
            @RequestParam(value = "planDateEnd", required = false) String planDateEnd) {

        QueryWrapper<SchedulePlan> q = new QueryWrapper<>();
        String normalizedStage = normalizeStage(stage);
        q.eq("stage", normalizedStage);

        if (orderNo != null && !orderNo.isEmpty()) {
            q.like("order_no", orderNo);
        }

        if (planDateStart != null && !planDateStart.isEmpty()) {
            q.ge("plan_date", planDateStart + " 00:00:00");
        }
        if (planDateEnd != null && !planDateEnd.isEmpty()) {
            q.le("plan_date", planDateEnd + " 23:59:59");
        }

        if (status != null && !status.isEmpty()) {
            String s = status.toUpperCase();
            if ("SCHEDULED".equals(s) || "UNSCHEDULED".equals(s) || "IN_PROGRESS".equals(s)) {
                q.in("status", "PLANNED", "CONFIRMED");
            } else if ("COMPLETED".equals(s)) {
                q.eq("status", "COMPLETED");
            } else if ("CANCELLED".equals(s)) {
                q.eq("status", "CANCELLED");
            }
        }

        q.orderByAsc("plan_date").orderByAsc("id");

        Page<SchedulePlan> page = new Page<>(pageNum, pageSize);
        Page<SchedulePlan> result = (Page<SchedulePlan>) schedulePlanService.page(page, q);

        List<SchedulePlan> plans = result.getRecords();
        Set<Long> equipmentIds = new HashSet<>();
        Set<Long> orderDetailIds = new HashSet<>();
        for (SchedulePlan p : plans) {
            if (p.getEquipment() == null) continue;
            try {
                equipmentIds.add(Long.parseLong(p.getEquipment()));
            } catch (Exception ignore) {
            }
            if (p.getOrderDetailId() != null) {
                orderDetailIds.add(p.getOrderDetailId());
            }
        }

        for (SchedulePlan p : plans) {
            if (p.getOrderDetailId() != null) {
                orderDetailIds.add(p.getOrderDetailId());
            }
        }

        Map<Long, Equipment> equipmentMap = new HashMap<>();
        if (!equipmentIds.isEmpty()) {
            List<Equipment> equipmentList = equipmentMapper.selectBatchIds(equipmentIds);
            for (Equipment e : equipmentList) {
                equipmentMap.put(e.getId(), e);
            }
        }

        Map<Long, SalesOrderItem> orderItemMap = new HashMap<>();
        if (!orderDetailIds.isEmpty()) {
            List<SalesOrderItem> itemList = salesOrderItemMapper.selectBatchIds(orderDetailIds);
            for (SalesOrderItem item : itemList) {
                orderItemMap.put(item.getId(), item);
            }
        }

        Map<Long, ManualSchedule> latestScheduleMap = new HashMap<>();
        if (!orderDetailIds.isEmpty()) {
            QueryWrapper<ManualSchedule> sq = new QueryWrapper<>();
            sq.in("order_detail_id", orderDetailIds).orderByDesc("id");
            List<ManualSchedule> schedules = manualScheduleMapper.selectList(sq);
            for (ManualSchedule ms : schedules) {
                if (ms.getOrderDetailId() == null) {
                    continue;
                }
                latestScheduleMap.putIfAbsent(ms.getOrderDetailId(), ms);
            }
        }

        Set<Long> manualScheduleIds = new HashSet<>();
        for (ManualSchedule ms : latestScheduleMap.values()) {
            if (ms != null && ms.getId() != null) {
                manualScheduleIds.add(ms.getId());
            }
        }
        Map<Long, String> taskNoByScheduleId = loadTaskNoByScheduleId(normalizedStage, manualScheduleIds);

        List<Map<String, Object>> list = new java.util.ArrayList<>();
        for (SchedulePlan p : plans) {
            SalesOrderItem item = p.getOrderDetailId() == null ? null : orderItemMap.get(p.getOrderDetailId());
            if (p.getOrderDetailId() != null && (item == null || Integer.valueOf(1).equals(item.getIsDeleted()))) {
                continue;
            }
            ManualSchedule latestSchedule = p.getOrderDetailId() == null ? null : latestScheduleMap.get(p.getOrderDetailId());
            Integer scheduleQty = latestSchedule == null ? null : latestSchedule.getScheduleQty();
            Integer durationMinutes = null;
            if (latestSchedule != null && latestSchedule.getId() != null) {
                durationMinutes = equipmentOccupationMapper.selectDurationMinutesByScheduleAndProcess(latestSchedule.getId(), normalizedStage);
            }
            Map<String, Object> row = new HashMap<>();
                Long manualScheduleId = latestSchedule == null ? null : latestSchedule.getId();
                String taskNo = manualScheduleId == null ? null : taskNoByScheduleId.get(manualScheduleId);
                String displayTaskNo = (taskNo != null && !taskNo.trim().isEmpty())
                    ? taskNo
                    : buildFallbackTaskNo(normalizedStage, p.getPlanDate(), p.getId());
            row.put("id", p.getId());
            row.put("planId", p.getId());
            row.put("sourceType", "PLAN");
                row.put("taskNo", displayTaskNo);
                row.put("batchNo", displayTaskNo);
            row.put("orderItemId", p.getOrderDetailId());
            row.put("orderNo", p.getOrderNo());
            row.put("materialCode", p.getMaterialCode() != null ? p.getMaterialCode() : (item == null ? null : item.getMaterialCode()));
            row.put("materialName", p.getMaterialName() != null ? p.getMaterialName() : (item == null ? null : item.getMaterialName()));
            row.put("thickness", p.getThickness() != null ? p.getThickness() : (item == null ? null : item.getThickness()));
            row.put("widthMm", p.getWidth() != null ? p.getWidth() : (item == null ? null : item.getWidth()));
            row.put("length", p.getLength() != null ? p.getLength() : (item == null ? null : item.getLength()));
            row.put("processType", normalizedStage);
            row.put("scheduleQty", scheduleQty);
            if ("REWINDING".equals(normalizedStage)) {
                Double rewindingWidth = latestSchedule != null && latestSchedule.getRewindingWidth() != null
                        ? latestSchedule.getRewindingWidth().doubleValue()
                        : 500D;
                row.put("rewindingWidth", rewindingWidth);
                row.put("rewinding_width", rewindingWidth);

                double planArea = p.getPlanArea() == null ? 0D : p.getPlanArea().doubleValue();
                Double orderLength = p.getLength() != null
                    ? Double.valueOf(p.getLength().doubleValue())
                    : (item != null && item.getLength() != null ? Double.valueOf(item.getLength().doubleValue()) : null);
                int rewindingQty = 0;
                if (planArea > 0 && rewindingWidth != null && rewindingWidth > 0 && orderLength != null && orderLength > 0) {
                    double singleRollArea = (rewindingWidth / 1000D) * orderLength;
                    if (singleRollArea > 0) {
                        rewindingQty = (int) Math.ceil((planArea / singleRollArea) - 1e-9D);
                    }
                }
                if (rewindingQty <= 0 && scheduleQty != null && scheduleQty > 0) {
                    rewindingQty = scheduleQty;
                }
                row.put("rewindingQty", rewindingQty);
                row.put("rewinding_roll_count", rewindingQty);
            }
            if ("SLITTING".equals(normalizedStage)) {
                row.put("slittingQty", scheduleQty);
            }
            row.put("area", p.getPlanArea());
            row.put("planStartTime", p.getPlanDate());
            row.put("planEndTime", null);
            row.put("durationMinutes", durationMinutes);
            row.put("status", mapPlanStatus(p.getStatus()));
            row.put("canShipBy48h", null);
            row.put("priorityScore", 0);

            Long equipmentId = null;
            if (p.getEquipment() != null && !p.getEquipment().isEmpty()) {
                try {
                    equipmentId = Long.parseLong(p.getEquipment());
                } catch (Exception ignore) {
                }
            }
            row.put("equipmentId", equipmentId);
            row.put("equipment", p.getEquipment());

            if (equipmentId != null) {
                Equipment e = equipmentMap.get(equipmentId);
                if (e != null) {
                    row.put("equipmentCode", e.getEquipmentCode());
                    row.put("equipmentName", e.getEquipmentName());
                }
            }
            list.add(row);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", result.getTotal());
        data.put("pageNum", result.getCurrent());
        data.put("pageSize", result.getSize());
        return ResponseResult.success(data);
    }

    private Map<Long, String> loadTaskNoByScheduleId(String normalizedStage, Set<Long> scheduleIds) {
        Map<Long, String> taskNoMap = new HashMap<>();
        if (scheduleIds == null || scheduleIds.isEmpty()) {
            return taskNoMap;
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        List<Long> ids = new ArrayList<>(scheduleIds);
        if ("COATING".equals(normalizedStage)) {
            rows = scheduleCoatingMapper.selectTaskNoByScheduleIds(ids);
        } else if ("REWINDING".equals(normalizedStage)) {
            rows = scheduleRewindingMapper.selectTaskNoByScheduleIds(ids);
        } else if ("SLITTING".equals(normalizedStage)) {
            rows = scheduleSlittingMapper.selectTaskNoByScheduleIds(ids);
        }

        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Long scheduleId = null;
            Object sid = row.get("scheduleId");
            if (sid instanceof Number) {
                scheduleId = ((Number) sid).longValue();
            } else if (sid != null) {
                try {
                    scheduleId = Long.parseLong(String.valueOf(sid));
                } catch (Exception ignore) {
                }
            }
            String taskNo = row.get("taskNo") == null ? null : String.valueOf(row.get("taskNo"));
            if (scheduleId != null && taskNo != null && !taskNo.trim().isEmpty()) {
                taskNoMap.putIfAbsent(scheduleId, taskNo.trim());
            }
        }
        return taskNoMap;
    }

    private String buildFallbackTaskNo(String normalizedStage, LocalDateTime planDate, Long planId) {
        String prefix;
        int width;
        if ("COATING".equals(normalizedStage)) {
            prefix = "TB";
            width = 2;
        } else if ("REWINDING".equals(normalizedStage)) {
            prefix = "FJ";
            width = 2;
        } else if ("SLITTING".equals(normalizedStage)) {
            prefix = "FQ";
            width = 3;
        } else {
            prefix = "PLAN";
            width = 3;
        }
        String dateCode = (planDate == null ? LocalDateTime.now() : planDate)
                .format(DateTimeFormatter.ofPattern("yyMMdd"));
        long base = planId == null ? 1L : Math.max(1L, planId % (long) Math.pow(10, width));
        return prefix + "-" + dateCode + "-" + String.format("%0" + width + "d", base);
    }

    private String normalizeStage(String stage) {
        if (stage == null || stage.trim().isEmpty()) {
            return "COATING";
        }
        String s = stage.trim().toUpperCase();
        if ("COATING".equals(s) || "REWINDING".equals(s) || "SLITTING".equals(s)) {
            return s;
        }
        return "COATING";
    }

    private String mapPlanStatus(String status) {
        if (status == null || status.isEmpty()) {
            return "SCHEDULED";
        }
        String s = status.toUpperCase();
        if ("CONFIRMED".equals(s) || "PLANNED".equals(s)) {
            return "SCHEDULED";
        }
        if ("COMPLETED".equals(s)) {
            return "COMPLETED";
        }
        if ("CANCELLED".equals(s)) {
            return "CANCELLED";
        }
        return "SCHEDULED";
    }

    private double toDouble(Object obj) {
        if (obj == null) {
            return 0D;
        }
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(obj));
        } catch (Exception e) {
            return 0D;
        }
    }

    private double round2(double value) {
        return java.math.BigDecimal.valueOf(value).setScale(2, java.math.BigDecimal.ROUND_HALF_UP).doubleValue();
    }
}
