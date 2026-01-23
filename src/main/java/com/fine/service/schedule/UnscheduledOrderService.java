package com.fine.service.schedule;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;
import java.util.Map;

/**
 * 未排程订单 Service
 * 主要用于查询和管理未排程的订单
 */
public interface UnscheduledOrderService extends IService<Map> {
    
    /**
     * 分页查询未排程订单列表
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @param orderNo 订单号(可选)
     * @param customerName 客户名称(可选)
     * @param scheduleStatus 排程状态(可选): PENDING/PARTIAL
     * @param materialCode 物料代码(可选)
     * @param statusBadge 操作建议(可选): 可直接排/需复卷分切/需手动处理/已入池待排程
     * @return 未排程订单页面
     */
    IPage<Map<String, Object>> getUnscheduledOrdersPage(
        Integer pageNum, 
        Integer pageSize, 
        String orderNo, 
        String customerName,
        String scheduleStatus,
        String materialCode,
        String statusBadge
    );
    
    /**
     * 获取未排程订单详情（包含物料明细、缺口信息）
     * @param orderId 订单ID
     * @return 订单的完整信息
     */
    Map<String, Object> getUnscheduledOrderDetail(Long orderId);
    
    /**
     * 获取订单的物料缺口详情
     * @param orderId 订单ID
     * @return 该订单所有的缺口信息
     */
    List<Map<String, Object>> getOrderShortages(Long orderId);
    
    /**
     * 获取订单可用的源物料（用于复卷分切）
     * @param orderId 订单ID
     * @return 可用源物料列表
     */
    List<Map<String, Object>> getAvailableSourceMaterials(Long orderId);
    
    /**
     * 获取未排程订单的统计信息
     * @return 包含：总数、可直接排、需分切、需手动处理的统计
     */
    Map<String, Object> getUnscheduledOrdersStats();
    
    /**
     * 进入待排程池（库存充足的订单）
     * @param orderId 订单ID
     * @param operator 操作人
     * @return 创建成功的池记录ID列表
     */
    List<Long> enterCoatingPool(Long orderId, String operator);
    
    /**
     * 批量进入待排程池
     * @param orderIds 订单ID列表
     * @param operator 操作人
     * @return 成功进入的订单数
     */
    Integer batchEnterCoatingPool(List<Long> orderIds, String operator);
    
    /**
     * 创建复卷分切任务（缺口处理）
     * @param shortageId 缺口ID
     * @param equipmentId 分切设备ID
     * @param scheduledDate 计划分切日期
     * @param operator 操作人
     * @return 创建的分切任务ID
     */
    Long createSlittingTaskForShortage(Long shortageId, Long equipmentId, String scheduledDate, String operator);
    
    /**
     * 刷新订单的排程状态
     * @param orderId 订单ID
     * @return 刷新后的订单状态
     */
    Map<String, Object> refreshOrderScheduleStatus(Long orderId);
    
    /**
     * 提高订单的优先级
     * @param orderId 订单ID
     * @param increment 优先级增加值
     * @param operator 操作人
     * @return 更新后的优先级
     */
    Double increaseOrderPriority(Long orderId, Double increment, String operator);
    
    /**
     * 获取订单的排程进度
     * @param orderId 订单ID
     * @return 排程进度信息
     */
    Map<String, Object> getOrderScheduleProgress(Long orderId);
}
