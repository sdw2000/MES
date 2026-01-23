// ============================================================
// 后端实现代码 - 批排程功能 (方案A)
// 文件: com/fine/model/production/ScheduleOrderItem.java
// ============================================================

package com.fine.model.production;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 排程订单明细 - 记录每一次排程操作
 */
public class ScheduleOrderItem {
    
    private Long id;
    
    /** 关键字段 */
    private Long orderItemId;        // 关联sales_order_items.id
    private Integer scheduleQty;     // 本次排程数量
    
    /** 排程信息 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate scheduleDate;  // 排程日期
    private String scheduleNo;       // 排程单号
    private Long scheduleId;         // 关联主排程单
    
    /** 状态 */
    private String status;           // pending/confirmed/producing/completed/cancelled
    
    /** 审计字段 */
    private String createdBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdTime;
    private String updatedBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedTime;
    
    // ===== Getters & Setters =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getOrderItemId() { return orderItemId; }
    public void setOrderItemId(Long orderItemId) { this.orderItemId = orderItemId; }
    
    public Integer getScheduleQty() { return scheduleQty; }
    public void setScheduleQty(Integer scheduleQty) { this.scheduleQty = scheduleQty; }
    
    public LocalDate getScheduleDate() { return scheduleDate; }
    public void setScheduleDate(LocalDate scheduleDate) { this.scheduleDate = scheduleDate; }
    
    public String getScheduleNo() { return scheduleNo; }
    public void setScheduleNo(String scheduleNo) { this.scheduleNo = scheduleNo; }
    
    public Long getScheduleId() { return scheduleId; }
    public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    
    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
    
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    
    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }
}

// ============================================================
// DAO 层
// 文件: com/fine/Dao/ScheduleOrderItemDAO.java
// ============================================================

package com.fine.Dao;

import com.fine.model.production.ScheduleOrderItem;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ScheduleOrderItemDAO {
    
    /**
     * 插入排程记录
     */
    @Insert("INSERT INTO schedule_order_item " +
            "(order_item_id, schedule_qty, schedule_date, schedule_no, status, created_by, created_time) " +
            "VALUES (#{orderItemId}, #{scheduleQty}, #{scheduleDate}, #{scheduleNo}, #{status}, #{createdBy}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ScheduleOrderItem scheduleItem);
    
    /**
     * 根据ID查询排程记录
     */
    @Select("SELECT * FROM schedule_order_item WHERE id = #{id}")
    ScheduleOrderItem selectById(Long id);
    
    /**
     * 根据订单明细ID查询所有排程记录（排程历史）
     */
    @Select("SELECT * FROM schedule_order_item " +
            "WHERE order_item_id = #{orderItemId} " +
            "ORDER BY schedule_date DESC, created_time DESC")
    List<ScheduleOrderItem> selectByOrderItemId(Long orderItemId);
    
    /**
     * 查询特定日期的排程记录
     */
    @Select("SELECT * FROM schedule_order_item " +
            "WHERE schedule_date = #{scheduleDate} " +
            "ORDER BY created_time DESC")
    List<ScheduleOrderItem> selectByScheduleDate(String scheduleDate);
    
    /**
     * 查询待排程的记录（status = 'pending'）
     */
    @Select("SELECT * FROM schedule_order_item " +
            "WHERE status = 'pending' " +
            "ORDER BY schedule_date, created_time")
    List<ScheduleOrderItem> selectPending();
    
    /**
     * 更新排程状态
     */
    @Update("UPDATE schedule_order_item " +
            "SET status = #{status}, updated_time = NOW() " +
            "WHERE id = #{id}")
    int updateStatus(Long id, String status);
    
    /**
     * 删除排程记录
     */
    @Delete("DELETE FROM schedule_order_item WHERE id = #{id}")
    int delete(Long id);
}

// ============================================================
// Service 实现
// 文件: com/fine/serviceIMPL/ScheduleServiceImpl.java
// 关键方法: batchSchedule()
// ============================================================

package com.fine.serviceIMPL;

import com.fine.Dao.ScheduleOrderItemDAO;
import com.fine.Dao.SalesOrderItemDAO;
import com.fine.Dao.ScheduleTaskDAO;
import com.fine.model.production.*;
import com.fine.service.ProductionScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProductionScheduleServiceImpl implements ProductionScheduleService {
    
    @Autowired
    private ScheduleOrderItemDAO scheduleOrderItemDAO;
    
    @Autowired
    private SalesOrderItemDAO salesOrderItemDAO;
    
    @Autowired
    private ScheduleTaskDAO scheduleTaskDAO;
    
    /**
     * 批量排程 - 核心方法
     * 
     * 业务流程：
     * 1. 验证每个排程项的数据有效性
     * 2. 创建排程关联记录（schedule_order_item）
     * 3. 更新订单明细的待排数量（pending_qty）
     * 4. 为每个排程创建工序任务（schedule_task）
     * 
     * @param request 批排程请求
     * @return 返回排程结果
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> batchSchedule(BatchScheduleRequest request) {
        List<ScheduleDetail> details = request.getDetails();
        String scheduleDate = request.getScheduleDate();
        String operator = request.getOperator();
        
        if (details == null || details.isEmpty()) {
            throw new RuntimeException("排程明细不能为空");
        }
        
        // ===== 第1步：验证 =====
        for (ScheduleDetail detail : details) {
            validateScheduleDetail(detail);
        }
        
        // ===== 第2步：创建排程关联记录 =====
        List<ScheduleOrderItem> scheduleItems = new ArrayList<>();
        for (ScheduleDetail detail : details) {
            ScheduleOrderItem scheduleItem = new ScheduleOrderItem();
            scheduleItem.setOrderItemId(detail.getOrderItemId());
            scheduleItem.setScheduleQty(detail.getScheduleQty());
            scheduleItem.setScheduleDate(LocalDate.parse(scheduleDate));
            scheduleItem.setStatus("pending");
            scheduleItem.setCreatedBy(operator);
            
            // 插入数据库
            scheduleOrderItemDAO.insert(scheduleItem);
            scheduleItems.add(scheduleItem);
        }
        
        // ===== 第3步：更新待排数量 =====
        for (ScheduleDetail detail : details) {
            // 这是最关键的操作！
            updatePendingQty(detail.getOrderItemId(), detail.getScheduleQty());
        }
        
        // ===== 第4步：创建工序任务 =====
        for (ScheduleOrderItem scheduleItem : scheduleItems) {
            createScheduleTasks(scheduleItem);
        }
        
        // ===== 返回结果 =====
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("scheduleCount", details.size());
        result.put("totalQty", details.stream()
            .mapToInt(ScheduleDetail::getScheduleQty)
            .sum());
        result.put("scheduleItems", scheduleItems);
        result.put("message", 
            String.format("成功排程 %d 个订单，共 %d 卷",
                details.size(),
                details.stream().mapToInt(ScheduleDetail::getScheduleQty).sum())
        );
        
        return result;
    }
    
    /**
     * 验证排程项的合法性
     */
    private void validateScheduleDetail(ScheduleDetail detail) {
        // 验证1: 订单明细是否存在
        SalesOrderItem orderItem = salesOrderItemDAO.selectById(detail.getOrderItemId());
        if (orderItem == null) {
            throw new RuntimeException(
                String.format("订单明细不存在: ID=%d", detail.getOrderItemId())
            );
        }
        
        // 验证2: 排程数量是否大于0
        if (detail.getScheduleQty() == null || detail.getScheduleQty() <= 0) {
            throw new RuntimeException(
                String.format("排程数量必须大于0，订单: %s", orderItem.getMaterialCode())
            );
        }
        
        // 验证3: 排程数量是否不超过待排数量 ★ 关键验证
        if (detail.getScheduleQty() > orderItem.getPendingQty()) {
            throw new RuntimeException(
                String.format(
                    "订单 %s 排程数量 %d 超过待排数量 %d",
                    orderItem.getMaterialCode(),
                    detail.getScheduleQty(),
                    orderItem.getPendingQty()
                )
            );
        }
    }
    
    /**
     * 更新待排数量 ★ 关键方法
     * 
     * SQL: UPDATE sales_order_items 
     *      SET pending_qty = pending_qty - ?,
     *          scheduled_qty = scheduled_qty + ?
     *      WHERE id = ?
     */
    private void updatePendingQty(Long orderItemId, Integer scheduleQty) {
        // 先查询当前的待排数量
        SalesOrderItem orderItem = salesOrderItemDAO.selectById(orderItemId);
        if (orderItem == null) {
            throw new RuntimeException("订单明细不存在");
        }
        
        int currentPending = orderItem.getPendingQty();
        int newPending = currentPending - scheduleQty;
        
        // 验证更新后的数量是否合法
        if (newPending < 0) {
            throw new RuntimeException(
                String.format(
                    "待排数量不足：当前%d，本次排程%d",
                    currentPending,
                    scheduleQty
                )
            );
        }
        
        // 执行更新
        salesOrderItemDAO.updatePendingQty(orderItemId, scheduleQty);
    }
    
    /**
     * 为排程记录创建工序任务
     * 
     * 根据产品信息，创建对应的涂布、复卷、分切、分条等工序任务
     */
    private void createScheduleTasks(ScheduleOrderItem scheduleItem) {
        // 获取订单明细信息
        SalesOrderItem orderItem = salesOrderItemDAO.selectById(
            scheduleItem.getOrderItemId()
        );
        
        Integer quantity = scheduleItem.getScheduleQty();
        Long scheduleItemId = scheduleItem.getId();
        
        // 根据产品类型创建不同的工序
        // 这里是示例，实际应根据产品配置动态创建
        
        // 1. 涂布工序
        createTaskForType(scheduleItemId, "coating", quantity);
        
        // 2. 复卷工序
        createTaskForType(scheduleItemId, "rewinding", quantity);
        
        // 3. 分切工序（如有需要）
        // createTaskForType(scheduleItemId, "slitting", quantity);
        
        // 4. 分条工序（如有需要）
        // createTaskForType(scheduleItemId, "stripping", quantity);
    }
    
    /**
     * 为特定工序创建任务
     */
    private void createTaskForType(Long scheduleItemId, String taskType, Integer quantity) {
        ScheduleTask task = new ScheduleTask();
        task.setScheduleItemId(scheduleItemId);
        task.setTaskType(taskType);
        task.setQuantity(quantity);
        task.setStatus("pending");
        
        scheduleTaskDAO.insert(task);
    }
    
    /**
     * 获取待排程订单
     */
    public List<SalesOrderItemVO> getPendingOrderItems(Map<String, Object> params) {
        return salesOrderItemDAO.selectPendingOrders(params);
    }
    
    /**
     * 查询排程历史
     */
    public List<ScheduleOrderItem> getScheduleHistory(Long orderItemId) {
        return scheduleOrderItemDAO.selectByOrderItemId(orderItemId);
    }
}

// ============================================================
// 请求DTO
// 文件: com/fine/model/production/BatchScheduleRequest.java
// ============================================================

package com.fine.model.production;

import java.util.List;

public class BatchScheduleRequest {
    
    private String scheduleDate;      // 排程日期
    private String scheduleType;      // 排程类型: order/safety
    private List<ScheduleDetail> details;  // 排程明细列表
    private String operator;          // 操作人
    
    // Getters & Setters
    public String getScheduleDate() { return scheduleDate; }
    public void setScheduleDate(String scheduleDate) { this.scheduleDate = scheduleDate; }
    
    public String getScheduleType() { return scheduleType; }
    public void setScheduleType(String scheduleType) { this.scheduleType = scheduleType; }
    
    public List<ScheduleDetail> getDetails() { return details; }
    public void setDetails(List<ScheduleDetail> details) { this.details = details; }
    
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
}

// ============================================================
// 排程明细DTO
// 文件: com/fine/model/production/ScheduleDetail.java
// ============================================================

package com.fine.model.production;

public class ScheduleDetail {
    
    private Long orderItemId;    // 订单明细ID
    private Integer scheduleQty; // 本次排程数量
    
    // Getters & Setters
    public Long getOrderItemId() { return orderItemId; }
    public void setOrderItemId(Long orderItemId) { this.orderItemId = orderItemId; }
    
    public Integer getScheduleQty() { return scheduleQty; }
    public void setScheduleQty(Integer scheduleQty) { this.scheduleQty = scheduleQty; }
}

// ============================================================
// Controller 接口
// 文件: com/fine/controller/production/ProductionScheduleController.java
// 新增方法
// ============================================================

package com.fine.controller.production;

import com.fine.Utils.ResponseResult;
import com.fine.model.production.*;
import com.fine.service.ProductionScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/production/schedule")
@CrossOrigin
public class ProductionScheduleController {
    
    @Autowired
    private ProductionScheduleService scheduleService;
    
    /**
     * 获取待排程订单
     */
    @GetMapping("/pending-orders")
    public ResponseResult<Map<String, Object>> getPendingOrderItems(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String customerLevel,
            @RequestParam(required = false) String materialCode) {
        
        Map<String, Object> params = new HashMap<>();
        params.put("pageNum", pageNum);
        params.put("pageSize", pageSize);
        params.put("customerLevel", customerLevel);
        params.put("materialCode", materialCode);
        
        try {
            List<SalesOrderItemVO> list = scheduleService.getPendingOrderItems(params);
            
            Map<String, Object> result = new HashMap<>();
            result.put("list", list);
            result.put("total", list.size()); // 实际应使用分页返回
            
            return ResponseResult.success(result);
        } catch (Exception e) {
            return ResponseResult.error("获取待排程订单失败: " + e.getMessage());
        }
    }
    
    /**
     * 批量排程 ★ 核心接口
     */
    @PostMapping("/batch-schedule")
    public ResponseResult<Map<String, Object>> batchSchedule(
            @RequestBody BatchScheduleRequest request) {
        
        try {
            // 验证请求
            if (request == null || request.getDetails() == null) {
                return ResponseResult.error("排程明细不能为空");
            }
            
            // 调用服务处理排程
            Map<String, Object> result = scheduleService.batchSchedule(request);
            
            return ResponseResult.success(result);
        } catch (Exception e) {
            return ResponseResult.error("批排程失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询排程历史
     */
    @GetMapping("/schedule-history/{orderItemId}")
    public ResponseResult<List<ScheduleOrderItem>> getScheduleHistory(
            @PathVariable Long orderItemId) {
        
        try {
            List<ScheduleOrderItem> history = scheduleService.getScheduleHistory(orderItemId);
            return ResponseResult.success(history);
        } catch (Exception e) {
            return ResponseResult.error("获取排程历史失败: " + e.getMessage());
        }
    }
}
