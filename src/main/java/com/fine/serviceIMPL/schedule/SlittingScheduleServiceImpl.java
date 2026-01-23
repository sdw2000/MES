package com.fine.serviceIMPL.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.mapper.schedule.*;
import com.fine.model.production.*;
import com.fine.service.schedule.SlittingScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * 复卷分切 Service 实现
 */
@Service
public class SlittingScheduleServiceImpl extends ServiceImpl<SlittingTaskMapper, SlittingTask> implements SlittingScheduleService {
    
    @Autowired
    private SlittingTaskMapper taskMapper;
    
    @Autowired
    private MaterialLockShortageMapper shortageMapper;
    
    @Autowired
    private SlittingResultMapper resultMapper;
    
    @Override
    public SlittingTask createSlittingTask(Long shortageId, Long equipmentId, Date scheduledDate) {
        MaterialLockShortage shortage = shortageMapper.selectById(shortageId);
        if (shortage == null) {
            throw new RuntimeException("物料缺口不存在：" + shortageId);
        }
        
        SlittingTask task = new SlittingTask();
        task.setTaskCode("ST-" + System.currentTimeMillis());
        task.setShortageId(shortageId);
        task.setOrderId(shortage.getOrderId());
        task.setOrderNo(shortage.getOrderNo());
        task.setSourceMaterialCode(shortage.getMaterialCode());
        task.setTargetMaterialCode(shortage.getMaterialCode());
        task.setQty(shortage.getShortageQty());
        task.setSlittingEquipmentId(equipmentId);
        task.setStatus("PENDING");
        task.setScheduledDate(scheduledDate);
        task.setCreatedAt(new Date());
        task.setUpdatedAt(new Date());
        
        taskMapper.insert(task);
        
        shortage.setStatus("IN_SLITTING");
        shortage.setSlittingTaskId(task.getId());
        shortage.setUpdatedAt(new Date());
        shortageMapper.updateById(shortage);
        
        return task;
    }
    
    @Override
    public IPage<Map<String, Object>> getSlittingTaskPage(Integer pageNum, Integer pageSize, String taskCode, String status) {
        LambdaQueryWrapper<SlittingTask> wrapper = new LambdaQueryWrapper<>();
        
        if (taskCode != null && !taskCode.isEmpty()) {
            wrapper.like(SlittingTask::getTaskCode, taskCode);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(SlittingTask::getStatus, status);
        }
        
        wrapper.orderByDesc(SlittingTask::getCreatedAt);
        
        IPage<SlittingTask> page = this.page(new Page<>(pageNum, pageSize), wrapper);
        
        IPage<Map<String, Object>> resultPage = new Page<>(pageNum, pageSize);
        resultPage.setTotal(page.getTotal());
        
        List<Map<String, Object>> records = new ArrayList<>();
        for (SlittingTask task : page.getRecords()) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", task.getId());
            map.put("taskCode", task.getTaskCode());
            map.put("orderNo", task.getOrderNo());
            map.put("targetMaterialCode", task.getTargetMaterialCode());
            map.put("qty", task.getQty());
            map.put("status", task.getStatus());
            map.put("completedQty", task.getCompletedQty());
            map.put("wasteQty", task.getWasteQty());
            map.put("scheduledDate", task.getScheduledDate());
            map.put("actualStart", task.getActualStart());
            map.put("actualEnd", task.getActualEnd());
            records.add(map);
        }
        resultPage.setRecords(records);
        
        return resultPage;
    }
    
    @Override
    public void startSlittingTask(Long taskId) {
        SlittingTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("分切任务不存在：" + taskId);
        }
        
        task.setStatus("RUNNING");
        task.setActualStart(new Date());
        task.setUpdatedAt(new Date());
        taskMapper.updateById(task);
    }
    
    @Override
    public void completeSlittingTask(Long taskId, Integer completedQty, Integer wasteQty) {
        SlittingTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("分切任务不存在：" + taskId);
        }
        
        task.setStatus("COMPLETED");
        task.setCompletedQty(completedQty);
        task.setWasteQty(wasteQty);
        task.setActualEnd(new Date());
        task.setUpdatedAt(new Date());
        taskMapper.updateById(task);
        
        // 创建分切结果
        SlittingResult result = new SlittingResult();
        result.setTaskId(taskId);
        result.setTaskCode(task.getTaskCode());
        result.setTargetMaterialCode(task.getTargetMaterialCode());
        result.setQty(completedQty);
        result.setWasteQty(wasteQty);
        result.setBatchNo("BTH-" + System.currentTimeMillis());
        result.setProducedAt(new Date());
        result.setWarehouseStatus("PENDING");
        result.setCreatedAt(new Date());
        resultMapper.insert(result);
        
        // 更新缺口状态
        MaterialLockShortage shortage = shortageMapper.selectById(task.getShortageId());
        if (shortage != null) {
            shortage.setStatus("COMPLETED");
            shortage.setUpdatedAt(new Date());
            shortageMapper.updateById(shortage);
        }
    }
    
    @Override
    public void failSlittingTask(Long taskId, String reason) {
        SlittingTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("分切任务不存在：" + taskId);
        }
        
        task.setStatus("FAILED");
        task.setActualEnd(new Date());
        task.setUpdatedAt(new Date());
        taskMapper.updateById(task);
        
        MaterialLockShortage shortage = shortageMapper.selectById(task.getShortageId());
        if (shortage != null) {
            shortage.setStatus("FAILED");
            shortage.setRemark(reason);
            shortage.setUpdatedAt(new Date());
            shortageMapper.updateById(shortage);
        }
    }
    
    @Override
    public Map<String, Object> getSlittingStats() {
        LambdaQueryWrapper<SlittingTask> pendingWrapper = new LambdaQueryWrapper<>();
        pendingWrapper.eq(SlittingTask::getStatus, "PENDING");
        long pendingCount = this.count(pendingWrapper);
        
        LambdaQueryWrapper<SlittingTask> runningWrapper = new LambdaQueryWrapper<>();
        runningWrapper.eq(SlittingTask::getStatus, "RUNNING");
        long runningCount = this.count(runningWrapper);
        
        LambdaQueryWrapper<SlittingTask> completedWrapper = new LambdaQueryWrapper<>();
        completedWrapper.eq(SlittingTask::getStatus, "COMPLETED");
        long completedCount = this.count(completedWrapper);
        
        Map<String, Object> result = new HashMap<>();
        result.put("pendingCount", pendingCount);
        result.put("runningCount", runningCount);
        result.put("completedCount", completedCount);
        result.put("totalCount", pendingCount + runningCount + completedCount);
        
        return result;
    }
}
