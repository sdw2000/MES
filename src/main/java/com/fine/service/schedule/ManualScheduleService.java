package com.fine.service.schedule;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.modle.schedule.ManualSchedule;

import java.util.List;
import java.util.Map;

/**
 * 手动排程服务
 */
public interface ManualScheduleService extends IService<ManualSchedule> {
    
    /**
     * 获取待排程订单列表
     */
    List<Map<String, Object>> getPendingOrders();
    
    /**
     * 获取已完成涂布待复卷的订单列表（按涂布日期排序）
     */
    List<Map<String, Object>> getCoatingCompletedOrders();
    
    /**
     * 匹配库存（先进先出）
     * @param materialCode 料号
     * @param width 宽度
     * @param thickness 厚度
     * @param requiredQty 需求数量（卷）
     * @return 匹配结果：库存列表、总可用数量、是否充足
     */
    Map<String, Object> matchStock(String materialCode, Integer width, Integer thickness, Integer requiredQty);
    
    /**
     * 计算涂布需求
     * @param orderNo 订单号
     * @param materialCode 料号
     * @return 涂布需求信息
     */
    Map<String, Object> calculateCoatingRequirement(String orderNo, String materialCode);

    /**
     * 获取涂布排程列表
     */
    List<Map<String, Object>> getCoatingSchedules();
    
    /**
     * 创建手动排程记录
     */
    boolean createSchedule(ManualSchedule schedule);
    
    /**
     * 锁定库存并创建复卷排程
     * @param scheduleId 手动排程ID
     * @param stockAllocations 库存分配列表 [{stockId, qty}]
     * @return 复卷排程ID
     */
    Long createRewindingSchedule(Long scheduleId, List<Map<String, Object>> stockAllocations);
    
    /**
     * 创建涂布排程
     * @param scheduleId 手动排程ID
     * @param coatingArea 涂布面积
     * @param coatingDate 排程日期
     * @param equipmentId 机台ID
     * @return 涂布排程ID
     */
    Long createCoatingSchedule(Long scheduleId, Double coatingArea, String coatingDate, String rewindingDate, String packagingDate, String equipmentId);
    
    /**
     * 确认排程 - 更新订单明细的已排程数量
     * @param orderNo 订单号
     * @param materialCode 料号
     * @param scheduleQty 本次排程数量
     * @return 是否成功
     */
    boolean confirmSchedule(String orderNo, String materialCode, Integer scheduleQty);
}
