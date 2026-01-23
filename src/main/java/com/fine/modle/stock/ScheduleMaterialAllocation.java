package com.fine.modle.stock;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 排程物料分配表
 * 记录每个订单的物料分配汇总信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("schedule_material_allocation")
public class ScheduleMaterialAllocation {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 批排程ID */
    private Long scheduleId;
    
    /** 订单ID */
    private Long orderId;
    
    /** 订单需求的总面积(m²) */
    private BigDecimal requiredArea;
    
    /** 实际分配的面积(m²) */
    private BigDecimal allocatedArea;
    
    /** 不足面积(m²) = required - allocated */
    private BigDecimal shortageArea;
    
    /** 分配状态：完全满足、部分满足、未满足 */
    private String allocationStatus;
    
    /** 是否需要触发涂布排程(0/1) */
    private Integer needCoating;
    
    /** 关联的涂布排程ID */
    private Long coatingScheduleId;
    
    /** 分配时间 */
    private LocalDateTime allocatedTime;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    /**
     * 分配状态常量
     */
    public static class AllocationStatus {
        /** 完全满足 */
        public static final String FULLY_MET = "完全满足";
        
        /** 部分满足 */
        public static final String PARTIALLY_MET = "部分满足";
        
        /** 未满足 */
        public static final String NOT_MET = "未满足";
    }
}
