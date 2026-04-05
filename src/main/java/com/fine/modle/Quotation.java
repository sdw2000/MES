package com.fine.modle;

import java.util.Date;
import java.util.List;

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
 * 报价单实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("quotations")
public class Quotation {
      @TableId(type = IdType.AUTO)
    private Long id;
    
    // 报价单号（系统生成，格式：QT-YYMMDD-001）
    private String quotationNo;
    
    // 客户名称
    private String customer;
    
    // 客户联系人
    private String contactPerson;
    
    // 联系电话
    private String contactPhone;

    // 来源送样单号
    private String sourceSampleNo;
    
    // 报价日期
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date quotationDate;
    
    // 有效期（截止日期）
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date validUntil;
    
    // 报价状态（draft-草稿，submitted-已提交，accepted-已接受，rejected-已拒绝，expired-已过期）
    private String status;
    
    // 备注
    private String remark;

    // 报价单位（按面积/按长度/按卷）
    private String pricingUnit;
    
    // 价格状态：PENDING / PRICED
    private String priceStatus;

    // 是否需要人工定价（true=需要，false=不需要）
    private Boolean needsPricing;
    
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
    
    // 报价明细（不映射到数据库）
    @TableField(exist = false)
    private List<QuotationItem> items;

    @TableField(exist = false)
    private Long customerId;

    @TableField(exist = false)
    private String expiryStatus;

    @TableField(exist = false)
    private Integer daysToExpire;
}