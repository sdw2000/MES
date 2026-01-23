package com.fine.model.inventory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 订单物料锁定表
 * 用于成品/复卷/母卷库存锁定，支持多订单共享
 */
@Data
@TableName("order_material_lock")
public class OrderMaterialLock {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 订单ID */
    private Long orderId;
    
    /** 订单编号 */
    private String orderNo;
    
    /** 物料二维码（指向成品/复卷/母卷库存） */
    private String materialQrCode;
    
    /** 物料类型：finished-成品，rewound-复卷，jumbo-母卷 */
    private String materialType;
    
    /** 料号 */
    private String materialCode;
    
    /** 锁定数量 */
    private BigDecimal lockedQuantity;
    
    /** 锁定单位：sqm-平方米，roll-卷 */
    private String lockUnit;
    
    /** 客户优先级评分 */
    private BigDecimal customerPriorityScore;
    
    /** 锁定状态：locked-已锁定，released-已释放，consumed-已消耗 */
    private String lockStatus;
    
    /** 锁定时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date lockedAt;
    
    /** 释放时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date releasedAt;
    
    /** 备注 */
    private String remark;
}
