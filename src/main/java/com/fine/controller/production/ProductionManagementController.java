package com.fine.controller.production;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fine.Utils.ResponseResult;
import com.fine.Dao.production.ScheduleCoatingMapper;
import com.fine.Dao.production.ScheduleRewindingMapper;
import com.fine.Dao.production.ScheduleSlittingMapper;
import com.fine.Dao.production.ScheduleOrderItemMapper;
import com.fine.Dao.rd.TapeSpecMapper;
import com.fine.Dao.SalesOrderItemMapper;
import com.fine.Dao.SampleItemMapper;
import com.fine.Dao.SampleOrderMapper;
import com.fine.modle.SampleItem;
import com.fine.modle.SampleOrder;
import com.fine.modle.schedule.ManualSchedule;
import com.fine.model.production.ScheduleCoating;
import com.fine.model.production.ScheduleRewinding;
import com.fine.model.production.ScheduleSlitting;
import com.fine.service.schedule.ManualScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Comparator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/production/management")
public class ProductionManagementController {

    @Autowired
    private ScheduleCoatingMapper coatingMapper;

    @Autowired
    private ScheduleRewindingMapper rewindingMapper;

    @Autowired
    private ScheduleSlittingMapper slittingMapper;

    @Autowired
    private ScheduleOrderItemMapper scheduleOrderItemMapper;

    @Autowired
    private TapeSpecMapper tapeSpecMapper;

    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;

    @Autowired
    private SampleOrderMapper sampleOrderMapper;

    @Autowired
    private SampleItemMapper sampleItemMapper;

    @Autowired
    private ManualScheduleService manualScheduleService;

    private static final String SAMPLE_TASK_MARKER = "[SAMPLE_TASK]";

    /**
     * 分切样板任务列表（样板信息不走排程）
     */
    @GetMapping("/slitting-sample-tasks")
    public ResponseResult<Map<String, Object>> listSlittingSampleTasks(
            @RequestParam(value = "orderNo", required = false) String orderNo,
            @RequestParam(value = "materialCode", required = false) String materialCode,
            @RequestParam(value = "specKeyword", required = false) String specKeyword,
            @RequestParam(value = "planDateStart", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date planDateStart,
            @RequestParam(value = "planDateEnd", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date planDateEnd,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {

        String orderNoKw = trimToNull(orderNo);
        String materialCodeKw = trimToNull(materialCode);
        String specKw = trimToNull(specKeyword);
        LocalDate start = toLocalDate(planDateStart);
        LocalDate end = toLocalDate(planDateEnd);

        QueryWrapper<SampleOrder> orderWrapper = new QueryWrapper<>();
        orderWrapper.eq("is_deleted", 0);
        if (orderNoKw != null) {
            orderWrapper.like("sample_no", orderNoKw);
        }
        if (start != null) {
            orderWrapper.ge("send_date", start);
        }
        if (end != null) {
            orderWrapper.le("send_date", end);
        }
        orderWrapper.orderByDesc("send_date", "id");
        List<SampleOrder> orders = sampleOrderMapper.selectList(orderWrapper);

        List<Map<String, Object>> all = new ArrayList<>();
        for (SampleOrder order : orders) {
            if (order == null || order.getSampleNo() == null || order.getSampleNo().trim().isEmpty()) {
                continue;
            }
            List<SampleItem> items = sampleItemMapper.selectBySampleNo(order.getSampleNo());
            if (items == null || items.isEmpty()) {
                continue;
            }
            for (SampleItem item : items) {
                if (item == null || item.getId() == null) {
                    continue;
                }
                if (materialCodeKw != null && (item.getMaterialCode() == null || !item.getMaterialCode().contains(materialCodeKw))) {
                    continue;
                }
                if (specKw != null && !matchSampleSpec(item, specKw)) {
                    continue;
                }

                Long scheduleId = findSampleScheduleId(item.getId(), order.getSampleNo());

                Map<String, Object> row = new HashMap<>();
                row.put("id", scheduleId != null ? scheduleId : item.getId());
                row.put("scheduleId", scheduleId);
                row.put("sampleItemId", item.getId());
                row.put("sampleNo", order.getSampleNo());
                row.put("isSampleTask", true);
                row.put("taskNo", "YB-" + order.getSampleNo() + "-" + item.getId());
                row.put("type", "slitting");
                String sampleStatus = "已完成".equals(order.getStatus()) ? "COMPLETED" : "UNCOMPLETED";
                row.put("status", sampleStatus);
                row.put("sampleStatus", "COMPLETED".equals(sampleStatus) ? "已完成" : "未完成");
                row.put("scheduleStatus", scheduleId != null ? "SCHEDULED" : "UNSCHEDULED");
                row.put("orderNo", order.getSampleNo());
                row.put("materialCode", item.getMaterialCode());
                row.put("materialName", item.getMaterialName());
                // 销售样板明细 thickness 字段口径为 μm，前端规格展示也按 μm*mm*m，
                // 这里直接使用原值，避免重复换算导致 16μm 显示为 16000μm。
                row.put("thickness", toInteger(item.getThickness()));
                row.put("widthMm", toInteger(item.getWidth()));
                row.put("length", toInteger(item.getLength()));
                row.put("qty", item.getQuantity() == null ? 0 : item.getQuantity());
                row.put("customerName", order.getCustomerName());
                row.put("customerShortName", order.getCustomerName()); // 暂时平替
                row.put("planStartTime", toDateTimeString(order.getSendDate()));
                row.put("planEndTime", toDateTimeString(order.getSendDate()));
                row.put("equipmentCode", "-");
                row.put("planDuration", "1.0");
                all.add(row);
            }
        }

        all.sort(Comparator.comparing(o -> String.valueOf(o.getOrDefault("taskNo", ""))));
        int total = all.size();
        int from = Math.max(0, ((pageNum == null ? 1 : pageNum) - 1) * (pageSize == null ? 20 : pageSize));
        int to = Math.min(total, from + (pageSize == null ? 20 : pageSize));
        List<Map<String, Object>> pageList = from >= total ? new ArrayList<>() : all.subList(from, to);

        Map<String, Object> result = new HashMap<>();
        result.put("records", pageList);
        result.put("total", total);
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        return ResponseResult.success(result);
    }

    /**
     * 确保样板明细有可报工的排程ID（自动创建直报排程）
     */
    @PostMapping("/slitting-sample-tasks/ensure-schedule")
    public ResponseResult<Map<String, Object>> ensureSlittingSampleSchedule(@RequestBody SampleScheduleRequest req) {
        if (req == null || req.getSampleItemId() == null || req.getSampleItemId() <= 0) {
            return ResponseResult.error("sampleItemId不能为空");
        }
        if (req.getSampleNo() == null || req.getSampleNo().trim().isEmpty()) {
            return ResponseResult.error("sampleNo不能为空");
        }

        Long existed = findSampleScheduleId(req.getSampleItemId(), req.getSampleNo());
        if (existed != null && existed > 0) {
            Map<String, Object> result = new HashMap<>();
            result.put("scheduleId", existed);
            return ResponseResult.success(result);
        }

        ManualSchedule schedule = new ManualSchedule();
        schedule.setOrderNo(req.getSampleNo().trim());
        schedule.setMaterialCode(trimToNull(req.getMaterialCode()));
        schedule.setMaterialName(trimToNull(req.getMaterialName()));
        Double qty = req.getQuantity() == null ? 0.0 : req.getQuantity();
        schedule.setScheduleQty(Math.max(1.0, qty));
        schedule.setShortageQty(0.0);
        schedule.setScheduleType("STOCK");
        schedule.setStatus("REWINDING_SCHEDULED");
        schedule.setRemark(buildSampleMarker(req.getSampleItemId(), req.getSampleNo()));
        Long scheduleId = manualScheduleService.createSchedule(schedule);

        Map<String, Object> result = new HashMap<>();
        result.put("scheduleId", scheduleId);
        return ResponseResult.success(result);
    }

    /**
     * 查询生产任务（支持类型过滤：coating/rewinding/slitting）。
     */
    @GetMapping("/tasks")
    public ResponseResult<Map<String, Object>> listTasks(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "equipmentId", required = false) Long equipmentId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "planDateStart", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date planDateStart,
            @RequestParam(value = "planDateEnd", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date planDateEnd,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize) {

        List<TaskVO> all = new ArrayList<>();

        boolean fetchAll = (type == null || type.isEmpty());

        if (fetchAll || "coating".equalsIgnoreCase(type)) {
            QueryWrapper<ScheduleCoating> w = new QueryWrapper<>();
            if (equipmentId != null) w.eq("equipment_id", equipmentId);
            if (status != null && !status.isEmpty()) w.eq("status", status);
            if (planDateStart != null) w.ge("plan_date", planDateStart);
            if (planDateEnd != null) w.le("plan_date", planDateEnd);
            w.orderByAsc("plan_start_time");
            List<ScheduleCoating> list = coatingMapper.selectList(w);
            for (ScheduleCoating t : list) {
                hydrateAndPersistCoating(t);
                TaskVO vo = new TaskVO();
                vo.setId(t.getId());
                vo.setTaskNo(t.getTaskNo());
                vo.setType("coating");
                vo.setMaterialCode(t.getMaterialCode());
                vo.setMaterialName(t.getMaterialName());
                // 涂布任务关联单一订单号
                vo.setOrderNo(t.getOrderNo());
                vo.setOrderId(t.getOrderId());
                vo.setOrderItemId(t.getOrderItemId());
                vo.setColorName(t.getColorName());
                vo.setThickness(t.getThickness());
                vo.setJumboWidth(t.getJumboWidth());
                vo.setPlanLength(t.getPlanLength());
                vo.setEquipmentId(t.getEquipmentId());
                vo.setEquipmentCode(t.getEquipmentCode());
                vo.setStatus(t.getStatus());
                vo.setPlanStartTime(t.getPlanStartTime());
                vo.setPlanEndTime(t.getPlanEndTime());
                vo.setPlanDuration(t.getPlanDuration());
                vo.setQty(t.getPlanSqm());
                if (t.getOrderItemId() != null) {
                    Map<String, Object> full = salesOrderItemMapper.selectFullItemById(t.getOrderItemId());
                    if (full != null) {
                        if (vo.getOrderNo() == null && full.get("order_no") != null) {
                            vo.setOrderNo(full.get("order_no").toString());
                        }
                        if (vo.getMaterialCode() == null && full.get("material_code") != null) {
                            vo.setMaterialCode(full.get("material_code").toString());
                        }
                        if (vo.getMaterialName() == null && full.get("material_name") != null) {
                            vo.setMaterialName(full.get("material_name").toString());
                        }
                        if (vo.getThickness() == null && full.get("thickness") != null) {
                            vo.setThickness(new java.math.BigDecimal(full.get("thickness").toString()));
                        }
                        if (vo.getJumboWidth() == null && full.get("width") != null) {
                            try { vo.setJumboWidth(((Number) full.get("width")).intValue()); } catch (Exception ignore) { }
                        }
                        if (vo.getPlanLength() == null && full.get("length") != null) {
                            try {
                                java.math.BigDecimal lenMm = new java.math.BigDecimal(full.get("length").toString());
                                vo.setPlanLength(lenMm.divide(new java.math.BigDecimal(1000)));
                            } catch (Exception ignore) { }
                        }
                    }
                }
                all.add(vo);
            }
        }

        if (fetchAll || "rewinding".equalsIgnoreCase(type)) {
            QueryWrapper<ScheduleRewinding> w = new QueryWrapper<>();
            if (equipmentId != null) w.eq("equipment_id", equipmentId);
            if (status != null && !status.isEmpty()) w.eq("status", status);
            if (planDateStart != null) w.ge("plan_date", planDateStart);
            if (planDateEnd != null) w.le("plan_date", planDateEnd);
            w.orderByAsc("plan_start_time");
            List<ScheduleRewinding> list = rewindingMapper.selectList(w);
            for (ScheduleRewinding t : list) {
                TaskVO vo = new TaskVO();
                vo.setId(t.getId());
                vo.setTaskNo(t.getTaskNo());
                vo.setType("rewinding");
                vo.setMaterialCode(t.getMaterialCode());
                vo.setMaterialName(t.getMaterialName());
                // 复卷任务可能关联多个订单，使用持久化的逗号分隔文本
                vo.setOrderNo(t.getOrderNosText());
                vo.setEquipmentId(t.getEquipmentId());
                vo.setEquipmentCode(t.getEquipmentCode());
                vo.setStatus(t.getStatus());
                vo.setPlanStartTime(t.getPlanStartTime());
                vo.setPlanEndTime(t.getPlanEndTime());
                vo.setPlanDuration(t.getPlanDuration());
                vo.setQty(t.getPlanRolls());
                all.add(vo);
            }
        }

        if (fetchAll || "slitting".equalsIgnoreCase(type)) {
            QueryWrapper<ScheduleSlitting> w = new QueryWrapper<>();
            if (equipmentId != null) w.eq("equipment_id", equipmentId);
            if (status != null && !status.isEmpty()) w.eq("status", status);
            if (planDateStart != null) w.ge("plan_date", planDateStart);
            if (planDateEnd != null) w.le("plan_date", planDateEnd);
            w.orderByAsc("plan_start_time");
            List<ScheduleSlitting> list = slittingMapper.selectList(w);
            for (ScheduleSlitting t : list) {
                TaskVO vo = new TaskVO();
                vo.setId(t.getId());
                vo.setTaskNo(t.getTaskNo());
                vo.setType("slitting");
                vo.setMaterialCode(t.getMaterialCode());
                vo.setMaterialName(t.getMaterialName());
                vo.setOrderNo(t.getOrderNo());
                vo.setEquipmentId(t.getEquipmentId());
                vo.setEquipmentCode(t.getEquipmentCode());
                vo.setStatus(t.getStatus());
                vo.setPlanStartTime(t.getPlanStartTime());
                vo.setPlanEndTime(t.getPlanEndTime());
                vo.setPlanDuration(t.getPlanDuration());
                vo.setQty(t.getPlanRolls());
                all.add(vo);
            }
        }

        // 简单分页
        int total = all.size();
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(total, from + pageSize);
        List<TaskVO> pageList = from >= total ? new ArrayList<>() : all.subList(from, to);

        Map<String, Object> result = new HashMap<>();
        result.put("list", pageList);
        result.put("total", total);
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        return ResponseResult.success(result);
    }

    /**
     * 为涂布任务回填颜色、厚度、宽度、长度等关键字段，并持久化到数据库。
     */
    private void hydrateAndPersistCoating(ScheduleCoating t) {
        boolean needUpdate = false;

        // 先尝试从排程订单明细获取
        if (t.getOrderItemId() != null) {
            com.fine.model.production.ScheduleOrderItem soi = scheduleOrderItemMapper.selectById(t.getOrderItemId());
            if (soi != null) {
                if (t.getOrderNo() == null && soi.getOrderNo() != null) { t.setOrderNo(soi.getOrderNo()); needUpdate = true; }
                if (t.getMaterialCode() == null && soi.getMaterialCode() != null) { t.setMaterialCode(soi.getMaterialCode()); needUpdate = true; }
                if (t.getMaterialName() == null && soi.getMaterialName() != null) { t.setMaterialName(soi.getMaterialName()); needUpdate = true; }
                if (t.getColorCode() == null && soi.getColorCode() != null) { t.setColorCode(soi.getColorCode()); needUpdate = true; }
                if (t.getThickness() == null && soi.getThickness() != null) { t.setThickness(soi.getThickness()); needUpdate = true; }
                if (t.getJumboWidth() == null && soi.getWidth() != null) { t.setJumboWidth(soi.getWidth().intValue()); needUpdate = true; }
                if (t.getPlanLength() == null && soi.getLength() != null) { t.setPlanLength(soi.getLength().divide(new java.math.BigDecimal(1000))); needUpdate = true; }
            }
        }

        // 再从规格库补充颜色名称
        if ((t.getColorName() == null || t.getColorName().isEmpty()) && t.getMaterialCode() != null) {
            com.fine.modle.rd.TapeSpec spec = tapeSpecMapper.selectByMaterialCode(t.getMaterialCode());
            if (spec != null) {
                if (spec.getColorName() != null && !spec.getColorName().isEmpty()) { t.setColorName(spec.getColorName()); needUpdate = true; }
                if (t.getThickness() == null && spec.getTotalThickness() != null) { t.setThickness(spec.getTotalThickness()); needUpdate = true; }
            }
        }

        // 如果仍缺失，尝试从订单明细表查
        if ((t.getThickness() == null || t.getJumboWidth() == null || t.getPlanLength() == null) && t.getOrderItemId() != null) {
            com.fine.modle.SalesOrderItem item = salesOrderItemMapper.selectById(t.getOrderItemId());
            if (item != null) {
                if (t.getMaterialCode() == null && item.getMaterialCode() != null) { t.setMaterialCode(item.getMaterialCode()); needUpdate = true; }
                if (t.getMaterialName() == null && item.getMaterialName() != null) { t.setMaterialName(item.getMaterialName()); needUpdate = true; }
                if (t.getThickness() == null && item.getThickness() != null) { t.setThickness(item.getThickness()); needUpdate = true; }
                if (t.getJumboWidth() == null && item.getWidth() != null) { t.setJumboWidth(item.getWidth().intValue()); needUpdate = true; }
                if (t.getPlanLength() == null && item.getLength() != null) { t.setPlanLength(item.getLength().divide(new java.math.BigDecimal(1000))); needUpdate = true; }
                if ((t.getColorName() == null || t.getColorName().isEmpty()) && item.getColorCode() != null) { t.setColorCode(item.getColorCode()); needUpdate = true; }
            }
        }

        // 订单号兜底（从销售订单明细联表查询）
        if ((t.getOrderNo() == null || t.getOrderNo().isEmpty()) && t.getOrderItemId() != null) {
            Map<String, Object> full = salesOrderItemMapper.selectFullItemById(t.getOrderItemId());
            if (full != null && full.get("order_no") != null) {
                t.setOrderNo(full.get("order_no").toString());
                needUpdate = true;
            }
        }

        if (needUpdate) {
            t.setUpdateBy("system");
            coatingMapper.update(t);
        }
    }

    /**
     * 更新涂布任务实际开始/结束时间；写入后同步状态：
     * - 录入实际开始 -> 状态改为 in_progress
     * - 录入实际结束 -> 状态改为 completed
     * 同时回填实际时长(分钟)
     */
    @PostMapping("/coating/{id}/actual-times")
    public ResponseResult<Void> updateCoatingActualTimes(
            @PathVariable Long id,
            @RequestBody ActualTimeDTO payload) {

        ScheduleCoating coating = coatingMapper.selectByIdWithEquipment(id);
        if (coating == null) {
            return ResponseResult.error("任务不存在");
        }

        Date actualStart = payload.getActualStartTime() != null ? payload.getActualStartTime() : coating.getActualStartTime();
        Date actualEnd = payload.getActualEndTime() != null ? payload.getActualEndTime() : coating.getActualEndTime();

        // 计算时长（分钟）
        Integer actualDuration = null;
        if (actualStart != null && actualEnd != null) {
            long minutes = TimeUnit.MILLISECONDS.toMinutes(actualEnd.getTime() - actualStart.getTime());
            actualDuration = (int) minutes;
        }

        // 状态推进
        String newStatus = coating.getStatus();
        if (actualEnd != null) {
            newStatus = "completed";
        } else if (actualStart != null) {
            newStatus = "in_progress";
        }

        coating.setActualStartTime(actualStart);
        coating.setActualEndTime(actualEnd);
        coating.setActualDuration(actualDuration != null ? actualDuration : coating.getActualDuration());
        coating.setStatus(newStatus);
        coating.setUpdateBy("system");
        coatingMapper.update(coating);

        return ResponseResult.success(null);
    }

    public static class ActualTimeDTO {
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private Date actualStartTime;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private Date actualEndTime;
        public Date getActualStartTime() { return actualStartTime; }
        public void setActualStartTime(Date actualStartTime) { this.actualStartTime = actualStartTime; }
        public Date getActualEndTime() { return actualEndTime; }
        public void setActualEndTime(Date actualEndTime) { this.actualEndTime = actualEndTime; }
    }

    /** 简单任务 VO */
    public static class TaskVO {
        private String orderNo;
        private Long orderId;
        private Long orderItemId;
        private Long id;
        private String taskNo;
        private String type;
        private String materialCode;
        private String materialName;
        private String colorName;
        private java.math.BigDecimal thickness;
        private Integer jumboWidth;
        private java.math.BigDecimal planLength;
        private Long equipmentId;
        private String equipmentCode;
        private String status;
        private Date planStartTime;
        private Date planEndTime;
        private Integer planDuration;
        private Object qty;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTaskNo() { return taskNo; }
        public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
        public String getOrderNo() { return orderNo; }
        public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public Long getOrderItemId() { return orderItemId; }
        public void setOrderItemId(Long orderItemId) { this.orderItemId = orderItemId; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getMaterialCode() { return materialCode; }
        public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }
        public String getMaterialName() { return materialName; }
        public void setMaterialName(String materialName) { this.materialName = materialName; }
        public String getColorName() { return colorName; }
        public void setColorName(String colorName) { this.colorName = colorName; }
        public java.math.BigDecimal getThickness() { return thickness; }
        public void setThickness(java.math.BigDecimal thickness) { this.thickness = thickness; }
        public Integer getJumboWidth() { return jumboWidth; }
        public void setJumboWidth(Integer jumboWidth) { this.jumboWidth = jumboWidth; }
        public java.math.BigDecimal getPlanLength() { return planLength; }
        public void setPlanLength(java.math.BigDecimal planLength) { this.planLength = planLength; }
        public Long getEquipmentId() { return equipmentId; }
        public void setEquipmentId(Long equipmentId) { this.equipmentId = equipmentId; }
        public String getEquipmentCode() { return equipmentCode; }
        public void setEquipmentCode(String equipmentCode) { this.equipmentCode = equipmentCode; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Date getPlanStartTime() { return planStartTime; }
        public void setPlanStartTime(Date planStartTime) { this.planStartTime = planStartTime; }
        public Date getPlanEndTime() { return planEndTime; }
        public void setPlanEndTime(Date planEndTime) { this.planEndTime = planEndTime; }
        public Integer getPlanDuration() { return planDuration; }
        public void setPlanDuration(Integer planDuration) { this.planDuration = planDuration; }
        public Object getQty() { return qty; }
        public void setQty(Object qty) { this.qty = qty; }
    }

    public static class SampleScheduleRequest {
        private String sampleNo;
        private Long sampleItemId;
        private String materialCode;
        private String materialName;
        private Double quantity;

        public String getSampleNo() { return sampleNo; }
        public void setSampleNo(String sampleNo) { this.sampleNo = sampleNo; }
        public Long getSampleItemId() { return sampleItemId; }
        public void setSampleItemId(Long sampleItemId) { this.sampleItemId = sampleItemId; }
        public String getMaterialCode() { return materialCode; }
        public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }
        public String getMaterialName() { return materialName; }
        public void setMaterialName(String materialName) { this.materialName = materialName; }
        public Double getQuantity() { return quantity; }
        public void setQuantity(Double quantity) { this.quantity = quantity; }
    }

    private Long findSampleScheduleId(Long sampleItemId, String sampleNo) {
        if (sampleItemId == null || sampleItemId <= 0) {
            return null;
        }
        String marker = buildSampleMarker(sampleItemId, sampleNo);
        LambdaQueryWrapper<ManualSchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(ManualSchedule::getRemark, marker)
                .orderByDesc(ManualSchedule::getId)
                .last("LIMIT 1");
        ManualSchedule latest = manualScheduleService.getOne(wrapper, false);
        return latest == null ? null : latest.getId();
    }

    private String buildSampleMarker(Long sampleItemId, String sampleNo) {
        return SAMPLE_TASK_MARKER + "sampleNo=" + (sampleNo == null ? "" : sampleNo.trim()) + ",sampleItemId=" + sampleItemId;
    }

    private static String trimToNull(String text) {
        if (text == null) return null;
        String t = text.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean matchSampleSpec(SampleItem item, String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) return true;
        String spec = (item.getSpecification() == null ? "" : item.getSpecification());
        if (spec.contains(kw)) return true;
        String combo = (toInteger(item.getThickness()) == null ? "" : toInteger(item.getThickness())) + "*"
                + (toInteger(item.getWidth()) == null ? "" : toInteger(item.getWidth())) + "*"
                + (toInteger(item.getLength()) == null ? "" : toInteger(item.getLength()));
        return combo.contains(kw);
    }

    private static Integer toInteger(java.math.BigDecimal value) {
        if (value == null) return null;
        return value.intValue();
    }

    private static LocalDate toLocalDate(Date date) {
        if (date == null) return null;
        return new java.sql.Date(date.getTime()).toLocalDate();
    }

    private static String toDateTimeString(LocalDate date) {
        LocalDateTime dt = (date == null ? LocalDate.now() : date).atTime(8, 0, 0);
        return dt.toString().replace('T', ' ');
    }
}
