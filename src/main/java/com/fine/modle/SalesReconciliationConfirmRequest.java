package com.fine.modle;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SalesReconciliationConfirmRequest {
    private String customerCode;
    private String month;
    private List<DeliveryConfirmItem> details;

    @Data
    public static class DeliveryConfirmItem {
        private Long noticeItemId;
        private String targetMonth;
        private BigDecimal splitQuantity;
        private BigDecimal splitArea;
        private BigDecimal splitAmount;
    }
}
