package com.fine.service;

import java.util.List;
import java.util.Map;

public interface SalesDashboardService {

    Map<String, Object> getSummary(Long salesUserId, Long documentationUserId);

    List<Map<String, Object>> getTopCustomers(Long salesUserId, Long documentationUserId);

    Map<String, Object> getYearTrend(Long salesUserId, Long documentationUserId);

    Map<String, Object> getShipmentStats(Long salesUserId, Long documentationUserId);

    List<Map<String, Object>> getTodayOrderItems(Long salesUserId, Long documentationUserId);
}
