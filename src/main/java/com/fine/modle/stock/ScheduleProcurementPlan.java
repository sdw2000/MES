package com.fine.modle.stock;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("schedule_procurement_plan")
public class ScheduleProcurementPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String planNo;

    private Long scheduleId;

    private String orderNo;

    private String materialCode;

    private BigDecimal requiredArea;

    private String status;

    private String purchaseOrderNo;

    private Long purchaseOrderItemId;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
