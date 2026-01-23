package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单物料锁定实体
 * 记录订单锁定的具体物料卷次
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("order_material_lock")
public class OrderMaterialLock implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单ID */
    private Long orderId;

    /** 订单明细ID */
    private Long orderItemId;

    /** 订单号 */
    @TableField("order_no")
    private String orderNo;

    /** 客户ID */
    @TableField("customer_id")
    private Long customerId;

    /** 客户名称（表字段非空） */
    @TableField("customer_name")
    private String customerName;

    /** 客户优先级分（表默认0） */
    @TableField("customer_priority_score")
    private BigDecimal customerPriorityScore;

    /** 预处理ID */
    @TableField("preprocessing_id")
    private Long preprocessingId;

    /** 胶带库存ID -> stock_record_id */
    @TableField("stock_record_id")
    private Long tapeStockId;

    /** 物料代码 */
    private String materialCode;

    /** 批次号（原始批次，表中无对应列，作为非持久化字段） */
    @TableField(exist = false)
    private String batchNo;

    /** 物料规格描述 -> material_spec */
    @TableField("material_spec")
    private String materialSpec;

    /** 二维码 -> material_qr_code */
    @TableField("material_qr_code")
    private String qrCode;

    /** 锁定卷数 -> locked_quantity */
    @TableField("locked_quantity")
    private BigDecimal lockQty;

    /** 锁定面积(m²) -> locked_area */
    @TableField("locked_area")
    private BigDecimal lockArea;

    /** 锁定状态: locked/used/released/cancelled */
    @TableField("lock_status")
    private String lockStatus;

    /** FIFO排序号 */
    @TableField("fifo_order")
    private Integer fifoOrder;

    /** 锁定人ID */
    @TableField("locked_by")
    private Long lockedBy;

    @TableField(value = "lock_time", fill = FieldFill.INSERT)
    private LocalDateTime lockedAt;

    /** 物料类型 -> stock_type */
    @TableField("stock_type")
    private String stockType;

    /** 物料表名 -> stock_table_name */
    @TableField("stock_table_name")
    private String stockTableName;

    /** 共享订单数量 */
    @TableField("shared_order_count")
    private Integer sharedOrderCount;

    /** 共享订单详情 */
    @TableField("shared_order_details")
    private String sharedOrderDetails;

    /** 备注 */
    private String remark;

    /**
     * 乐观锁版本号（表无此列，避免查询报错）
     */
    @TableField(exist = false)
    private Integer version;

    /**
     * 锁定状态常量
     */
    public static class LockStatusEnum {
        public static final String LOCKED = "locked";
        public static final String USED = "used";
        public static final String RELEASED = "released";
        public static final String CANCELLED = "cancelled";
    }
}
