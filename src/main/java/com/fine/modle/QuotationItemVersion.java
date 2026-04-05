package com.fine.modle;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("quotation_item_versions")
public class QuotationItemVersion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long quotationId;

    private Long quotationItemId;

    private String quotationNo;

    private String customer;

    private String materialCode;

    private String materialName;

    private String specification;

    private String model;

    private String colorCode;

    private BigDecimal length;

    private BigDecimal width;

    private BigDecimal thickness;

    private String unit;

    private BigDecimal unitPrice;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date quotationDate;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date validUntil;

    private String quotationStatus;

    private Integer versionNo;

    private String specKey;

    private String sourceSampleNo;

    private String createdBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
}