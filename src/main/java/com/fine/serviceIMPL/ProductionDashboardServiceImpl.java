package com.fine.serviceIMPL;

import com.fine.modle.LoginUser;
import com.fine.service.ProductionDashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ProductionDashboardServiceImpl implements ProductionDashboardService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, Object> getSummary(String shiftCode) {
        String normalizedShift = normalizeShiftCode(shiftCode);
        LoginUser loginUser = getLoginUser();
        List<Map<String, Object>> rows = queryReportRows(LocalDate.now().withDayOfYear(1).minusDays(1), LocalDate.now().plusDays(1));

        BigDecimal todayArea = BigDecimal.ZERO;
        BigDecimal monthArea = BigDecimal.ZERO;
        BigDecimal yearArea = BigDecimal.ZERO;
        BigDecimal todayQty = BigDecimal.ZERO;
        BigDecimal monthQty = BigDecimal.ZERO;
        BigDecimal yearQty = BigDecimal.ZERO;
        int todayReportCount = 0;

        LocalDate today = LocalDate.now();
        for (Map<String, Object> row : rows) {
            if (!canViewRowByOperator(row, loginUser)) continue;
            LocalDateTime ts = extractReportDateTime(row);
            String groupCode = extractGroupCode(row);
            if (normalizedShift != null && !normalizedShift.isEmpty() && !normalizedShift.equalsIgnoreCase(groupCode)) continue;

            LocalDate statDate = ts.toLocalDate();
            BigDecimal sqm = toBigDecimal(row.get("outputSqm"));
            BigDecimal qty = toBigDecimal(row.get("outputQty"));

            if (statDate.equals(today)) {
                todayArea = todayArea.add(sqm);
                todayQty = todayQty.add(qty);
                todayReportCount += 1;
            }
            if (statDate.getYear() == today.getYear() && statDate.getMonthValue() == today.getMonthValue()) {
                monthArea = monthArea.add(sqm);
                monthQty = monthQty.add(qty);
            }
            if (statDate.getYear() == today.getYear()) {
                yearArea = yearArea.add(sqm);
                yearQty = yearQty.add(qty);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("shiftCode", normalizedShift);
        result.put("todayArea", todayArea);
        result.put("monthArea", monthArea);
        result.put("yearArea", yearArea);
        result.put("todayQty", todayQty);
        result.put("monthQty", monthQty);
        result.put("yearQty", yearQty);
        result.put("todayReportCount", todayReportCount);
        return result;
    }

    @Override
    public List<Map<String, Object>> getTopProcesses(String shiftCode) {
        String normalizedShift = normalizeShiftCode(shiftCode);
        LoginUser loginUser = getLoginUser();
        List<Map<String, Object>> rows = queryReportRows(LocalDate.now().withDayOfYear(1).minusDays(1), LocalDate.now().plusDays(1));

        LocalDate today = LocalDate.now();
        Map<String, BigDecimal> processAreaMap = new HashMap<>();

        for (Map<String, Object> row : rows) {
            if (!canViewRowByOperator(row, loginUser)) continue;
            LocalDateTime ts = extractReportDateTime(row);
            String groupCode = extractGroupCode(row);
            if (normalizedShift != null && !normalizedShift.isEmpty() && !normalizedShift.equalsIgnoreCase(groupCode)) continue;

            LocalDate statDate = ts.toLocalDate();
            if (statDate.getYear() != today.getYear()) continue;

            String process = String.valueOf(row.get("taskType"));
            if (process == null || process.trim().isEmpty() || "null".equalsIgnoreCase(process)) {
                process = "UNKNOWN";
            }
            process = process.trim().toUpperCase();
            processAreaMap.put(process, processAreaMap.getOrDefault(process, BigDecimal.ZERO).add(toBigDecimal(row.get("outputSqm"))));
        }

        List<Map<String, Object>> result = new ArrayList<>();
        processAreaMap.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(10)
                .forEach(entry -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("processName", entry.getKey());
                    item.put("totalArea", entry.getValue());
                    result.add(item);
                });
        return result;
    }

    @Override
    public Map<String, Object> getYearTrend(String shiftCode) {
        String normalizedShift = normalizeShiftCode(shiftCode);
        LoginUser loginUser = getLoginUser();
        List<Map<String, Object>> rows = queryReportRows(LocalDate.now().withDayOfYear(1).minusDays(1), LocalDate.now().plusDays(1));
        Map<String, BigDecimal> monthAreaMap = new HashMap<>();
        LocalDate today = LocalDate.now();

        for (Map<String, Object> row : rows) {
            if (!canViewRowByOperator(row, loginUser)) continue;
            LocalDateTime ts = extractReportDateTime(row);
            String groupCode = extractGroupCode(row);
            if (normalizedShift != null && !normalizedShift.isEmpty() && !normalizedShift.equalsIgnoreCase(groupCode)) continue;

            LocalDate statDate = ts.toLocalDate();
            if (statDate.getYear() != today.getYear()) continue;

            String month = String.format("%02d", statDate.getMonthValue());
            monthAreaMap.put(month, monthAreaMap.getOrDefault(month, BigDecimal.ZERO).add(toBigDecimal(row.get("outputSqm"))));
        }

        List<String> months = new ArrayList<>();
        List<BigDecimal> areas = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            String key = String.format("%02d", m);
            months.add(m + "月");
            areas.add(monthAreaMap.getOrDefault(key, BigDecimal.ZERO));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("months", months);
        result.put("areas", areas);
        return result;
    }

    @Override
    public List<Map<String, Object>> getTodayReports(String shiftCode) {
        String normalizedShift = normalizeShiftCode(shiftCode);
        LoginUser loginUser = getLoginUser();
        List<Map<String, Object>> rows = queryReportRows(LocalDate.now().minusDays(2), LocalDate.now().plusDays(1));
        LocalDate today = LocalDate.now();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (!canViewRowByOperator(row, loginUser)) continue;
            LocalDateTime ts = extractReportDateTime(row);
            String groupCode = extractGroupCode(row);
            if (normalizedShift != null && !normalizedShift.isEmpty() && !normalizedShift.equalsIgnoreCase(groupCode)) continue;

            LocalDate statDate = ts.toLocalDate();
            if (!statDate.equals(today)) continue;

            Map<String, Object> item = new HashMap<>();
            item.put("id", row.get("id"));
            item.put("shiftCode", groupCode);
            item.put("taskType", row.get("taskType"));
            item.put("taskNo", row.get("taskNo"));
            item.put("staffName", row.get("staffName"));
            item.put("outputQty", toBigDecimal(row.get("outputQty")));
            item.put("outputSqm", toBigDecimal(row.get("outputSqm")));
            item.put("reportTime", row.get("reportTime"));
            item.put("statDate", statDate.toString());
            result.add(item);
        }

        result.sort(Comparator.comparing(
                m -> String.valueOf(m.get("reportTime") == null ? "" : m.get("reportTime")),
                Comparator.reverseOrder()
        ));
        return result;
    }

    private List<Map<String, Object>> queryReportRows(LocalDate startDate, LocalDate endDate) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT r.id AS id, ")
            .append("NULL AS shiftCode, ")
            .append("r.operator_name AS operatorName, ")
            .append("COALESCE(r.process_type, 'UNKNOWN') AS taskType, ")
            .append("CONCAT(COALESCE(r.process_type, 'UNK'), '-', COALESCE(r.schedule_id, 0), '-', r.id) AS taskNo, ")
            .append("r.operator_name AS staffName, ")
            .append("COALESCE(r.produced_qty, 0) AS outputQty, ")
            .append("CASE ")
            .append("  WHEN r.process_type = 'COATING' THEN COALESCE(cr.roll_area_sum, COALESCE(r.produced_qty, 0), 0) ")
            .append("  ELSE ROUND(COALESCE(r.produced_qty, 0) * COALESCE(soi.width, 0) / 1000 * COALESCE(soi.length, 0), 2) ")
            .append("END AS outputSqm, ")
            .append("COALESCE(r.end_time, r.start_time, r.created_at) AS reportTime ")
            .append("FROM manual_schedule_process_report r ")
            .append("LEFT JOIN manual_schedule ms ON ms.id = r.schedule_id ")
            .append("LEFT JOIN sales_order_items soi ON soi.id = ms.order_detail_id AND soi.is_deleted = 0 ")
            .append("LEFT JOIN (SELECT report_id, SUM(COALESCE(area, 0)) AS roll_area_sum ")
            .append("           FROM manual_schedule_coating_roll WHERE is_deleted = 0 GROUP BY report_id) cr ON cr.report_id = r.id ")
            .append("WHERE r.is_deleted = 0 ")
            .append("AND DATE(COALESCE(r.end_time, r.start_time, r.created_at)) >= ? ")
            .append("AND DATE(COALESCE(r.end_time, r.start_time, r.created_at)) <= ? ");
        params.add(java.sql.Date.valueOf(startDate));
        params.add(java.sql.Date.valueOf(endDate));
        sql.append("ORDER BY reportTime DESC");

        String querySql = Objects.requireNonNull(sql.toString());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(querySql, params.toArray());
        return rows != null ? rows : new ArrayList<>();
    }

    private LocalDateTime extractReportDateTime(Map<String, Object> row) {
        Object value = row.get("reportTime");
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime();
        }
        if (value instanceof java.util.Date) {
            return new Timestamp(((java.util.Date) value).getTime()).toLocalDateTime();
        }
        try {
            return LocalDateTime.parse(String.valueOf(value).replace(' ', 'T'));
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private String extractGroupCode(Map<String, Object> row) {
        if (row == null) return "未识别";
        String byCode = normalizeShiftCode(String.valueOf(row.get("shiftCode")));
        if (byCode != null && !byCode.isEmpty() && !"NULL".equalsIgnoreCase(byCode)) {
            return byCode;
        }

        String operator = String.valueOf(row.get("operatorName"));
        if (operator == null || operator.trim().isEmpty() || "null".equalsIgnoreCase(operator)) {
            return "未识别";
        }
        String raw = operator.trim().toUpperCase();
        raw = raw.replace("白班", "").replace("夜班", "").replace("班", "");
        String[] parts = raw.split("[-_\\s]+");
        String last = parts.length > 0 ? parts[parts.length - 1] : raw;
        last = last.replaceAll("[^A-Z0-9\\u4E00-\\u9FA5]", "");
        return (last == null || last.isEmpty() || "NULL".equalsIgnoreCase(last)) ? "未识别" : last;
    }

    private String normalizeShiftCode(String shiftCode) {
        return shiftCode == null ? "" : shiftCode.trim().toUpperCase();
    }

    private LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return (LoginUser) authentication.getPrincipal();
        }
        return null;
    }

    private boolean canViewRowByOperator(Map<String, Object> row, LoginUser loginUser) {
        if (loginUser == null) {
            return true;
        }
        if (loginUser.getPermissions() != null && loginUser.getPermissions().contains("admin")) {
            return true;
        }

        String operator = row == null ? "" : String.valueOf(row.get("operatorName"));
        if (operator == null || operator.trim().isEmpty() || "null".equalsIgnoreCase(operator)) {
            return false;
        }
        String op = operator.trim();

        String username = loginUser.getUsername();
        String realName = loginUser.getUser() == null ? null : loginUser.getUser().getRealName();

        if (username != null && !username.trim().isEmpty()) {
            String u = username.trim();
            if (op.equalsIgnoreCase(u)
                    || op.toUpperCase().startsWith((u + "-").toUpperCase())
                    || op.toUpperCase().startsWith((u + "_").toUpperCase())) {
                return true;
            }
        }

        if (realName != null && !realName.trim().isEmpty()) {
            String r = realName.trim();
            if (op.equalsIgnoreCase(r)
                    || op.toUpperCase().startsWith((r + "-").toUpperCase())
                    || op.toUpperCase().startsWith((r + "_").toUpperCase())) {
                return true;
            }
        }

        return false;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
