package com.fine.modle.schedule;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
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

    /** 产品编码/料号（手工排程时直接录入并落库） */
    private String materialCode;

    /** 产品名称（手工排程时直接录入并落库） */
    private String materialName;
    
    /** 订单详情ID */
    private Long orderDetailId;
    
    /** 排程数量（卷） */
    private Double scheduleQty;
    
    /** 缺口数量（卷） */
    private Double shortageQty;
    
    /** 涂布面积㎡ */
    private BigDecimal coatingArea;

    /** 涂布宽度(mm) - 计划员手工输入 */
    private BigDecimal coatingWidth;

    /** 涂布长度(米) - 计划员手工输入 */
    private BigDecimal coatingLength;

    /** 手工涂布速度(米/分) */
    private BigDecimal manualCoatingSpeed;
    
    /** 复卷已排程面积㎡ */
    private BigDecimal rewindingScheduledArea;

    /** 复卷宽度(mm) */
    private BigDecimal rewindingWidth;
    
    /** 排程类型：STOCK(库存直接复卷)/COATING(需涂布) */
    private String scheduleType;
    
    /** 状态：PENDING/COATING_SCHEDULED/REWINDING_SCHEDULED/COMPLETED */
    private String status;
    
    /** 涂布排程日期 */
    private LocalDate coatingScheduleDate;

    /** 涂布日期 */
    @TableField("coating_date")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime coatingDate;
    
    /** 涂布机台 */
    private String coatingEquipment;

    /** 复卷机台号 */
    private String rewindingEquipment;

    /** 包装班组 */
    private String packagingTeam;
    
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

    /** 锁定库存分配(JSON) */
    private String stockAllocations;
    
    /** 创建人 */
    private String createdBy;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
