package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 涂布排程
 * 动态分配待涂布物料到具体的涂布机台和时间段
 */
@Data
@TableName("coating_schedule")
public class CoatingSchedule {
    
    @TableId
    private Long id;
    
    @TableField("schedule_code")
    private String scheduleCode;
    
    @TableField("pool_id")
    private Long poolId;
    
    @TableField("order_id")
    private Long orderId;
    
    @TableField("order_no")
    private String orderNo;
    
    @TableField("equipment_id")
    private Long equipmentId;
    
    @TableField("equipment_name")
    private String equipmentName;
    
    @TableField("film_width")
    private BigDecimal filmWidth;
    
    @TableField("qty")
    private Integer qty;
    
    @TableField("scheduled_start")
    private Date scheduledStart;
    
    @TableField("scheduled_end")
    private Date scheduledEnd;
    
    @TableField("actual_start")
    private Date actualStart;
    
    @TableField("actual_end")
    private Date actualEnd;
    
    @TableField("estimated_time_minutes")
    private Integer estimatedTimeMinutes;
    
    @TableField("status")
    private String status; // PENDING, RUNNING, COMPLETED, CANCELLED
    
    @TableField("conflict_status")
    private String conflictStatus; // CLEAN, WARNING, ERROR
    
    @TableField("customer_priority")
    private BigDecimal customerPriority;
    
    @TableField("created_at")
    private Date createdAt;
    
    @TableField("updated_at")
    private Date updatedAt;
}
