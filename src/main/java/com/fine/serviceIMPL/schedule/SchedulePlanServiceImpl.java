package com.fine.serviceIMPL.schedule;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.schedule.SchedulePlanMapper;
import com.fine.modle.schedule.SchedulePlan;
import com.fine.service.schedule.SchedulePlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class SchedulePlanServiceImpl extends ServiceImpl<SchedulePlanMapper, SchedulePlan> implements SchedulePlanService {

    @Autowired
    private SchedulePlanMapper planMapper;

    @Override
    @Transactional
    public boolean upsertPlan(SchedulePlan plan) {
        return planMapper.upsertPlan(plan) > 0;
    }

    @Override
    public List<Map<String, Object>> getDailyPlan(String date) {
        if (date == null || date.trim().isEmpty()) {
            return planMapper.selectAllPlan();
        }
        LocalDate day = LocalDate.parse(date);
        String start = day.toString() + " 00:00:00";
        String end = day.plusDays(1).toString() + " 00:00:00";
        return planMapper.selectDailyPlan(start, end);
    }
}
