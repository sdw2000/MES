package com.fine.controller.production;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fine.Utils.ResponseResult;
import com.fine.Dao.production.ScheduleCoatingMapper;
import com.fine.Dao.production.ScheduleRewindingMapper;
import com.fine.Dao.production.ScheduleSlittingMapper;
import com.fine.Dao.production.ScheduleOrderItemMapper;
import com.fine.Dao.rd.TapeSpecMapper;
import com.fine.Dao.SalesOrderItemMapper;
import com.fine.model.production.ScheduleCoating;
import com.fine.model.production.ScheduleRewinding;
import com.fine.model.production.ScheduleSlitting;
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
                vo.setEquipmentId(t.getEquipmentId());
                vo.setEquipmentCode(t.getEquipmentCode());
                vo.setStatus(t.getStatus());
                vo.setPlanStartTime(t.getPlanStartTime());
                vo.setPlanEndTime(t.getPlanEndTime());
                vo.setPlanDuration(t.getPlanDuration());
                vo.setQty(t.getPlanSqm());
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
                if (t.getThickness() == null && item.getThickness() != null) { t.setThickness(item.getThickness()); needUpdate = true; }
                if (t.getJumboWidth() == null && item.getWidth() != null) { t.setJumboWidth(item.getWidth().intValue()); needUpdate = true; }
                if (t.getPlanLength() == null && item.getLength() != null) { t.setPlanLength(item.getLength().divide(new java.math.BigDecimal(1000))); needUpdate = true; }
                if ((t.getColorName() == null || t.getColorName().isEmpty()) && item.getColorCode() != null) { t.setColorCode(item.getColorCode()); needUpdate = true; }
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
        private Long id;
        private String taskNo;
        private String type;
        private String materialCode;
        private String materialName;
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
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getMaterialCode() { return materialCode; }
        public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }
        public String getMaterialName() { return materialName; }
        public void setMaterialName(String materialName) { this.materialName = materialName; }
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
}
