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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProductionDashboardServiceImpl implements ProductionDashboardService {

    private static final DateTimeFormatter REPORT_TIME_MINUTE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Pattern SHIFT_TOKEN_PATTERN = Pattern.compile("(?:^|[^A-Z])([ABCD])(?:班|组|$)", Pattern.CASE_INSENSITIVE);

    private final Map<String, String> operatorShiftCache = new ConcurrentHashMap<>();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, Object> getSummary(String shiftCode) {
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
    result.put("shiftCode", "ALL");
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
        LoginUser loginUser = getLoginUser();
        List<Map<String, Object>> rows = queryReportRows(LocalDate.now().withDayOfYear(1).minusDays(1), LocalDate.now().plusDays(1));

        LocalDate today = LocalDate.now();
        Map<String, BigDecimal> processAreaMap = new HashMap<>();

        for (Map<String, Object> row : rows) {
            if (!canViewRowByOperator(row, loginUser)) continue;
            LocalDateTime ts = extractReportDateTime(row);

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
        LoginUser loginUser = getLoginUser();
        List<Map<String, Object>> rows = queryReportRows(LocalDate.now().withDayOfYear(1).minusDays(1), LocalDate.now().plusDays(1));
        Map<String, BigDecimal> monthAreaMap = new HashMap<>();
        LocalDate today = LocalDate.now();

        for (Map<String, Object> row : rows) {
            if (!canViewRowByOperator(row, loginUser)) continue;
            LocalDateTime ts = extractReportDateTime(row);

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
        LoginUser loginUser = getLoginUser();
        List<Map<String, Object>> rows = queryReportRows(LocalDate.now().minusDays(2), LocalDate.now().plusDays(1));
        LocalDate today = LocalDate.now();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (!canViewRowByOperator(row, loginUser)) continue;
            LocalDateTime ts = extractReportDateTime(row);
            if (ts == null) continue;
            String groupCode = resolveShiftCode(row, ts);

            LocalDate statDate = ts.toLocalDate();
            if (!statDate.equals(today)) continue;

            Map<String, Object> item = new HashMap<>();
            item.put("id", row.get("id"));
            item.put("shiftCode", groupCode);
            item.put("taskType", row.get("taskType"));
            item.put("taskNo", row.get("taskNo"));
            item.put("staffName", normalizeStaffName(row.get("staffName")));
            item.put("outputQty", toBigDecimal(row.get("outputQty")));
            item.put("outputSqm", toBigDecimal(row.get("outputSqm")));
            item.put("reportTime", ts.format(REPORT_TIME_MINUTE_FORMATTER));
            item.put("reportTimeSort", ts);
            item.put("statDate", statDate.toString());
            result.add(item);
        }

        result.sort(Comparator.comparing(
                m -> (LocalDateTime) m.get("reportTimeSort"),
                Comparator.nullsLast(Comparator.reverseOrder())
        ));
        result.forEach(item -> item.remove("reportTimeSort"));
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
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime();
        }
        if (value instanceof java.util.Date) {
            return new Timestamp(((java.util.Date) value).getTime()).toLocalDateTime();
        }
        try {
            return LocalDateTime.parse(String.valueOf(value).replace(' ', 'T'));
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveShiftCode(Map<String, Object> row, LocalDateTime reportTime) {
        String byOperator = extractGroupCode(row);
        if (isValidShiftCode(byOperator)) {
            return byOperator;
        }
        return "未识别";
    }

    private String extractGroupCode(Map<String, Object> row) {
        if (row == null) return "";
        String byCode = normalizeShiftCode(String.valueOf(row.get("shiftCode")));
        if (isValidShiftCode(byCode)) {
            return byCode;
        }

        String operator = String.valueOf(row.get("operatorName"));
        if (operator == null || operator.trim().isEmpty() || "null".equalsIgnoreCase(operator)) {
            return "";
        }
        String direct = normalizeShiftToken(operator);
        if (isValidShiftCode(direct)) {
            return direct;
        }
        return resolveShiftFromStaffTeam(operator);
    }

    private String normalizeStaffName(Object rawName) {
        String text = rawName == null ? "" : String.valueOf(rawName).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
            return "";
        }
        return text.replaceFirst("[-_][A-Za-z0-9\\u4E00-\\u9FA5]+班$", "");
    }

    private String normalizeShiftCode(String shiftCode) {
        String normalized = normalizeShiftToken(shiftCode);
        return normalized == null ? "" : normalized;
    }

    private String normalizeShiftToken(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim().toUpperCase();
        if (text.isEmpty()) {
            return "";
        }
        if ("A".equals(text) || "B".equals(text) || "C".equals(text) || "D".equals(text)) {
            return text;
        }
        if (text.contains("A班") || text.contains("A组")) return "A";
        if (text.contains("B班") || text.contains("B组")) return "B";
        if (text.contains("C班") || text.contains("C组")) return "C";
        if (text.contains("D班") || text.contains("D组")) return "D";
        Matcher matcher = SHIFT_TOKEN_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        if (text.contains("甲")) return "A";
        if (text.contains("乙")) return "B";
        if (text.contains("丙")) return "C";
        if (text.contains("丁")) return "D";
        if (text.contains("白")) {
            return "A";
        }
        if (text.contains("夜")) {
            return "B";
        }
        return "";
    }

    private boolean isValidShiftCode(String code) {
        if (code == null) return false;
        String c = code.trim().toUpperCase();
        return "A".equals(c) || "B".equals(c) || "C".equals(c) || "D".equals(c);
    }

    private String resolveShiftFromStaffTeam(String operatorRaw) {
        if (operatorRaw == null || operatorRaw.trim().isEmpty()) {
            return "";
        }
        final String cacheKey = operatorRaw.trim().toUpperCase();
        String cached = operatorShiftCache.get(cacheKey);
        if (isValidShiftCode(cached)) {
            return cached;
        }

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String op = operatorRaw.trim();
        candidates.add(op);
        String[] parts = op.split("[-_\\s]+");
        if (parts.length > 0 && parts[0] != null && !parts[0].trim().isEmpty()) {
            candidates.add(parts[0].trim());
        }

        for (String candidate : candidates) {
            if (candidate == null || candidate.trim().isEmpty()) {
                continue;
            }
            String c = candidate.trim();
            try {
                List<String> teamNames = jdbcTemplate.query(
                        "SELECT pt.team_name " +
                                "FROM production_staff ps " +
                                "LEFT JOIN production_team pt ON pt.id = ps.team_id " +
                                "WHERE IFNULL(ps.is_deleted, 0) = 0 " +
                                "AND (ps.staff_name = ? OR ps.staff_code = ?) " +
                                "LIMIT 1",
                        (rs, rowNum) -> rs.getString(1),
                        c, c
                );
                String shift = normalizeShiftToken((teamNames == null || teamNames.isEmpty()) ? null : teamNames.get(0));
                if (isValidShiftCode(shift)) {
                    operatorShiftCache.put(cacheKey, shift);
                    return shift;
                }
            } catch (Exception ignored) {
            }

            try {
                List<String> teamNames = jdbcTemplate.query(
                        "SELECT pt.team_name " +
                                "FROM users u " +
                                "LEFT JOIN production_staff ps ON ps.id = u.staff_id AND IFNULL(ps.is_deleted, 0) = 0 " +
                                "LEFT JOIN production_team pt ON pt.id = ps.team_id " +
                                "WHERE IFNULL(u.del_flag, 0) = 0 " +
                                "AND (u.username = ? OR u.real_name = ?) " +
                                "LIMIT 1",
                        (rs, rowNum) -> rs.getString(1),
                        c, c
                );
                String shift = normalizeShiftToken((teamNames == null || teamNames.isEmpty()) ? null : teamNames.get(0));
                if (isValidShiftCode(shift)) {
                    operatorShiftCache.put(cacheKey, shift);
                    return shift;
                }
            } catch (Exception ignored) {
            }
        }

        return "";
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

        for (String alias : resolveOperatorAliases(loginUser)) {
            if (alias == null || alias.trim().isEmpty()) continue;
            String a = alias.trim();
            if (op.equalsIgnoreCase(a)
                    || op.toUpperCase().startsWith((a + "-").toUpperCase())
                    || op.toUpperCase().startsWith((a + "_").toUpperCase())) {
                return true;
            }
        }

        return false;
    }

    private List<String> resolveOperatorAliases(LoginUser loginUser) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        if (loginUser == null) {
            return new ArrayList<>(aliases);
        }

        if (loginUser.getUsername() != null && !loginUser.getUsername().trim().isEmpty()) {
            aliases.add(loginUser.getUsername().trim());
        }

        Long staffId = null;
        if (loginUser.getUser() != null) {
            String realName = loginUser.getUser().getRealName();
            if (realName != null && !realName.trim().isEmpty()) {
                aliases.add(realName.trim());
            }
            staffId = loginUser.getUser().getStaffId();
        }

        if (staffId != null && staffId > 0) {
            try {
                List<String> rows = jdbcTemplate.query(
                        "SELECT staff_name FROM production_staff WHERE id = ? AND IFNULL(is_deleted, 0) = 0 LIMIT 1",
                        (rs, rowNum) -> rs.getString(1),
                        staffId
                );
                if (rows != null) {
                    for (String name : rows) {
                        if (name != null && !name.trim().isEmpty()) {
                            aliases.add(name.trim());
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return new ArrayList<>(aliases);
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
