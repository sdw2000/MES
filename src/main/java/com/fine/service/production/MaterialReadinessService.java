package com.fine.service.production;

import java.time.LocalDate;
import java.util.Map;

public interface MaterialReadinessService {

    /**
     * 原料齐套汇总（基于销售未完成订单 + BOM + 库存 + 在途）
     */
    Map<String, Object> getChemicalReadinessSummary(LocalDate requiredByDate, String orderNo, String materialCode);

    /**
     * 单个订单明细齐套评估
     */
    Map<String, Object> getOrderItemReadiness(Long orderItemId);
}
