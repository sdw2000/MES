package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 设备实体类
 */
@Data
@TableName("equipment")
public class Equipment implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备编号 */
    private String equipmentCode;

    /** 设备名称 */
    private String equipmentName;

    /** 设备类型编码 */
    private String equipmentType;

    /** 所属车间ID */
    private Long workshopId;

    /** 品牌 */
    private String brand;

    /** 型号 */
    private String model;

    /** 最大加工宽度(mm) */
    private Integer maxWidth;

    /** 最大速度(米/分钟) */
    private BigDecimal maxSpeed;

    /** 日产能(平方米) */
    private BigDecimal dailyCapacity;

    /** 购买日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date purchaseDate;

    /** 状态：normal-正常，maintenance-维护中，fault-故障 */
    private String status;

    /** 设备位置 */
    private String location;

    /** 备注 */
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    private String createBy;

    private String updateBy;

    @TableLogic
    private Integer isDeleted;

    // ========== 非数据库字段 ==========

    /** 设备类型名称 */
    @TableField(exist = false)
    private String equipmentTypeName;

    /** 车间名称 */
    @TableField(exist = false)
    private String workshopName;
}
