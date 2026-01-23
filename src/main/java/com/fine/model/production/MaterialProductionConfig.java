package com.fine.model.production;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 物料生产配置表
 * 包含 MOQ、推荐宽度、速度、换单时间等
 */
@Data
@TableName("material_production_config")
public class MaterialProductionConfig {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 料号 */
    private String materialCode;
    
    /** 料号名称 */
    private String materialName;
    
    /** 颜色代码 */
    private String colorCode;
    
    /** 颜色名称 */
    private String colorName;
    
    /** 厚度（μm） */
    private Integer thickness;
    
    /** 最小涂布量（平方米） */
    private BigDecimal moqSqm;
    
    /** 最小涂布长度（米） */
    private BigDecimal moqLength;
    
    /** 推荐薄膜宽度（mm） */
    private Integer recommendedWidth;
    
    /** 涂布速度（米/分钟） */
    private BigDecimal coatingSpeed;
    
    /** 复卷速度（米/分钟） */
    private BigDecimal rewindingSpeed;
    
    /** 分切速度（米/分钟） */
    private BigDecimal slittingSpeed;
    
    /** 涂布换单时间（分钟） */
    private Integer changeoverTimeCoating;
    
    /** 复卷换单时间（分钟） */
    private Integer changeoverTimeRewinding;
    
    /** 分切换单时间（分钟） */
    private Integer changeoverTimeSlitting;
    
    /** 最小涂布时长（分钟） */
    private Integer minCoatingDuration;
    
    /** 最小复卷时长（分钟） */
    private Integer minRewindingDuration;
    
    /** 最小分切时长（分钟） */
    private Integer minSlittingDuration;
    
    /** 质检包装时长（分钟） */
    private Integer qcPackagingTime;
    
    /** 损耗率（%） */
    private BigDecimal wasteRate;
    
    /** 备注 */
    private String remark;
    
    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
    
    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;
}
