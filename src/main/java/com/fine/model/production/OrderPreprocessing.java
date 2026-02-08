package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单预处理实体
 * 人工锁定订单物料，决定订单进入的生产步骤
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("order_preprocessing")
public class OrderPreprocessing implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单ID */
    private Long orderId;

    /** 订单号 */
    private String orderNo;

    /** 订单明细ID */
    private Long orderItemId;

    /** 订单明细代码 */
    private String orderItemCode;

    /** 物料代码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 规格描述 */
    private String specDesc;

    /** 需求数量(m²) - 可编辑 */
    private BigDecimal requiredQty;

    /** 选中的锁定ID */
    private Long selectedLockId;

    /** 锁定状态: 未锁定/部分锁定/全部锁定 */
    private String lockStatus;

    /** 已锁定数量(m²) */
    private BigDecimal lockedQty;

    /** 排程类型: 涂布/复卷/分切 */
    private String scheduleType;

    /** 目标待排池 */
    private String targetPool;

    /** 状态: preprocessing/locked/dispatched */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建人 */
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新人 */
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 锁定状态常量
     */
    public static class LockStatusEnum {
        public static final String UNLOCKED = "未锁定";
        public static final String PARTIAL = "部分锁定";
        public static final String LOCKED = "全部锁定";
    }

    /**
     * 排程类型常量
     */
    public static class ScheduleTypeEnum {
        public static final String COATING = "涂布";
        public static final String REWINDING = "复卷";
        public static final String SLITTING = "分切";
    }

    /**
     * 预处理状态常量
     */
    public static class PreprocessingStatusEnum {
        public static final String PREPROCESSING = "preprocessing";
        public static final String LOCKED = "locked";
        public static final String DISPATCHED = "dispatched";
        public static final String CANCELLED = "cancelled";
    }
}
