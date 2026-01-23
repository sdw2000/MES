package com.fine.model.inventory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 订单物料出库单表
 * 记录实际出库情况
 */
@Data
@TableName("order_material_issue")
public class OrderMaterialIssue {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /** 出库单号 */
    private String issueNo;
    
    /** 订单ID */
    private Long orderId;
    
    /** 订单编号 */
    private String orderNo;
    
    /** 关联锁定记录ID */
    private Long lockId;
    
    /** 物料二维码 */
    private String materialQrCode;
    
    /** 出库数量 */
    private BigDecimal issuedQuantity;
    
    /** 出库单位：sqm-平方米，roll-卷 */
    private String issueUnit;
    
    /** 出库时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date issuedAt;
    
    /** 创建人 */
    private String createdBy;
    
    /** 备注 */
    private String remark;
}
