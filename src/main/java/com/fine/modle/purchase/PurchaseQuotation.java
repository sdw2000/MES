package com.fine.modle.purchase;

import java.math.BigDecimal;
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

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("purchase_quotations")
public class PurchaseQuotation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String quotationNo;
    private String supplier;
    private String contactPerson;
    private String contactPhone;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date quotationDate;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date validUntil;

    private BigDecimal totalAmount;
    private BigDecimal totalArea;

    private String status; // draft/submitted/accepted/rejected/expired
    private String remark;

    private String createdBy;
    private String updatedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;

    @TableLogic
    private Integer isDeleted;

    @TableField(exist = false)
    private List<PurchaseQuotationItem> items;
}
