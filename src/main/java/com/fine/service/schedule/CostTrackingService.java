package com.fine.service.schedule;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.model.production.CostTracking;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 成本追溯 Service
 */
public interface CostTrackingService extends IService<CostTracking> {
    
    /**
     * 初始化成本追溯（订单创建时）
     */
    CostTracking initializeCostTracking(Long orderId);
    
    /**
     * 更新物料成本
     */
    void updateMaterialCost(Long orderId, BigDecimal cost);
    
    /**
     * 更新分切成本
     */
    void updateSlittingCost(Long orderId, BigDecimal cost);
    
    /**
     * 更新涂布成本
     */
    void updateCoatingCost(Long orderId, BigDecimal cost);
    
    /**
     * 更新人工成本
     */
    void updateLaborCost(Long orderId, BigDecimal cost);
    
    /**
     * 完成成本汇总
     */
    void completeCostTracking(Long orderId, Integer finishedQty);
    
    /**
     * 分页查询成本
     */
    IPage<Map<String, Object>> getCostTrackingPage(Integer pageNum, Integer pageSize, String orderNo);
    
    /**
     * 获取订单总成本
     */
    Map<String, Object> getOrderCost(Long orderId);
}
