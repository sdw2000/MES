package com.fine.modle.purchase;

import lombok.Data;

import java.util.List;

@Data
public class PurchaseReconciliationConfirmRequest {
    private String supplier;
    private String month;
    private String targetMonth;
    private List<Long> detailIds;
}
