package com.fine.modle.purchase;

import java.math.BigDecimal;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("purchase_quotation_items")
public class PurchaseQuotationItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long quotationId;

    private String materialCode;
    private String materialName;
    private String specifications;
    private BigDecimal length;
    private BigDecimal width;
    private BigDecimal thickness;
    private Integer quantity;
    private String unit;
    private BigDecimal sqm;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String remark;

    private String createdBy;
    private String updatedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;

    @TableLogic
    private Integer isDeleted;
}
