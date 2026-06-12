package com.fine.modle.purchase;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("purchase_return_items")
public class PurchaseReturnItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long returnId;
    private String purchaseOrderNo;
    
    private String materialCode;
    private String materialName;
    private String colorCode;
    
    private BigDecimal thickness;
    private BigDecimal width;
    private BigDecimal length;
    
    private Integer rolls;
    private BigDecimal sqm;
    
    private String priceUnit;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    
    private String remark;

    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer isDeleted;
}
