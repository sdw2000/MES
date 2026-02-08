package com.fine.service.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.model.production.*;

import java.util.List;
import java.util.Map;

public interface ProductionScheduleService {
    
    // ========== 排程主表操作 ==========
    
    IPage<ProductionSchedule> getScheduleList(Map<String, Object> params);
    ProductionSchedule getScheduleById(Long id);
    ProductionSchedule getScheduleByNo(String scheduleNo);
    ProductionSchedule createSchedule(ProductionSchedule schedule);
    int updateSchedule(ProductionSchedule schedule);
    int deleteSchedule(Long id, String operator);
    int confirmSchedule(Long id, String operator);
    int cancelSchedule(Long id, String operator);
    int publishSchedule(Long id, String operator);
    int completeSchedule(Long id, String operator);
    Map<String, Object> getScheduleStats();
    
    // ========== 涂布任务 ==========
    
    IPage<ScheduleCoating> getCoatingTasks(Map<String, Object> params);
    List<ScheduleCoating> getCoatingTasksByScheduleId(Long scheduleId);
    ScheduleCoating addCoatingTask(ScheduleCoating task);
    int updateCoatingTask(ScheduleCoating task);
    int deleteCoatingTask(Long id);
    int startCoatingTask(Long taskId, String operator);
    int completeCoatingTask(Long taskId, String outputBatchNo, String operator);
    
    // ========== 复卷任务 ==========
    
    IPage<ScheduleRewinding> getRewindingTasks(Map<String, Object> params);
    ScheduleRewinding addRewindingTask(ScheduleRewinding rewinding);
    int updateRewindingTask(ScheduleRewinding rewinding);
    
    // ========== 分切任务 ==========
    
    IPage<ScheduleSlitting> getSlittingTasks(Map<String, Object> params);
    ScheduleSlitting addSlittingTask(ScheduleSlitting slitting);
    int updateSlittingTask(ScheduleSlitting slitting);
    int deleteSlittingTask(Long id);
    int startSlittingTask(Long taskId, String operator);
    int completeSlittingTask(Long taskId, Integer actualRolls, String operator);
    
    // ========== 分条计划 ==========
    
    IPage<ScheduleStripping> getStrippingTasks(Map<String, Object> params);
    ScheduleStripping addStrippingTask(ScheduleStripping stripping);
    int updateStrippingTask(ScheduleStripping stripping);
    
    // ========== 生产看板 ==========
    
    List<Map<String, Object>> getEquipmentBoard(String planDate);
    Map<String, Object> getProgressBoard(String planDate);
    
    // ========== 审批流程 ==========
    
    int submitApproval(Long scheduleId, String operator);
    int approveSchedule(Long scheduleId, boolean approved, String remark, String operator);
    List<ScheduleApprovalLog> getApprovalLogs(Long scheduleId);
    
    // ========== 质检反馈 ==========
    
    IPage<QualityInspection> getInspectionList(Map<String, Object> params);
    QualityInspection getInspectionById(Long id);
    QualityInspection submitInspection(QualityInspection inspection);
    int updateInspection(QualityInspection inspection);
    List<QualityInspection> getInspectionByTask(String taskType, Long taskId);
    Map<String, Object> getInspectionStatistics();
    
    // ========== 生产报告 ==========
    
    IPage<ProductionReport> getReportList(Map<String, Object> params);
    ProductionReport submitReport(ProductionReport report);
    List<Map<String, Object>> getTodayOutput();
    
    // ========== 排程明细 ==========
    
    IPage<ScheduleOrderItem> getScheduleOrderItems(Map<String, Object> params);
    int addScheduleOrderItem(ScheduleOrderItem item);
    int updateScheduleOrderItem(ScheduleOrderItem item);
    int deleteScheduleOrderItem(Long id);
    
    // ========== 印刷计划 ==========
    
    IPage<SchedulePrinting> getPrintingTasks(Map<String, Object> params);
    SchedulePrinting addPrintingTask(SchedulePrinting printing);
    int updatePrintingTask(SchedulePrinting printing);
    int startPrintingTask(Long taskId, String operator);
    int completePrintingTask(Long taskId, String outputBatchNo, String operator);
    
    // ========== 其他方法 ==========
    
    IPage<Map<String, Object>> getPendingOrderItems(Map<String, Object> params);
    Map<String, Object> getScheduleStatistics();
    ProductionSchedule autoSchedule(Map<String, Object> params);
    // 动态排程新方法
    ProductionSchedule autoSchedule(List<Long> orderItemIds, Map<Long, Integer> itemQuantities, String planDate, String operator);
    IPage<com.fine.entity.PendingScheduleOrder> getPendingOrders(Map<String, Object> params);
    List<com.fine.entity.PendingScheduleOrder> getPendingOrdersGroupByMaterial();
    List<com.fine.entity.PendingScheduleOrder> getPendingOrdersByMaterial(String materialCode);
    List<ScheduleCoating> autoScheduleCoating(String materialCode, Integer filmWidth, String operator);
    List<ScheduleCoating> batchScheduleCoating(List<Long> orderItemIds, Integer filmWidth, String planDate, String operator);
    UrgentOrderLog createUrgentOrder(UrgentOrderLog urgentOrder);
    IPage<UrgentOrderLog> getUrgentOrderList(Map<String, Object> params);
    boolean approveUrgentOrder(Long orderId, boolean approved, String remark, String operator);
    boolean executeUrgentOrder(Long orderId, String operator);
    Map<String, Object> getMaterialInfoByCode(String materialCode);
    List<Map<String, Object>> getGanttData(Map<String, Object> params);
    
    // ========== 涂布排程看板接口 ==========
    
    /**
     * 获取涂布任务队列
     */
    List<Map<String, Object>> getCoatingQueue(String planDate);
    
    /**
     * 获取涂布时间轴数据
     */
    List<Map<String, Object>> getCoatingTimeline(String planDate);
    
    /**
     * 获取涂布合并记录
     * @deprecated 前端已移除“涂布合并记录”板块，不再对外展示该数据；
     *             如业务需要，请改走统计报表或联系排程组确认新接口。
     */
    @Deprecated
    List<Map<String, Object>> getCoatingMergeRecords(String planDate);
    
    /**
     * 获取涂布统计数据
     */
    Map<String, Object> getCoatingStats();

    /**
     * 获取复卷汇总（按料号+长度聚合），用于前端复卷汇总视图
     */
    List<Map<String, Object>> getRewindSummary();
    
    /**
     * 获取涂布原材料锁定情况
     * @deprecated 前端已移除“原材料锁定情况”板块；请改用库存锁定机制相关接口与页面。
     */
    @Deprecated
    List<Map<String, Object>> getCoatingMaterialLocks();
    
    /**
     * 调整涂布任务时间
     */
    boolean adjustCoatingTaskTime(Long taskId, Map<String, Object> data);

    /**
     * 调整涂布任务涂布量（㎡）
     */
    boolean adjustCoatingTaskQuantity(Long taskId, Map<String, Object> data);

    /**
     * 更新涂布任务的设备
     */
    boolean updateCoatingEquipment(Long taskId, Long equipmentId);

    /**
     * 更新复卷任务的设备并重新排时间
     */
    boolean updateRewindingEquipment(Long taskId, Long equipmentId);
    
    /**
     * 获取涂布任务详情（含关联订单）
     */
    Map<String, Object> getCoatingTaskDetail(Long taskId);

    /**
     * 按顺序重算涂布任务的开始/结束时间
     * @param planDate 计划日期(yyyy-MM-dd)，可空
     * @param gapMinutes 任务间准备时间(分钟)，可空默认10
     * @return 是否执行成功
     */
    boolean recalculateCoatingPlan(String planDate, Integer gapMinutes);

    /**
     * 调整复卷任务时间
     */
    boolean adjustRewindingTaskTime(Long taskId, Map<String, Object> data);
    
    // ========== 待涂布订单池接口 ==========
    
    /**
     * 获取待涂布订单池列表
     */
    Map<String, Object> getPendingCoatingPool(Map<String, Object> params);
    
    /**
     * 按料号分组获取待涂布订单
     */
    List<Map<String, Object>> getPendingCoatingByMaterial();
    
    /**
     * 添加订单到待涂布池
     */
    void addToPendingCoatingPool(Map<String, Object> data);
    
    /**
     * 从待涂布池移除订单
     */
    void removeFromPendingCoatingPool(Long poolId, String operator);
    
    /**
     * 生成涂布排程任务
     */
    Map<String, Object> generateCoatingTasks(Map<String, Object> data);
}
