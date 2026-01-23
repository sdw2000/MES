package com.fine.modle.purchase;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import com.fine.modle.purchase.PurchaseSupplierContact;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("purchase_suppliers")
public class PurchaseSupplier {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String supplierCode;
    private String supplierName;
    private String shortName;

    private String primaryContactName;
    private String primaryContactMobile;
    private String contactEmail;
    private String contactAddress;

    private String taxNo;
    private String bankName;
    private String bankAccount;

    private String status; // active / inactive
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
    private List<PurchaseSupplierContact> contacts;
}
