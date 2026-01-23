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
@TableName("purchase_receipt_items")
public class PurchaseReceiptItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long receiptId;
    private String materialCode;
    private String materialName;
    private String specification;
    private Integer expectedQty;
    private Integer receivedQty;
    private String unit;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String remark;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer isDeleted;
}
