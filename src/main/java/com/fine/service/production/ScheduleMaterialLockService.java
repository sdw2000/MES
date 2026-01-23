package com.fine.service.production;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.modle.stock.ScheduleMaterialLock;
import com.fine.model.production.ScheduleMaterialLockDTO;

import java.util.List;
import java.util.Map;

public interface ScheduleMaterialLockService extends IService<ScheduleMaterialLock> {
    
    /**
     * 锁定排程物料
     */
    boolean lockMaterialForSchedule(ScheduleMaterialLockDTO lockDTO);
    
    /**
     * 自动锁定物料
     */
    Map<String, Object> autoLockMaterial(Long scheduleId, Integer filmWidth, Integer filmThickness);
    
    /**
     * 释放排程物料锁定
     */
    boolean unlockMaterialForSchedule(Long scheduleId);
    
    /**
     * 获取已锁定物料列表
     */
    List<Map<String, Object>> getLockedMaterials(Long scheduleId);
    
    /**
     * 批量锁定物料
     */
    int batchLockMaterial(List<ScheduleMaterialLockDTO> lockDTOs);
}
