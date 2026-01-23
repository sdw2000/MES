package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 安全库存实体类
 */
@Data
@TableName("safety_stock")
public class SafetyStock implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 产品料号 */
    private String materialCode;

    /** 库存类型：semi-半成品，finished-成品 */
    private String stockType;

    /** 安全库存数量(卷) */
    private Integer safetyQty;

    /** 安全库存面积(平方米) */
    private BigDecimal safetyArea;

    /** 最大库存数量 */
    private Integer maxQty;

    /** 补货触发点(低于此值触发补货) */
    private Integer reorderPoint;

    /** 经济批量(每次生产批量) */
    private Integer economicLot;

    /** 提前期(天) */
    private Integer leadTime;

    /** 优先级：1-最高，5-最低 */
    private Integer priority;

    /** 是否启用自动备货：0-否，1-是 */
    private Integer autoRestock;

    /** 备注 */
    private String remark;

    /** 状态：0-停用，1-启用 */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    private String createBy;
    private String updateBy;

    // ========== 非数据库字段 ==========

    /** 产品名称 */
    @TableField(exist = false)
    private String materialName;

    /** 当前库存数量 */
    @TableField(exist = false)
    private Integer currentQty;

    /** 当前库存面积 */
    @TableField(exist = false)
    private BigDecimal currentArea;

    /** 库存状态：normal-正常，low-偏低，critical-严重不足，over-超储 */
    @TableField(exist = false)
    private String stockStatus;
}
