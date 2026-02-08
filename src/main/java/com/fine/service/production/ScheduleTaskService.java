package com.fine.service.production;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.model.production.ScheduleTask;
import com.fine.model.production.ScheduleTaskPlanRequest;

import java.util.Map;

public interface ScheduleTaskService extends IService<ScheduleTask> {
    Map<String, Object> planTasks(ScheduleTaskPlanRequest request);

    ScheduleTask assignPlan(Long taskId, Long equipmentId, java.util.Date startTime);

    void createRewindingFromCoatingBatch(Long coatingBatchId);
}
