package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 工艺参数实体类
 */
@Data
@TableName("process_params")
public class ProcessParams implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 产品料号 */
    private String materialCode;

    /** 工序类型：COATING/REWINDING/SLITTING/STRIPPING */
    private String processType;

    // ========== 涂布参数 ==========
    /** 涂布速度(米/分钟) */
    private BigDecimal coatingSpeed;

    /** 烘箱温度(℃) */
    private BigDecimal ovenTemp;

    /** 涂布厚度(μm) */
    private BigDecimal coatingThickness;

    /** 换色清洗时间(分钟) */
    private Integer colorChangeTime;

    /** 换厚度调机时间(分钟) */
    private Integer thicknessChangeTime;

    // ========== 复卷参数 ==========
    /** 复卷速度(米/分钟) */
    private BigDecimal rewindingSpeed;

    /** 张力设定 */
    private BigDecimal tensionSetting;

    /** 换卷时间(分钟) */
    private Integer rollChangeTime;

    // ========== 分切参数 ==========
    /** 分切速度(米/分钟) */
    private BigDecimal slittingSpeed;

    /** 刀片类型 */
    private String bladeType;

    /** 换刀时间(分钟) */
    private Integer bladeChangeTime;

    /** 最小分切宽度(mm) */
    private Integer minSlitWidth;

    /** 最大刀数 */
    private Integer maxBlades;

    /** 首尾损耗(mm) */
    private Integer edgeLoss;

    // ========== 分条机参数 ==========
    /** 分条速度(米/分钟) */
    private BigDecimal strippingSpeed;

    // ========== 通用参数 ==========
    /** 首检时间(分钟) */
    private Integer firstCheckTime;

    /** 末检时间(分钟) */
    private Integer lastCheckTime;

    /** 准备时间(分钟) */
    private Integer setupTime;

    /** 备注 */
    private String remark;

    /** 状态：0-停用，1-启用 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    // ========== 非数据库字段 ==========

    /** 产品名称 */
    @TableField(exist = false)
    private String materialName;

    /** 工序类型名称 */
    @TableField(exist = false)
    private String processTypeName;
}
