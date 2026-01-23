package com.fine.service.schedule;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.model.production.SlittingTask;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 复卷分切 Service
 */
public interface SlittingScheduleService extends IService<SlittingTask> {
    
    /**
     * 从物料缺口创建分切任务
     */
    SlittingTask createSlittingTask(Long shortageId, Long equipmentId, Date scheduledDate);
    
    /**
     * 分页查询分切任务
     */
    IPage<Map<String, Object>> getSlittingTaskPage(Integer pageNum, Integer pageSize, String taskCode, String status);
    
    /**
     * 启动分切任务
     */
    void startSlittingTask(Long taskId);
    
    /**
     * 完成分切任务（回流库存）
     */
    void completeSlittingTask(Long taskId, Integer completedQty, Integer wasteQty);
    
    /**
     * 标记分切失败
     */
    void failSlittingTask(Long taskId, String reason);
    
    /**
     * 获取分切统计
     */
    Map<String, Object> getSlittingStats();
}
