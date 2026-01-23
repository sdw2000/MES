package com.fine.service.schedule;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.model.schedule.OrderCustomerPriority;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 客户优先级计算服务接口
 */
public interface CustomerPriorityService {
    
    /**
     * 计算订单的客户优先级得分
     * @param orderId 订单ID
     * @param orderNo 订单编号
     * @param customerId 客户ID
     * @param materialCode 物料编号
     * @param unitPrice 订单单价
     * @param orderTime 下单时间
     * @return 优先级记录
     */
    OrderCustomerPriority calculateOrderPriority(Long orderId, String orderNo, Long customerId, 
                                                  String materialCode, BigDecimal unitPrice, 
                                                  java.util.Date orderTime);
    
    /**
     * 批量计算订单优先级
     * @param orderIds 订单ID列表
     * @return 优先级记录列表
     */
    List<OrderCustomerPriority> batchCalculateOrderPriority(List<Long> orderIds);

    /**
     * 批量计算订单优先级（仅参数名不同）
     */
    void calculatePriorities(List<Long> orderIds);
    
    /**
     * 根据优先级排序订单
     * @param orderIds 订单ID列表
     * @return 按优先级排序后的订单ID列表
     */
    List<Long> sortOrdersByPriority(List<Long> orderIds);
    
    /**
     * 更新客户交易统计数据（定时任务调用）
     */
    void updateCustomerTransactionStats();
    
    /**
     * 更新客户料号单价统计（定时任务调用）
     */
    void updateCustomerMaterialPriceStats();

    /**
     * 获取客户优先级分页列表
     */
    IPage<OrderCustomerPriority> getCustomerPriorityPage(Map<String, Object> params);

    /**
     * 根据ID获取优先级信息
     */
    OrderCustomerPriority getById(Long orderId);

    /**
     * 重新计算所有待排程订单的优先级
     */
    void recalculateAllPriorities();

    /**
     * 获取客户交易统计
     */
    Map<String, Object> getCustomerTransactionStats(Long customerId);

    /**
     * 获取客户料号单价统计
     */
    Map<String, Object> getCustomerMaterialPriceStats(Long customerId, String materialCode);
}

