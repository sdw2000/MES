package com.fine.modle.purchase;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class PurchaseReconciliationConfirmRequest {
    private String supplierCode;
    private String month;
    private List<ReceiptConfirmItem> details;

    @Data
    public static class ReceiptConfirmItem {
        private Long receiptItemId;
        private String targetMonth;
        private BigDecimal splitQuantity;
        private BigDecimal splitAmount;
    }
}
