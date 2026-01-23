package com.fine.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物料生产配置实体类
 * 用于管理物料的生产参数、MOQ、时间参数等
 */
@Data
@TableName("material_production_config")
public class MaterialProductionConfig {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 物料编号
     */
    private String materialCode;

    /**
     * 物料名称
     */
    private String materialName;

    /**
     * 物料类型：coating-涂布,printing-印刷
     */
    private String materialType;

    /**
     * 最小生产数量
     */
    private Integer minProductionQty;

    /**
     * 最小生产面积(㎡)
     */
    private BigDecimal minProductionArea;

    /**
     * 标准批量
     */
    private Integer standardBatchSize;

    /**
     * 最大批量
     */
    private Integer maxBatchSize;

    /**
     * 调机时间(分钟)
     */
    private Integer setupTime;

    /**
     * 单位时间(分钟/㎡)
     */
    private BigDecimal unitTime;

    /**
     * 清理时间(分钟)
     */
    private Integer cleanupTime;

    /**
     * 损耗率(%)
     */
    private BigDecimal lossRate;

    /**
     * 合格率(%)
     */
    private BigDecimal qualifiedRate;

    /**
     * 单位成本(元/㎡)
     */
    private BigDecimal unitCost;

    /**
     * 推荐薄膜宽度(mm)
     */
    private Integer recommendedWidth;

    /**
     * 推荐厚度(μm)
     */
    private Integer recommendedThickness;

    /**
     * 是否启用
     */
    private Integer isActive;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
