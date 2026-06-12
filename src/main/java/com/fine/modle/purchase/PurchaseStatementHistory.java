package com.fine.modle.purchase;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("purchase_statement_history")
public class PurchaseStatementHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String supplierCode;
    private String statementMonth;
    private BigDecimal unpaidAmount;
    private BigDecimal invoiceAmount;
    private LocalDate invoiceDate;
    private String remark;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer isDeleted;
}
