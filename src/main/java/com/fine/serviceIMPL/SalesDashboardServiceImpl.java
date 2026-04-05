package com.fine.serviceIMPL;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fine.modle.LoginUser;
import com.fine.service.SalesDashboardService;

@Service
public class SalesDashboardServiceImpl implements SalesDashboardService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public Map<String, Object> getSummary(Long salesUserId, Long documentationUserId) {
        Map<String, Object> result = new HashMap<>();
        LoginUser loginUser = getLoginUser();
        Long salesFilter = normalizeFilterId(salesUserId);
        Long docFilter = normalizeFilterId(documentationUserId);
        // 客户总数（按当前用户可见订单口径）
        long customerTotal = countVisibleCustomers(loginUser, salesFilter, docFilter);
        result.put("customerTotal", customerTotal);

        result.put("todayAmount", sumOrderAmountByItemsWithDateExpr("so.order_date = CURDATE()", loginUser, salesFilter, docFilter));
        result.put("monthAmount", sumOrderAmountByItemsWithDateExpr(
            "so.order_date >= DATE_FORMAT(CURDATE(), '%Y-%m-01') AND so.order_date <= CURDATE()",
            loginUser, salesFilter, docFilter));
        result.put("yearAmount", sumOrderAmountByItemsWithDateExpr(
            "so.order_date >= DATE_FORMAT(CURDATE(), '%Y-01-01') AND so.order_date <= CURDATE()",
            loginUser, salesFilter, docFilter));

        // 下单面积（㎡）统计：按订单表口径（sales_orders.total_area）
        result.put("todayArea", sumOrderAreaByOrdersWithDateExpr("so.order_date = CURDATE()", loginUser, salesFilter, docFilter));
        result.put("monthArea", sumOrderAreaByOrdersWithDateExpr(
            "so.order_date >= DATE_FORMAT(CURDATE(), '%Y-%m-01') AND so.order_date <= CURDATE()",
            loginUser, salesFilter, docFilter));
        result.put("yearArea", sumOrderAreaByOrdersWithDateExpr(
            "so.order_date >= DATE_FORMAT(CURDATE(), '%Y-01-01') AND so.order_date <= CURDATE()",
            loginUser, salesFilter, docFilter));

        return result;
    }

    @Override
    public List<Map<String, Object>> getTopCustomers(Long salesUserId, Long documentationUserId) {
        LoginUser loginUser = getLoginUser();
        Long salesFilter = normalizeFilterId(salesUserId);
        Long docFilter = normalizeFilterId(documentationUserId);
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ")
            .append("COALESCE(NULLIF(c.short_name, ''), NULLIF(c.customer_name, ''), so.customer) AS customerName, ")
            .append("COALESCE(SUM(COALESCE(soi.amount, 0)), 0) AS amount ")
            .append("FROM sales_order_items soi ")
            .append("INNER JOIN sales_orders so ON so.id = soi.order_id AND so.is_deleted = 0 ")
            .append("LEFT JOIN customers c ON c.is_deleted = 0 AND (")
            .append("c.customer_code COLLATE utf8mb4_unicode_ci = so.customer COLLATE utf8mb4_unicode_ci ")
            .append("OR c.short_name COLLATE utf8mb4_unicode_ci = so.customer COLLATE utf8mb4_unicode_ci ")
            .append("OR c.customer_name COLLATE utf8mb4_unicode_ci = so.customer COLLATE utf8mb4_unicode_ci)")
            .append(" ")
            .append("WHERE soi.is_deleted = 0 ")
            .append("AND so.order_date >= DATE_FORMAT(CURDATE(), '%Y-01-01') AND so.order_date <= CURDATE() ");

        String scopeSql = buildOrderScopeSql(loginUser, params, salesFilter, docFilter);
        if (!scopeSql.isEmpty()) {
            sql.append("AND (").append(scopeSql).append(") ");
        }

        sql.append("GROUP BY so.customer, COALESCE(NULLIF(c.short_name, ''), NULLIF(c.customer_name, ''), so.customer) ")
                .append("ORDER BY amount DESC LIMIT 10");

        final String querySql = sql.toString();
        if (querySql == null) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> records = jdbcTemplate.queryForList(querySql, params.toArray());
        return records != null ? records : new ArrayList<>();
    }

    @Override
    public Map<String, Object> getYearTrend(Long salesUserId, Long documentationUserId) {
        LoginUser loginUser = getLoginUser();
        Long salesFilter = normalizeFilterId(salesUserId);
        Long docFilter = normalizeFilterId(documentationUserId);
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT DATE_FORMAT(so.order_date, '%m') AS month, ")
            .append("COALESCE(SUM(COALESCE(soi.amount, 0)), 0) AS amount ")
            .append("FROM sales_order_items soi ")
            .append("INNER JOIN sales_orders so ON so.id = soi.order_id AND so.is_deleted = 0 ")
            .append("LEFT JOIN customers c ON c.is_deleted = 0 AND (")
            .append("c.customer_code COLLATE utf8mb4_unicode_ci = so.customer COLLATE utf8mb4_unicode_ci ")
            .append("OR c.short_name COLLATE utf8mb4_unicode_ci = so.customer COLLATE utf8mb4_unicode_ci ")
            .append("OR c.customer_name COLLATE utf8mb4_unicode_ci = so.customer COLLATE utf8mb4_unicode_ci)")
            .append(" ")
            .append("WHERE soi.is_deleted = 0 ")
            .append("AND so.order_date >= DATE_FORMAT(CURDATE(), '%Y-01-01') AND so.order_date <= CURDATE() ");

        String scopeSql = buildOrderScopeSql(loginUser, params, salesFilter, docFilter);
        if (!scopeSql.isEmpty()) {
            sql.append("AND (").append(scopeSql).append(") ");
        }
        sql.append("GROUP BY DATE_FORMAT(so.order_date, '%m') ORDER BY month");

        Map<String, BigDecimal> monthAmountMap = new HashMap<>();
        String querySql = Objects.requireNonNull(sql.toString());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(querySql, params.toArray());
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                String monthStr = String.valueOf(row.get("month"));
                BigDecimal amount = parseBigDecimal(row.get("amount"));
                monthAmountMap.put(monthStr, amount);
            }
        }

        List<String> months = new ArrayList<>();
        List<BigDecimal> amounts = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            String key = String.format("%02d", m);
            months.add(m + "月");
            amounts.add(monthAmountMap.getOrDefault(key, BigDecimal.ZERO));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("months", months);
        result.put("amounts", amounts);
        return result;
    }

    @Override
    public Map<String, Object> getShipmentStats(Long salesUserId, Long documentationUserId) {
        Map<String, Object> result = new HashMap<>();
        LoginUser loginUser = getLoginUser();
        Long salesFilter = normalizeFilterId(salesUserId);
        Long docFilter = normalizeFilterId(documentationUserId);
        LocalDate today = LocalDate.now();
        LocalDate yearStart = today.withDayOfYear(1);
        LocalDate monthStart = today.withDayOfMonth(1);

        // 出货量统计平米数（当年）
        BigDecimal shippedArea = sumOrderAreaByExpr("COALESCE(soi.delivered_area, 0)", yearStart, today, loginUser, salesFilter, docFilter);
        // 当年订单完成平米数（按明细完成判定）
        BigDecimal completedArea = sumOrderAreaByExpr(
                "CASE WHEN (COALESCE(soi.remaining_qty, 0) <= 0 OR LOWER(COALESCE(soi.production_status, '')) = 'completed') " +
                        "THEN COALESCE(soi.sqm, 0) ELSE 0 END",
                yearStart, today, loginUser, salesFilter, docFilter);
        // 当月已完成订单平米数
        BigDecimal monthCompletedArea = sumOrderAreaByExpr(
            "CASE WHEN (COALESCE(soi.remaining_qty, 0) <= 0 OR LOWER(COALESCE(soi.production_status, '')) = 'completed') " +
                "THEN COALESCE(soi.sqm, 0) ELSE 0 END",
            monthStart, today, loginUser, salesFilter, docFilter);
        // 当年订单未出货平米数
        BigDecimal unshippedArea = sumOrderAreaByExpr(
                "GREATEST(COALESCE(soi.sqm, 0) - COALESCE(soi.delivered_area, 0), 0)",
                yearStart, today, loginUser, salesFilter, docFilter);

        result.put("shippedArea", shippedArea);
        result.put("completedArea", completedArea);
        result.put("unshippedArea", unshippedArea);
        result.put("monthCompletedArea", monthCompletedArea);

        // 兼容旧字段（前端若仍引用不至于报错）
        result.put("shipped", shippedArea);
        result.put("pending", unshippedArea);
        result.put("overdue", BigDecimal.ZERO);

        return result;
    }

    @Override
    public List<Map<String, Object>> getTodayOrderItems(Long salesUserId, Long documentationUserId) {
        LoginUser loginUser = getLoginUser();
        Long salesFilter = normalizeFilterId(salesUserId);
        Long docFilter = normalizeFilterId(documentationUserId);
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ")
                .append("COALESCE(NULLIF(c.short_name, ''), NULLIF(c.customer_name, ''), so.customer) AS customerName, ")
                .append("so.customer AS customerCode, ")
                .append("soi.material_code AS materialCode, ")
                .append("soi.thickness AS thickness, ")
                .append("soi.width AS width, ")
                .append("soi.length AS length, ")
                .append("COALESCE(soi.sqm, 0) AS sqm, ")
                .append("COALESCE(soi.amount, 0) AS amount ")
                .append("FROM sales_order_items soi ")
                .append("INNER JOIN sales_orders so ON so.id = soi.order_id AND so.is_deleted = 0 ")
                .append("LEFT JOIN customers c ON c.is_deleted = 0 AND (")
                .append("c.customer_code COLLATE utf8mb4_unicode_ci = so.customer COLLATE utf8mb4_unicode_ci ")
                .append("OR c.short_name COLLATE utf8mb4_unicode_ci = so.customer COLLATE utf8mb4_unicode_ci ")
                .append("OR c.customer_name COLLATE utf8mb4_unicode_ci = so.customer COLLATE utf8mb4_unicode_ci)")
                .append(" ")
                .append("WHERE soi.is_deleted = 0 AND so.order_date = CURDATE() ");

        String scopeSql = buildOrderScopeSql(loginUser, params, salesFilter, docFilter);
        if (!scopeSql.isEmpty()) {
            sql.append("AND (").append(scopeSql).append(") ");
        }
        sql.append("ORDER BY so.id DESC, soi.id ASC");

        String querySql = Objects.requireNonNull(sql.toString());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(querySql, params.toArray());
        return rows != null ? rows : new ArrayList<>();
    }

    private BigDecimal sumOrderAreaByExpr(String sumExpr, LocalDate start, LocalDate end, LoginUser loginUser, Long salesFilter, Long docFilter) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COALESCE(SUM(").append(sumExpr).append("), 0) AS total ")
                .append("FROM sales_order_items soi ")
                .append("INNER JOIN sales_orders so ON so.id = soi.order_id AND so.is_deleted = 0 ")
            .append("LEFT JOIN customers c ON c.is_deleted = 0 AND (")
            .append("c.customer_code COLLATE utf8mb4_unicode_ci = so.customer COLLATE utf8mb4_unicode_ci ")
            .append("OR c.short_name COLLATE utf8mb4_unicode_ci = so.customer COLLATE utf8mb4_unicode_ci ")
            .append("OR c.customer_name COLLATE utf8mb4_unicode_ci = so.customer COLLATE utf8mb4_unicode_ci)")
            .append(" ")
                .append("WHERE soi.is_deleted = 0 ");

        if (start != null) {
            sql.append("AND so.order_date >= ? ");
            params.add(toDate(start));
        }
        if (end != null) {
            sql.append("AND so.order_date <= ? ");
            params.add(toDate(end));
        }

        String scopeSql = buildOrderScopeSql(loginUser, params, salesFilter, docFilter);
        if (!scopeSql.isEmpty()) {
            sql.append("AND (").append(scopeSql).append(") ");
        }

        final String querySql = sql.toString();
        if (querySql == null) {
            return BigDecimal.ZERO;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(querySql, params.toArray());
        if (rows != null && !rows.isEmpty()) {
            return parseBigDecimal(rows.get(0).get("total"));
        }
        return BigDecimal.ZERO;
    }

    private String buildOrderScopeSql(LoginUser loginUser, List<Object> params, Long salesFilter, Long docFilter) {
        boolean isAdmin = loginUser != null && loginUser.getPermissions() != null && loginUser.getPermissions().contains("admin");
        Long uid = getCurrentUserId(loginUser);
        StringBuilder scope = new StringBuilder();

        if (!isAdmin) {
            if (uid == null) {
                return "1 = 0";
            }
            params.add(uid);
            params.add(uid);
            scope.append("(c.sales = ? OR c.documentation_person = ?)");
        }

        if (salesFilter != null || docFilter != null) {
            StringBuilder filter = new StringBuilder();
            if (salesFilter != null) {
                if (filter.length() > 0) filter.append(" AND ");
                filter.append("c.sales = ?");
                params.add(salesFilter);
            }
            if (docFilter != null) {
                if (filter.length() > 0) filter.append(" AND ");
                filter.append("c.documentation_person = ?");
                params.add(docFilter);
            }
            if (filter.length() > 0) {
                if (scope.length() > 0) scope.append(" AND ");
                scope.append("(").append(filter).append(")");
            }
        }

        return scope.toString();
    }

    private BigDecimal sumOrderAmountByItemsWithDateExpr(String dateExpr, LoginUser loginUser, Long salesFilter, Long docFilter) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COALESCE(SUM(COALESCE(soi.amount, 0)), 0) AS total ")
                .append("FROM sales_order_items soi ")
                .append("INNER JOIN sales_orders so ON so.id = soi.order_id AND so.is_deleted = 0 ")
                .append("LEFT JOIN customers c ON c.is_deleted = 0 AND (")
                .append("c.customer_code COLLATE utf8mb4_unicode_ci = so.customer COLLATE utf8mb4_unicode_ci ")
                .append("OR c.short_name COLLATE utf8mb4_unicode_ci = so.customer COLLATE utf8mb4_unicode_ci ")
                .append("OR c.customer_name COLLATE utf8mb4_unicode_ci = so.customer COLLATE utf8mb4_unicode_ci)")
                .append(" ")
                .append("WHERE soi.is_deleted = 0 ");

        if (dateExpr != null && !dateExpr.trim().isEmpty()) {
            sql.append("AND (").append(dateExpr).append(") ");
        }

        String scopeSql = buildOrderScopeSql(loginUser, params, salesFilter, docFilter);
        if (!scopeSql.isEmpty()) {
            sql.append("AND (").append(scopeSql).append(") ");
        }

        String querySql = Objects.requireNonNull(sql.toString());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(querySql, params.toArray());
        if (rows != null && !rows.isEmpty()) {
            return parseBigDecimal(rows.get(0).get("total"));
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal sumOrderAreaByOrdersWithDateExpr(String dateExpr, LoginUser loginUser, Long salesFilter, Long docFilter) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COALESCE(SUM(COALESCE(so.total_area, 0)), 0) AS total ")
                .append("FROM sales_orders so ")
                .append("LEFT JOIN customers c ON c.is_deleted = 0 AND (")
                .append("c.customer_code COLLATE utf8mb4_unicode_ci = so.customer COLLATE utf8mb4_unicode_ci ")
                .append("OR c.short_name COLLATE utf8mb4_unicode_ci = so.customer COLLATE utf8mb4_unicode_ci ")
                .append("OR c.customer_name COLLATE utf8mb4_unicode_ci = so.customer COLLATE utf8mb4_unicode_ci)")
                .append(" ")
                .append("WHERE so.is_deleted = 0 ");

        if (dateExpr != null && !dateExpr.trim().isEmpty()) {
            sql.append("AND (").append(dateExpr).append(") ");
        }

        String scopeSql = buildOrderScopeSql(loginUser, params, salesFilter, docFilter);
        if (!scopeSql.isEmpty()) {
            sql.append("AND (").append(scopeSql).append(") ");
        }

        String querySql = Objects.requireNonNull(sql.toString());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(querySql, params.toArray());
        if (rows != null && !rows.isEmpty()) {
            return parseBigDecimal(rows.get(0).get("total"));
        }
        return BigDecimal.ZERO;
    }

    private long countVisibleCustomers(LoginUser loginUser, Long salesFilter, Long docFilter) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(1) AS customerTotal ")
                .append("FROM customers c ")
                .append("WHERE c.is_deleted = 0 ");

        boolean isAdmin = loginUser != null && loginUser.getPermissions() != null && loginUser.getPermissions().contains("admin");
        Long uid = getCurrentUserId(loginUser);

        if (!isAdmin) {
            if (uid == null) {
                return 0L;
            }
            sql.append("AND (c.sales = ? OR c.documentation_person = ?) ");
            params.add(uid);
            params.add(uid);
        }

        if (salesFilter != null) {
            sql.append("AND c.sales = ? ");
            params.add(salesFilter);
        }
        if (docFilter != null) {
            sql.append("AND c.documentation_person = ? ");
            params.add(docFilter);
        }

        final String querySql = sql.toString();
        if (querySql == null) {
            return 0L;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(querySql, params.toArray());
        if (rows != null && !rows.isEmpty()) {
            return parseBigDecimal(rows.get(0).get("customerTotal")).longValue();
        }
        return 0L;
    }


    private LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return (LoginUser) authentication.getPrincipal();
        }
        return null;
    }

    private Long getCurrentUserId(LoginUser loginUser) {
        return loginUser != null && loginUser.getUser() != null ? loginUser.getUser().getId() : null;
    }

    private Long normalizeFilterId(Long value) {
        if (value == null) return null;
        return value > 0 ? value : null;
    }

    private java.util.Date toDate(LocalDate localDate) {
        return java.util.Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }
}
