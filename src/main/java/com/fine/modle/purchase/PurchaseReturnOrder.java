package com.fine.modle.purchase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("purchase_return_orders")
public class PurchaseReturnOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String returnNo;
    private String supplier;
    private LocalDate returnDate;
    private String status; // confirmed, etc.
    
    private BigDecimal totalAmount;
    private BigDecimal totalArea;
    private BigDecimal statementAmount;
    private String statementMonth;
    
    private String reason;
    private String remark;

    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer isDeleted;

    @TableField(exist = false)
    private List<PurchaseReturnItem> items;
}
