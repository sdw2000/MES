package com.fine.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 待排程订单视图实体
 * 对应视图：v_pending_schedule_orders
 */
@Data
public class PendingScheduleOrder {

    /**
     * 订单明细ID
     */
    private Long orderItemId;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 物料编号
     */
    private String materialCode;

    /**
     * 物料名称
     */
    private String materialName;

    /**
     * 客户名称
     */
    private String customer;

    /**
     * 订单数量
     */
    private Integer orderQty;

    /**
     * 已排程数量
     */
    private Integer scheduledQty;

    /**
     * 待排程数量
     */
    private Integer pendingQty;

    /**
     * 宽度(mm)
     */
    private BigDecimal width;

    /**
     * 长度(mm)
     */
    private BigDecimal length;

    /**
     * 待排程面积(㎡)
     */
    private BigDecimal pendingArea;

    /**
     * 交货日期
     */
    private LocalDateTime deliveryDate;

    /**
     * 最小生产面积(㎡)
     */
    private BigDecimal minProductionArea;

    /**
     * 推荐薄膜宽度(mm)
     */
    private Integer recommendedWidth;
}
