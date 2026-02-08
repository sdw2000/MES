package com.fine.model.production;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ScheduleTaskPlanRequest {
    private List<PlanItem> items;
    private Integer gapMinutes;
    private Integer lockStock;
    private String operator;

    @Data
    public static class PlanItem {
        private Long orderItemId;
        private Integer quantity;
        private BigDecimal area;
        private BigDecimal priorityScore;
    }
}
