package com.fine.modle.purchase;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

@Data
@TableName("purchase_supplier_contacts")
public class PurchaseSupplierContact {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long supplierId;

    private String contactName;
    private String contactPosition;
    private String contactPhone;
    private String contactEmail;
    private String contactWechat;

    private Integer isPrimary;
    private Integer isDecisionMaker;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;

    @TableLogic
    private Integer isDeleted;

    @TableField(exist = false)
    private Integer sortOrder;
}
