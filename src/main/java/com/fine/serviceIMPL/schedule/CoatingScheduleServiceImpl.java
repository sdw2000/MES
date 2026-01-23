package com.fine.serviceIMPL.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.mapper.schedule.CoatingScheduleMapper;
import com.fine.mapper.schedule.PendingCoatingPoolMapper;
import com.fine.model.production.CoatingSchedule;
import com.fine.model.production.PendingCoatingPool;
import com.fine.service.schedule.CoatingScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * 涂布排程 Service 实现
 */
@Service
public class CoatingScheduleServiceImpl extends ServiceImpl<CoatingScheduleMapper, CoatingSchedule> implements CoatingScheduleService {
    
    @Autowired
    private CoatingScheduleMapper scheduleMapper;
    
    @Autowired
    private PendingCoatingPoolMapper poolMapper;
    
    @Override
    public CoatingSchedule scheduleFromPool(Long poolId, Long equipmentId, Date scheduledStart) {
        PendingCoatingPool pool = poolMapper.selectById(poolId);
        if (pool == null) {
            throw new RuntimeException("待涂布池不存在：" + poolId);
        }
        
        CoatingSchedule schedule = new CoatingSchedule();
        schedule.setScheduleCode("CS-" + System.currentTimeMillis());
        schedule.setPoolId(poolId);
        schedule.setOrderId(pool.getOrderId());
        schedule.setOrderNo(pool.getOrderNo());
        schedule.setEquipmentId(equipmentId);
        schedule.setFilmWidth(pool.getFilmWidth());
        schedule.setQty(pool.getQty());
        schedule.setScheduledStart(scheduledStart);
        schedule.setScheduledEnd(new Date(scheduledStart.getTime() + 3600000)); // +1小时估算
        schedule.setEstimatedTimeMinutes(60);
        schedule.setStatus("PENDING");
        schedule.setConflictStatus("CLEAN");
        schedule.setCustomerPriority(pool.getCustomerPriority());
        schedule.setCreatedAt(new Date());
        schedule.setUpdatedAt(new Date());
        
        scheduleMapper.insert(schedule);
        
        pool.setStatus("SCHEDULED");
        pool.setCoatingScheduleId(schedule.getId());
        pool.setUpdatedAt(new Date());
        poolMapper.updateById(pool);
        
        return schedule;
    }
    
    @Override
    public IPage<Map<String, Object>> getSchedulePage(Integer pageNum, Integer pageSize, String scheduleCode, String equipmentName, String status) {
        LambdaQueryWrapper<CoatingSchedule> wrapper = new LambdaQueryWrapper<>();
        
        if (scheduleCode != null && !scheduleCode.isEmpty()) {
            wrapper.like(CoatingSchedule::getScheduleCode, scheduleCode);
        }
        if (equipmentName != null && !equipmentName.isEmpty()) {
            wrapper.like(CoatingSchedule::getEquipmentName, equipmentName);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(CoatingSchedule::getStatus, status);
        }
        
        wrapper.orderByDesc(CoatingSchedule::getCreatedAt);
        
        IPage<CoatingSchedule> page = this.page(new Page<>(pageNum, pageSize), wrapper);
        
        IPage<Map<String, Object>> resultPage = new Page<>(pageNum, pageSize);
        resultPage.setTotal(page.getTotal());
        
        List<Map<String, Object>> records = new ArrayList<>();
        for (CoatingSchedule schedule : page.getRecords()) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", schedule.getId());
            map.put("scheduleCode", schedule.getScheduleCode());
            map.put("orderNo", schedule.getOrderNo());
            map.put("equipmentName", schedule.getEquipmentName());
            map.put("qty", schedule.getQty());
            map.put("filmWidth", schedule.getFilmWidth());
            map.put("status", schedule.getStatus());
            map.put("conflictStatus", schedule.getConflictStatus());
            map.put("customerPriority", schedule.getCustomerPriority());
            map.put("scheduledStart", schedule.getScheduledStart());
            map.put("actualStart", schedule.getActualStart());
            map.put("actualEnd", schedule.getActualEnd());
            records.add(map);
        }
        resultPage.setRecords(records);
        
        return resultPage;
    }
    
    @Override
    public Map<String, Object> checkEquipmentConflicts(Long equipmentId, Date startTime, Date endTime) {
        LambdaQueryWrapper<CoatingSchedule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CoatingSchedule::getEquipmentId, equipmentId)
                .eq(CoatingSchedule::getStatus, "RUNNING")
                .between(CoatingSchedule::getScheduledStart, startTime, endTime);
        
        long conflictCount = this.count(wrapper);
        
        Map<String, Object> result = new HashMap<>();
        result.put("equipmentId", equipmentId);
        result.put("conflictCount", conflictCount);
        result.put("hasConflict", conflictCount > 0);
        result.put("message", conflictCount > 0 ? "存在时间冲突" : "无冲突");
        
        return result;
    }
    
    @Override
    public void completeSchedule(Long scheduleId, Date actualEnd) {
        CoatingSchedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new RuntimeException("排程不存在：" + scheduleId);
        }
        
        schedule.setStatus("COMPLETED");
        schedule.setActualEnd(actualEnd);
        schedule.setUpdatedAt(new Date());
        scheduleMapper.updateById(schedule);
        
        PendingCoatingPool pool = poolMapper.selectById(schedule.getPoolId());
        if (pool != null) {
            pool.setStatus("COMPLETED");
            pool.setUpdatedAt(new Date());
            poolMapper.updateById(pool);
        }
    }
    
    @Override
    public void cancelSchedule(Long scheduleId) {
        CoatingSchedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new RuntimeException("排程不存在：" + scheduleId);
        }
        
        schedule.setStatus("CANCELLED");
        schedule.setUpdatedAt(new Date());
        scheduleMapper.updateById(schedule);
        
        PendingCoatingPool pool = poolMapper.selectById(schedule.getPoolId());
        if (pool != null) {
            pool.setStatus("WAITING");
            pool.setCoatingScheduleId(null);
            pool.setUpdatedAt(new Date());
            poolMapper.updateById(pool);
        }
    }
    
    @Override
    public Map<String, Object> getScheduleStats() {
        LambdaQueryWrapper<CoatingSchedule> pendingWrapper = new LambdaQueryWrapper<>();
        pendingWrapper.eq(CoatingSchedule::getStatus, "PENDING");
        long pendingCount = this.count(pendingWrapper);
        
        LambdaQueryWrapper<CoatingSchedule> runningWrapper = new LambdaQueryWrapper<>();
        runningWrapper.eq(CoatingSchedule::getStatus, "RUNNING");
        long runningCount = this.count(runningWrapper);
        
        LambdaQueryWrapper<CoatingSchedule> completedWrapper = new LambdaQueryWrapper<>();
        completedWrapper.eq(CoatingSchedule::getStatus, "COMPLETED");
        long completedCount = this.count(completedWrapper);
        
        Map<String, Object> result = new HashMap<>();
        result.put("pendingCount", pendingCount);
        result.put("runningCount", runningCount);
        result.put("completedCount", completedCount);
        result.put("totalCount", pendingCount + runningCount + completedCount);
        
        return result;
    }
}
