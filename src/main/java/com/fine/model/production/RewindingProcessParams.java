package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("rewinding_process_params")
public class RewindingProcessParams implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String materialCode;

    private String equipmentCode;

    private BigDecimal rewindingSpeed;

    private BigDecimal tensionSetting;

    private Integer rollChangeTime;

    private Integer setupTime;

    private Integer firstCheckTime;

    private Integer lastCheckTime;

    private String remark;

    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    @TableField(exist = false)
    private String materialName;

    @TableField(exist = false)
    private String equipmentName;

    @TableField(exist = false)
    private String processType;

    @TableField(exist = false)
    private String processTypeName;
}
