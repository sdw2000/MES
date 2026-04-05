package com.fine.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("customer_material_mapping")
public class CustomerMaterialMapping {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户代码 */
    private String customerCode;

    /** 我司料号 */
    private String materialCode;

    /** 厚度(μm) */
    private BigDecimal thickness;

    /** 宽度(mm) */
    private BigDecimal width;

    /** 长度(m) */
    private BigDecimal length;

    /** 客户厚度(μm) */
    private BigDecimal customerThickness;

    /** 客户宽度(mm) */
    private BigDecimal customerWidth;

    /** 客户长度(m) */
    private BigDecimal customerLength;

    /** 客户物料代码 */
    private String customerMaterialCode;

    /** 客户材料名称 */
    private String customerMaterialName;

    /** 是否启用：1启用，0禁用 */
    private Integer isActive;

    private String remark;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
