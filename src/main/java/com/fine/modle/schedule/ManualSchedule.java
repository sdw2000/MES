package com.fine.modle.schedule;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 手动排程记录
 */
@Data
@TableName("manual_schedule")
public class ManualSchedule {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 订单号 */
    private String orderNo;
    
    /** 订单详情ID */
    private Long orderDetailId;
    
    /** 料号 */
    private String materialCode;
    
    /** 品名 */
    private String materialName;
    
    /** 宽度mm */
    private Integer width;
    
    /** 长度m */
    private Integer length;
    
    /** 厚度μm */
    private Integer thickness;
    
    /** 订单数量（卷） */
    private Integer orderQty;
    
    /** 排程数量（卷） */
    private Integer scheduleQty;
    
    /** 缺口数量（卷） */
    private Integer shortageQty;
    
    /** 涂布面积㎡ */
    private BigDecimal coatingArea;
    
    /** 复卷已排程面积㎡ */
    private BigDecimal rewindingScheduledArea;
    
    /** 排程类型：STOCK(库存直接复卷)/COATING(需涂布) */
    private String scheduleType;
    
    /** 状态：PENDING/COATING_SCHEDULED/REWINDING_SCHEDULED/COMPLETED */
    private String status;
    
    /** 涂布排程日期 */
    private LocalDate coatingScheduleDate;

    /** 涂布日期 */
    @TableField("coating_date")
    private LocalDate coatingDate;
    
    /** 涂布机台 */
    private String coatingEquipment;
    
    /** 复卷排程日期 */
    private LocalDate rewindingScheduleDate;

    /** 复卷日期 */
    @TableField("rewinding_date")
    private LocalDate rewindingDate;
    
    /** 分切排程日期 */
    private LocalDate slittingScheduleDate;

    /** 包装日期 */
    @TableField("packaging_date")
    private LocalDate packagingDate;
    
    /** 备注 */
    private String remark;
    
    /** 创建人 */
    private String createdBy;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
