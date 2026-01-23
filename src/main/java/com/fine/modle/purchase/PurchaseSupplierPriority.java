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
@TableName("purchase_supplier_priority")
public class PurchaseSupplierPriority {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String supplierCode;
    private String supplierName;

    private BigDecimal score;
    private String level; // HIGH / MEDIUM / LOW
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;

    private String createdBy;
    private String updatedBy;

    @TableLogic
    private Integer isDeleted;
}
