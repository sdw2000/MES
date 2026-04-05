package com.fine.modle.schedule;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("manual_schedule_coating_allocation")
public class ManualScheduleCoatingAllocation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long scheduleId;

    private String orderNo;

    private String materialCode;

    private BigDecimal thickness;

    private Integer remainingQty;

    private BigDecimal remainingArea;

    private BigDecimal includedArea;

    private Integer includedFlag;

    private BigDecimal priorityScore;

    private Integer sortNo;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
