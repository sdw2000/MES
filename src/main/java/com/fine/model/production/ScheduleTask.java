package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 单表排程任务
 */
@Data
@TableName("schedule_task")
public class ScheduleTask {
    @TableId(type = IdType.AUTO)
    private Long id;

    @com.baomidou.mybatisplus.annotation.TableField("batch_id")
    private Long batchId;

    @com.baomidou.mybatisplus.annotation.TableField("batch_no")
    private String batchNo;

    private Long orderId;
    private Long orderItemId;
    private String orderNo;

    private String materialCode;
    private String materialName;

    private BigDecimal widthMm;
    @com.baomidou.mybatisplus.annotation.TableField("length")
    private BigDecimal length;

    private Integer quantity;
    private BigDecimal area;

    /** COATING/REWINDING/SLITTING */
    private String processType;

    private Long equipmentId;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String equipmentCode;

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String equipmentName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date planStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date planEndTime;

    private Integer planDurationMin;

    /** SCHEDULED/UNSCHEDULED/IN_PROGRESS/COMPLETED/CANCELLED */
    private String status;

    private Integer canShipBy48h;

    private BigDecimal priorityScore;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date deliveryDate;

    private Integer lockStock;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
}
