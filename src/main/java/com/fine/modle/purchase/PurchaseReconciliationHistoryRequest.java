package com.fine.modle.purchase;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PurchaseReconciliationHistoryRequest {
    private Long id;
    private String supplier;
    private String statementMonth;
    private BigDecimal unpaidAmount;
    private BigDecimal paidAmount;
    private String paymentDate;
    private String remark;
}
