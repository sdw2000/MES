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
 * 采购订单明细
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("purchase_order_items")
public class PurchaseOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String materialCode;
    private String materialName;
    private String colorCode;

    private BigDecimal thickness;
    private BigDecimal width;
    private BigDecimal length;

    private Integer rolls;

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

    @TableField(exist = false)
    private String orderNo;

    @TableField(exist = false)
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date deliveryDate;
}
