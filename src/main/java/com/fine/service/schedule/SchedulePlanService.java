package com.fine.service.schedule;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.modle.schedule.SchedulePlan;

import java.util.List;
import java.util.Map;

public interface SchedulePlanService extends IService<SchedulePlan> {

    boolean upsertPlan(SchedulePlan plan);

    List<Map<String, Object>> getDailyPlan(String date);
}
