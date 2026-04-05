package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("slitting_process_params")
public class SlittingProcessParams implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String materialCode;

    private String equipmentCode;

    private BigDecimal slittingSpeed;

    @TableField("production_speed")
    private BigDecimal productionSpeed;

    @TableField("total_thickness")
    private BigDecimal totalThickness;

    @TableField("process_length")
    private BigDecimal processLength;

    @TableField("process_width")
    private BigDecimal processWidth;

    private String bladeType;

    private Integer bladeChangeTime;

    private Integer minSlitWidth;

    private Integer maxBlades;

    private Integer edgeLoss;

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
