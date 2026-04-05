package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 加班记录实体
 */
@Data
@TableName("production_overtime_record")
public class ProductionOvertimeRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long staffId;
    private String staffCode;
    private String staffName;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date overtimeDate;

    private String startTime;
    private String endTime;
    private BigDecimal hours;
    private String reason;
    private String status;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    private String createBy;
    private String updateBy;
    private Integer isDeleted;
}
