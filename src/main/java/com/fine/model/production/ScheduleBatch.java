package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("schedule_batch")
public class ScheduleBatch {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchNo;

    /** COATING/REWINDING */
    private String processType;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date planDate;

    private String materialCode;
    private String materialName;
    private String colorCode;
    private BigDecimal thickness;
    private BigDecimal widthMm;
    private BigDecimal length;

    private Integer totalQty;
    private BigDecimal totalArea;

    /** SCHEDULED/UNSCHEDULED/IN_PROGRESS/COMPLETED/CANCELLED */
    private String status;

    private Long sourceBatchId;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
}
