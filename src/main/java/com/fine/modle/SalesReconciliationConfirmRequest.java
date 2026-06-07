package com.fine.modle;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SalesReconciliationConfirmRequest {
    private String customerCode;
    private String month;
    private List<DeliveryConfirmItem> details;
    /**
     * 财务确认时：
     * false/空 -> 默认仅基于销售已持久化数据进行审核确认，不覆盖明细
     * true    -> 允许将当前提交明细覆盖到持久化结果
     */
    private Boolean overrideSalesDetails;

    @Data
    public static class DeliveryConfirmItem {
        private Long noticeItemId;
        private String targetMonth;
        private BigDecimal splitQuantity;
        private BigDecimal splitArea;
        private BigDecimal splitAmount;
    }
}
