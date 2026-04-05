package com.fine.modle;

import java.math.BigDecimal;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 报价单明细实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("quotation_items")
public class QuotationItem {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    // 关联的报价单ID
    private Long quotationId;
    
    // 物料代码
    private String materialCode;
    
    // 物料名称
    private String materialName;

    // 规格
    private String specification;

    // 型号
    private String model;

    // 颜色
    private String colorCode;
    
    // 长度（毫米）
    private BigDecimal length;
    
    // 宽度（毫米）
    private BigDecimal width;
      // 厚度（微米）
    private BigDecimal thickness;
    
    // 单位
    private String unit;

    // 来源送样单号
    private String sampleNo;
    
    // 单价（每平方米）
    private BigDecimal unitPrice;
    
    // 匹配的规则ID（来自 price_rule / 报价规则）
    private Long appliedRuleId;

    // 匹配路径/说明（如 strict-spec -> material+unit）
    private String matchPath;
    
    // 备注
    private String remark;
    
    // 创建人
    private String createdBy;
    
    // 更新人
    private String updatedBy;
    
    // 创建时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
    
    // 更新时间
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;
    
    // 逻辑删除标记
    @TableLogic
    private Integer isDeleted;

    @TableField(exist = false)
    private Integer versionNo;

    @TableField(exist = false)
    private java.util.List<QuotationItemVersion> versionHistory;
}
