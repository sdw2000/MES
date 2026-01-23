package com.fine.modle.stock;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 排程物料锁定表
 * 记录排程启动时的物料锁定信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("schedule_material_lock")
public class ScheduleMaterialLock {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 批排程ID */
    private Long scheduleId;
    
    /** 订单ID */
    private Long orderId;
    
    /** 被锁定的薄膜库存ID */
    private Long filmStockId;
    
    /** 被锁定的薄膜库存明细ID（卷号） */
    private Long filmStockDetailId;
    
    /** 锁定的面积(m²) */
    private BigDecimal lockedArea;
    
    /** 订单需求面积(m²) */
    private BigDecimal requiredArea;
    
    /** 锁定状态：锁定中、已领料、已消耗、已释放、已取消 */
    private String lockStatus;
    
    /** 锁定时间 */
    private LocalDateTime lockedTime;
    
    /** 分配时间(生产领料时) */
    private LocalDateTime allocatedTime;
    
    /** 消耗时间(生产反馈时) */
    private LocalDateTime consumedTime;
    
    /** 释放时间(异常取消时) */
    private LocalDateTime releasedTime;
    
    /** 操作者ID(排程人) */
    private Long lockedByUserId;
    
    /** 领料人ID */
    private Long allocatedByUserId;
    
    /** 版本号，用于乐观锁 */
    private Integer version;
    
    /** 备注信息 */
    private String remark;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    /**
     * 锁定状态常量
     */
    public static class LockStatus {
        /** 锁定中 */
        public static final String LOCKED = "锁定中";
        
        /** 已领料 */
        public static final String ALLOCATED = "已领料";
        
        /** 已消耗 */
        public static final String CONSUMED = "已消耗";
        
        /** 已释放 */
        public static final String RELEASED = "已释放";
        
        /** 已取消 */
        public static final String CANCELLED = "已取消";
    }
}
