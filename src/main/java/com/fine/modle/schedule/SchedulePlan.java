package com.fine.modle.schedule;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 统一排程计划表
 */
@Data
@TableName("schedule_plan")
public class SchedulePlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单详情ID */
    private Long orderDetailId;

    /** 订单号 */
    private String orderNo;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 厚度 */
    private Integer thickness;

    /** 宽度 */
    private Integer width;

    /** 长度 */
    private Integer length;

    /** 工序阶段：COATING/REWINDING/SLITTING */
    private String stage;

    /** 计划时间 */
    private LocalDateTime planDate;

    /** 机台 */
    private String equipment;

    /** 计划面积 */
    private BigDecimal planArea;

    /** 状态：PLANNED/CONFIRMED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
