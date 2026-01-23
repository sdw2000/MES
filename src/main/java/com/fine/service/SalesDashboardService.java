package com.fine.service;

import java.util.List;
import java.util.Map;

public interface SalesDashboardService {

    Map<String, Object> getSummary();

    List<Map<String, Object>> getTopCustomers();

    Map<String, Object> getYearTrend();

    Map<String, Object> getShipmentStats();
}
