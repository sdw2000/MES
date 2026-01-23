package com.fine.model.production;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 排程订单关联表实体类
 */
@Data
public class ScheduleOrderItem {
    
    private Long id;
    
    /** 排程ID */
    private Long scheduleId;
    
    /** 订单ID */
    private Long orderId;
    
    /** 订单明细ID */
    private Long orderItemId;
    
    /** 订单号 */
    private String orderNo;
    
    /** 客户 */
    private String customer;
    
    /** 客户等级 */
    private String customerLevel;
    
    /** 产品料号 */
    private String materialCode;
    
    /** 产品名称 */
    private String materialName;
    
    /** 颜色代码 */
    private String colorCode;
    
    /** 厚度(mm) */
    private BigDecimal thickness;
    
    /** 宽度(mm) */
    private BigDecimal width;
    
    /** 长度(mm) */
    private BigDecimal length;
    
    /** 订单数量(卷) */
    private Integer orderQty;
    
    /** 本次排程数量(卷) */
    private Integer scheduleQty;
    
    /** 交货日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date deliveryDate;
    
    /** 优先级（数值越小越优先） */
    private Integer priority;
    
    /** 来源类型：stock-库存，production-生产 */
    private String sourceType;
    
    /** 使用库存ID（如直接出库） */
    private Long stockId;
    
    /** 状态：pending-待处理，processing-处理中，completed-已完成 */
    private String status;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
