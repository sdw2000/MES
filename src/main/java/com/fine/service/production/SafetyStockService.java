package com.fine.service.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.model.production.SafetyStock;

import java.util.List;
import java.util.Map;

public interface SafetyStockService extends IService<SafetyStock> {
    
    /**
     * 获取安全库存分页
     */
    IPage<SafetyStock> getSafetyStockPage(String materialCode, String stockType, 
                                         Boolean lowStockOnly, Integer page, Integer size);
    
    /**
     * 获取安全库存列表
     */
    Map<String, Object> getSafetyStockList(String materialCode, String stockType,
                                          Boolean lowStockOnly, Integer page, Integer size);
    
    /**
     * 根据料号和库存类型获取
     */
    SafetyStock getByMaterialAndType(String materialCode, String stockType);
    
    /**
     * 添加安全库存配置
     */
    boolean addSafetyStock(SafetyStock safetyStock);
    
    /**
     * 更新安全库存配置
     */
    boolean updateSafetyStock(SafetyStock safetyStock);
    
    /**
     * 删除安全库存配置
     */
    boolean deleteSafetyStock(Long id);
    
    /**
     * 获取需要补货的列表
     */
    List<SafetyStock> getNeedRestockList();
    
    /**
     * 获取库存预警统计
     */
    Map<String, Object> getStockWarningStats();
}
