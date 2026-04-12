package com.fine.modle;

import lombok.Data;

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
    }
}
