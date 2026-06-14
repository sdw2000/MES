package com.fine.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;
import java.util.Map;

public interface ProductionDashboardService {

    Map<String, Object> getSummary(String shiftCode);

    List<Map<String, Object>> getTopProcesses(String shiftCode);

    Map<String, Object> getYearTrend(String shiftCode);

    IPage<Map<String, Object>> getTodayReports(String shiftCode, Integer pageNum, Integer pageSize);
}
