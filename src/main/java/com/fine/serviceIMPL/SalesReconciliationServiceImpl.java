package com.fine.serviceIMPL;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fine.Dao.CustomerMapper;
import com.fine.Dao.SalesStatementHistoryMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.Customer;
import com.fine.modle.LoginUser;
import com.fine.modle.SalesStatementHistory;
import com.fine.service.SalesReconciliationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class SalesReconciliationServiceImpl implements SalesReconciliationService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private SalesStatementHistoryMapper salesStatementHistoryMapper;

    private volatile boolean historyTableChecked = false;

    @Override
    public ResponseResult<?> getStatement(String customerCode, String month) {
        try {
            ensureHistoryTable();
            if (customerCode == null || customerCode.trim().isEmpty()) {
                return new ResponseResult<>(400, "客户不能为空", null);
            }
            if (!canAccessCustomer(customerCode)) {
                return new ResponseResult<>(403, "无权限访问该客户", null);
            }
            if (month == null || !month.matches("\\d{4}-\\d{2}")) {
                return new ResponseResult<>(400, "月份格式应为yyyy-MM", null);
            }
            Customer customer = customerMapper.selectByCustomerCode(customerCode.trim());
            Set<String> customerKeys = new LinkedHashSet<>();
            customerKeys.add(customerCode.trim());
            if (customer != null) {
                if (hasText(customer.getCustomerName())) {
                    customerKeys.add(customer.getCustomerName().trim());
                }
                if (hasText(customer.getShortName())) {
                    customerKeys.add(customer.getShortName().trim());
                }
            }

            List<Map<String, Object>> detailRows = new ArrayList<>();
            detailRows.addAll(queryDeliveryRows(new ArrayList<>(customerKeys), month));
            detailRows.addAll(queryReturnRows(new ArrayList<>(customerKeys), month));
                detailRows.sort(Comparator.comparing((Map<String, Object> row) -> String.valueOf(row.get("bizDate") == null ? "" : row.get("bizDate")))
                    .thenComparing((Map<String, Object> row) -> String.valueOf(row.get("documentNo") == null ? "" : row.get("documentNo"))));

            BigDecimal totalRolls = BigDecimal.ZERO;
            BigDecimal totalArea = BigDecimal.ZERO;
            BigDecimal totalAmount = BigDecimal.ZERO;
            BigDecimal deliveryAmount = BigDecimal.ZERO;
            BigDecimal returnAmount = BigDecimal.ZERO;
            for (Map<String, Object> row : detailRows) {
                BigDecimal rolls = toDecimal(row.get("quantity"));
                BigDecimal area = toDecimal(row.get("areaSize"));
                BigDecimal amount = toDecimal(row.get("amount"));
                totalRolls = totalRolls.add(rolls);
                totalArea = totalArea.add(area);
                totalAmount = totalAmount.add(amount);
                if ("return".equals(row.get("bizType"))) {
                    returnAmount = returnAmount.add(amount);
                } else {
                    deliveryAmount = deliveryAmount.add(amount);
                }
            }

            List<SalesStatementHistory> histories = salesStatementHistoryMapper.selectList(
                    new LambdaQueryWrapper<SalesStatementHistory>()
                            .eq(SalesStatementHistory::getCustomerCode, customerCode.trim())
                            .eq(SalesStatementHistory::getIsDeleted, 0)
                            .orderByDesc(SalesStatementHistory::getStatementMonth)
                            .orderByDesc(SalesStatementHistory::getInvoiceDate)
            );

            List<SalesStatementHistory> printHistories = new ArrayList<>();
            for (SalesStatementHistory history : histories) {
                if (history != null && hasText(history.getStatementMonth()) && history.getStatementMonth().compareTo(month) <= 0) {
                    printHistories.add(history);
                }
            }
            printHistories.sort(Comparator.comparing(SalesStatementHistory::getStatementMonth));

            Map<String, Object> summary = new HashMap<>();
            summary.put("totalRolls", totalRolls.setScale(2, RoundingMode.HALF_UP));
            summary.put("totalArea", totalArea.setScale(2, RoundingMode.HALF_UP));
            summary.put("totalAmount", totalAmount.setScale(2, RoundingMode.HALF_UP));
            summary.put("deliveryAmount", deliveryAmount.setScale(2, RoundingMode.HALF_UP));
            summary.put("returnAmount", returnAmount.setScale(2, RoundingMode.HALF_UP));

            Map<String, Object> data = new HashMap<>();
            data.put("customerCode", customerCode.trim());
            data.put("customerName", customer != null && hasText(customer.getCustomerName()) ? customer.getCustomerName() : customerCode.trim());
            data.put("customerShortName", customer != null ? customer.getShortName() : "");
            data.put("month", month);
            data.put("detailRows", detailRows);
            data.put("historyRows", histories);
            data.put("printHistoryRows", printHistories);
            data.put("summary", summary);
            return new ResponseResult<>(200, "查询成功", data);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "查询对账单失败: " + e.getMessage(), null);
        }
    }

    @Override
    public ResponseResult<?> getHistory(String customerCode) {
        try {
            ensureHistoryTable();
            if (!hasText(customerCode)) {
                return new ResponseResult<>(400, "客户不能为空", null);
            }
            if (!canAccessCustomer(customerCode)) {
                return new ResponseResult<>(403, "无权限访问该客户", null);
            }
            List<SalesStatementHistory> histories = salesStatementHistoryMapper.selectList(
                    new LambdaQueryWrapper<SalesStatementHistory>()
                            .eq(SalesStatementHistory::getCustomerCode, customerCode.trim())
                            .eq(SalesStatementHistory::getIsDeleted, 0)
                            .orderByDesc(SalesStatementHistory::getStatementMonth)
                            .orderByDesc(SalesStatementHistory::getInvoiceDate)
            );
            return new ResponseResult<>(200, "查询成功", histories);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "查询历史台账失败: " + e.getMessage(), null);
        }
    }

    @Override
    public ResponseResult<?> saveHistory(SalesStatementHistory history) {
        try {
            ensureHistoryTable();
            if (history == null || !hasText(history.getCustomerCode())) {
                return new ResponseResult<>(400, "客户不能为空", null);
            }
            if (!canAccessCustomer(history.getCustomerCode())) {
                return new ResponseResult<>(403, "无权限操作该客户", null);
            }
            if (!hasText(history.getStatementMonth()) || !history.getStatementMonth().matches("\\d{4}-\\d{2}")) {
                return new ResponseResult<>(400, "对账月份格式应为yyyy-MM", null);
            }
            history.setCustomerCode(history.getCustomerCode().trim());
            history.setStatementMonth(history.getStatementMonth().trim());
            history.setUnpaidAmount(defaultDecimal(history.getUnpaidAmount()));
            history.setInvoiceAmount(defaultDecimal(history.getInvoiceAmount()));
            history.setUpdatedAt(new Date());
            history.setUpdatedBy(getCurrentUsername());
            if (history.getId() == null) {
                history.setCreatedAt(new Date());
                history.setCreatedBy(getCurrentUsername());
                history.setIsDeleted(0);
                salesStatementHistoryMapper.insert(history);
            } else {
                SalesStatementHistory existing = salesStatementHistoryMapper.selectById(history.getId());
                if (existing == null || Objects.equals(existing.getIsDeleted(), 1)) {
                    return new ResponseResult<>(404, "历史台账不存在", null);
                }
                if (!canAccessCustomer(existing.getCustomerCode())) {
                    return new ResponseResult<>(403, "无权限操作该客户", null);
                }
                history.setCreatedAt(existing.getCreatedAt());
                history.setCreatedBy(existing.getCreatedBy());
                history.setIsDeleted(0);
                salesStatementHistoryMapper.updateById(history);
            }
            return new ResponseResult<>(200, "保存成功", history);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "保存历史台账失败: " + e.getMessage(), null);
        }
    }

    @Override
    public ResponseResult<?> deleteHistory(Long id) {
        try {
            ensureHistoryTable();
            if (id == null) {
                return new ResponseResult<>(400, "ID不能为空", null);
            }
            SalesStatementHistory existing = salesStatementHistoryMapper.selectById(id);
            if (existing == null || Objects.equals(existing.getIsDeleted(), 1)) {
                return new ResponseResult<>(404, "历史台账不存在", null);
            }
            if (!canAccessCustomer(existing.getCustomerCode())) {
                return new ResponseResult<>(403, "无权限操作该客户", null);
            }
            existing.setIsDeleted(1);
            existing.setUpdatedAt(new Date());
            existing.setUpdatedBy(getCurrentUsername());
            salesStatementHistoryMapper.updateById(existing);
            return new ResponseResult<>(200, "删除成功", null);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "删除历史台账失败: " + e.getMessage(), null);
        }
    }

    private List<Map<String, Object>> queryDeliveryRows(List<String> customerKeys, String month) {
        if (customerKeys == null || customerKeys.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = buildPlaceholders(customerKeys.size());
        String sql = "SELECT DATE_FORMAT(dn.delivery_date, '%Y-%m-%d') AS bizDate, " +
                "'delivery' AS bizType, " +
                "dn.notice_no AS documentNo, " +
                "dn.order_no AS orderNo, " +
                "COALESCE(dn.customer_order_no, so.customer_order_no, '') AS customerOrderNo, " +
                "dni.material_code AS materialCode, " +
            "COALESCE(ts.product_name, soi.material_name, '') AS materialName, " +
                "COALESCE(dni.spec, '') AS spec, " +
                "soi.thickness AS thickness, " +
                "soi.width AS width, " +
                "soi.length AS length, " +
                "COALESCE(dni.quantity, 0) AS quantity, " +
                "COALESCE(dni.area_size, 0) AS areaSize, " +
                "COALESCE(soi.unit_price, 0) AS unitPrice, " +
                "ROUND(COALESCE(dni.area_size, 0) * COALESCE(soi.unit_price, 0), 2) AS amount " +
                "FROM delivery_notices dn " +
                "LEFT JOIN delivery_notice_items dni ON dn.id = dni.notice_id " +
                "LEFT JOIN sales_order_items soi ON dni.order_item_id = soi.id " +
            "LEFT JOIN tape_spec ts ON " +
            "CONVERT(ts.material_code USING utf8mb4) COLLATE utf8mb4_unicode_ci = " +
            "CONVERT(dni.material_code USING utf8mb4) COLLATE utf8mb4_unicode_ci " +
                "LEFT JOIN sales_orders so ON so.id = dn.order_id " +
                "WHERE dn.is_deleted = 0 AND (dn.status IS NULL OR dn.status NOT IN ('cancelled', '已作废')) " +
                "AND DATE_FORMAT(dn.delivery_date, '%Y-%m') = ? " +
                "AND dn.customer IN (" + placeholders + ") " +
                "ORDER BY dn.delivery_date ASC, dn.notice_no ASC";
        List<Object> args = new ArrayList<>();
        args.add(month);
        args.addAll(customerKeys);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
        for (Map<String, Object> row : rows) {
            row.put("spec", resolveSpec(row));
            row.put("quantity", toDecimal(row.get("quantity")).setScale(0, RoundingMode.HALF_UP));
            row.put("areaSize", toDecimal(row.get("areaSize")).setScale(2, RoundingMode.HALF_UP));
            row.put("unitPrice", toDecimal(row.get("unitPrice")).setScale(4, RoundingMode.HALF_UP));
            row.put("amount", toDecimal(row.get("amount")).setScale(2, RoundingMode.HALF_UP));
            row.put("typeLabel", "发货");
        }
        return rows;
    }

    private List<Map<String, Object>> queryReturnRows(List<String> customerKeys, String month) {
        if (customerKeys == null || customerKeys.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = buildPlaceholders(customerKeys.size());
        String sql = "SELECT DATE_FORMAT(sro.return_date, '%Y-%m-%d') AS bizDate, " +
                "'return' AS bizType, " +
                "sro.return_no AS documentNo, " +
                "sri.order_no AS orderNo, " +
                "COALESCE(so.customer_order_no, '') AS customerOrderNo, " +
                "sri.material_code AS materialCode, " +
                "sri.material_name AS materialName, " +
                "'' AS spec, " +
                "sri.thickness AS thickness, " +
                "sri.width AS width, " +
                "sri.length AS length, " +
                "-COALESCE(sri.rolls, 0) AS quantity, " +
                "-COALESCE(sri.sqm, 0) AS areaSize, " +
                "COALESCE(sri.unit_price, 0) AS unitPrice, " +
                "-COALESCE(sri.amount, 0) AS amount " +
                "FROM sales_return_orders sro " +
                "LEFT JOIN sales_return_items sri ON sro.id = sri.return_id " +
                "LEFT JOIN sales_orders so ON " +
                "CONVERT(so.order_no USING utf8mb4) COLLATE utf8mb4_unicode_ci = " +
                "CONVERT(sri.order_no USING utf8mb4) COLLATE utf8mb4_unicode_ci " +
                "AND so.is_deleted = 0 " +
                "WHERE sro.is_deleted = 0 AND sro.statement_month = ? " +
                "AND sro.customer IN (" + placeholders + ") " +
                "ORDER BY sro.return_date ASC, sro.return_no ASC";
        List<Object> args = new ArrayList<>();
        args.add(month);
        args.addAll(customerKeys);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
        for (Map<String, Object> row : rows) {
            row.put("spec", resolveSpec(row));
            row.put("quantity", toDecimal(row.get("quantity")).setScale(0, RoundingMode.HALF_UP));
            row.put("areaSize", toDecimal(row.get("areaSize")).setScale(2, RoundingMode.HALF_UP));
            row.put("unitPrice", toDecimal(row.get("unitPrice")).setScale(4, RoundingMode.HALF_UP));
            row.put("amount", toDecimal(row.get("amount")).setScale(2, RoundingMode.HALF_UP));
            row.put("typeLabel", "退货");
        }
        return rows;
    }

    private void ensureHistoryTable() {
        if (historyTableChecked) {
            return;
        }
        synchronized (this) {
            if (historyTableChecked) {
                return;
            }
            if (!tableExists("sales_statement_history")) {
                throw new IllegalStateException("对账模块缺少历史台账表，请先执行版本化脚本: sql/V20260316_02__sales_statement_history.sql");
            }
            historyTableChecked = true;
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private String buildPlaceholders(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("?");
        }
        return sb.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String resolveSpec(Map<String, Object> row) {
        String spec = String.valueOf(row.getOrDefault("spec", ""));
        if (hasText(spec)) {
            return spec;
        }
        String thickness = trimDecimal(row.get("thickness"));
        String width = trimDecimal(row.get("width"));
        String length = trimDecimal(row.get("length"));
        if (!hasText(thickness) && !hasText(width) && !hasText(length)) {
            return "-";
        }
        return thickness + "μm*" + width + "mm*" + length + "m";
    }

    private String trimDecimal(Object value) {
        BigDecimal decimal = toDecimal(value).stripTrailingZeros();
        if (decimal.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        return decimal.toPlainString();
    }

    private LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return (LoginUser) authentication.getPrincipal();
        }
        return null;
    }

    private boolean hasRole(LoginUser loginUser, String role) {
        return loginUser != null && loginUser.getPermissions() != null && loginUser.getPermissions().contains(role);
    }

    private Long getCurrentUserId(LoginUser loginUser) {
        return loginUser != null && loginUser.getUser() != null ? loginUser.getUser().getId() : null;
    }

    private boolean canAccessCustomer(String customerCode) {
        if (!hasText(customerCode)) {
            return false;
        }
        LoginUser loginUser = getLoginUser();
        if (loginUser == null || hasRole(loginUser, "admin")) {
            return true;
        }
        Long uid = getCurrentUserId(loginUser);
        if (uid == null) {
            return false;
        }

        String target = customerCode.trim();
        Customer customer = customerMapper.selectByCustomerCode(target);
        Set<String> allowed = new LinkedHashSet<>();
        List<String> names = customerMapper.selectCustomerNamesByOwner(uid);
        List<String> codes = customerMapper.selectCustomerCodesByOwner(uid);
        if (names != null) {
            for (String name : names) {
                if (hasText(name)) {
                    allowed.add(name.trim());
                }
            }
        }
        if (codes != null) {
            for (String code : codes) {
                if (hasText(code)) {
                    allowed.add(code.trim());
                }
            }
        }
        if (customer != null) {
            if (hasText(customer.getCustomerName())) {
                allowed.add(customer.getCustomerName().trim());
            }
            if (hasText(customer.getShortName())) {
                allowed.add(customer.getShortName().trim());
            }
            if (hasText(customer.getCustomerCode())) {
                allowed.add(customer.getCustomerCode().trim());
            }
        }
        return allowed.contains(target);
    }

    private String getCurrentUsername() {
        LoginUser loginUser = getLoginUser();
        return loginUser != null ? loginUser.getUsername() : "system";
    }
}
