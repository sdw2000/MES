package com.fine.modle;

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

/**
 * 采购订单实体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("purchase_orders")
public class PurchaseOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 采购单号（系统生成）
    private String orderNo;

    // 供应商名称（暂复用客户表）
    private String supplier;

    // 供应商订单号
    private String supplierOrderNo;

    // 采购员（用户ID）
    @TableField("buyer")
    private Long buyerUserId;

    // 跟单员（用户ID）
    @TableField("handler")
    private Long handlerUserId;

    // 前端展示字段
    @TableField(exist = false)
    private String buyerUserName;
    @TableField(exist = false)
    private String handlerUserName;

    // 联系人信息
    private String contactName;
    private String contactPhone;

    // 金额/面积
    private BigDecimal totalAmount;
    private BigDecimal totalArea;
    private BigDecimal requiredArea;

    // 规格概要（可选）
    private Integer thickness;
    private Integer width;

    // 下单/交货日期
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date orderDate;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date deliveryDate;

    private String deliveryAddress;

    // 状态：pending/processing/completed/cancelled
    private String status;

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
    private List<PurchaseOrderItem> items;
}
