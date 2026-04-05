package com.fine.service.schedule;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.modle.schedule.ManualSchedule;

import java.math.BigDecimal;
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
    * 获取待排程订单列表（可包含已拍完）
    */
   List<Map<String, Object>> getPendingOrders(boolean includeCompleted);

    /**
     * 分页获取待排程订单列表
     */
    IPage<Map<String, Object>> getPendingOrdersPage(long current, long size);

   /**
    * 分页获取待排程订单列表（可包含已拍完）
    */
   IPage<Map<String, Object>> getPendingOrdersPage(long current, long size, boolean includeCompleted);

   /**
    * 分页获取待排程订单列表（可包含已拍完，支持按订单号过滤）
    */
   IPage<Map<String, Object>> getPendingOrdersPage(long current, long size, boolean includeCompleted, String orderNo);
    
    /**
     * 获取已完成涂布待复卷的订单列表（按涂布日期排序）
     */
    List<Map<String, Object>> getCoatingCompletedOrders();

    /**
     * 分页获取已完成涂布待复卷订单
     */
    IPage<Map<String, Object>> getCoatingCompletedOrdersPage(long current, long size);
    
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
        * @param plannedArea 计划面积(㎡)
     * @return 涂布需求信息
     */
        Map<String, Object> calculateCoatingRequirement(String orderNo, String materialCode, java.math.BigDecimal plannedArea);

        /**
        * 保存涂布需求分配明细
        */
        boolean saveCoatingAllocationDetails(Long scheduleId, List<Map<String, Object>> details);

    /**
     * 获取涂布排程列表
     */
    List<Map<String, Object>> getCoatingSchedules();

    /**
     * 分页获取涂布排程列表
     */
    IPage<Map<String, Object>> getCoatingSchedulesPage(long current, long size);

    /**
     * 获取分切已排列表
     */
    List<Map<String, Object>> getSlittingSchedules();

    /**
     * 分页获取分切已排列表
     */
    IPage<Map<String, Object>> getSlittingSchedulesPage(long current, long size);

   /**
    * 分页获取分切已排列表（支持订单号过滤）
    */
   IPage<Map<String, Object>> getSlittingSchedulesPage(long current, long size, String orderNo);

    /**
     * 获取复卷已排列表
     */
    /**
     * 更新分切/包装日期
     */
   boolean updateSlittingInfo(Long scheduleId, String packagingDate, String slittingEquipment);
    List<Map<String, Object>> getRewindingSchedules();

    /**
     * 分页获取复卷已排列表
     */
    IPage<Map<String, Object>> getRewindingSchedulesPage(long current, long size);
    /**
     * 获取锁定库存列表
     */
    List<Map<String, Object>> getLockedStocks();
    
    /**
     * 创建手动排程记录
     */
    Long createSchedule(ManualSchedule schedule);
    
    /**
     * 锁定库存并创建复卷排程
     * @param scheduleId 手动排程ID
     * @param stockAllocations 库存分配列表 [{stockId, qty}]
     * @return 复卷排程ID
     */
    Long createRewindingSchedule(Long scheduleId, List<Map<String, Object>> stockAllocations);

    /**
     * 确认复卷排程（更新复卷日期/机台/已排面积）
     */
   boolean updateRewindingInfo(Long scheduleId, Double rewindingArea, String rewindingDate, String rewindingEquipment, Double rewindingWidth);
    
    /**
     * 创建涂布排程
     * @param scheduleId 手动排程ID
     * @param coatingArea 涂布面积
     * @param coatingDate 排程日期
     * @param equipmentId 机台ID
     * @return 涂布排程ID
     */
   Long createCoatingSchedule(Long scheduleId, Double coatingArea, String coatingDate, String rewindingDate, String packagingDate, String equipmentId,
                        Double coatingWidth, Double coatingLength);

   /**
    * 预估涂布机台占用（按料号+工序+机台速度计算）
    */
   Map<String, Object> previewCoatingOccupation(Long scheduleId, String equipmentId, String coatingDate, Double coatingLength);

   /**
    * 预估复卷机台占用（按料号+工序+机台速度计算）
    */
   Map<String, Object> previewRewindingOccupation(Long scheduleId, String rewindingEquipment, String rewindingDate);

   /**
    * 预估分切机台占用（按厚度+宽度+长度+机台速度计算）
    */
   Map<String, Object> previewSlittingOccupation(Long scheduleId, String slittingEquipment, String packagingDate);
    
    /**
     * 确认排程 - 更新订单明细的已排程数量
     * @param orderNo 订单号
     * @param materialCode 料号
     * @param scheduleQty 本次排程数量
     * @return 是否成功
     */
    boolean confirmSchedule(String orderNo, String materialCode, Integer scheduleQty, Long scheduleId, Long orderDetailId,
                            String coatingDate, String rewindingDate, String packagingDate);

   /**
    * 终止排程（保留已开工部分，回滚未开工部分）
    */
   boolean terminateSchedule(Long scheduleId, String reason, String operator);

   /**
    * 排程减量（仅回滚未开工部分）
    */
   boolean reduceSchedule(Long scheduleId, Integer reduceQty, String reason, String operator);

   /**
    * 工序报工
    */
   boolean reportProcessWork(Long scheduleId,
                             Long orderDetailId,
                             String processType,
                             String startTime,
                             String endTime,
                             BigDecimal producedQty,
                             Boolean proceedNextProcess,
                             List<Map<String, Object>> producedRolls,
                             List<Map<String, Object>> materialIssues,
                             String operator,
                             String remark);

   /**
    * 工序领料登记（可独立于报工）
    */
   boolean issueProcessMaterial(Long scheduleId,
                                Long orderDetailId,
                                String processType,
                                List<Map<String, Object>> materialIssues,
                                String operator,
                                String remark);

   /**
    * 查询工序报工明细
    */
   List<Map<String, Object>> getProcessWorkReports(Long scheduleId, String processType);

   /**
    * 更新工序报工记录
    */
   boolean updateProcessWorkReport(Long reportId,
                                   String startTime,
                                   String endTime,
                                   BigDecimal producedQty,
                                   Boolean proceedNextProcess,
                                   String operator,
                                   String remark);

   /**
    * 删除工序报工记录（软删除）
    */
   boolean deleteProcessWorkReport(Long reportId);

   /**
    * 查询订单锁定的涂布母卷明细
    */
   List<Map<String, Object>> getCoatingRollLocks(String orderNo);

   /**
    * 查询工序领料明细
    */
   List<Map<String, Object>> getProcessMaterialIssues(Long scheduleId, String processType);

   /**
    * 生成涂布母卷号：yyMMdd + 线号 + 班组 + 流水号(至少2位)
    */
   String generateNextCoatingRollCode(Long scheduleId, String workGroup, String productionDateTime);

   /**
    * 按订单明细获取最新排程ID
    */
   Long getLatestScheduleIdByOrderDetailId(Long orderDetailId);

   /**
    * 清空订单明细的排程数据并允许重排
    */
   boolean resetScheduleByOrderDetailId(Long orderDetailId, String reason, String operator);

   /**
    * 急单物料抢占：先锁未锁定库存；不足时释放低优先级订单锁定并转给急单
    */
   Map<String, Object> allocateUrgentOrderMaterial(String orderNo, String materialCode, BigDecimal requiredArea, String operator);

   /**
    * 获取急单抢占保护参数
    */
   Map<String, Object> getUrgentPreemptConfig();

   /**
    * 保存急单抢占保护参数
    */
   Map<String, Object> saveUrgentPreemptConfig(Map<String, Object> config, String operator);
}
