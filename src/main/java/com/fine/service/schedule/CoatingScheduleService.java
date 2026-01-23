package com.fine.service.schedule;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.model.production.CoatingSchedule;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 涂布排程 Service
 */
public interface CoatingScheduleService extends IService<CoatingSchedule> {
    
    /**
     * 从待涂布池创建排程
     */
    CoatingSchedule scheduleFromPool(Long poolId, Long equipmentId, Date scheduledStart);
    
    /**
     * 分页查询排程
     */
    IPage<Map<String, Object>> getSchedulePage(Integer pageNum, Integer pageSize, String scheduleCode, String equipmentName, String status);
    
    /**
     * 获取设备冲突情况
     */
    Map<String, Object> checkEquipmentConflicts(Long equipmentId, Date startTime, Date endTime);
    
    /**
     * 完成排程
     */
    void completeSchedule(Long scheduleId, Date actualEnd);
    
    /**
     * 取消排程
     */
    void cancelSchedule(Long scheduleId);
    
    /**
     * 获取排程统计信息
     */
    Map<String, Object> getScheduleStats();
}
