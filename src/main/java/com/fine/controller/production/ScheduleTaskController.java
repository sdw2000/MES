package com.fine.controller.production;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.Utils.ResponseResult;
import com.fine.Dao.production.EquipmentMapper;
import com.fine.Dao.production.ScheduleBatchMapper;
import com.fine.Dao.production.ScheduleBatchOrderMapper;
import com.fine.model.production.Equipment;
import com.fine.model.production.ScheduleBatch;
import com.fine.model.production.ScheduleBatchOrder;
import com.fine.model.production.ScheduleTask;
import com.fine.model.production.ScheduleTaskPlanRequest;
import com.fine.service.production.ScheduleTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/production/schedule-task")
@CrossOrigin
public class ScheduleTaskController {

    @Autowired
    private ScheduleTaskService scheduleTaskService;

    @Autowired
    private EquipmentMapper equipmentMapper;

    @Autowired
    private ScheduleBatchMapper scheduleBatchMapper;

    @Autowired
    private ScheduleBatchOrderMapper scheduleBatchOrderMapper;

    @PostMapping("/plan")
    public ResponseResult<Map<String, Object>> plan(@RequestBody ScheduleTaskPlanRequest request) {
        Map<String, Object> res = scheduleTaskService.planTasks(request);
        return ResponseResult.success(res);
    }

    @GetMapping("/page")
    public ResponseResult<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String processType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderNo) {

        QueryWrapper<ScheduleTask> q = new QueryWrapper<>();
        if (processType != null && !processType.isEmpty()) {
            q.eq("process_type", processType);
        }
        if (status != null && !status.isEmpty()) {
            q.eq("status", status);
        } else {
            q.ne("status", "CANCELLED");
        }
        if (orderNo != null && !orderNo.isEmpty()) {
            q.like("order_no", orderNo);
        }
        q.orderByDesc("priority_score").orderByAsc("delivery_date").orderByAsc("id");

        Page<ScheduleTask> page = new Page<>(pageNum, pageSize);
        Page<ScheduleTask> result = (Page<ScheduleTask>) scheduleTaskService.page(page, q);

        java.util.List<ScheduleTask> records = result.getRecords();
        if (records != null && !records.isEmpty()) {
            java.util.Set<Long> ids = new java.util.HashSet<>();
            for (ScheduleTask t : records) {
                if (t.getEquipmentId() != null) {
                    ids.add(t.getEquipmentId());
                }
            }
            if (!ids.isEmpty()) {
                java.util.List<Equipment> equipments = equipmentMapper.selectBatchIds(ids);
                java.util.Map<Long, Equipment> map = new java.util.HashMap<>();
                for (Equipment e : equipments) {
                    map.put(e.getId(), e);
                }
                for (ScheduleTask t : records) {
                    if (t.getEquipmentId() != null) {
                        Equipment e = map.get(t.getEquipmentId());
                        if (e != null) {
                            t.setEquipmentCode(e.getEquipmentCode());
                            t.setEquipmentName(e.getEquipmentName());
                        }
                    }
                }
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("list", result.getRecords());
        data.put("total", result.getTotal());
        data.put("pageNum", result.getCurrent());
        data.put("pageSize", result.getSize());
        return ResponseResult.success(data);
    }

    @PostMapping("/{id}/status")
    public ResponseResult<String> updateStatus(@PathVariable Long id, @RequestParam String status) {
        ScheduleTask task = scheduleTaskService.getById(id);
        if (task == null) {
            return ResponseResult.error("任务不存在");
        }
        task.setStatus(status);
        boolean ok = scheduleTaskService.updateById(task);
        if (ok && "COMPLETED".equalsIgnoreCase(status) && "COATING".equalsIgnoreCase(task.getProcessType())) {
            scheduleTaskService.createRewindingFromCoatingBatch(task.getBatchId());
        }
        return ok ? ResponseResult.success("更新成功") : ResponseResult.error("更新失败");
    }

    @PostMapping("/status/by-order-item")
    public ResponseResult<String> updateStatusByOrderItem(@RequestParam Long orderItemId, @RequestParam String status) {
        List<ScheduleTask> tasks = scheduleTaskService.list(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScheduleTask>()
                        .eq("order_item_id", orderItemId)
        );
        boolean ok = scheduleTaskService.update(
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ScheduleTask>()
                        .eq("order_item_id", orderItemId)
                        .set("status", status)
        );

        if (!ok) {
            List<Long> batchIds = scheduleBatchOrderMapper.selectBatchIdsByOrderItemId(orderItemId);
            if (batchIds != null && !batchIds.isEmpty()) {
                tasks = scheduleTaskService.list(
                        new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScheduleTask>()
                                .in("batch_id", batchIds)
                );
                ok = scheduleTaskService.update(
                        new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ScheduleTask>()
                                .in("batch_id", batchIds)
                                .set("status", status)
                );
            }
        }

        if (ok && tasks != null && !tasks.isEmpty() && "COMPLETED".equalsIgnoreCase(status)) {
            for (ScheduleTask t : tasks) {
                if (t != null && "COATING".equalsIgnoreCase(t.getProcessType())) {
                    scheduleTaskService.createRewindingFromCoatingBatch(t.getBatchId());
                }
            }
        }
        return ok ? ResponseResult.success("更新成功") : ResponseResult.error("更新失败");
    }

    @GetMapping("/batch/{batchId}")
    public ResponseResult<Map<String, Object>> batchDetail(@PathVariable Long batchId) {
        ScheduleBatch batch = scheduleBatchMapper.selectById(batchId);
        if (batch == null) {
            return ResponseResult.error("排程单不存在");
        }
        List<ScheduleBatchOrder> orders = scheduleBatchOrderMapper.selectByBatchId(batchId);
        Map<String, Object> data = new HashMap<>();
        data.put("batch", batch);
        data.put("orders", orders);
        return ResponseResult.success(data);
    }

    @GetMapping("/equipments")
    public ResponseResult<List<Equipment>> equipments(@RequestParam String processType) {
        List<Equipment> list = equipmentMapper.selectAvailableByType(processType);
        return ResponseResult.success(list);
    }

    @PostMapping("/{id}/plan")
    public ResponseResult<ScheduleTask> planTask(@PathVariable Long id, @RequestBody PlanRequest request) {
        if (request == null || request.getEquipmentId() == null) {
            return ResponseResult.error("缺少机台");
        }
        Date startTime = null;
        if (request.getStartTime() != null && !request.getStartTime().isEmpty()) {
            try {
                startTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(request.getStartTime());
            } catch (ParseException ignore) {
                return ResponseResult.error("开始时间格式错误");
            }
        }
        ScheduleTask updated = scheduleTaskService.assignPlan(id, request.getEquipmentId(), startTime);
        if (updated == null) {
            return ResponseResult.error("排程失败");
        }
        return ResponseResult.success(updated);
    }

    public static class PlanRequest {
        private Long equipmentId;
        private String startTime;

        public Long getEquipmentId() {
            return equipmentId;
        }

        public void setEquipmentId(Long equipmentId) {
            this.equipmentId = equipmentId;
        }

        public String getStartTime() {
            return startTime;
        }

        public void setStartTime(String startTime) {
            this.startTime = startTime;
        }
    }
}
