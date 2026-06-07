package com.fine.serviceIMPL;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fine.Dao.CustomerMapper;
import com.fine.Dao.DeliveryNoticeItemMapper;
import com.fine.Dao.DeliveryNoticeMapper;
import com.fine.Dao.SalesOrderItemMapper;
import com.fine.Dao.SalesStatementHistoryMapper;
import com.fine.Dao.production.SalesOrderMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.Customer;
import com.fine.modle.DeliveryNotice;
import com.fine.modle.DeliveryNoticeItem;
import com.fine.modle.LoginUser;
import com.fine.modle.SalesOrder;
import com.fine.modle.SalesOrderItem;
import com.fine.modle.SalesStatementHistory;
import com.fine.service.SalesReconciliationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import com.fine.modle.SalesReconciliationConfirmRequest;

@Service
public class SalesReconciliationServiceImpl implements SalesReconciliationService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private SalesStatementHistoryMapper salesStatementHistoryMapper;

    @Autowired
    private SalesOrderMapper salesOrderMapper;

    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;

    @Autowired
    private DeliveryNoticeMapper deliveryNoticeMapper;

    @Autowired
    private DeliveryNoticeItemMapper deliveryNoticeItemMapper;

    private static final Logger logger = LoggerFactory.getLogger(SalesReconciliationServiceImpl.class);

    private volatile boolean historyTableChecked = false;
    private static final String RECON_BASIS_SHIPPED = "SHIPPED";
    private static final String RECON_BASIS_RECEIVED = "RECEIVED";
    private static final String RP_CUSTOMER_CODE_KEYWORD = "RP";
    private static final long OVERVIEW_CACHE_TTL_MS = 600_000L;
    private final ConcurrentMap<String, OverviewCacheEntry> overviewCache = new ConcurrentHashMap<>();

    private static class OverviewCacheEntry {
        private final List<Map<String, Object>> rows;
        private final long expiresAt;

        private OverviewCacheEntry(List<Map<String, Object>> rows, long expiresAt) {
            this.rows = rows == null ? Collections.emptyList() : rows;
            this.expiresAt = expiresAt;
        }

        private boolean isExpired(long now) {
            return now >= expiresAt;
        }
    }

    @Override
    public ResponseResult<?> getStatement(String customerCode, String month) {
        try {
            ensureHistoryTable();
            ensureDeliveryConfirmTable();
            ensureDeliverySplitTable();
            ensureReturnConfirmTable();
            ensureStatementMonthConfirmTable();
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

            boolean rpNaturalMonthLocked = isRpCustomer(customer, customerCode);
            int reconciliationDay = resolveEffectiveReconciliationDay(customer, customerCode);
            String reconciliationBasis = normalizeReconciliationBasis(customer == null ? null : customer.getReconciliationBasis());
            LocalDate periodStart = getStatementPeriodStart(month, reconciliationDay);
            LocalDate periodEnd = getStatementPeriodEnd(month, reconciliationDay);

            boolean excludeLegacyYearDeliveries = shouldExcludeLegacyYearDeliveries(customerCode, month);
            List<Map<String, Object>> detailRows = new ArrayList<>();
            detailRows.addAll(queryDeliveryRows(new ArrayList<>(customerKeys), month, periodStart, periodEnd, reconciliationDay, reconciliationBasis, rpNaturalMonthLocked, excludeLegacyYearDeliveries));
            detailRows.addAll(queryReturnRows(new ArrayList<>(customerKeys), month, rpNaturalMonthLocked));
            detailRows.sort(Comparator.comparing((Map<String, Object> row) -> String.valueOf(row.get("bizDate") == null ? "" : row.get("bizDate")))
                    .thenComparing((Map<String, Object> row) -> String.valueOf(row.get("documentNo") == null ? "" : row.get("documentNo"))));

            BigDecimal totalRolls = BigDecimal.ZERO;
            BigDecimal totalArea = BigDecimal.ZERO;
            BigDecimal totalAmount = BigDecimal.ZERO;
            BigDecimal deliveryAmount = BigDecimal.ZERO;
            BigDecimal returnAmount = BigDecimal.ZERO;
            for (Map<String, Object> row : detailRows) {
                boolean includeInCurrent = Boolean.TRUE.equals(row.get("includeInCurrentStatement"));
                if (!includeInCurrent) {
                    continue;
                }
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

                appendAutoPreviousMonthHistory(histories, customerCode.trim(), month);

            List<SalesStatementHistory> visibleHistories = new ArrayList<>();
            for (SalesStatementHistory history : histories) {
                if (history != null && hasText(history.getStatementMonth()) && history.getStatementMonth().compareTo(month) <= 0) {
                    visibleHistories.add(history);
                }
            }
            visibleHistories.sort(Comparator.comparing(SalesStatementHistory::getStatementMonth).reversed());

            List<SalesStatementHistory> printHistories = new ArrayList<>();
            printHistories.addAll(visibleHistories);
            printHistories.sort(Comparator.comparing(SalesStatementHistory::getStatementMonth));

            Map<String, Object> summary = new HashMap<>();
            summary.put("totalRolls", totalRolls.setScale(2, RoundingMode.HALF_UP));
            summary.put("totalArea", totalArea.setScale(2, RoundingMode.HALF_UP));
            summary.put("totalAmount", totalAmount.setScale(2, RoundingMode.HALF_UP));
            summary.put("deliveryAmount", deliveryAmount.setScale(2, RoundingMode.HALF_UP));
            summary.put("returnAmount", returnAmount.setScale(2, RoundingMode.HALF_UP));

            int currentDeliveryCount = 0;
            int currentConfirmedDeliveryCount = 0;
            for (Map<String, Object> row : detailRows) {
                if (!"delivery".equals(String.valueOf(row.get("bizType")))) {
                    continue;
                }
                if (!Boolean.TRUE.equals(row.get("includeInCurrentStatement"))) {
                    continue;
                }
                currentDeliveryCount++;
                if (Boolean.TRUE.equals(row.get("isConfirmed"))) {
                    currentConfirmedDeliveryCount++;
                }
            }
            int currentPendingDeliveryCount = Math.max(0, currentDeliveryCount - currentConfirmedDeliveryCount);
            String reconciliationStatus = (currentPendingDeliveryCount == 0) ? "RECONCILED" : "UNRECONCILED";
            String reconciliationStatusLabel = "RECONCILED".equals(reconciliationStatus) ? "已对账" : "未对账";

            Map<String, Object> monthConfirmInfo = loadStatementMonthConfirmInfo(customerCode.trim(), month);
            LoginUser loginUser = getLoginUser();
            boolean financeView = isFinanceUser(loginUser);
            String salesConfirmedAt = safeTrim(String.valueOf(monthConfirmInfo.getOrDefault("salesConfirmedAt", "")));
            String financeConfirmedAt = safeTrim(String.valueOf(monthConfirmInfo.getOrDefault("financeConfirmedAt", "")));
            if (financeView && !hasText(salesConfirmedAt)) {
                return new ResponseResult<>(403, "销售未确认，财务暂不可查看该对账单", null);
            }

            // 当以财务身份查看时，详情页的对账状态应以财务确认为准，避免展示与总览不一致的状态
            if (financeView) {
                reconciliationStatus = hasText(financeConfirmedAt) ? "RECONCILED" : "UNRECONCILED";
                reconciliationStatusLabel = hasText(financeConfirmedAt) ? "财务已确认" : "销售已确认，财务未确认";
            }

            Map<String, Object> data = new HashMap<>();
            data.put("customerCode", customerCode.trim());
            data.put("customerName", customer != null && hasText(customer.getCustomerName()) ? customer.getCustomerName() : customerCode.trim());
            data.put("customerShortName", customer != null ? customer.getShortName() : "");
            data.put("month", month);
            data.put("defaultReconciliationDay", reconciliationDay);
            data.put("rpNaturalMonthLocked", rpNaturalMonthLocked);
            data.put("reconciliationBasis", reconciliationBasis);
            data.put("periodStart", periodStart.toString());
            data.put("periodEnd", periodEnd.toString());
            data.put("detailRows", detailRows);
            data.put("historyRows", visibleHistories);
            data.put("printHistoryRows", printHistories);
            data.put("summary", summary);
            data.put("reconciliationStatus", reconciliationStatus);
            data.put("reconciliationStatusLabel", reconciliationStatusLabel);
            data.put("currentDeliveryCount", currentDeliveryCount);
            data.put("confirmedDeliveryCount", currentConfirmedDeliveryCount);
            data.put("pendingDeliveryCount", currentPendingDeliveryCount);
            data.putAll(monthConfirmInfo);
            return new ResponseResult<>(200, "查询成功", data);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "查询对账单失败: " + e.getMessage(), null);
        }
    }

    @Override
    public ResponseResult<?> getStatementOverview(String month, String customerCode, String reconciledStatus, Integer current, Integer size, String sortProp, String sortOrder) {
        try {
            ensureHistoryTable();
            ensureDeliveryConfirmTable();
            ensureStatementMonthConfirmTable();
            ensureReturnConfirmTable();

            if (!hasText(month) || !month.matches("\\d{4}-\\d{2}")) {
                return new ResponseResult<>(400, "月份格式应为yyyy-MM", null);
            }
            LoginUser loginUser = getLoginUser();
            boolean isAdmin = hasRole(loginUser, "admin");
            boolean financeUser = isFinanceUser(loginUser);

            int safeCurrent = (current == null || current <= 0) ? 1 : current;
            int safeSize = (size == null || size <= 0) ? 20 : Math.min(size, 200);

            String cacheKey = buildOverviewCacheKey(month);
            Set<String> scopedCustomerCodes = null;
            if (hasText(customerCode)) {
                scopedCustomerCodes = new LinkedHashSet<>();
                scopedCustomerCodes.add(customerCode.trim());
            }
            List<Map<String, Object>> allRows = (scopedCustomerCodes == null || scopedCustomerCodes.isEmpty())
                    ? getOrBuildOverviewRows(cacheKey, month)
                    : getOrBuildOverviewRows(cacheKey, month, scopedCustomerCodes, false);

            String keyword = hasText(customerCode) ? customerCode.trim() : "";
            String statusFilter = hasText(reconciledStatus) ? reconciledStatus.trim().toUpperCase(Locale.ROOT) : "";

            List<Map<String, Object>> filtered = new ArrayList<>();
            for (Map<String, Object> row : allRows) {
                String rowCustomerCode = String.valueOf(row.getOrDefault("customerCode", ""));
                String rowCustomerName = String.valueOf(row.getOrDefault("customerName", ""));
                String salesConfirmedAt = safeTrim(String.valueOf(row.getOrDefault("salesConfirmedAt", "")));
                String financeConfirmedAt = safeTrim(String.valueOf(row.getOrDefault("financeConfirmedAt", "")));

                if (financeUser && !isAdmin && !hasText(salesConfirmedAt)) {
                    continue;
                }

                if (hasText(keyword)) {
                    String k = keyword.toLowerCase(Locale.ROOT);
                    if (!rowCustomerCode.toLowerCase(Locale.ROOT).contains(k)
                            && !rowCustomerName.toLowerCase(Locale.ROOT).contains(k)) {
                        continue;
                    }
                }

                if (hasText(statusFilter)) {
                    if ("RECONCILED".equals(statusFilter)) {
                        if (!hasText(financeConfirmedAt)) {
                            continue;
                        }
                    } else if ("UNRECONCILED".equals(statusFilter)) {
                        if (financeUser && !isAdmin) {
                            if (!hasText(salesConfirmedAt) || hasText(financeConfirmedAt)) {
                                continue;
                            }
                        } else {
                            if (hasText(salesConfirmedAt) || hasText(financeConfirmedAt)) {
                                continue;
                            }
                        }
                    } else if ("SALES_CONFIRMED".equals(statusFilter)) {
                        if (!hasText(salesConfirmedAt) || hasText(financeConfirmedAt)) {
                            continue;
                        }
                    }
                }

                filtered.add(row);
            }

            String safeSortProp = hasText(sortProp) ? sortProp.trim() : "statementAmount";
            String safeSortOrder = hasText(sortOrder) ? sortOrder.trim().toLowerCase(Locale.ROOT) : "descending";
            boolean isDesc = "descending".equals(safeSortOrder) || "desc".equals(safeSortOrder);
            filtered.sort(buildOverviewComparator(safeSortProp, isDesc));

            int total = filtered.size();
            int fromIndex = Math.max(0, (safeCurrent - 1) * safeSize);
            int toIndex = Math.min(total, fromIndex + safeSize);
            List<Map<String, Object>> pageRows = fromIndex >= total ? Collections.emptyList() : filtered.subList(fromIndex, toIndex);

            Map<String, Object> data = new HashMap<>();
            data.put("month", month);
            data.put("current", safeCurrent);
            data.put("size", safeSize);
            data.put("total", total);
            data.put("records", pageRows);
            data.put("rows", pageRows);
            return ResponseResult.success(data);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "查询概览失败: " + e.getMessage(), null);
        }
    }

    private String buildOverviewCacheKey(String month) {
        return getCurrentUsername() + "|" + month;
    }

    private List<Map<String, Object>> getOrBuildOverviewRows(String cacheKey, String month) {
        return getOrBuildOverviewRows(cacheKey, month, null, true);
    }

    private List<Map<String, Object>> getOrBuildOverviewRows(String cacheKey, String month, Set<String> scopedCustomerCodes, boolean useCache) {
        if (!useCache || (scopedCustomerCodes != null && !scopedCustomerCodes.isEmpty())) {
            return buildOverviewRows(month, scopedCustomerCodes);
        }

        long now = System.currentTimeMillis();
        OverviewCacheEntry cached = overviewCache.get(cacheKey);
        if (cached != null && !cached.isExpired(now)) {
            return cached.rows;
        }

        List<Map<String, Object>> rebuilt = buildOverviewRows(month, null);
        overviewCache.put(cacheKey, new OverviewCacheEntry(rebuilt, now + OVERVIEW_CACHE_TTL_MS));
        return rebuilt;
    }

    private List<Map<String, Object>> buildOverviewRows(String month) {
        return buildOverviewRows(month, null);
    }

    private List<Map<String, Object>> buildOverviewRows(String month, Set<String> scopedCustomerCodes) {
        List<Customer> customers = loadAccessibleCustomersForOverview();
        if (customers == null || customers.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> scoped = null;
        if (scopedCustomerCodes != null && !scopedCustomerCodes.isEmpty()) {
            scoped = scopedCustomerCodes.stream()
                    .filter(this::hasText)
                    .map(String::trim)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        Map<String, Customer> customerByCode = new LinkedHashMap<>();
        Set<String> customerCodes = new LinkedHashSet<>();
        for (Customer customer : customers) {
            if (customer == null || !hasText(customer.getCustomerCode())) {
                continue;
            }
            String code = customer.getCustomerCode().trim();
            if (scoped != null && !scoped.contains(code)) {
                continue;
            }
            customerByCode.put(code, customer);
            customerCodes.add(code);
        }

        if (customerByCode.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, OverviewAggregate> aggregateMap = new HashMap<>();
        for (String customerCode : customerByCode.keySet()) {
            aggregateMap.put(customerCode, new OverviewAggregate());
        }

        Map<String, Map<String, Object>> monthConfirmMap = new HashMap<>();
        if (!customerCodes.isEmpty()) {
            String confirmPlaceholders = buildPlaceholders(customerCodes.size());
            List<Object> confirmArgs = new ArrayList<>();
            confirmArgs.add(month);
            confirmArgs.addAll(customerCodes);
            List<Map<String, Object>> confirmRows = jdbcTemplate.queryForList(
                    "SELECT customer_code AS customerCode, " +
                            "IFNULL(sales_confirmed_by,'') AS salesConfirmedBy, " +
                            "DATE_FORMAT(sales_confirmed_at, '%Y-%m-%d %H:%i:%s') AS salesConfirmedAt, " +
                            "IFNULL(finance_confirmed_by,'') AS financeConfirmedBy, " +
                            "DATE_FORMAT(finance_confirmed_at, '%Y-%m-%d %H:%i:%s') AS financeConfirmedAt " +
                            "FROM sales_statement_month_confirm WHERE statement_month = ? AND customer_code IN (" + confirmPlaceholders + ")",
                    confirmArgs.toArray()
            );
            for (Map<String, Object> row : confirmRows) {
                String code = row.get("customerCode") == null ? "" : String.valueOf(row.get("customerCode")).trim();
                if (hasText(code)) {
                    monthConfirmMap.put(code, row);
                }
            }
        }

        // 1) 按客户复用详情同口径计算（含拆分顺延/退货/计价规则），避免总览与详情不一致
        for (Map.Entry<String, Customer> entry : customerByCode.entrySet()) {
            String customerCode = entry.getKey();
            Customer customer = entry.getValue();

            Set<String> customerKeys = new LinkedHashSet<>();
            customerKeys.add(customerCode);
            if (customer != null) {
                if (hasText(customer.getCustomerName())) {
                    customerKeys.add(customer.getCustomerName().trim());
                }
                if (hasText(customer.getShortName())) {
                    customerKeys.add(customer.getShortName().trim());
                }
            }

            int reconciliationDay = resolveEffectiveReconciliationDay(customer, customerCode);
            String reconciliationBasis = normalizeReconciliationBasis(customer == null ? null : customer.getReconciliationBasis());
            boolean rpNaturalMonthLocked = isRpCustomer(customer, customerCode);
            boolean excludeLegacyYearDeliveries = shouldExcludeLegacyYearDeliveries(customerCode, month);
            LocalDate periodStart = getStatementPeriodStart(month, reconciliationDay);
            LocalDate periodEnd = getStatementPeriodEnd(month, reconciliationDay);

            List<Map<String, Object>> detailRows = new ArrayList<>();
            detailRows.addAll(queryDeliveryRows(
                    new ArrayList<>(customerKeys),
                    month,
                    periodStart,
                    periodEnd,
                    reconciliationDay,
                    reconciliationBasis,
                    rpNaturalMonthLocked,
                    excludeLegacyYearDeliveries
            ));
            detailRows.addAll(queryReturnRows(new ArrayList<>(customerKeys), month, rpNaturalMonthLocked));

            OverviewAggregate agg = aggregateMap.computeIfAbsent(customerCode, k -> new OverviewAggregate());
            for (Map<String, Object> row : detailRows) {
                if (!Boolean.TRUE.equals(row.get("includeInCurrentStatement"))) {
                    continue;
                }
                String bizType = String.valueOf(row.getOrDefault("bizType", "")).trim();
                BigDecimal amount = toDecimal(row.get("amount"));
                if ("delivery".equals(bizType)) {
                    agg.currentDeliveryCount++;
                    if (Boolean.TRUE.equals(row.get("isConfirmed"))) {
                        agg.confirmedDeliveryCount++;
                    }
                    agg.deliveryAmount = agg.deliveryAmount.add(amount);
                } else if ("return".equals(bizType)) {
                    agg.returnAmount = agg.returnAmount.add(amount);
                }
            }
        }

        // 3) 批量拉取当月开票金额
        Map<String, BigDecimal> invoiceMap = new HashMap<>();
        if (!customerCodes.isEmpty()) {
            String codePlaceholders = buildPlaceholders(customerCodes.size());
            List<Object> invoiceArgs = new ArrayList<>();
            invoiceArgs.add(month);
            invoiceArgs.addAll(customerCodes);
            String invoiceSql = "SELECT customer_code AS customerCode, COALESCE(SUM(invoice_amount), 0) AS invoiceAmount " +
                    "FROM sales_statement_history " +
                    "WHERE is_deleted = 0 AND statement_month = ? AND customer_code IN (" + codePlaceholders + ") " +
                    "GROUP BY customer_code";
            List<Map<String, Object>> invoiceRows = jdbcTemplate.queryForList(invoiceSql, invoiceArgs.toArray());
            for (Map<String, Object> row : invoiceRows) {
                String code = row.get("customerCode") == null ? "" : String.valueOf(row.get("customerCode")).trim();
                if (hasText(code)) {
                    invoiceMap.put(code, toDecimal(row.get("invoiceAmount")).setScale(2, RoundingMode.HALF_UP));
                }
            }
        }

        // 4) 批量拉取历史欠账/开票汇总，确保与详情页期末应付口径一致
        Map<String, BigDecimal> historyUnpaidMap = new HashMap<>();
        Map<String, BigDecimal> historyInvoiceMap = new HashMap<>();
        if (!customerCodes.isEmpty()) {
            String codePlaceholders = buildPlaceholders(customerCodes.size());
            List<Object> historyArgs = new ArrayList<>();
            historyArgs.add(month);
            historyArgs.addAll(customerCodes);
            String historySql = "SELECT customer_code AS customerCode, " +
                    "COALESCE(SUM(unpaid_amount), 0) AS totalUnpaid, " +
                    "COALESCE(SUM(invoice_amount), 0) AS totalInvoice " +
                    "FROM sales_statement_history " +
                    "WHERE is_deleted = 0 AND statement_month <= ? AND customer_code IN (" + codePlaceholders + ") " +
                    "GROUP BY customer_code";
            List<Map<String, Object>> historyRows = jdbcTemplate.queryForList(historySql, historyArgs.toArray());
            for (Map<String, Object> row : historyRows) {
                String code = row.get("customerCode") == null ? "" : String.valueOf(row.get("customerCode")).trim();
                if (!hasText(code)) {
                    continue;
                }
                historyUnpaidMap.put(code, toDecimal(row.get("totalUnpaid")).setScale(2, RoundingMode.HALF_UP));
                historyInvoiceMap.put(code, toDecimal(row.get("totalInvoice")).setScale(2, RoundingMode.HALF_UP));
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Customer> entry : customerByCode.entrySet()) {
            String customerCode = entry.getKey();
            Customer customer = entry.getValue();
            OverviewAggregate agg = aggregateMap.getOrDefault(customerCode, new OverviewAggregate());

            BigDecimal statementAmount = agg.deliveryAmount.add(agg.returnAmount).setScale(2, RoundingMode.HALF_UP);
            int pendingDeliveryCount = Math.max(0, agg.currentDeliveryCount - agg.confirmedDeliveryCount);

            Map<String, Object> row = new HashMap<>();
            row.put("customerCode", customerCode);
            row.put("customerName", hasText(customer.getShortName()) ? customer.getShortName() : customer.getCustomerName());
            row.put("month", month);
            row.put("statementAmount", statementAmount);
            row.put("invoiceAmount", invoiceMap.getOrDefault(customerCode, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)));
            row.put("receivedAmount", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            BigDecimal historyUnpaid = historyUnpaidMap.getOrDefault(customerCode, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            BigDecimal historyInvoice = historyInvoiceMap.getOrDefault(customerCode, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            BigDecimal totalBalance = statementAmount.add(historyUnpaid).subtract(historyInvoice).setScale(2, RoundingMode.HALF_UP);
            row.put("totalBalance", totalBalance);
            row.put("pendingDeliveryCount", pendingDeliveryCount);
            row.put("confirmedDeliveryCount", agg.confirmedDeliveryCount);
            row.put("currentDeliveryCount", agg.currentDeliveryCount);

            Map<String, Object> confirm = monthConfirmMap.get(customerCode);
            String salesConfirmedBy = confirm == null ? "" : safeTrim(String.valueOf(confirm.getOrDefault("salesConfirmedBy", "")));
            String salesConfirmedAt = confirm == null ? "" : safeTrim(String.valueOf(confirm.getOrDefault("salesConfirmedAt", "")));
            String financeConfirmedBy = confirm == null ? "" : safeTrim(String.valueOf(confirm.getOrDefault("financeConfirmedBy", "")));
            String financeConfirmedAt = confirm == null ? "" : safeTrim(String.valueOf(confirm.getOrDefault("financeConfirmedAt", "")));
            row.put("salesConfirmedBy", salesConfirmedBy);
            row.put("salesConfirmedAt", salesConfirmedAt);
            row.put("financeConfirmedBy", financeConfirmedBy);
            row.put("financeConfirmedAt", financeConfirmedAt);
            boolean financeConfirmed = hasText(financeConfirmedAt);
            boolean salesConfirmed = hasText(salesConfirmedAt);
            row.put("reconciliationStatus", financeConfirmed ? "RECONCILED" : "UNRECONCILED");
            
            if (financeConfirmed) {
                row.put("reconciliationStatusLabel", "财务已确认");
            } else if (salesConfirmed) {
                row.put("reconciliationStatusLabel", "待财务确认");
            } else {
                row.put("reconciliationStatusLabel", pendingDeliveryCount == 0 ? "已对账" : "未对账");
            }

            if (hasText(financeConfirmedAt)) {
                row.put("confirmStage", "FINANCE");
            } else if (hasText(salesConfirmedAt)) {
                row.put("confirmStage", "SALES");
            } else {
                row.put("confirmStage", "UNCONFIRMED");
            }
            rows.add(row);
        }
        return rows;
    }

    private Set<String> loadSalesConfirmedCustomerCodes(String month) {
        if (!hasText(month)) {
            return Collections.emptySet();
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT DISTINCT customer_code AS customerCode FROM sales_statement_month_confirm " +
                        "WHERE statement_month = ? AND sales_confirmed_at IS NOT NULL",
                month.trim()
        );
        Set<String> result = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            String code = row == null ? "" : safeTrim(String.valueOf(row.getOrDefault("customerCode", "")));
            if (hasText(code)) {
                result.add(code);
            }
        }
        return result;
    }

    private void appendAutoPreviousMonthHistory(List<SalesStatementHistory> histories, String customerCode, String month) {
        if (histories == null || !hasText(customerCode) || !hasText(month)) {
            return;
        }

        BigDecimal totalUnpaid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        try {
            BigDecimal orderUnpaid = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(unpaid_amount), 0) FROM finance_ar_order_payment_status WHERE is_deleted = 0 AND customer_code = ?",
                    BigDecimal.class,
                    customerCode.trim()
            );
            BigDecimal historyUnpaid = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(unpaid_amount), 0) FROM sales_statement_history WHERE is_deleted = 0 AND customer_code = ?",
                    BigDecimal.class,
                    customerCode.trim()
            );
            if (orderUnpaid != null) {
                totalUnpaid = totalUnpaid.add(orderUnpaid);
            }
            if (historyUnpaid != null) {
                totalUnpaid = totalUnpaid.add(historyUnpaid);
            }
        } catch (Exception ex) {
            // ignore and continue
        }
        if (totalUnpaid.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String previousMonth;
        try {
            previousMonth = YearMonth.parse(month.trim()).minusMonths(1).toString();
        } catch (Exception ex) {
            return;
        }

        boolean exists = histories.stream()
                .anyMatch(h -> h != null && hasText(h.getStatementMonth()) && previousMonth.equals(h.getStatementMonth().trim()));
        if (exists) {
            return;
        }

        Set<String> scopedCustomerCodes = new LinkedHashSet<>();
        scopedCustomerCodes.add(customerCode.trim());
        List<Map<String, Object>> previousRows = getOrBuildOverviewRows(
            buildOverviewCacheKey(previousMonth),
            previousMonth,
            scopedCustomerCodes,
            false
        );
        if (previousRows == null || previousRows.isEmpty()) {
            return;
        }
        Map<String, Object> matched = null;
        for (Map<String, Object> row : previousRows) {
            String code = row == null ? "" : safeTrim(String.valueOf(row.getOrDefault("customerCode", "")));
            if (customerCode.equals(code)) {
                matched = row;
                break;
            }
        }
        if (matched == null) {
            return;
        }

        BigDecimal statementAmount = toDecimal(matched.get("statementAmount")).setScale(2, RoundingMode.HALF_UP);
        if (statementAmount.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        SalesStatementHistory autoHistory = new SalesStatementHistory();
        autoHistory.setId(-1L);
        autoHistory.setCustomerCode(customerCode);
        autoHistory.setStatementMonth(previousMonth);
        autoHistory.setUnpaidAmount(statementAmount);
        autoHistory.setInvoiceAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        autoHistory.setInvoiceDate(null);
        autoHistory.setRemark("系统带出-上月对账金额");
        autoHistory.setCreatedBy("system");
        autoHistory.setUpdatedBy("system");
        autoHistory.setCreatedAt(new Date());
        autoHistory.setUpdatedAt(new Date());
        autoHistory.setIsDeleted(0);
        histories.add(autoHistory);
        histories.sort(Comparator.comparing(SalesStatementHistory::getStatementMonth).reversed());
    }

    private static class OverviewAggregate {
        private BigDecimal deliveryAmount = BigDecimal.ZERO;
        private BigDecimal returnAmount = BigDecimal.ZERO;
        private int currentDeliveryCount = 0;
        private int confirmedDeliveryCount = 0;
    }

    private void addCustomerIdentifier(String customerCode,
                                       String identifier,
                                       Set<String> identifiers,
                                       Map<String, String> identifierToCode) {
        if (!hasText(customerCode) || !hasText(identifier)) {
            return;
        }
        String code = customerCode.trim();
        String key = identifier.trim();
        identifiers.add(key);
        identifierToCode.putIfAbsent(key, code);
    }

    private String resolveOverviewCustomerCode(Object customerKey, Map<String, String> identifierToCode) {
        if (customerKey == null) {
            return "";
        }
        String key = String.valueOf(customerKey).trim();
        if (!hasText(key)) {
            return "";
        }
        String code = identifierToCode.get(key);
        return code == null ? "" : code;
    }

    private boolean isReceivedDeliveryStatus(String status) {
        if (!hasText(status)) {
            return false;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return "已收货".equals(status)
                || "部分收货".equals(status)
                || "RECEIVED".equals(normalized)
                || "PARTIAL_RECEIVED".equals(normalized);
    }

    private void clearOverviewCache() {
        overviewCache.clear();
    }

    private Comparator<Map<String, Object>> buildOverviewComparator(String sortProp, boolean desc) {
        Comparator<Map<String, Object>> comparator;
        switch (sortProp) {
            case "customerName":
                comparator = Comparator.comparing(
                        row -> String.valueOf(row.getOrDefault("customerName", "")),
                        Comparator.nullsLast(String::compareToIgnoreCase)
                );
                break;
            case "statementAmount":
                comparator = Comparator.comparing(row -> toDecimal(row.get("statementAmount")));
                break;
            case "invoiceAmount":
                comparator = Comparator.comparing(row -> toDecimal(row.get("invoiceAmount")));
                break;
            case "receivedAmount":
                comparator = Comparator.comparing(row -> toDecimal(row.get("receivedAmount")));
                break;
            case "pendingDeliveryCount":
                comparator = Comparator.comparingInt(row -> toInt(row.get("pendingDeliveryCount")));
                break;
            case "confirmedDeliveryCount":
                comparator = Comparator.comparingInt(row -> toInt(row.get("confirmedDeliveryCount")));
                break;
            case "currentDeliveryCount":
                comparator = Comparator.comparingInt(row -> toInt(row.get("currentDeliveryCount")));
                break;
            case "reconciliationStatus":
            default:
                comparator = Comparator.comparingInt(row -> "RECONCILED".equals(String.valueOf(row.getOrDefault("reconciliationStatus", "UNRECONCILED"))) ? 1 : 0);
                break;
        }

        Comparator<Map<String, Object>> fallback = Comparator
                .comparingInt((Map<String, Object> row) -> "RECONCILED".equals(String.valueOf(row.getOrDefault("reconciliationStatus", "UNRECONCILED"))) ? 1 : 0)
                .thenComparing((Map<String, Object> row) -> toDecimal(row.get("statementAmount")), Comparator.reverseOrder())
                .thenComparing(row -> String.valueOf(row.getOrDefault("customerName", "")), String::compareToIgnoreCase);

        Comparator<Map<String, Object>> merged = comparator.thenComparing(fallback);
        return desc ? merged.reversed() : merged;
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
                        .gt(SalesStatementHistory::getUnpaidAmount, BigDecimal.ZERO)
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
    public ResponseResult<?> adminClearOverviewCache() {
        try {
            clearOverviewCache();
            return new ResponseResult<>(200, "overview cache cleared", null);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "clear overview cache failed: " + e.getMessage(), null);
        }
    }

    @Override
    public ResponseResult<?> adminDiagnoseDeletedConfirms(String noticeItemIdsCsv) {
        try {
            if (noticeItemIdsCsv == null || noticeItemIdsCsv.trim().isEmpty()) {
                // 返回最近 200 条被标记为已删除的确认记录，供诊断使用
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                        "SELECT notice_item_id, statement_month, is_deleted, updated_by, updated_at FROM sales_statement_delivery_confirm WHERE is_deleted != 0 ORDER BY updated_at DESC LIMIT 200"
                );
                Map<String, Object> data = new HashMap<>();
                data.put("rows", rows);
                return new ResponseResult<>(200, "diagnostic rows", data);
            }

            String[] parts = noticeItemIdsCsv.split("[,;\\s]+");
            List<Long> ids = new ArrayList<>();
            for (String p : parts) {
                try {
                    ids.add(Long.parseLong(p.trim()));
                } catch (Exception ignore) {
                }
            }
            if (ids.isEmpty()) {
                return new ResponseResult<>(400, "no valid noticeItemId provided", null);
            }
            String ph = buildPlaceholders(ids.size());
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT notice_item_id, statement_month, is_deleted, updated_by, updated_at FROM sales_statement_delivery_confirm WHERE notice_item_id IN (" + ph + ") AND is_deleted != 0",
                    ids.toArray()
            );
            Map<String, Object> data = new HashMap<>();
            data.put("rows", rows);
            return new ResponseResult<>(200, "diagnostic rows", data);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "diagnose failed: " + e.getMessage(), null);
        }
    }

    @Override
    public ResponseResult<?> adminRollbackFinanceConfirm(String customerCode, String month) {
        try {
            ensureStatementMonthConfirmTable();
            if (!hasText(customerCode) || !hasText(month)) {
                return new ResponseResult<>(400, "客户和月份不能为空", null);
            }
            if (!month.matches("\\d{4}-\\d{2}")) {
                return new ResponseResult<>(400, "月份格式应为yyyy-MM", null);
            }
            if (!canAccessCustomer(customerCode)) {
                return new ResponseResult<>(403, "无权限操作该客户", null);
            }

            Map<String, Object> info = loadStatementMonthConfirmInfo(customerCode.trim(), month.trim());
            boolean financeConfirmed = hasText(String.valueOf(info.getOrDefault("financeConfirmedAt", "")).trim());
            if (!financeConfirmed) {
                return new ResponseResult<>(400, "该对账单未财务确认，无需回退", null);
            }

            jdbcTemplate.update(
                    "UPDATE sales_statement_month_confirm SET finance_confirmed_by = NULL, finance_confirmed_at = NULL, updated_at = NOW() WHERE customer_code = ? AND statement_month = ?",
                    customerCode.trim(),
                    month.trim()
            );
            clearOverviewCache();
            return new ResponseResult<>(200, "已回退财务确认", null);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "回退财务确认失败: " + e.getMessage(), null);
        }
    }

    @Override
    public ResponseResult<?> saveHistory(SalesStatementHistory history) {
        try {
            ensureHistoryTable();
            ensureStatementMonthConfirmTable();
            if (history == null || !hasText(history.getCustomerCode())) {
                return new ResponseResult<>(400, "客户不能为空", null);
            }
            if (!canAccessCustomer(history.getCustomerCode())) {
                return new ResponseResult<>(403, "无权限操作该客户", null);
            }
            if (!hasText(history.getStatementMonth()) || !history.getStatementMonth().matches("\\d{4}-\\d{2}")) {
                return new ResponseResult<>(400, "对账月份格式应为yyyy-MM", null);
            }
            if (isStatementMonthFinanceConfirmed(history.getCustomerCode(), history.getStatementMonth())) {
                return new ResponseResult<>(400, "对账已财务确认，历史台账不可修改", null);
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
            clearOverviewCache();
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
            ensureStatementMonthConfirmTable();
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
            if (isStatementMonthFinanceConfirmed(existing.getCustomerCode(), existing.getStatementMonth())) {
                return new ResponseResult<>(400, "对账已财务确认，历史台账不可修改", null);
            }
            existing.setIsDeleted(1);
            existing.setUpdatedAt(new Date());
            existing.setUpdatedBy(getCurrentUsername());
            salesStatementHistoryMapper.updateById(existing);
            clearOverviewCache();
            return new ResponseResult<>(200, "删除成功", null);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "删除历史台账失败: " + e.getMessage(), null);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> confirmStatementDetails(SalesReconciliationConfirmRequest request) {
        try {
            ensureDeliveryConfirmTable();
            ensureDeliverySplitTable();
            ensureStatementMonthConfirmTable();
            ensureStatementChangeAuditTable();
            if (request == null || !hasText(request.getCustomerCode()) || !hasText(request.getMonth())) {
                return new ResponseResult<>(400, "客户和月份不能为空", null);
            }
            if (!request.getMonth().matches("\\d{4}-\\d{2}")) {
                return new ResponseResult<>(400, "月份格式应为yyyy-MM", null);
            }
            if (!canAccessCustomer(request.getCustomerCode())) {
                return new ResponseResult<>(403, "无权限操作该客户", null);
            }
            Customer customer = customerMapper.selectByCustomerCode(request.getCustomerCode().trim());
            boolean rpNaturalMonthLocked = isRpCustomer(customer, request.getCustomerCode());
            String operator = getCurrentUsername();
            LoginUser loginUser = getLoginUser();
            boolean financeOperator = loginUser != null && isFinanceUser(loginUser);

            Map<String, Object> monthConfirmInfo = loadStatementMonthConfirmInfo(request.getCustomerCode().trim(), request.getMonth().trim());
            boolean salesAlreadyConfirmed = hasText(String.valueOf(monthConfirmInfo.getOrDefault("salesConfirmedAt", "")).trim());
            boolean financeAlreadyConfirmed = hasText(String.valueOf(monthConfirmInfo.getOrDefault("financeConfirmedAt", "")).trim());
            if (financeAlreadyConfirmed) {
                return new ResponseResult<>(400, "已财务确认，禁止修改对账明细", null);
            }
            if (financeOperator && !salesAlreadyConfirmed) {
                return new ResponseResult<>(400, "请先由销售确认，再进行财务确认", null);
            }

            boolean overrideSalesDetails = Boolean.TRUE.equals(request.getOverrideSalesDetails());
            boolean skipDetailPersistence = financeOperator && salesAlreadyConfirmed && !overrideSalesDetails;

            List<SalesReconciliationConfirmRequest.DeliveryConfirmItem> details = request.getDetails();
            if (details == null || details.isEmpty()) {
                if (skipDetailPersistence) {
                    details = Collections.emptyList();
                } else {
                    return new ResponseResult<>(200, "无变更", null);
                }
            }

                String auditAction = financeOperator
                    ? (skipDetailPersistence ? "FINANCE_CONFIRM_NO_CHANGE" : "FINANCE_CONFIRM_OVERRIDE")
                    : "SALES_CONFIRM";
                List<Map<String, Object>> beforeSnapshot = (financeOperator && overrideSalesDetails)
                    ? loadPersistedAssignmentSnapshot(request.getCustomerCode().trim(), request.getMonth().trim())
                    : Collections.emptyList();

            Set<String> touchedOrders = new LinkedHashSet<>();
            Map<Long, Map<String, SalesReconciliationConfirmRequest.DeliveryConfirmItem>> grouped = new LinkedHashMap<>();

            if (!skipDetailPersistence) {
                for (SalesReconciliationConfirmRequest.DeliveryConfirmItem item : details) {
                    if (item == null || item.getNoticeItemId() == null || !hasText(item.getTargetMonth())) {
                        continue;
                    }
                    if (!item.getTargetMonth().matches("\\d{4}-\\d{2}")) {
                        continue;
                    }
                    if (rpNaturalMonthLocked && !request.getMonth().trim().equals(item.getTargetMonth().trim())) {
                        return new ResponseResult<>(400, "RP客户固定自然月，不能调整到下月", null);
                    }
                    Long noticeItemId = item.getNoticeItemId();
                    String targetMonth = item.getTargetMonth().trim();
                    grouped.computeIfAbsent(noticeItemId, k -> new LinkedHashMap<>());
                    SalesReconciliationConfirmRequest.DeliveryConfirmItem existing = grouped.get(noticeItemId).get(targetMonth);
                    if (existing == null) {
                        grouped.get(noticeItemId).put(targetMonth, item);
                    } else {
                        existing.setSplitQuantity(toDecimal(existing.getSplitQuantity()).add(toDecimal(item.getSplitQuantity())));
                        existing.setSplitArea(toDecimal(existing.getSplitArea()).add(toDecimal(item.getSplitArea())));
                        existing.setSplitAmount(toDecimal(existing.getSplitAmount()).add(toDecimal(item.getSplitAmount())));
                    }
                }

                for (Map.Entry<Long, Map<String, SalesReconciliationConfirmRequest.DeliveryConfirmItem>> entry : grouped.entrySet()) {
                    Long noticeItemId = entry.getKey();
                    Map<String, SalesReconciliationConfirmRequest.DeliveryConfirmItem> monthMap = entry.getValue();
                    if (noticeItemId == null || monthMap == null || monthMap.isEmpty()) {
                        continue;
                    }

                    if (monthMap.size() <= 1) {
                        SalesReconciliationConfirmRequest.DeliveryConfirmItem only = monthMap.values().iterator().next();
                        String targetMonth = only.getTargetMonth().trim();
                        jdbcTemplate.update(
                                "INSERT INTO sales_statement_delivery_confirm (notice_item_id, statement_month, updated_by, updated_at, is_deleted) " +
                                        "VALUES (?, ?, ?, NOW(), 0) " +
                                        "ON DUPLICATE KEY UPDATE statement_month = VALUES(statement_month), updated_by = VALUES(updated_by), updated_at = NOW(), is_deleted = 0",
                                noticeItemId, targetMonth, operator
                        );
                        jdbcTemplate.update(
                                "UPDATE sales_statement_delivery_split SET is_deleted = 1, updated_by = ?, updated_at = NOW() WHERE notice_item_id = ? AND is_deleted = 0",
                                operator,
                                noticeItemId
                        );
                    } else {
                        // 跨月拆分：改用拆分表持久化，确认表标记为删除避免单月覆盖
                        jdbcTemplate.update(
                                "UPDATE sales_statement_delivery_confirm SET is_deleted = 1, updated_by = ?, updated_at = NOW() WHERE notice_item_id = ?",
                                operator,
                                noticeItemId
                        );

                        Set<String> activeMonths = new LinkedHashSet<>();
                        for (SalesReconciliationConfirmRequest.DeliveryConfirmItem item : monthMap.values()) {
                            String targetMonth = item.getTargetMonth().trim();
                            activeMonths.add(targetMonth);
                            jdbcTemplate.update(
                                    "INSERT INTO sales_statement_delivery_split (notice_item_id, statement_month, split_quantity, split_area, split_amount, updated_by, updated_at, is_deleted) " +
                                            "VALUES (?, ?, ?, ?, ?, ?, NOW(), 0) " +
                                            "ON DUPLICATE KEY UPDATE split_quantity = VALUES(split_quantity), split_area = VALUES(split_area), split_amount = VALUES(split_amount), updated_by = VALUES(updated_by), updated_at = NOW(), is_deleted = 0",
                                    noticeItemId,
                                    targetMonth,
                                    toDecimal(item.getSplitQuantity()).setScale(2, RoundingMode.HALF_UP),
                                    toDecimal(item.getSplitArea()).setScale(2, RoundingMode.HALF_UP),
                                    toDecimal(item.getSplitAmount()).setScale(2, RoundingMode.HALF_UP),
                                    operator
                            );
                        }

                        if (!activeMonths.isEmpty()) {
                            String placeholders = buildPlaceholders(activeMonths.size());
                            List<Object> deleteArgs = new ArrayList<>();
                            deleteArgs.add(operator);
                            deleteArgs.add(noticeItemId);
                            deleteArgs.addAll(activeMonths);
                            jdbcTemplate.update(
                                    "UPDATE sales_statement_delivery_split SET is_deleted = 1, updated_by = ?, updated_at = NOW() " +
                                            "WHERE notice_item_id = ? AND is_deleted = 0 AND statement_month NOT IN (" + placeholders + ")",
                                    deleteArgs.toArray()
                            );
                        }
                    }

                    List<Map<String, Object>> orderRows = jdbcTemplate.queryForList(
                            "SELECT dn.order_no AS orderNo FROM delivery_notice_items dni " +
                                    "INNER JOIN delivery_notices dn ON dn.id = dni.notice_id " +
                                    "WHERE dni.id = ? AND dn.is_deleted = 0",
                            noticeItemId
                    );
                    if (!orderRows.isEmpty()) {
                        String orderNo = String.valueOf(orderRows.get(0).get("orderNo"));
                        if (hasText(orderNo)) {
                            touchedOrders.add(orderNo.trim());
                        }
                    }
                }
            } else {
                touchedOrders.addAll(loadTouchedOrdersFromPersistedDetails(request.getCustomerCode().trim(), request.getMonth().trim()));
            }

            if (financeOperator) {
                for (String orderNo : touchedOrders) {
                    Integer currentMonthCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(DISTINCT dni.id) FROM delivery_notice_items dni " +
                            "INNER JOIN delivery_notices dn ON dn.id = dni.notice_id " +
                            "LEFT JOIN sales_statement_delivery_confirm c ON c.notice_item_id = dni.id AND c.is_deleted = 0 " +
                            "LEFT JOIN sales_statement_delivery_split s ON s.notice_item_id = dni.id AND s.is_deleted = 0 " +
                            "WHERE dn.order_no = ? AND dn.is_deleted = 0 AND (c.statement_month = ? OR s.statement_month = ?)",
                        Integer.class,
                        orderNo,
                        request.getMonth().trim(),
                        request.getMonth().trim()
                    );
                    if (currentMonthCount != null && currentMonthCount > 0) {
                        jdbcTemplate.update(
                                "UPDATE sales_orders SET status = '已对账', updated_at = NOW(), updated_by = ? WHERE order_no = ? AND is_deleted = 0",
                                operator,
                                orderNo
                        );
                    } else {
                        jdbcTemplate.update(
                                "UPDATE sales_orders SET status = '已发货', updated_at = NOW(), updated_by = ? " +
                                        "WHERE order_no = ? AND is_deleted = 0 AND status = '已对账'",
                                operator,
                                orderNo
                        );
                    }
                }
            }

            upsertStatementMonthConfirm(request.getCustomerCode().trim(), request.getMonth().trim(), operator, financeOperator);
            if (financeOperator) {
                syncArOrderPaymentFromFinanceConfirmedRows(request.getCustomerCode().trim(), request.getMonth().trim(), operator);
                syncArOrderPaymentFromFinanceConfirmedReturnRows(request.getCustomerCode().trim(), request.getMonth().trim(), operator);
            }

                List<Map<String, Object>> afterSnapshot = (financeOperator && overrideSalesDetails)
                    ? loadPersistedAssignmentSnapshot(request.getCustomerCode().trim(), request.getMonth().trim())
                    : Collections.emptyList();
                saveStatementChangeAudit(
                    request.getCustomerCode().trim(),
                    request.getMonth().trim(),
                    operator,
                    financeOperator ? "FINANCE" : "SALES",
                    auditAction,
                    overrideSalesDetails,
                    details,
                    beforeSnapshot,
                    afterSnapshot
                );

            clearOverviewCache();
            Map<String, Object> result = loadStatementMonthConfirmInfo(request.getCustomerCode().trim(), request.getMonth().trim());
            result.put("confirmStage", financeOperator ? "FINANCE" : "SALES");
            return new ResponseResult<>(200, financeOperator ? "财务确认成功" : "销售确认成功", result);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "确认对账明细失败: " + e.getMessage(), null);
        }
    }

    private Set<String> loadTouchedOrdersFromPersistedDetails(String customerCode, String month) {
        Set<String> result = new LinkedHashSet<>();
        if (!hasText(customerCode) || !hasText(month)) {
            return result;
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
        if (customerKeys.isEmpty()) {
            return result;
        }

        String placeholders = buildPlaceholders(customerKeys.size());
        List<Object> args = new ArrayList<>();
        args.addAll(customerKeys);
        args.add(month.trim());
        args.add(month.trim());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT DISTINCT dn.order_no AS orderNo " +
                        "FROM delivery_notice_items dni " +
                        "INNER JOIN delivery_notices dn ON dn.id = dni.notice_id " +
                        "LEFT JOIN sales_statement_delivery_confirm c ON c.notice_item_id = dni.id AND c.is_deleted = 0 " +
                        "LEFT JOIN sales_statement_delivery_split s ON s.notice_item_id = dni.id AND s.is_deleted = 0 " +
                        "WHERE dn.is_deleted = 0 AND dn.customer IN (" + placeholders + ") " +
                        "AND (c.statement_month = ? OR s.statement_month = ?)",
                args.toArray()
        );
        for (Map<String, Object> row : rows) {
            String orderNo = row.get("orderNo") == null ? "" : String.valueOf(row.get("orderNo")).trim();
            if (hasText(orderNo)) {
                result.add(orderNo);
            }
        }
        return result;
    }

    private void saveStatementChangeAudit(String customerCode,
                                          String month,
                                          String operator,
                                          String operatorRole,
                                          String actionType,
                                          boolean overrideSalesDetails,
                                          List<SalesReconciliationConfirmRequest.DeliveryConfirmItem> details,
                                          List<Map<String, Object>> beforeSnapshot,
                                          List<Map<String, Object>> afterSnapshot) {
        try {
            String submittedPayloadJson = buildConfirmItemsJson(details);
            String beforeSnapshotJson = buildSnapshotJson(beforeSnapshot);
            String afterSnapshotJson = buildSnapshotJson(afterSnapshot);
            int detailCount = details == null ? 0 : details.size();
            jdbcTemplate.update(
                    "INSERT INTO sales_statement_change_audit(" +
                            "customer_code, statement_month, operator, operator_role, action_type, " +
                            "override_sales_details, detail_count, submitted_payload_json, before_snapshot_json, after_snapshot_json, created_at" +
                            ") VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                    customerCode,
                    month,
                    operator,
                    operatorRole,
                    actionType,
                    overrideSalesDetails ? 1 : 0,
                    detailCount,
                    submittedPayloadJson,
                    beforeSnapshotJson,
                    afterSnapshotJson
            );
        } catch (Exception ex) {
            logger.warn("saveStatementChangeAudit failed, customerCode={}, month={}, actionType={}, err={}", customerCode, month, actionType, ex.getMessage());
        }
    }

    private List<Map<String, Object>> loadPersistedAssignmentSnapshot(String customerCode, String month) {
        if (!hasText(customerCode) || !hasText(month)) {
            return Collections.emptyList();
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
        if (customerKeys.isEmpty()) {
            return Collections.emptyList();
        }

        String placeholders = buildPlaceholders(customerKeys.size());
        List<Object> args = new ArrayList<>();
        args.add(month.trim());
        args.addAll(customerKeys);
        args.add(month.trim());
        args.addAll(customerKeys);

        String sql =
                "SELECT c.notice_item_id AS noticeItemId, c.statement_month AS statementMonth, " +
                        "COALESCE(dni.quantity, 0) AS splitQuantity, COALESCE(dni.area_size, 0) AS splitArea, NULL AS splitAmount, " +
                        "'CONFIRM' AS sourceType " +
                        "FROM sales_statement_delivery_confirm c " +
                        "INNER JOIN delivery_notice_items dni ON dni.id = c.notice_item_id " +
                        "INNER JOIN delivery_notices dn ON dn.id = dni.notice_id " +
                        "WHERE c.is_deleted = 0 AND c.statement_month = ? AND dn.is_deleted = 0 AND dn.customer IN (" + placeholders + ") " +
                        "UNION ALL " +
                        "SELECT s.notice_item_id AS noticeItemId, s.statement_month AS statementMonth, " +
                        "COALESCE(s.split_quantity, 0) AS splitQuantity, COALESCE(s.split_area, 0) AS splitArea, COALESCE(s.split_amount, 0) AS splitAmount, " +
                        "'SPLIT' AS sourceType " +
                        "FROM sales_statement_delivery_split s " +
                        "INNER JOIN delivery_notice_items dni ON dni.id = s.notice_item_id " +
                        "INNER JOIN delivery_notices dn ON dn.id = dni.notice_id " +
                        "WHERE s.is_deleted = 0 AND s.statement_month = ? AND dn.is_deleted = 0 AND dn.customer IN (" + placeholders + ") " +
                        "ORDER BY noticeItemId ASC, statementMonth ASC, sourceType ASC";
        return jdbcTemplate.queryForList(sql, args.toArray());
    }

    private String buildConfirmItemsJson(List<SalesReconciliationConfirmRequest.DeliveryConfirmItem> details) {
        if (details == null || details.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for (SalesReconciliationConfirmRequest.DeliveryConfirmItem item : details) {
            if (item == null) {
                continue;
            }
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("{")
                    .append("\"noticeItemId\":").append(item.getNoticeItemId() == null ? "null" : item.getNoticeItemId())
                    .append(",\"targetMonth\":").append(toJsonString(item.getTargetMonth()))
                    .append(",\"splitQuantity\":").append(toDecimal(item.getSplitQuantity()).setScale(2, RoundingMode.HALF_UP).toPlainString())
                    .append(",\"splitArea\":").append(toDecimal(item.getSplitArea()).setScale(2, RoundingMode.HALF_UP).toPlainString())
                    .append(",\"splitAmount\":").append(toDecimal(item.getSplitAmount()).setScale(2, RoundingMode.HALF_UP).toPlainString())
                    .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String buildSnapshotJson(List<Map<String, Object>> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for (Map<String, Object> row : snapshot) {
            if (row == null) {
                continue;
            }
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("{")
                    .append("\"noticeItemId\":").append(getLong(row.get("noticeItemId")) == null ? "null" : getLong(row.get("noticeItemId")))
                    .append(",\"statementMonth\":").append(toJsonString(String.valueOf(row.getOrDefault("statementMonth", ""))))
                    .append(",\"splitQuantity\":").append(toDecimal(row.get("splitQuantity")).setScale(2, RoundingMode.HALF_UP).toPlainString())
                    .append(",\"splitArea\":").append(toDecimal(row.get("splitArea")).setScale(2, RoundingMode.HALF_UP).toPlainString())
                    .append(",\"splitAmount\":").append(toDecimal(row.get("splitAmount")).setScale(2, RoundingMode.HALF_UP).toPlainString())
                    .append(",\"sourceType\":").append(toJsonString(String.valueOf(row.getOrDefault("sourceType", ""))))
                    .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String toJsonString(String value) {
        if (value == null) {
            return "null";
        }
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }

    @Override
    public ResponseResult<?> queryUnreconciledCandidates(String customerCode, String month, String orderNo) {
        try {
            ensureDeliveryConfirmTable();
            ensureDeliverySplitTable();
            ensureStatementMonthConfirmTable();
            if (!hasText(customerCode) || !hasText(month)) {
                return new ResponseResult<>(400, "客户和月份不能为空", null);
            }
            if (!month.matches("\\d{4}-\\d{2}")) {
                return new ResponseResult<>(400, "月份格式应为yyyy-MM", null);
            }
            if (!canAccessCustomer(customerCode)) {
                return new ResponseResult<>(403, "无权限操作该客户", null);
            }
            if (isStatementMonthFinanceConfirmed(customerCode, month)) {
                return new ResponseResult<>(400, "对账已财务确认，不可查询候选明细", null);
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

            int reconciliationDay = resolveEffectiveReconciliationDay(customer, customerCode);
            LocalDate periodEnd = getStatementPeriodEnd(month, reconciliationDay);
            boolean excludeLegacyYearDeliveries = shouldExcludeLegacyYearDeliveries(customerCode, month);
            int targetYear = parseStatementYear(month);
            boolean orderSearch = hasText(orderNo);

            if (orderSearch) {
                ensureOrderSearchCanBeReconciled(orderNo.trim(), periodEnd);
            }

            String placeholders = buildPlaceholders(customerKeys.size());
            String sql = "SELECT dni.id AS noticeItemId, " +
                    "DATE_FORMAT(dn.delivery_date, '%Y-%m-%d') AS bizDate, " +
                    "dn.notice_no AS documentNo, " +
                    "COALESCE(NULLIF(dn.customer_order_no, ''), dn.order_no) AS orderNo, " +
                    "dni.material_code AS materialCode, " +
                    "COALESCE(NULLIF(ts.product_name, ''), CONCAT('产品-', COALESCE(dni.material_code, '未知'))) AS materialName, " +
                    "COALESCE(NULLIF(dni.spec, ''), CONCAT(" +
                    "TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(COALESCE(soi.thickness, 0) AS CHAR))), 'μm*', " +
                    "TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(COALESCE(soi.width, 0) AS CHAR))), 'mm*', " +
                    "TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(COALESCE(soi.length, 0) AS CHAR))), 'm')) AS spec, " +
                    "COALESCE(dni.quantity, 0) AS quantity, " +
                    "COALESCE(dni.area_size, 0) AS areaSize " +
                    "FROM delivery_notice_items dni " +
                    "INNER JOIN delivery_notices dn ON dn.id = dni.notice_id " +
                    "LEFT JOIN sales_order_items soi ON dni.order_item_id = soi.id " +
                    "LEFT JOIN tape_spec ts ON " +
                    "TRIM(CONVERT(ts.material_code USING utf8mb4) COLLATE utf8mb4_unicode_ci) = " +
                    "TRIM(CONVERT(dni.material_code USING utf8mb4) COLLATE utf8mb4_unicode_ci) " +
                    "LEFT JOIN sales_statement_delivery_confirm c ON c.notice_item_id = dni.id AND c.is_deleted = 0 " +
                    "LEFT JOIN sales_statement_delivery_split sp ON sp.notice_item_id = dni.id AND sp.is_deleted = 0 " +
                    "WHERE dn.is_deleted = 0 " +
                    "AND (dn.status IS NULL OR dn.status NOT IN ('cancelled', '已作废')) " +
                        (orderSearch ? "" : "AND dn.customer IN (" + placeholders + ") ") +
                    "AND dn.delivery_date <= ? " +
                        (orderSearch ? "AND (dn.order_no LIKE ? OR dn.customer_order_no LIKE ?) " : "") +
                        (orderSearch
                            ? "AND ((c.notice_item_id IS NULL OR c.statement_month IS NULL OR c.statement_month <> ?) AND sp.notice_item_id IS NULL) "
                            : "AND c.notice_item_id IS NULL AND sp.notice_item_id IS NULL ") +
                    "ORDER BY dn.delivery_date ASC, dn.notice_no ASC, dni.id ASC";
            List<Object> args = new ArrayList<>();
                    if (!orderSearch) {
                    args.addAll(customerKeys);
                    }
            args.add(java.sql.Date.valueOf(periodEnd));
                    if (orderSearch) {
                args.add("%" + orderNo.trim() + "%");
                args.add("%" + orderNo.trim() + "%");
                    args.add(month.trim());
                    }
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());

            // 诊断：检查 appendUnreconciledDetails 查询到的 rows 是否在确认表中存在 is_deleted != 0 的记录
            try {
                List<Long> ids = new ArrayList<>();
                for (Map<String, Object> r : rows) {
                    Long id = getLong(r.get("noticeItemId"));
                    if (id != null) ids.add(id);
                }
                if (!ids.isEmpty()) {
                    String ph = buildPlaceholders(ids.size());
                    List<Map<String, Object>> deleted = jdbcTemplate.queryForList(
                            "SELECT notice_item_id, statement_month, is_deleted FROM sales_statement_delivery_confirm WHERE notice_item_id IN (" + ph + ") AND is_deleted != 0",
                            ids.toArray()
                    );
                    if (deleted != null && !deleted.isEmpty()) {
                        for (Map<String, Object> d : deleted) {
                            logger.warn("appendUnreconciledDetails: found deleted confirm row notice_item_id={} statement_month={} is_deleted={}", d.get("notice_item_id"), d.get("statement_month"), d.get("is_deleted"));
                        }
                    }
                }
            } catch (Exception ex) {
                logger.warn("appendUnreconciledDetails diagnostic failed: {}", ex.getMessage());
            }
                if (orderSearch && (rows == null || rows.isEmpty())) {
                String fallbackSql = "SELECT dni.id AS noticeItemId, " +
                    "DATE_FORMAT(dn.delivery_date, '%Y-%m-%d') AS bizDate, " +
                    "dn.notice_no AS documentNo, " +
                    "COALESCE(NULLIF(dn.customer_order_no, ''), dn.order_no) AS orderNo, " +
                    "dni.material_code AS materialCode, " +
                    "COALESCE(NULLIF(ts.product_name, ''), CONCAT('产品-', COALESCE(dni.material_code, '未知'))) AS materialName, " +
                    "COALESCE(NULLIF(dni.spec, ''), CONCAT(" +
                    "TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(COALESCE(soi.thickness, 0) AS CHAR))), 'μm*', " +
                    "TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(COALESCE(soi.width, 0) AS CHAR))), 'mm*', " +
                    "TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(COALESCE(soi.length, 0) AS CHAR))), 'm')) AS spec, " +
                    "COALESCE(dni.quantity, 0) AS quantity, " +
                    "COALESCE(dni.area_size, 0) AS areaSize " +
                    "FROM delivery_notice_items dni " +
                    "INNER JOIN delivery_notices dn ON dn.id = dni.notice_id " +
                    "LEFT JOIN sales_order_items soi ON dni.order_item_id = soi.id " +
                    "LEFT JOIN tape_spec ts ON " +
                    "TRIM(CONVERT(ts.material_code USING utf8mb4) COLLATE utf8mb4_unicode_ci) = " +
                    "TRIM(CONVERT(dni.material_code USING utf8mb4) COLLATE utf8mb4_unicode_ci) " +
                    "LEFT JOIN sales_statement_delivery_confirm c ON c.notice_item_id = dni.id AND c.is_deleted = 0 " +
                    "LEFT JOIN sales_statement_delivery_split sp ON sp.notice_item_id = dni.id AND sp.is_deleted = 0 " +
                    "WHERE dn.is_deleted = 0 " +
                    "AND (dn.status IS NULL OR dn.status NOT IN ('cancelled', '已作废')) " +
                    "AND dn.delivery_date <= ? " +
                    "AND (dn.order_no LIKE ? OR dn.customer_order_no LIKE ?) " +
                    "AND ((c.notice_item_id IS NULL OR c.statement_month IS NULL OR c.statement_month <> ?) AND sp.notice_item_id IS NULL) " +
                    "ORDER BY dn.delivery_date ASC, dn.notice_no ASC, dni.id ASC";
                rows = jdbcTemplate.queryForList(
                    fallbackSql,
                    java.sql.Date.valueOf(periodEnd),
                    "%" + orderNo.trim() + "%",
                    "%" + orderNo.trim() + "%",
                    month.trim()
                );
                }

            if (excludeLegacyYearDeliveries && rows != null && !rows.isEmpty()) {
                List<Map<String, Object>> filtered = new ArrayList<>();
                for (Map<String, Object> row : rows) {
                    String bizDate = row.get("bizDate") == null ? "" : String.valueOf(row.get("bizDate"));
                    if (isBeforeTargetYear(bizDate, targetYear)) {
                        continue;
                    }
                    filtered.add(row);
                }
                rows = filtered;
            }

            for (Map<String, Object> row : rows) {
                row.put("spec", resolveSpec(row));
                row.put("quantity", toDecimal(row.get("quantity")).setScale(0, RoundingMode.HALF_UP));
                row.put("areaSize", toDecimal(row.get("areaSize")).setScale(2, RoundingMode.HALF_UP));
            }

            Map<String, Object> result = new HashMap<>();
            result.put("periodEnd", periodEnd.toString());
            result.put("reconciliationDay", reconciliationDay);
                if (orderSearch && (rows == null || rows.isEmpty())) {
                Map<String, Object> orderMeta = null;
                List<Map<String, Object>> orderRows = jdbcTemplate.queryForList(
                    "SELECT id, order_no AS orderNo, customer_order_no AS customerOrderNo, customer " +
                        "FROM sales_orders WHERE is_deleted = 0 AND (order_no = ? OR customer_order_no = ?) LIMIT 1",
                    orderNo.trim(),
                    orderNo.trim()
                );
                if (!orderRows.isEmpty()) {
                    orderMeta = orderRows.get(0);
                }
                if (orderMeta != null) {
                    Long salesOrderId = getLong(orderMeta.get("id"));
                    Integer noticeItemCount = salesOrderId == null ? 0 : jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM delivery_notice_items dni " +
                            "INNER JOIN delivery_notices dn ON dn.id = dni.notice_id " +
                            "WHERE dn.is_deleted = 0 AND dn.order_id = ?",
                        Integer.class,
                        salesOrderId
                    );
                    Integer deliveredItemCount = salesOrderId == null ? 0 : jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM sales_order_items WHERE is_deleted = 0 AND order_id = ? AND COALESCE(delivered_qty, 0) > 0",
                        Integer.class,
                        salesOrderId
                    );
                    if ((noticeItemCount == null || noticeItemCount <= 0) && deliveredItemCount != null && deliveredItemCount > 0) {
                    result.put("hint", "该订单存在已发货数量，但没有发货通知明细，暂时无法加入对账。请先补录发货通知后再插入当月对账。");
                    }
                }
                }
            result.put("rows", rows);
            return new ResponseResult<>(200, "查询成功", result);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "查询未对账候选失败: " + e.getMessage(), null);
        }
    }

    private void ensureOrderSearchCanBeReconciled(String orderNo, LocalDate periodEnd) {
        if (!hasText(orderNo)) {
            return;
        }
        QueryWrapper<SalesOrder> orderQw = new QueryWrapper<>();
        orderQw.eq("is_deleted", 0)
                .and(w -> w.eq("order_no", orderNo).or().eq("customer_order_no", orderNo))
                .last("LIMIT 1");
        SalesOrder order = salesOrderMapper.selectOne(orderQw);
        if (order == null || order.getId() == null) {
            return;
        }

        Integer existingNoticeItems = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM delivery_notice_items dni " +
                        "INNER JOIN delivery_notices dn ON dn.id = dni.notice_id " +
                        "WHERE dn.is_deleted = 0 AND dn.order_id = ?",
                Integer.class,
                order.getId()
        );
        if (existingNoticeItems != null && existingNoticeItems > 0) {
            return;
        }

        QueryWrapper<SalesOrderItem> itemQw = new QueryWrapper<>();
        itemQw.eq("order_id", order.getId()).eq("is_deleted", 0);
        List<SalesOrderItem> orderItems = salesOrderItemMapper.selectList(itemQw);
        if (orderItems == null || orderItems.isEmpty()) {
            return;
        }

        DeliveryNotice notice = new DeliveryNotice();
        notice.setNoticeNo(buildReconciliationNoticeNo(periodEnd));
        notice.setOrderId(order.getId());
        notice.setOrderNo(order.getOrderNo());
        notice.setCustomer(order.getCustomer());
        notice.setCustomerOrderNo(order.getCustomerOrderNo());
        notice.setDeliveryDate(order.getDeliveryDate() == null ? periodEnd : order.getDeliveryDate());
        notice.setStatus("已收货");
        notice.setRemark("对账初始化自动补录");
        notice.setCreatedBy(getCurrentUsername());
        notice.setUpdatedBy(getCurrentUsername());
        notice.setCreatedAt(new Date());
        notice.setUpdatedAt(new Date());
        notice.setIsDeleted(0);
        deliveryNoticeMapper.insert(notice);

        int inserted = 0;
        for (SalesOrderItem item : orderItems) {
            if (item == null || item.getId() == null) {
                continue;
            }
            Integer existedByItem = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM delivery_notice_items dni " +
                            "INNER JOIN delivery_notices dn ON dn.id = dni.notice_id " +
                            "WHERE dn.is_deleted = 0 AND dni.order_item_id = ?",
                    Integer.class,
                    item.getId()
            );
            if (existedByItem != null && existedByItem > 0) {
                continue;
            }

            int quantity = item.getDeliveredQty() == null || item.getDeliveredQty() <= 0
                    ? (item.getRolls() == null ? 0 : item.getRolls())
                    : item.getDeliveredQty();
            if (quantity <= 0) {
                continue;
            }

            BigDecimal area = BigDecimal.ZERO;
            if (item.getDeliveredArea() != null && item.getDeliveredArea().compareTo(BigDecimal.ZERO) > 0) {
                area = item.getDeliveredArea();
            } else if (item.getSqm() != null && item.getSqm().compareTo(BigDecimal.ZERO) > 0) {
                if (item.getRolls() != null && item.getRolls() > 0) {
                    area = item.getSqm()
                            .divide(BigDecimal.valueOf(item.getRolls()), 8, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(quantity));
                } else {
                    area = item.getSqm();
                }
            }

            DeliveryNoticeItem noticeItem = new DeliveryNoticeItem();
            noticeItem.setNoticeId(notice.getId());
            noticeItem.setOrderItemId(item.getId());
            noticeItem.setMaterialCode(item.getMaterialCode());
            noticeItem.setSpec(buildReconciliationSpec(item));
            noticeItem.setQuantity(quantity);
            noticeItem.setAreaSize(area.setScale(2, RoundingMode.HALF_UP));
            noticeItem.setRemark("对账初始化自动补录");
            deliveryNoticeItemMapper.insert(noticeItem);
            inserted++;
        }

        if (inserted <= 0) {
            deliveryNoticeMapper.deleteById(notice.getId());
        }
    }

    private String buildReconciliationNoticeNo(LocalDate deliveryDate) {
        LocalDate bizDate = deliveryDate == null
                ? LocalDate.now(ZoneId.of("Asia/Shanghai"))
                : deliveryDate;
        String datePart = bizDate.format(DateTimeFormatter.ofPattern("yyMMdd"));
        String prefix = "Fine" + datePart;

        QueryWrapper<DeliveryNotice> wrapper = new QueryWrapper<>();
        wrapper.likeRight("notice_no", prefix).eq("is_deleted", 0);
        List<DeliveryNotice> exists = deliveryNoticeMapper.selectList(wrapper);

        int maxSeq = 0;
        for (DeliveryNotice one : exists) {
            if (one == null || one.getNoticeNo() == null) {
                continue;
            }
            String no = one.getNoticeNo().trim();
            if (!no.startsWith(prefix)) {
                continue;
            }
            String tail = no.substring(prefix.length());
            if (tail.matches("\\d{3}")) {
                maxSeq = Math.max(maxSeq, Integer.parseInt(tail));
            }
        }
        return prefix + String.format("%03d", maxSeq + 1);
    }

    private String buildReconciliationSpec(SalesOrderItem item) {
        if (item == null) {
            return "";
        }
        String thickness = item.getThickness() == null ? "0" : item.getThickness().stripTrailingZeros().toPlainString();
        String width = item.getWidth() == null ? "0" : item.getWidth().stripTrailingZeros().toPlainString();
        String length = item.getLength() == null ? "0" : item.getLength().stripTrailingZeros().toPlainString();
        return thickness + "μm*" + width + "mm*" + length + "m";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> appendUnreconciledDetails(String customerCode, String month) {
        try {
            ensureDeliveryConfirmTable();
            ensureDeliverySplitTable();
            ensureStatementMonthConfirmTable();
            if (!hasText(customerCode) || !hasText(month)) {
                return new ResponseResult<>(400, "客户和月份不能为空", null);
            }
            if (!month.matches("\\d{4}-\\d{2}")) {
                return new ResponseResult<>(400, "月份格式应为yyyy-MM", null);
            }
            if (!canAccessCustomer(customerCode)) {
                return new ResponseResult<>(403, "无权限操作该客户", null);
            }
            if (isStatementMonthFinanceConfirmed(customerCode, month)) {
                return new ResponseResult<>(400, "对账已财务确认，禁止补充明细", null);
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

            int reconciliationDay = resolveEffectiveReconciliationDay(customer, customerCode);
            LocalDate periodEnd = getStatementPeriodEnd(month, reconciliationDay);
                boolean excludeLegacyYearDeliveries = shouldExcludeLegacyYearDeliveries(customerCode, month);
                int targetYear = parseStatementYear(month);

            String placeholders = buildPlaceholders(customerKeys.size());
            String sql = "SELECT dni.id AS noticeItemId FROM delivery_notice_items dni " +
                    "INNER JOIN delivery_notices dn ON dn.id = dni.notice_id " +
                    "LEFT JOIN sales_statement_delivery_confirm c ON c.notice_item_id = dni.id AND c.is_deleted = 0 " +
                    "LEFT JOIN sales_statement_delivery_split sp ON sp.notice_item_id = dni.id AND sp.is_deleted = 0 " +
                    "WHERE dn.is_deleted = 0 " +
                    "AND (dn.status IS NULL OR dn.status NOT IN ('cancelled', '已作废')) " +
                    "AND dn.customer IN (" + placeholders + ") " +
                    "AND dn.delivery_date <= ? " +
                        (excludeLegacyYearDeliveries ? "AND YEAR(dn.delivery_date) >= ? " : "") +
                    "AND c.notice_item_id IS NULL AND sp.notice_item_id IS NULL";
            List<Object> args = new ArrayList<>();
            args.addAll(customerKeys);
            args.add(java.sql.Date.valueOf(periodEnd));
                    if (excludeLegacyYearDeliveries) {
                    args.add(targetYear);
                    }
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());

            String operator = getCurrentUsername();
            int appendedCount = 0;
            for (Map<String, Object> row : rows) {
                Long noticeItemId = getLong(row.get("noticeItemId"));
                if (noticeItemId == null) {
                    continue;
                }
                jdbcTemplate.update(
                        "INSERT INTO sales_statement_delivery_confirm (notice_item_id, statement_month, updated_by, updated_at, is_deleted) " +
                                "VALUES (?, ?, ?, NOW(), 0) " +
                                "ON DUPLICATE KEY UPDATE statement_month = VALUES(statement_month), updated_by = VALUES(updated_by), updated_at = NOW(), is_deleted = 0",
                        noticeItemId,
                        month.trim(),
                        operator
                );
                appendedCount++;
            }

            Map<String, Object> result = new HashMap<>();
            result.put("appendedCount", appendedCount);
            result.put("periodEnd", periodEnd.toString());
            clearOverviewCache();
            return new ResponseResult<>(200, appendedCount > 0 ? "已补充未对账订单" : "未发现可补充的未对账订单", result);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "补充未对账订单失败: " + e.getMessage(), null);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> removeStatementDetail(String customerCode, String month, Long detailId, String bizType) {
        try {
            ensureDeliveryConfirmTable();
            ensureDeliverySplitTable();
            ensureReturnConfirmTable();
            ensureStatementMonthConfirmTable();
            if (!hasText(customerCode) || !hasText(month) || detailId == null) {
                return new ResponseResult<>(400, "客户、月份和明细ID不能为空", null);
            }
            if (!month.matches("\\d{4}-\\d{2}")) {
                return new ResponseResult<>(400, "月份格式应为yyyy-MM", null);
            }
            if (!canAccessCustomer(customerCode)) {
                return new ResponseResult<>(403, "无权限操作该客户", null);
            }
            if (isStatementMonthFinanceConfirmed(customerCode, month)) {
                return new ResponseResult<>(400, "对账已财务确认，禁止删除对账明细", null);
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
            boolean rpNaturalMonthLocked = isRpCustomer(customer, customerCode);
            if (rpNaturalMonthLocked) {
                return new ResponseResult<>(400, "RP客户固定自然月，不支持顺延到下月", null);
            }
            String nextMonth = YearMonth.parse(month.trim()).plusMonths(1).toString();
                String keyPlaceholders = buildPlaceholders(customerKeys.size());
                String normalizedBizType = hasText(bizType) ? bizType.trim().toLowerCase(Locale.ROOT) : "delivery";

                if ("return".equals(normalizedBizType)) {
                List<Object> existsArgs = new ArrayList<>();
                existsArgs.add(detailId);
                existsArgs.addAll(customerKeys);
                Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM sales_return_items sri " +
                        "INNER JOIN sales_return_orders sro ON sro.id = sri.return_id " +
                        "WHERE sri.id = ? AND sri.is_deleted = 0 AND sro.is_deleted = 0 " +
                        "AND sro.customer IN (" + keyPlaceholders + ")",
                    Integer.class,
                    existsArgs.toArray()
                );
                if (exists == null || exists <= 0) {
                    return new ResponseResult<>(404, "未找到该退货明细或客户不匹配", null);
                }

                jdbcTemplate.update(
                    "INSERT INTO sales_statement_return_confirm (return_item_id, statement_month, updated_by, updated_at, is_deleted) " +
                        "VALUES (?, ?, ?, NOW(), 0) " +
                        "ON DUPLICATE KEY UPDATE statement_month = VALUES(statement_month), updated_by = VALUES(updated_by), updated_at = NOW(), is_deleted = 0",
                    detailId,
                    nextMonth,
                    getCurrentUsername()
                );
                } else {
                List<Object> existsArgs = new ArrayList<>();
                existsArgs.add(detailId);
                existsArgs.addAll(customerKeys);
                Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM delivery_notice_items dni " +
                        "INNER JOIN delivery_notices dn ON dn.id = dni.notice_id " +
                        "WHERE dni.id = ? AND dn.is_deleted = 0 AND dn.customer IN (" + keyPlaceholders + ")",
                    Integer.class,
                    existsArgs.toArray()
                );
                if (exists == null || exists <= 0) {
                    return new ResponseResult<>(404, "未找到该发货明细或客户不匹配", null);
                }

                jdbcTemplate.update(
                    "INSERT INTO sales_statement_delivery_confirm (notice_item_id, statement_month, updated_by, updated_at, is_deleted) " +
                        "VALUES (?, ?, ?, NOW(), 0) " +
                        "ON DUPLICATE KEY UPDATE statement_month = VALUES(statement_month), updated_by = VALUES(updated_by), updated_at = NOW(), is_deleted = 0",
                    detailId,
                    nextMonth,
                    getCurrentUsername()
                );
                jdbcTemplate.update(
                    "UPDATE sales_statement_delivery_split SET is_deleted = 1, updated_by = ?, updated_at = NOW() WHERE notice_item_id = ? AND is_deleted = 0",
                    getCurrentUsername(),
                    detailId
                );
                }

            Map<String, Object> result = new HashMap<>();
            result.put("detailId", detailId);
            result.put("bizType", normalizedBizType);
            result.put("removedFromMonth", month.trim());
            result.put("movedToMonth", nextMonth);
            clearOverviewCache();
            return new ResponseResult<>(200, "已从当前对账月移除，已顺延到下月", result);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "移除对账明细失败: " + e.getMessage(), null);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> migrateLegacyReceiptStatus(String cutoffDate) {
        try {
            LocalDate cutoff = parseCutoffDate(cutoffDate);
            java.sql.Date cutoffSqlDate = java.sql.Date.valueOf(cutoff);

            int fullByStatus = jdbcTemplate.update(
                "UPDATE sales_orders so " +
                    "SET so.status = 'RECEIVED', so.updated_at = NOW() " +
                    "WHERE so.is_deleted = 0 " +
                    "AND so.order_date < ? " +
                    "AND UPPER(IFNULL(so.status, '')) IN ('COMPLETED','SHIPPED_FULL','PAID','CLOSED')",
                cutoffSqlDate
            );

            int partialByStatus = jdbcTemplate.update(
                "UPDATE sales_orders so " +
                    "SET so.status = 'PARTIAL_RECEIVED', so.updated_at = NOW() " +
                    "WHERE so.is_deleted = 0 " +
                    "AND so.order_date < ? " +
                    "AND UPPER(IFNULL(so.status, '')) IN ('PROCESSING','SHIPPED_PARTIAL','IN_PRODUCTION','PRODUCED')",
                cutoffSqlDate
            );

            int byItemCompletion = jdbcTemplate.update(
                "UPDATE sales_orders so " +
                    "INNER JOIN (" +
                    "  SELECT oi.order_id, " +
                    "         COUNT(*) AS total_cnt, " +
                    "         SUM(CASE WHEN UPPER(IFNULL(oi.production_status,'')) IN ('COMPLETED','已完成') THEN 1 ELSE 0 END) AS completed_cnt " +
                    "  FROM sales_order_items oi " +
                    "  WHERE oi.is_deleted = 0 " +
                    "  GROUP BY oi.order_id" +
                    ") x ON x.order_id = so.id " +
                    "SET so.status = CASE " +
                    "  WHEN x.total_cnt > 0 AND x.completed_cnt = x.total_cnt THEN 'RECEIVED' " +
                    "  WHEN x.completed_cnt > 0 THEN 'PARTIAL_RECEIVED' " +
                    "  ELSE so.status " +
                    "END, so.updated_at = NOW() " +
                    "WHERE so.is_deleted = 0 " +
                    "AND so.order_date < ? " +
                    "AND UPPER(IFNULL(so.status, '')) NOT IN ('CANCELLED','CANCELED')",
                cutoffSqlDate
            );

            int noticeStatusUpdated = jdbcTemplate.update(
                "UPDATE delivery_notices dn " +
                    "INNER JOIN sales_orders so ON so.id = dn.order_id AND so.is_deleted = 0 " +
                    "SET dn.status = CASE " +
                    "  WHEN so.status = 'RECEIVED' THEN '已收货' " +
                    "  WHEN so.status = 'PARTIAL_RECEIVED' THEN '部分收货' " +
                    "  ELSE dn.status " +
                    "END, dn.updated_at = NOW() " +
                    "WHERE dn.is_deleted = 0 " +
                    "AND dn.delivery_date < ? " +
                    "AND so.order_date < ?",
                cutoffSqlDate, cutoffSqlDate
            );

            Map<String, Object> data = new HashMap<>();
            data.put("cutoffDate", cutoff.toString());
            data.put("salesOrdersUpdatedByStatus", fullByStatus + partialByStatus);
            data.put("salesOrdersUpdatedByItemCompletion", byItemCompletion);
            data.put("deliveryNoticesUpdated", noticeStatusUpdated);
            return new ResponseResult<>(200, "历史订单生命周期状态迁移完成", data);
        } catch (Exception e) {
            return new ResponseResult<>(500, "迁移失败: " + e.getMessage(), null);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> importHistory(String customerCode, MultipartFile file) {
        try {
            ensureHistoryTable();
            if (file == null || file.isEmpty()) {
                return new ResponseResult<>(400, "导入文件不能为空", null);
            }

            String operator = getCurrentUsername();
            String normalizedCustomerCode = hasText(customerCode) ? customerCode.trim() : "";

            if (isExcelFile(file)) {
                if (hasText(normalizedCustomerCode)) {
                    if (!canAccessCustomer(normalizedCustomerCode)) {
                        return new ResponseResult<>(403, "无权限操作该客户", null);
                    }
                    return importHistoryFromWideExcel(normalizedCustomerCode, file, operator);
                }
                return importHistoryFromWideExcelAll(file, operator);
            }

            if (!hasText(normalizedCustomerCode)) {
                return new ResponseResult<>(400, "CSV导入必须选择客户；Excel支持不选客户直接全表导入", null);
            }
            if (!canAccessCustomer(normalizedCustomerCode)) {
                return new ResponseResult<>(403, "无权限操作该客户", null);
            }

            int successCount = 0;
            int skipCount = 0;
            List<String> errors = new ArrayList<>();

            try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                int lineNo = 0;
                while ((line = reader.readLine()) != null) {
                    lineNo++;
                    String trimmed = lineNo == 1 ? stripBom(line).trim() : line.trim();
                    if (!hasText(trimmed)) {
                        continue;
                    }
                    if (lineNo == 1 && isHistoryCsvHeader(trimmed)) {
                        continue;
                    }

                    String[] parts = splitCsvLine(trimmed);
                    if (parts.length < 3) {
                        skipCount++;
                        errors.add("第" + lineNo + "行格式错误，至少需要3列(月份,欠款金额,开票金额)");
                        continue;
                    }

                    String statementMonth = safeTrim(parts[0]);
                    if (!hasText(statementMonth) || !statementMonth.matches("\\d{4}-\\d{2}")) {
                        skipCount++;
                        errors.add("第" + lineNo + "行月份格式错误，应为yyyy-MM");
                        continue;
                    }

                    BigDecimal unpaidAmount = parseDecimal(parts[1]);
                    BigDecimal invoiceAmount = parseDecimal(parts[2]);
                    LocalDate invoiceDate = parts.length > 3 ? parseDate(parts[3]) : null;
                    String remark = parts.length > 4 ? safeTrim(parts[4]) : "";

                    upsertHistoryByMonth(normalizedCustomerCode, statementMonth, unpaidAmount, invoiceAmount, invoiceDate, remark, operator, true);
                    successCount++;
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("successCount", successCount);
            result.put("skipCount", skipCount);
            result.put("errors", errors);
            clearOverviewCache();
            return new ResponseResult<>(200, "导入完成", result);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "导入历史台账失败: " + e.getMessage(), null);
        }
    }

    private ResponseResult<?> importHistoryFromWideExcel(String customerCode, MultipartFile file, String operator) throws IOException {
        int successCount = 0;
        int skipCount = 0;
        List<String> errors = new ArrayList<>();
        boolean matchedCustomerRow = false;

        DataFormatter formatter = new DataFormatter();
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            if (workbook.getNumberOfSheets() <= 0) {
                return new ResponseResult<>(400, "Excel中未找到工作表", null);
            }
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return new ResponseResult<>(400, "Excel首个工作表为空", null);
            }

            Row yearRow = sheet.getRow(0);
            Row monthRow = sheet.getRow(1);
            Map<Integer, String> monthColumnMap = buildWideMonthColumnMap(yearRow, monthRow, formatter);
            if (monthColumnMap.isEmpty()) {
                return new ResponseResult<>(400, "未识别到月份列，请检查表头（如 2026年 + 3月）", null);
            }

            String targetCode = normalizeCode(customerCode);
            int lastRowNum = sheet.getLastRowNum();
            for (int r = 2; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                String code = normalizeCode(readCell(row, 0, formatter));
                if (!hasText(code)) {
                    continue;
                }
                if (!targetCode.equals(code)) {
                    continue;
                }

                matchedCustomerRow = true;
                for (Map.Entry<Integer, String> entry : monthColumnMap.entrySet()) {
                    int col = entry.getKey();
                    String statementMonth = entry.getValue();
                    String raw = safeTrim(readCell(row, col, formatter));
                    BigDecimal unpaidAmount = parseWideAmount(raw);
                    if (unpaidAmount.compareTo(BigDecimal.ZERO) <= 0) {
                        skipCount++;
                        continue;
                    }
                    upsertHistoryByMonth(
                            customerCode,
                            statementMonth,
                            unpaidAmount,
                            BigDecimal.ZERO,
                            null,
                            "宽表导入",
                            operator,
                            true
                    );
                    successCount++;
                }
                break;
            }
        } catch (Exception ex) {
            throw new IOException(ex.getMessage(), ex);
        }

        if (!matchedCustomerRow) {
            return new ResponseResult<>(400, "在Excel中未找到客户代码: " + customerCode, null);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("skipCount", skipCount);
        result.put("errors", errors);
        result.put("mode", "wide-excel");
        clearOverviewCache();
        return new ResponseResult<>(200, "导入完成", result);
    }

    private ResponseResult<?> importHistoryFromWideExcelAll(MultipartFile file, String operator) throws IOException {
        int successCount = 0;
        int skipCount = 0;
        List<String> errors = new ArrayList<>();
        Set<String> ignoredCodes = new LinkedHashSet<>();

        Map<String, String> allowedCustomerCodeMap = buildImportableCustomerCodeMap();
        if (allowedCustomerCodeMap.isEmpty()) {
            return new ResponseResult<>(403, "当前账号没有可导入的客户", null);
        }

        DataFormatter formatter = new DataFormatter();
        try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
            if (workbook.getNumberOfSheets() <= 0) {
                return new ResponseResult<>(400, "Excel中未找到工作表", null);
            }
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return new ResponseResult<>(400, "Excel首个工作表为空", null);
            }

            Row yearRow = sheet.getRow(0);
            Row monthRow = sheet.getRow(1);
            Map<Integer, String> monthColumnMap = buildWideMonthColumnMap(yearRow, monthRow, formatter);
            if (monthColumnMap.isEmpty()) {
                return new ResponseResult<>(400, "未识别到月份列，请检查表头（如 2026年 + 3月）", null);
            }

            int lastRowNum = sheet.getLastRowNum();
            for (int r = 2; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }

                String rawCode = safeTrim(readCell(row, 0, formatter));
                String normalizedCode = normalizeCode(rawCode);
                if (!hasText(normalizedCode)) {
                    continue;
                }

                String actualCustomerCode = allowedCustomerCodeMap.get(normalizedCode);
                if (!hasText(actualCustomerCode)) {
                    ignoredCodes.add(rawCode);
                    continue;
                }

                for (Map.Entry<Integer, String> entry : monthColumnMap.entrySet()) {
                    int col = entry.getKey();
                    String statementMonth = entry.getValue();
                    BigDecimal unpaidAmount = parseWideAmount(safeTrim(readCell(row, col, formatter)));
                    if (unpaidAmount.compareTo(BigDecimal.ZERO) <= 0) {
                        skipCount++;
                        continue;
                    }
                    upsertHistoryByMonth(
                            actualCustomerCode,
                            statementMonth,
                            unpaidAmount,
                            BigDecimal.ZERO,
                            null,
                            "宽表全量导入",
                            operator,
                            true
                    );
                    successCount++;
                }
            }
        } catch (Exception ex) {
            throw new IOException(ex.getMessage(), ex);
        }

        if (!ignoredCodes.isEmpty()) {
            errors.add("以下客户代码未导入（系统无权限或不存在）: " + String.join(",", ignoredCodes));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("skipCount", skipCount);
        result.put("errors", errors);
        result.put("ignoredCustomerCount", ignoredCodes.size());
        result.put("mode", "wide-excel-all");
        clearOverviewCache();
        return new ResponseResult<>(200, "导入完成", result);
    }

    private Map<String, String> buildImportableCustomerCodeMap() {
        Map<String, String> result = new HashMap<>();
        LoginUser loginUser = getLoginUser();
        if (loginUser == null || hasRole(loginUser, "admin")) {
            List<Customer> customers = customerMapper.selectList(
                    new LambdaQueryWrapper<Customer>()
                            .eq(Customer::getIsDeleted, 0)
                            .select(Customer::getCustomerCode)
            );
            if (customers != null) {
                for (Customer customer : customers) {
                    if (customer == null || !hasText(customer.getCustomerCode())) {
                        continue;
                    }
                    result.put(normalizeCode(customer.getCustomerCode()), customer.getCustomerCode().trim());
                }
            }
            return result;
        }

        Long uid = getCurrentUserId(loginUser);
        if (uid == null) {
            return result;
        }
        List<String> codes = customerMapper.selectCustomerCodesByOwner(uid);
        if (codes != null) {
            for (String code : codes) {
                if (!hasText(code)) {
                    continue;
                }
                result.put(normalizeCode(code), code.trim());
            }
        }
        return result;
    }

    private boolean isExcelFile(MultipartFile file) {
        if (file == null) {
            return false;
        }
        String name = file.getOriginalFilename();
        if (name == null) {
            return false;
        }
        if (!hasText(name)) {
            return false;
        }
        String lower = name.trim().toLowerCase(Locale.ROOT);
        return lower.endsWith(".xlsx") || lower.endsWith(".xls");
    }

    private Map<Integer, String> buildWideMonthColumnMap(Row yearRow, Row monthRow, DataFormatter formatter) {
        Map<Integer, String> result = new LinkedHashMap<>();
        int max = Math.max(
                yearRow == null ? 0 : yearRow.getLastCellNum(),
                monthRow == null ? 0 : monthRow.getLastCellNum()
        );
        String currentYear = "";
        for (int c = 0; c < max; c++) {
            String yearText = safeTrim(readCell(yearRow, c, formatter));
            String monthText = safeTrim(readCell(monthRow, c, formatter));

            String parsedYear = extractYearText(yearText);
            if (hasText(parsedYear)) {
                currentYear = parsedYear;
            }

            String statementMonth = "";
            if (monthText.matches("\\d{4}-\\d{2}")) {
                statementMonth = monthText;
            } else {
                String monthNum = extractMonthText(monthText);
                if (!hasText(monthNum)) {
                    monthNum = extractMonthText(yearText);
                }
                String colYear = currentYear;
                if (!hasText(colYear)) {
                    colYear = extractYearText(monthText);
                }
                if (hasText(colYear) && hasText(monthNum)) {
                    int m = Integer.parseInt(monthNum);
                    if (m >= 1 && m <= 12) {
                        statementMonth = colYear + "-" + String.format("%02d", m);
                    }
                }
            }

            if (hasText(statementMonth)) {
                result.put(c, statementMonth);
            }
        }
        return result;
    }

    private String readCell(Row row, int col, DataFormatter formatter) {
        if (row == null || col < 0) {
            return "";
        }
        Cell cell = row.getCell(col);
        if (cell == null) {
            return "";
        }
        return formatter == null ? String.valueOf(cell) : formatter.formatCellValue(cell);
    }

    private String extractYearText(String text) {
        String t = safeTrim(text);
        if (!hasText(t)) {
            return "";
        }
        String digits = t.replaceAll("[^0-9]", "");
        if (digits.length() >= 4) {
            for (int i = 0; i <= digits.length() - 4; i++) {
                String y = digits.substring(i, i + 4);
                if (y.startsWith("20")) {
                    return y;
                }
            }
        }
        return "";
    }

    private String extractMonthText(String text) {
        String t = safeTrim(text);
        if (!hasText(t)) {
            return "";
        }
        if (t.matches("\\d{1,2}")) {
            return t;
        }
        String digits = t.replaceAll("[^0-9]", "");
        if (!hasText(digits)) {
            return "";
        }
        if (digits.length() <= 2) {
            return digits;
        }
        if (digits.length() >= 6 && digits.startsWith("20")) {
            return digits.substring(4, 6);
        }
        return digits.substring(0, Math.min(2, digits.length()));
    }

    private BigDecimal parseWideAmount(String text) {
        String cleaned = safeTrim(text)
                .replace(",", "")
                .replace("，", "")
                .replace("—", "")
                .replace("-", "")
                .replace("\u00A0", "");
        if (!hasText(cleaned)) {
            return BigDecimal.ZERO;
        }
        return parseDecimal(cleaned);
    }

    private String normalizeCode(String code) {
        if (!hasText(code)) {
            return "";
        }
        return code.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> initializeHistory(SalesStatementHistory history) {
        try {
            ensureHistoryTable();
            ensureStatementMonthConfirmTable();
            if (history == null || !hasText(history.getCustomerCode())) {
                return new ResponseResult<>(400, "客户不能为空", null);
            }
            if (!canAccessCustomer(history.getCustomerCode())) {
                return new ResponseResult<>(403, "无权限操作该客户", null);
            }
            if (!hasText(history.getStatementMonth()) || !history.getStatementMonth().matches("\\d{4}-\\d{2}")) {
                return new ResponseResult<>(400, "对账月份格式应为yyyy-MM", null);
            }
            if (isStatementMonthFinanceConfirmed(history.getCustomerCode(), history.getStatementMonth())) {
                return new ResponseResult<>(400, "对账已财务确认，不可操作", null);
            }

            SalesStatementHistory saved = upsertHistoryByMonth(
                    history.getCustomerCode().trim(),
                    history.getStatementMonth().trim(),
                    defaultDecimal(history.getUnpaidAmount()),
                    defaultDecimal(history.getInvoiceAmount()),
                    history.getInvoiceDate(),
                    history.getRemark(),
                    getCurrentUsername(),
                    false
            );
            clearOverviewCache();
            return new ResponseResult<>(200, "历史初始化成功", saved);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "历史初始化失败: " + e.getMessage(), null);
        }
    }

    @Override
    public void exportStatement(String customerCode, String month, HttpServletResponse response) {
        try {
            ResponseResult<?> statementResult = getStatement(customerCode, month);
            if (statementResult == null || statementResult.getCode() != 200 || !(statementResult.getData() instanceof Map)) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("导出失败");
                return;
            }

            Map<String, Object> data = castObjectMap(statementResult.getData());
            String exportMonth = hasText(month) ? month.trim() : LocalDate.now().toString();
            String fileName = URLEncoder.encode("销售对账单_" + exportMonth + ".csv", "UTF-8");

            response.setCharacterEncoding("UTF-8");
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);

            try (OutputStream os = response.getOutputStream()) {
                os.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
                String csv = buildStatementCsv(data);
                os.write(csv.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        } catch (Exception e) {
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("导出失败: " + e.getMessage());
            } catch (IOException ignore) {
            }
        }
    }

    private List<Map<String, Object>> queryDeliveryRows(List<String> customerKeys, String month, LocalDate periodStart, LocalDate periodEnd, int reconciliationDay, String reconciliationBasis, boolean rpNaturalMonthLocked, boolean excludeLegacyYearDeliveries) {
        if (customerKeys == null || customerKeys.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = buildPlaceholders(customerKeys.size());
        String deliveryStatusFilter = RECON_BASIS_RECEIVED.equals(reconciliationBasis)
            ? "AND UPPER(IFNULL(dn.status, '')) IN ('已收货', 'RECEIVED', '部分收货', 'PARTIAL_RECEIVED') "
            : "";
        String sql = "SELECT DATE_FORMAT(dn.delivery_date, '%Y-%m-%d') AS bizDate, " +
                "'delivery' AS bizType, " +
                "dni.id AS noticeItemId, " +
                "dn.notice_no AS documentNo, " +
                "dn.order_no AS orderNo, " +
                "COALESCE(dn.customer_order_no, so.customer_order_no, '') AS customerOrderNo, " +
                "dni.material_code AS materialCode, " +
                "COALESCE(NULLIF(ts.product_name, ''), CONCAT('产品-', COALESCE(dni.material_code, '未知'))) AS materialName, " +
                "COALESCE(NULLIF(dni.spec, ''), CONCAT(" +
                "TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(COALESCE(soi.thickness, 0) AS CHAR))), 'μm*', " +
                "TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(COALESCE(soi.width, 0) AS CHAR))), 'mm*', " +
                "TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(COALESCE(soi.length, 0) AS CHAR))), 'm')) AS spec, " +
                "soi.thickness AS thickness, " +
                "soi.width AS width, " +
                "soi.length AS length, " +
                "soi.rolls AS orderItemRolls, " +
                "soi.sqm AS orderItemSqm, " +
                "soi.amount AS orderItemAmount, " +
                "COALESCE(dni.quantity, 0) AS quantity, " +
                "COALESCE(dni.area_size, 0) AS areaSize, " +
                "COALESCE(NULLIF(soi.unit, ''), '㎡') AS priceUnit, " +
                "COALESCE(soi.unit_price, 0) AS unitPrice, " +
                "0 AS amount " +
                "FROM delivery_notices dn " +
                "LEFT JOIN delivery_notice_items dni ON dn.id = dni.notice_id " +
                "LEFT JOIN sales_order_items soi ON dni.order_item_id = soi.id " +
            "LEFT JOIN tape_spec ts ON " +
            "TRIM(CONVERT(ts.material_code USING utf8mb4) COLLATE utf8mb4_unicode_ci) = " +
            "TRIM(CONVERT(dni.material_code USING utf8mb4) COLLATE utf8mb4_unicode_ci) " +
                "LEFT JOIN sales_orders so ON so.id = dn.order_id " +
                "WHERE dn.is_deleted = 0 AND (dn.status IS NULL OR dn.status NOT IN ('cancelled', '已作废')) " +
                deliveryStatusFilter +
                "AND dn.customer IN (" + placeholders + ") " +
                                "AND (" +
                                "      (dn.delivery_date BETWEEN ? AND ?) " +
                                (rpNaturalMonthLocked
                                                ? ""
                                                : "      OR dni.id IN (SELECT notice_item_id FROM sales_statement_delivery_confirm WHERE statement_month = ? AND is_deleted = 0) " +
                                                    "      OR dni.id IN (SELECT notice_item_id FROM sales_statement_delivery_split WHERE statement_month = ? AND is_deleted = 0) ") +
                                ") " +
                "ORDER BY dn.delivery_date ASC, dn.notice_no ASC";
        List<Object> args = new ArrayList<>();
        args.addAll(customerKeys);
        args.add(java.sql.Date.valueOf(periodStart));
        args.add(java.sql.Date.valueOf(periodEnd));
        if (!rpNaturalMonthLocked) {
            args.add(month);
            args.add(month);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());

        Map<Long, String> confirmedMonthMap = loadConfirmedStatementMonth(rows);
        Map<Long, List<Map<String, Object>>> splitMap = loadDeliverySplitRows(rows);
        List<Map<String, Object>> normalizedRows = new ArrayList<>();
        int targetYear = parseStatementYear(month);
        for (Map<String, Object> row : rows) {
            String bizDate = row.get("bizDate") == null ? "" : String.valueOf(row.get("bizDate"));
            if (excludeLegacyYearDeliveries && isBeforeTargetYear(bizDate, targetYear)) {
                continue;
            }
            Long noticeItemId = getLong(row.get("noticeItemId"));
            String confirmedMonth = noticeItemId == null ? null : confirmedMonthMap.get(noticeItemId);
            String defaultMonth = resolveDefaultStatementMonth(String.valueOf(row.get("bizDate")), reconciliationDay);
            String statementMonth = rpNaturalMonthLocked
                    ? defaultMonth
                    : (hasText(confirmedMonth) ? confirmedMonth : defaultMonth);

            row.put("spec", resolveSpec(row));
            row.put("quantity", toDecimal(row.get("quantity")).setScale(0, RoundingMode.HALF_UP));
            row.put("areaSize", toDecimal(row.get("areaSize")).setScale(2, RoundingMode.HALF_UP));
            row.put("priceUnit", normalizePricingUnit(row.get("priceUnit")));
            row.put("unitPrice", toDecimal(row.get("unitPrice")).setScale(4, RoundingMode.HALF_UP));
            row.put("amount", calculateDeliveryAmount(row).setScale(2, RoundingMode.HALF_UP));
            row.put("typeLabel", "发货");
                List<Map<String, Object>> splitRows = rpNaturalMonthLocked
                    ? Collections.emptyList()
                    : (noticeItemId == null ? Collections.emptyList() : splitMap.getOrDefault(noticeItemId, Collections.emptyList()));
            if (splitRows == null || splitRows.isEmpty()) {
                row.put("reconcileTargetMonth", statementMonth);
                row.put("defaultReconcileMonth", defaultMonth);
                row.put("isConfirmed", hasText(confirmedMonth));
                row.put("includeInCurrentStatement", month.equals(statementMonth));
                normalizedRows.add(row);
                continue;
            }

            BigDecimal totalQty = toDecimal(row.get("quantity")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalArea = toDecimal(row.get("areaSize")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalAmount = toDecimal(row.get("amount")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal usedQty = BigDecimal.ZERO;
            BigDecimal usedArea = BigDecimal.ZERO;
            BigDecimal usedAmount = BigDecimal.ZERO;

            for (Map<String, Object> split : splitRows) {
                String splitMonth = split.get("statement_month") == null ? "" : String.valueOf(split.get("statement_month")).trim();
                if (!hasText(splitMonth)) {
                    continue;
                }
                BigDecimal splitQty = toDecimal(split.get("split_quantity")).setScale(2, RoundingMode.HALF_UP);
                BigDecimal splitArea = toDecimal(split.get("split_area")).setScale(2, RoundingMode.HALF_UP);
                BigDecimal splitAmount = toDecimal(split.get("split_amount")).setScale(2, RoundingMode.HALF_UP);

                usedQty = usedQty.add(splitQty);
                usedArea = usedArea.add(splitArea);
                usedAmount = usedAmount.add(splitAmount);

                Map<String, Object> cloned = new HashMap<>(row);
                cloned.put("quantity", splitQty.setScale(0, RoundingMode.HALF_UP));
                cloned.put("areaSize", splitArea);
                cloned.put("amount", splitAmount);
                cloned.put("reconcileTargetMonth", splitMonth);
                cloned.put("defaultReconcileMonth", defaultMonth);
                cloned.put("isConfirmed", true);
                cloned.put("includeInCurrentStatement", month.equals(splitMonth));
                normalizedRows.add(cloned);
            }

            BigDecimal remainQty = totalQty.subtract(usedQty);
            BigDecimal remainArea = totalArea.subtract(usedArea);
            BigDecimal remainAmount = totalAmount.subtract(usedAmount);
            if (remainQty.abs().compareTo(new BigDecimal("0.01")) > 0
                    || remainArea.abs().compareTo(new BigDecimal("0.01")) > 0
                    || remainAmount.abs().compareTo(new BigDecimal("0.01")) > 0) {
                Map<String, Object> remain = new HashMap<>(row);
                remain.put("quantity", remainQty.setScale(0, RoundingMode.HALF_UP));
                remain.put("areaSize", remainArea.setScale(2, RoundingMode.HALF_UP));
                remain.put("amount", remainAmount.setScale(2, RoundingMode.HALF_UP));
                remain.put("reconcileTargetMonth", statementMonth);
                remain.put("defaultReconcileMonth", defaultMonth);
                remain.put("isConfirmed", true);
                remain.put("includeInCurrentStatement", month.equals(statementMonth));
                normalizedRows.add(remain);
            }
        }
        return normalizedRows;
    }

    private List<Map<String, Object>> queryReturnRows(List<String> customerKeys, String month, boolean rpNaturalMonthLocked) {
        if (customerKeys == null || customerKeys.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = buildPlaceholders(customerKeys.size());
        String sql = "SELECT DATE_FORMAT(sro.return_date, '%Y-%m-%d') AS bizDate, " +
                "'return' AS bizType, " +
                "sri.id AS returnItemId, " +
                "sro.return_no AS documentNo, " +
                "sri.order_no AS orderNo, " +
                "COALESCE(so.customer_order_no, '') AS customerOrderNo, " +
                "sri.material_code AS materialCode, " +
            "COALESCE(NULLIF(sri.material_name, ''), ts.product_name, CONCAT('产品-', COALESCE(sri.material_code, '未知'))) AS materialName, " +
                "'' AS spec, " +
                "sri.thickness AS thickness, " +
                "sri.width AS width, " +
                "sri.length AS length, " +
                "-COALESCE(sri.rolls, 0) AS quantity, " +
                "-COALESCE(sri.sqm, 0) AS areaSize, " +
                "COALESCE(NULLIF(sri.price_unit, ''), '㎡') AS priceUnit, " +
                "COALESCE(sri.unit_price, 0) AS unitPrice, " +
                "rc.statement_month AS confirmedMonth, " +
                "sro.statement_month AS orderStatementMonth, " +
                "-COALESCE(sri.amount, 0) AS amount " +
                "FROM sales_return_orders sro " +
                "LEFT JOIN sales_return_items sri ON sro.id = sri.return_id " +
                "LEFT JOIN sales_statement_return_confirm rc ON rc.return_item_id = sri.id AND rc.is_deleted = 0 " +
            "LEFT JOIN tape_spec ts ON " +
            "CONVERT(ts.material_code USING utf8mb4) COLLATE utf8mb4_unicode_ci = " +
            "CONVERT(sri.material_code USING utf8mb4) COLLATE utf8mb4_unicode_ci " +
                "LEFT JOIN sales_orders so ON " +
                "CONVERT(so.order_no USING utf8mb4) COLLATE utf8mb4_unicode_ci = " +
                "CONVERT(sri.order_no USING utf8mb4) COLLATE utf8mb4_unicode_ci " +
                "AND so.is_deleted = 0 " +
                "WHERE sro.is_deleted = 0 AND sri.is_deleted = 0 " +
                "AND (sro.statement_month = ? OR rc.statement_month = ?) " +
                "AND sro.customer IN (" + placeholders + ") " +
                "ORDER BY sro.return_date ASC, sro.return_no ASC";
        List<Object> args = new ArrayList<>();
        args.add(month);
        args.add(month);
        args.addAll(customerKeys);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
        List<Map<String, Object>> normalized = new ArrayList<>();
        Set<String> dedupKeys = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            String confirmedMonth = row.get("confirmedMonth") == null ? null : String.valueOf(row.get("confirmedMonth")).trim();
            String orderStatementMonth = row.get("orderStatementMonth") == null ? null : String.valueOf(row.get("orderStatementMonth")).trim();
            String bizDate = row.get("bizDate") == null ? null : String.valueOf(row.get("bizDate")).trim();
            String statementMonth = rpNaturalMonthLocked
                    ? resolveNaturalStatementMonth(bizDate)
                    : (hasText(confirmedMonth) ? confirmedMonth : orderStatementMonth);
            if (!month.equals(statementMonth)) {
                continue;
            }
            row.put("spec", resolveSpec(row));
            row.put("quantity", toDecimal(row.get("quantity")).setScale(0, RoundingMode.HALF_UP));
            row.put("areaSize", toDecimal(row.get("areaSize")).setScale(2, RoundingMode.HALF_UP));
            row.put("priceUnit", normalizePricingUnit(row.get("priceUnit")));
            row.put("unitPrice", toDecimal(row.get("unitPrice")).setScale(4, RoundingMode.HALF_UP));
            row.put("amount", toDecimal(row.get("amount")).setScale(2, RoundingMode.HALF_UP));
            row.put("typeLabel", "退货");
            row.put("reconcileTargetMonth", statementMonth);
            row.put("defaultReconcileMonth", orderStatementMonth);
            row.put("isConfirmed", hasText(confirmedMonth));
            row.put("includeInCurrentStatement", true);

            String dedupKey = String.join("|",
                    String.valueOf(row.get("bizDate") == null ? "" : row.get("bizDate")),
                    String.valueOf(row.get("documentNo") == null ? "" : row.get("documentNo")),
                    String.valueOf(row.get("materialCode") == null ? "" : row.get("materialCode")),
                    String.valueOf(row.get("spec") == null ? "" : row.get("spec")),
                    toDecimal(row.get("quantity")).setScale(0, RoundingMode.HALF_UP).toPlainString(),
                    toDecimal(row.get("areaSize")).setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    toDecimal(row.get("unitPrice")).setScale(4, RoundingMode.HALF_UP).toPlainString(),
                    toDecimal(row.get("amount")).setScale(2, RoundingMode.HALF_UP).toPlainString()
            );
            if (dedupKeys.add(dedupKey)) {
                normalized.add(row);
            }
        }
        return normalized;
    }

    private BigDecimal calculateDeliveryAmount(Map<String, Object> row) {
        if (row == null) {
            return BigDecimal.ZERO;
        }
        String pricingUnit = normalizePricingUnit(row.get("priceUnit"));
        BigDecimal quantity = toDecimal(row.get("quantity"));
        BigDecimal areaSize = toDecimal(row.get("areaSize"));
        BigDecimal length = toDecimal(row.get("length"));
        BigDecimal orderItemRolls = toDecimal(row.get("orderItemRolls"));
        BigDecimal orderItemSqm = toDecimal(row.get("orderItemSqm"));
        BigDecimal orderItemAmount = toDecimal(row.get("orderItemAmount"));

        // 优先采用销售订单行金额口径：按本次发货计价数量占比拆分
        BigDecimal allocatedByOrderAmount = BigDecimal.ZERO;
        if (orderItemAmount.compareTo(BigDecimal.ZERO) != 0) {
            BigDecimal orderChargeQty;
            BigDecimal deliveryChargeQty;
            if ("卷".equals(pricingUnit)) {
                orderChargeQty = orderItemRolls;
                deliveryChargeQty = quantity;
            } else if ("m".equals(pricingUnit)) {
                orderChargeQty = orderItemRolls.multiply(length);
                deliveryChargeQty = quantity.multiply(length);
            } else {
                orderChargeQty = orderItemSqm;
                deliveryChargeQty = areaSize;
            }
            if (orderChargeQty.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal perChargeAmount = orderItemAmount.divide(orderChargeQty, 8, RoundingMode.HALF_UP);
                allocatedByOrderAmount = perChargeAmount.multiply(deliveryChargeQty);
                return allocatedByOrderAmount;
            }
        }

        // 兜底：按单价口径计算
        BigDecimal unitPrice = toDecimal(row.get("unitPrice"));
        if (unitPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal chargeQty;
        if ("卷".equals(pricingUnit)) {
            chargeQty = quantity;
        } else if ("m".equals(pricingUnit)) {
            chargeQty = length.compareTo(BigDecimal.ZERO) > 0 ? quantity.multiply(length) : areaSize;
        } else {
            chargeQty = areaSize;
        }
        return chargeQty.multiply(unitPrice);
    }

    private String normalizePricingUnit(Object unitObj) {
        String unit = unitObj == null ? "" : String.valueOf(unitObj).trim();
        if (!hasText(unit)) {
            return "㎡";
        }
        if ("卷".equals(unit)) {
            return "卷";
        }
        if ("m".equalsIgnoreCase(unit) || "米".equals(unit)) {
            return "m";
        }
        if ("平方米".equals(unit) || "m²".equalsIgnoreCase(unit) || "m2".equalsIgnoreCase(unit) || "㎡".equals(unit)) {
            return "㎡";
        }
        return "㎡";
    }

    private boolean shouldExcludeLegacyYearDeliveries(String customerCode, String month) {
        if (!hasText(customerCode) || !hasText(month)) {
            return false;
        }
        try {
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM sales_statement_history WHERE is_deleted = 0 AND customer_code = ? AND statement_month < ?",
                    Integer.class,
                    customerCode.trim(),
                    month.trim()
            );
            return cnt != null && cnt > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private int parseStatementYear(String month) {
        if (!hasText(month) || month.length() < 4) {
            return LocalDate.now().getYear();
        }
        try {
            return Integer.parseInt(month.substring(0, 4));
        } catch (Exception ex) {
            return LocalDate.now().getYear();
        }
    }

    private boolean isBeforeTargetYear(String bizDate, int targetYear) {
        if (!hasText(bizDate) || bizDate.length() < 4) {
            return false;
        }
        try {
            int year = Integer.parseInt(bizDate.substring(0, 4));
            return year < targetYear;
        } catch (Exception ex) {
            return false;
        }
    }

    private SalesStatementHistory upsertHistoryByMonth(String customerCode,
                                                       String statementMonth,
                                                       BigDecimal unpaidAmount,
                                                       BigDecimal invoiceAmount,
                                                       LocalDate invoiceDate,
                                                       String remark,
                                                       String operator,
                                                       boolean appendRemark) {
        if (isStatementMonthFinanceConfirmed(customerCode, statementMonth)) {
            // 如果已财务确认，静默跳过（或根据需要抛出异常，但在批量导入中跳过较稳妥）
            return null;
        }
        List<SalesStatementHistory> exists = salesStatementHistoryMapper.selectList(
                new LambdaQueryWrapper<SalesStatementHistory>()
                        .eq(SalesStatementHistory::getCustomerCode, customerCode)
                        .eq(SalesStatementHistory::getStatementMonth, statementMonth)
                        .eq(SalesStatementHistory::getIsDeleted, 0)
                        .orderByAsc(SalesStatementHistory::getId)
        );

        Date now = new Date();
        String safeRemark = safeTrim(remark);
        if (!hasText(safeRemark)) {
            safeRemark = appendRemark ? "导入初始化" : "历史初始化";
        }

        SalesStatementHistory target;
        if (exists != null && !exists.isEmpty()) {
            target = exists.get(0);
            target.setUnpaidAmount(defaultDecimal(unpaidAmount));
            target.setInvoiceAmount(defaultDecimal(invoiceAmount));
            target.setInvoiceDate(invoiceDate);
            target.setRemark(safeRemark);
            target.setUpdatedAt(now);
            target.setUpdatedBy(operator);
            target.setIsDeleted(0);
            salesStatementHistoryMapper.updateById(target);

            if (exists.size() > 1) {
                for (int i = 1; i < exists.size(); i++) {
                    SalesStatementHistory duplicate = exists.get(i);
                    duplicate.setIsDeleted(1);
                    duplicate.setUpdatedAt(now);
                    duplicate.setUpdatedBy(operator);
                    salesStatementHistoryMapper.updateById(duplicate);
                }
            }
            return target;
        }

        target = new SalesStatementHistory();
        target.setCustomerCode(customerCode);
        target.setStatementMonth(statementMonth);
        target.setUnpaidAmount(defaultDecimal(unpaidAmount));
        target.setInvoiceAmount(defaultDecimal(invoiceAmount));
        target.setInvoiceDate(invoiceDate);
        target.setRemark(safeRemark);
        target.setCreatedAt(now);
        target.setUpdatedAt(now);
        target.setCreatedBy(operator);
        target.setUpdatedBy(operator);
        target.setIsDeleted(0);
        salesStatementHistoryMapper.insert(target);
        return target;
    }

    private String buildStatementCsv(Map<String, Object> data) {
        StringWriter writer = new StringWriter();
        writer.append("类型,日期,单号,订单号,产品,规格,数量(R),面积(㎡),单价(元/㎡),金额,对账月份\n");

        List<Map<String, Object>> detailRows = castRowList(data.get("detailRows"));
        for (Map<String, Object> row : detailRows) {
            writer.append(csvCell("delivery".equals(String.valueOf(row.get("bizType"))) ? "发货" : "退货")).append(',')
                    .append(csvCell(String.valueOf(row.get("bizDate")))).append(',')
                    .append(csvCell(String.valueOf(row.get("documentNo")))).append(',')
                    .append(csvCell(String.valueOf(row.get("orderNo")))).append(',')
                    .append(csvCell(String.valueOf(row.get("materialName")))).append(',')
                    .append(csvCell(String.valueOf(row.get("spec")))).append(',')
                    .append(csvCell(toDecimal(row.get("quantity")).setScale(0, RoundingMode.HALF_UP).toPlainString())).append(',')
                    .append(csvCell(toDecimal(row.get("areaSize")).setScale(2, RoundingMode.HALF_UP).toPlainString())).append(',')
                    .append(csvCell(toDecimal(row.get("unitPrice")).setScale(4, RoundingMode.HALF_UP).toPlainString())).append(',')
                    .append(csvCell(toDecimal(row.get("amount")).setScale(2, RoundingMode.HALF_UP).toPlainString())).append(',')
                    .append(csvCell(String.valueOf(row.get("reconcileTargetMonth"))))
                    .append("\n");
        }

        writer.append("\n");
        writer.append("历史月份,欠款金额,开票金额,开票日期,备注\n");
        List<SalesStatementHistory> histories = castHistoryList(data.get("historyRows"));
        for (SalesStatementHistory history : histories) {
            writer.append(csvCell(history.getStatementMonth())).append(',')
                    .append(csvCell(defaultDecimal(history.getUnpaidAmount()).toPlainString())).append(',')
                    .append(csvCell(defaultDecimal(history.getInvoiceAmount()).toPlainString())).append(',')
                    .append(csvCell(history.getInvoiceDate() == null ? "" : history.getInvoiceDate().toString())).append(',')
                    .append(csvCell(history.getRemark()))
                    .append("\n");
        }

        writer.append("\n");
        Map<String, Object> summary = castObjectMap(data.get("summary"));
        writer.append("汇总项,值\n");
        writer.append("发货金额,").append(csvCell(toDecimal(summary.get("deliveryAmount")).setScale(2, RoundingMode.HALF_UP).toPlainString())).append("\n");
        writer.append("退货影响,").append(csvCell(toDecimal(summary.get("returnAmount")).setScale(2, RoundingMode.HALF_UP).toPlainString())).append("\n");
        writer.append("本月对账金额,").append(csvCell(toDecimal(summary.get("totalAmount")).setScale(2, RoundingMode.HALF_UP).toPlainString())).append("\n");
        return writer.toString();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castRowList(Object rows) {
        if (!(rows instanceof List)) {
            return Collections.emptyList();
        }
        List<?> raw = (List<?>) rows;
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof Map) {
                result.add((Map<String, Object>) item);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castObjectMap(Object data) {
        if (!(data instanceof Map)) {
            return new HashMap<>();
        }
        return (Map<String, Object>) data;
    }

    private List<SalesStatementHistory> castHistoryList(Object histories) {
        if (!(histories instanceof List)) {
            return Collections.emptyList();
        }
        List<?> raw = (List<?>) histories;
        List<SalesStatementHistory> result = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof SalesStatementHistory) {
                result.add((SalesStatementHistory) item);
            }
        }
        return result;
    }

    private String csvCell(String value) {
        String text = value == null ? "" : value;
        String escaped = text.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private boolean isHistoryCsvHeader(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("statementmonth") || lower.contains("月份") || lower.contains("month");
    }

    private String stripBom(String text) {
        if (text == null) {
            return "";
        }
        if (!text.isEmpty() && text.charAt(0) == '\ufeff') {
            return text.substring(1);
        }
        return text;
    }

    private String[] splitCsvLine(String line) {
        String safeLine = stripBom(line);
        if (!safeLine.contains(",")) {
            return safeLine.split("\\t", -1);
        }
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < safeLine.length(); i++) {
            char c = safeLine.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < safeLine.length() && safeLine.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }
            if (c == ',' && !inQuotes) {
                result.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        result.add(current.toString().trim());
        return result.toArray(new String[0]);
    }

    private String safeTrim(String text) {
        if (text == null || "null".equalsIgnoreCase(text.trim())) {
            return "";
        }
        return text.trim();
    }

    private BigDecimal parseDecimal(String text) {
        String cleaned = safeTrim(text).replace(",", "");
        if (!hasText(cleaned)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private LocalDate parseDate(String text) {
        String value = safeTrim(text);
        if (!hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ignored) {
            return null;
        }
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

    private void ensureDeliveryConfirmTable() {
        if (tableExists("sales_statement_delivery_confirm")) {
            return;
        }
        throw new IllegalStateException("对账模块缺少明细确认表，请先执行版本化脚本: sql/V20260410_01__sales_reconciliation_cycle.sql");
    }

    private void ensureDeliverySplitTable() {
        if (tableExists("sales_statement_delivery_split")) {
            return;
        }
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS sales_statement_delivery_split (" +
                        "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                        "notice_item_id BIGINT NOT NULL," +
                        "statement_month VARCHAR(7) NOT NULL," +
                        "split_quantity DECIMAL(18,2) NOT NULL DEFAULT 0," +
                        "split_area DECIMAL(18,2) NOT NULL DEFAULT 0," +
                        "split_amount DECIMAL(18,2) NOT NULL DEFAULT 0," +
                        "updated_by VARCHAR(64) DEFAULT NULL," +
                        "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                        "is_deleted TINYINT(1) NOT NULL DEFAULT 0," +
                        "UNIQUE KEY uk_notice_month (notice_item_id, statement_month)," +
                        "INDEX idx_notice_item (notice_item_id)," +
                        "INDEX idx_statement_month (statement_month)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
    }

    private void ensureReturnConfirmTable() {
        if (tableExists("sales_statement_return_confirm")) {
            return;
        }
        throw new IllegalStateException("对账模块缺少退货明细确认表，请先执行版本化脚本: sql/V20260428_04__sales_statement_return_confirm.sql");
    }

    private void ensureStatementMonthConfirmTable() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS sales_statement_month_confirm (" +
                        "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                        "customer_code VARCHAR(64) NOT NULL," +
                        "statement_month VARCHAR(7) NOT NULL," +
                        "sales_confirmed_by VARCHAR(64) NULL," +
                        "sales_confirmed_at DATETIME NULL," +
                        "finance_confirmed_by VARCHAR(64) NULL," +
                        "finance_confirmed_at DATETIME NULL," +
                        "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                        "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                        "UNIQUE KEY uk_customer_month (customer_code, statement_month)," +
                        "KEY idx_statement_month (statement_month)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
    }

    private void ensureStatementChangeAuditTable() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS sales_statement_change_audit (" +
                        "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                        "customer_code VARCHAR(64) NOT NULL," +
                        "statement_month VARCHAR(7) NOT NULL," +
                        "operator VARCHAR(64) DEFAULT NULL," +
                        "operator_role VARCHAR(32) DEFAULT NULL," +
                        "action_type VARCHAR(64) NOT NULL," +
                        "override_sales_details TINYINT(1) NOT NULL DEFAULT 0," +
                        "detail_count INT NOT NULL DEFAULT 0," +
                        "submitted_payload_json LONGTEXT NULL," +
                        "before_snapshot_json LONGTEXT NULL," +
                        "after_snapshot_json LONGTEXT NULL," +
                        "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                        "KEY idx_customer_month (customer_code, statement_month)," +
                        "KEY idx_created_at (created_at)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
    }

    private Map<String, Object> loadStatementMonthConfirmInfo(String customerCode, String month) {
        Map<String, Object> result = new HashMap<>();
        result.put("salesConfirmedBy", "");
        result.put("salesConfirmedAt", "");
        result.put("financeConfirmedBy", "");
        result.put("financeConfirmedAt", "");
        result.put("confirmStage", "UNCONFIRMED");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT IFNULL(sales_confirmed_by,'') AS salesConfirmedBy, " +
                        "DATE_FORMAT(sales_confirmed_at, '%Y-%m-%d %H:%i:%s') AS salesConfirmedAt, " +
                        "IFNULL(finance_confirmed_by,'') AS financeConfirmedBy, " +
                        "DATE_FORMAT(finance_confirmed_at, '%Y-%m-%d %H:%i:%s') AS financeConfirmedAt " +
                        "FROM sales_statement_month_confirm WHERE customer_code = ? AND statement_month = ? LIMIT 1",
                customerCode,
                month
        );
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            String salesConfirmedBy = safeTrim(String.valueOf(row.getOrDefault("salesConfirmedBy", "")));
            String salesConfirmedAt = safeTrim(String.valueOf(row.getOrDefault("salesConfirmedAt", "")));
            String financeConfirmedBy = safeTrim(String.valueOf(row.getOrDefault("financeConfirmedBy", "")));
            String financeConfirmedAt = safeTrim(String.valueOf(row.getOrDefault("financeConfirmedAt", "")));
            result.put("salesConfirmedBy", salesConfirmedBy);
            result.put("salesConfirmedAt", salesConfirmedAt);
            result.put("financeConfirmedBy", financeConfirmedBy);
            result.put("financeConfirmedAt", financeConfirmedAt);
            if (hasText(financeConfirmedAt)) {
                result.put("confirmStage", "FINANCE");
            } else if (hasText(salesConfirmedAt)) {
                result.put("confirmStage", "SALES");
            }
        }
        return result;
    }

    private boolean isStatementMonthFinanceConfirmed(String customerCode, String month) {
        if (!hasText(customerCode) || !hasText(month)) {
            return false;
        }
        ensureStatementMonthConfirmTable();
        Map<String, Object> info = loadStatementMonthConfirmInfo(customerCode.trim(), month.trim());
        return hasText(String.valueOf(info.getOrDefault("financeConfirmedAt", "")).trim());
    }

    private void upsertStatementMonthConfirm(String customerCode, String month, String operator, boolean financeOperator) {
        if (financeOperator) {
            jdbcTemplate.update(
                    "INSERT INTO sales_statement_month_confirm(customer_code, statement_month, finance_confirmed_by, finance_confirmed_at, created_at, updated_at) " +
                            "VALUES (?, ?, ?, NOW(), NOW(), NOW()) " +
                            "ON DUPLICATE KEY UPDATE finance_confirmed_by = VALUES(finance_confirmed_by), finance_confirmed_at = NOW(), updated_at = NOW()",
                    customerCode,
                    month,
                    operator
            );
            // 更新确认信息后清理 overview 缓存，确保前端总览能及时反映变更
            clearOverviewCache();
        } else {
            jdbcTemplate.update(
                    "INSERT INTO sales_statement_month_confirm(customer_code, statement_month, sales_confirmed_by, sales_confirmed_at, created_at, updated_at) " +
                            "VALUES (?, ?, ?, NOW(), NOW(), NOW()) " +
                            "ON DUPLICATE KEY UPDATE sales_confirmed_by = VALUES(sales_confirmed_by), sales_confirmed_at = NOW(), updated_at = NOW()",
                    customerCode,
                    month,
                    operator
            );
            // 更新确认信息后清理 overview 缓存，确保前端总览能及时反映变更
            clearOverviewCache();
        }
    }

    private void syncArOrderPaymentFromFinanceConfirmedRows(String customerCode, String month, String operator) {
        if (!tableExists("finance_ar_order_payment_status")) {
            return;
        }

        List<Map<String, Object>> rows = getFinanceConfirmedDeliveryRows(customerCode, month);
        for (Map<String, Object> row : rows) {
            Long noticeItemId = getLong(row.get("noticeItemId"));
            if (noticeItemId == null) {
                continue;
            }
            String orderNo = String.valueOf(row.getOrDefault("orderNo", "")).trim();
            if (!hasText(orderNo)) {
                continue;
            }

            BigDecimal shipmentAmount = toDecimal(row.get("amount")).setScale(2, RoundingMode.HALF_UP);
            if (shipmentAmount.compareTo(BigDecimal.ZERO) < 0) {
                shipmentAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            Long orderItemId = getLong(row.get("orderItemId"));
            LocalDate orderDateLocal = parseDate(String.valueOf(row.getOrDefault("bizDate", "")));
            if (orderDateLocal == null) {
                orderDateLocal = LocalDate.now();
            }
            java.sql.Date orderDate = java.sql.Date.valueOf(orderDateLocal);
            LocalDate shipmentDateLocal = parseDate(String.valueOf(row.getOrDefault("bizDate", "")));
            java.sql.Date shipmentDate = shipmentDateLocal == null ? null : java.sql.Date.valueOf(shipmentDateLocal);

            String token = "SALES_NOTICE_ITEM:" + noticeItemId;
            List<Map<String, Object>> existingRows = jdbcTemplate.queryForList(
                    "SELECT id, IFNULL(paid_amount,0) AS paidAmount FROM finance_ar_order_payment_status " +
                        "WHERE is_deleted = 0 AND sync_token = ? ORDER BY id DESC LIMIT 1",
                    token
            );
            
            // 兼容逻辑：如果没有找到带 token 的记录，尝试“认领”一个相同订单项且没有 token 的旧记录
            if (existingRows.isEmpty() && orderItemId != null) {
                existingRows = jdbcTemplate.queryForList(
                    "SELECT id, IFNULL(paid_amount,0) AS paidAmount FROM finance_ar_order_payment_status " +
                    "WHERE is_deleted = 0 AND order_no = ? AND order_item_id = ? AND sync_token IS NULL " +
                    "ORDER BY id ASC LIMIT 1",
                    orderNo, orderItemId
                );
            }

                // 去重：同一 notice_item_id 对应的自动同步记录只保留最新一条，避免收款明细重复显示
                List<Map<String, Object>> duplicatedRows = jdbcTemplate.queryForList(
                    "SELECT id FROM finance_ar_order_payment_status " +
                        "WHERE is_deleted = 0 AND sync_token = ? ORDER BY id DESC",
                    token
                );
                if (duplicatedRows.size() > 1) {
                List<Object> dedupArgs = new ArrayList<>();
                dedupArgs.add(token);
                Long keepId = getLong(duplicatedRows.get(0).get("id"));
                if (keepId != null) {
                    dedupArgs.add(keepId);
                    jdbcTemplate.update(
                        "UPDATE finance_ar_order_payment_status " +
                            "SET is_deleted = 1, updated_at = NOW() " +
                            "WHERE is_deleted = 0 AND sync_token = ? AND id <> ?",
                        dedupArgs.toArray()
                    );
                }
                }

            BigDecimal paidAmount = existingRows.isEmpty()
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : toDecimal(existingRows.get(0).get("paidAmount")).setScale(2, RoundingMode.HALF_UP);
            if (paidAmount.compareTo(shipmentAmount) > 0) {
                paidAmount = shipmentAmount;
            }
            BigDecimal unpaidAmount = shipmentAmount.subtract(paidAmount).setScale(2, RoundingMode.HALF_UP);
            int isPaid = unpaidAmount.compareTo(BigDecimal.ZERO) <= 0 ? 1 : 0;

            String productName = String.valueOf(row.getOrDefault("materialName", "")).trim();
            String materialCode = String.valueOf(row.getOrDefault("materialCode", "")).trim();
            String spec = String.valueOf(row.getOrDefault("spec", "")).trim();
            BigDecimal rolls = toDecimal(row.get("quantity")).setScale(4, RoundingMode.HALF_UP);
            BigDecimal sqm = toDecimal(row.get("areaSize")).setScale(4, RoundingMode.HALF_UP);
            BigDecimal unitPrice = toDecimal(row.get("unitPrice")).setScale(4, RoundingMode.HALF_UP);

            if (!existingRows.isEmpty()) {
                Long id = getLong(existingRows.get(0).get("id"));
                jdbcTemplate.update(
                    "UPDATE finance_ar_order_payment_status SET customer_code = ?, order_no = ?, order_item_id = ?, " +
                        "order_date = ?, statement_month = ?, order_amount = ?, shipment_amount = ?, paid_amount = ?, unpaid_amount = ?, is_paid = ?, " +
                    "sync_token = ?, import_product_name = ?, import_order_detail = ?, import_order_spec = ?, " +
                        "import_order_rolls = ?, import_order_sqm = ?, import_unit_price = ?, import_shipment_date = ?, created_by = ?, updated_at = NOW() " +
                        "WHERE id = ? AND is_deleted = 0",
                    customerCode,
                    orderNo,
                    orderItemId,
                    orderDate,
                    month,
                    shipmentAmount,
                    shipmentAmount,
                    paidAmount,
                    unpaidAmount,
                    isPaid,
                    token,
                    hasText(productName) ? productName : null,
                    hasText(materialCode) ? materialCode : null,
                    hasText(spec) ? spec : null,
                    rolls,
                    sqm,
                    unitPrice,
                    shipmentDate,
                    operator,
                    id
                );
            } else {
                jdbcTemplate.update(
                        "INSERT INTO finance_ar_order_payment_status(" +
                        "customer_code, order_no, order_item_id, order_date, statement_month, order_amount, shipment_amount, paid_amount, unpaid_amount, is_paid, " +
                    "sync_token, import_product_name, import_order_detail, import_order_spec, import_order_rolls, import_order_sqm, import_unit_price, import_shipment_date, " +
                    "created_by, created_at, updated_at, is_deleted" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), 0)",
                        customerCode,
                        orderNo,
                        orderItemId,
                        orderDate,
                    month,
                        shipmentAmount,
                        shipmentAmount,
                        paidAmount,
                        unpaidAmount,
                        isPaid,
                        token,
                        hasText(productName) ? productName : null,
                        hasText(materialCode) ? materialCode : null,
                        hasText(spec) ? spec : null,
                        rolls,
                        sqm,
                        unitPrice,
                        shipmentDate,
                        operator
                );
            }
        }
    }

    private void syncArOrderPaymentFromFinanceConfirmedReturnRows(String customerCode, String month, String operator) {
        if (!tableExists("finance_ar_order_payment_status")) {
            return;
        }

        List<Map<String, Object>> rows = getFinanceConfirmedReturnRows(customerCode, month);
        for (Map<String, Object> row : rows) {
            Long returnItemId = getLong(row.get("returnItemId"));
            if (returnItemId == null) {
                continue;
            }
            String orderNo = String.valueOf(row.getOrDefault("orderNo", "")).trim();
            BigDecimal amount = toDecimal(row.get("amount")).setScale(2, RoundingMode.HALF_UP);
            
            // 退货金额通常在对账单中是负数，同步到应收明细也应保持负数
            BigDecimal shipmentAmount = amount; 
            
            LocalDate bizDateLocal = parseDate(String.valueOf(row.getOrDefault("bizDate", "")));
            if (bizDateLocal == null) {
                bizDateLocal = LocalDate.now();
            }
            java.sql.Date bizDate = java.sql.Date.valueOf(bizDateLocal);

            String token = "SALES_RETURN_ITEM:" + returnItemId;
            List<Map<String, Object>> existingRows = jdbcTemplate.queryForList(
                    "SELECT id, IFNULL(paid_amount,0) AS paidAmount FROM finance_ar_order_payment_status " +
                        "WHERE is_deleted = 0 AND sync_token = ? ORDER BY id DESC LIMIT 1",
                    token
            );

            // 去重逻辑
            List<Map<String, Object>> duplicatedRows = jdbcTemplate.queryForList(
                "SELECT id FROM finance_ar_order_payment_status " +
                    "WHERE is_deleted = 0 AND sync_token = ? ORDER BY id DESC",
                token
            );
            if (duplicatedRows.size() > 1) {
                Long keepId = getLong(duplicatedRows.get(0).get("id"));
                if (keepId != null) {
                    jdbcTemplate.update(
                        "UPDATE finance_ar_order_payment_status SET is_deleted = 1, updated_at = NOW() " +
                        "WHERE is_deleted = 0 AND sync_token = ? AND id <> ?",
                        token, keepId
                    );
                }
            }

            BigDecimal paidAmount = existingRows.isEmpty()
                    ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : toDecimal(existingRows.get(0).get("paidAmount")).setScale(2, RoundingMode.HALF_UP);
            
            // 对于负数金额，unpaidAmount = settlement - paid
            // 例如：amount = -100, paid = 0 -> unpaid = -100 (欠款减少)
            BigDecimal unpaidAmount = shipmentAmount.subtract(paidAmount).setScale(2, RoundingMode.HALF_UP);
            int isPaid = (shipmentAmount.compareTo(BigDecimal.ZERO) < 0) 
                ? (unpaidAmount.compareTo(BigDecimal.ZERO) >= 0 ? 1 : 0) // 负数金额，如果未付金额 >= 0 则视为已冲抵
                : (unpaidAmount.compareTo(BigDecimal.ZERO) <= 0 ? 1 : 0);

            String productName = String.valueOf(row.getOrDefault("materialName", "")).trim();
            String materialCode = String.valueOf(row.getOrDefault("materialCode", "")).trim();
            BigDecimal rolls = toDecimal(row.get("quantity")).setScale(4, RoundingMode.HALF_UP);
            BigDecimal sqm = toDecimal(row.get("areaSize")).setScale(4, RoundingMode.HALF_UP);
            BigDecimal unitPrice = toDecimal(row.get("unitPrice")).setScale(4, RoundingMode.HALF_UP);

            if (!existingRows.isEmpty()) {
                Long id = getLong(existingRows.get(0).get("id"));
                jdbcTemplate.update(
                    "UPDATE finance_ar_order_payment_status SET customer_code = ?, order_no = ?, " +
                        "order_date = ?, statement_month = ?, order_amount = ?, shipment_amount = ?, paid_amount = ?, unpaid_amount = ?, is_paid = ?, " +
                    "sync_token = ?, import_product_name = ?, import_order_detail = ?, " +
                        "import_order_rolls = ?, import_order_sqm = ?, import_unit_price = ?, import_shipment_date = ?, created_by = ?, updated_at = NOW() " +
                        "WHERE id = ? AND is_deleted = 0",
                    customerCode,
                    orderNo,
                    bizDate,
                    month,
                    shipmentAmount,
                    shipmentAmount,
                    paidAmount,
                    unpaidAmount,
                    isPaid,
                    token,
                    hasText(productName) ? productName : null,
                    hasText(materialCode) ? materialCode : null,
                    rolls,
                    sqm,
                    unitPrice,
                    bizDate,
                    operator,
                    id
                );
            } else {
                jdbcTemplate.update(
                        "INSERT INTO finance_ar_order_payment_status(" +
                        "customer_code, order_no, order_date, statement_month, order_amount, shipment_amount, paid_amount, unpaid_amount, is_paid, " +
                    "sync_token, import_product_name, import_order_detail, import_order_rolls, import_order_sqm, import_unit_price, import_shipment_date, " +
                    "created_by, created_at, updated_at, is_deleted" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), 0)",
                        customerCode,
                        orderNo,
                        bizDate,
                    month,
                        shipmentAmount,
                        shipmentAmount,
                        paidAmount,
                        unpaidAmount,
                        isPaid,
                        token,
                        hasText(productName) ? productName : null,
                        hasText(materialCode) ? materialCode : null,
                        rolls,
                        sqm,
                        unitPrice,
                        bizDate,
                        operator
                );
            }
        }
    }

    private List<Map<String, Object>> getFinanceConfirmedReturnRows(String customerCode, String month) {
        if (!hasText(customerCode) || !hasText(month)) {
            return Collections.emptyList();
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
        
        String placeholders = buildPlaceholders(customerKeys.size());
        String sql = "SELECT sri.id AS returnItemId, sro.return_no AS orderNo, " +
                "DATE_FORMAT(sro.return_date, '%Y-%m-%d') AS bizDate, " +
                "sri.material_code AS materialCode, " +
                "COALESCE(NULLIF(sri.material_name, ''), ts.product_name) AS materialName, " +
                "-COALESCE(sri.rolls, 0) AS quantity, " +
                "-COALESCE(sri.sqm, 0) AS areaSize, " +
                "COALESCE(sri.unit_price, 0) AS unitPrice, " +
                "-COALESCE(sri.amount, 0) AS amount " +
                "FROM sales_return_items sri " +
                "INNER JOIN sales_return_orders sro ON sro.id = sri.return_id " +
                "LEFT JOIN sales_statement_return_confirm rc ON rc.return_item_id = sri.id AND rc.is_deleted = 0 " +
                "LEFT JOIN tape_spec ts ON ts.material_code = sri.material_code " +
                "WHERE sro.is_deleted = 0 AND sri.is_deleted = 0 " +
                "AND sro.customer IN (" + placeholders + ") " +
                "AND (sro.statement_month = ? OR rc.statement_month = ?) " +
                "ORDER BY sro.return_date ASC, sro.return_no ASC";
        
        List<Object> args = new ArrayList<>();
        args.addAll(customerKeys);
        args.add(month);
        args.add(month);
        return jdbcTemplate.queryForList(sql, args.toArray());
    }

    private List<Map<String, Object>> getFinanceConfirmedDeliveryRows(String customerCode, String month) {
        if (!hasText(customerCode) || !hasText(month)) {
            return Collections.emptyList();
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
        if (customerKeys.isEmpty()) {
            return Collections.emptyList();
        }

        String placeholders = buildPlaceholders(customerKeys.size());
        String sql = "SELECT dni.id AS noticeItemId, soi.id AS orderItemId, " +
                "DATE_FORMAT(dn.delivery_date, '%Y-%m-%d') AS bizDate, " +
                "dn.order_no AS orderNo, dni.material_code AS materialCode, " +
                "COALESCE(NULLIF(ts.product_name, ''), CONCAT('产品-', COALESCE(dni.material_code, '未知'))) AS materialName, " +
                "COALESCE(NULLIF(dni.spec, ''), CONCAT(" +
                "TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(COALESCE(soi.thickness, 0) AS CHAR))), 'μm*', " +
                "TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(COALESCE(soi.width, 0) AS CHAR))), 'mm*', " +
                "TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(COALESCE(soi.length, 0) AS CHAR))), 'm')) AS spec, " +
                "COALESCE(split.split_quantity, COALESCE(dni.quantity, 0)) AS quantity, " +
                "COALESCE(split.split_area, COALESCE(dni.area_size, 0)) AS areaSize, " +
                "COALESCE(soi.unit_price, 0) AS unitPrice, " +
                "COALESCE(split.split_amount, COALESCE(dni.area_size * soi.unit_price, 0)) AS amount " +
                "FROM delivery_notice_items dni " +
                "INNER JOIN delivery_notices dn ON dn.id = dni.notice_id " +
                "LEFT JOIN sales_order_items soi ON soi.id = dni.order_item_id " +
                "LEFT JOIN tape_spec ts ON TRIM(CONVERT(ts.material_code USING utf8mb4) COLLATE utf8mb4_unicode_ci) = " +
                "TRIM(CONVERT(dni.material_code USING utf8mb4) COLLATE utf8mb4_unicode_ci) " +
                "LEFT JOIN sales_statement_delivery_split split ON split.notice_item_id = dni.id AND split.statement_month = ? AND split.is_deleted = 0 " +
                "LEFT JOIN sales_statement_delivery_confirm c ON c.notice_item_id = dni.id AND c.is_deleted = 0 " +
                "WHERE dn.is_deleted = 0 AND (dn.status IS NULL OR dn.status NOT IN ('cancelled', '已作废')) " +
                "AND dn.customer IN (" + placeholders + ") " +
                "AND (split.notice_item_id IS NOT NULL OR c.statement_month = ?) " +
                "ORDER BY dn.delivery_date ASC, dn.notice_no ASC, dni.id ASC";
            List<Object> args = new ArrayList<>();
            args.add(month);
            args.addAll(customerKeys);
            args.add(month);
            return jdbcTemplate.queryForList(sql, args.toArray());
    }

    private int normalizeReconciliationDay(Integer day) {
        if (day == null) {
            return 25;
        }
        return Math.max(1, Math.min(31, day));
    }

    private int resolveEffectiveReconciliationDay(Customer customer, String customerCode) {
        if (isRpCustomer(customer, customerCode)) {
            return 31;
        }
        return normalizeReconciliationDay(customer == null ? null : customer.getDefaultReconciliationDay());
    }

    private boolean isRpCustomer(Customer customer, String customerCode) {
        if (customer != null && hasText(customer.getCustomerCode())) {
            return isRpCustomerCode(customer.getCustomerCode());
        }
        return isRpCustomerCode(customerCode);
    }

    private boolean isRpCustomerCode(String customerCode) {
        if (!hasText(customerCode)) {
            return false;
        }
        return customerCode.trim().toUpperCase(Locale.ROOT).contains(RP_CUSTOMER_CODE_KEYWORD);
    }

    private String resolveNaturalStatementMonth(String bizDate) {
        if (!hasText(bizDate)) {
            return "";
        }
        try {
            return YearMonth.from(LocalDate.parse(bizDate.trim())).toString();
        } catch (Exception ex) {
            return "";
        }
    }

    private String normalizeReconciliationBasis(String basis) {
        if (!hasText(basis)) {
            return RECON_BASIS_SHIPPED;
        }
        String normalized = basis.trim().toUpperCase();
        return RECON_BASIS_RECEIVED.equals(normalized) ? RECON_BASIS_RECEIVED : RECON_BASIS_SHIPPED;
    }

    private LocalDate parseCutoffDate(String cutoffDate) {
        if (!hasText(cutoffDate)) {
            return LocalDate.of(2026, 4, 5);
        }
        try {
            return LocalDate.parse(cutoffDate.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException("cutoffDate格式应为yyyy-MM-dd");
        }
    }

    private LocalDate getStatementPeriodStart(String month, int day) {
        YearMonth ym = YearMonth.parse(month);
        YearMonth prev = ym.minusMonths(1);
        int prevLen = prev.lengthOfMonth();
        int cut = Math.min(day, prevLen);
        return prev.atDay(cut).plusDays(1);
    }

    private LocalDate getStatementPeriodEnd(String month, int day) {
        YearMonth ym = YearMonth.parse(month);
        int len = ym.lengthOfMonth();
        int cut = Math.min(day, len);
        return ym.atDay(cut);
    }

    private String resolveDefaultStatementMonth(String bizDate, int reconciliationDay) {
        if (!hasText(bizDate)) {
            return "";
        }
        LocalDate date = LocalDate.parse(bizDate.trim());
        int dayOfMonth = date.getDayOfMonth();
        if (dayOfMonth <= reconciliationDay) {
            return YearMonth.from(date).toString();
        }
        return YearMonth.from(date).plusMonths(1).toString();
    }

    private Map<Long, String> loadConfirmedStatementMonth(List<Map<String, Object>> rows) {
        Map<Long, String> result = new HashMap<>();
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long id = getLong(row.get("noticeItemId"));
            if (id != null) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return result;
        }
        String placeholders = buildPlaceholders(ids.size());
        List<Object> args = new ArrayList<>(ids);
        List<Map<String, Object>> data = jdbcTemplate.queryForList(
                "SELECT notice_item_id, statement_month FROM sales_statement_delivery_confirm WHERE is_deleted = 0 AND notice_item_id IN (" + placeholders + ")",
                args.toArray()
        );
        for (Map<String, Object> row : data) {
            Long id = getLong(row.get("notice_item_id"));
            String statementMonth = row.get("statement_month") == null ? null : String.valueOf(row.get("statement_month"));
            String normalizedMonth = statementMonth == null ? null : statementMonth.trim();
            if (id != null && hasText(normalizedMonth)) {
                result.put(id, normalizedMonth);
            }
        }
        return result;
    }

    private Map<Long, List<Map<String, Object>>> loadDeliverySplitRows(List<Map<String, Object>> rows) {
        Map<Long, List<Map<String, Object>>> result = new HashMap<>();
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long id = getLong(row.get("noticeItemId"));
            if (id != null) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return result;
        }
        String placeholders = buildPlaceholders(ids.size());
        List<Map<String, Object>> data = jdbcTemplate.queryForList(
                "SELECT notice_item_id, statement_month, split_quantity, split_area, split_amount " +
                        "FROM sales_statement_delivery_split WHERE is_deleted = 0 AND notice_item_id IN (" + placeholders + ") " +
                        "ORDER BY statement_month ASC",
                ids.toArray()
        );
        for (Map<String, Object> row : data) {
            Long id = getLong(row.get("notice_item_id"));
            if (id == null) {
                continue;
            }
            result.computeIfAbsent(id, k -> new ArrayList<>());
            result.get(id).add(row);
        }
        return result;
    }

    private Long getLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
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

    public ResponseResult<?> batchSyncConfirmedStatementsToAr() {
        try {
            LoginUser loginUser = getLoginUser();
            if (loginUser == null || !isFinanceUser(loginUser)) {
                return new ResponseResult<>(403, "只有财务人员可以执行同步操作", null);
            }

            List<Map<String, Object>> confirmedStatements = jdbcTemplate.queryForList(
                "SELECT customer_code, statement_month FROM sales_statement_month_confirm " +
                "WHERE finance_confirmed_at IS NOT NULL"
            );

            int count = 0;
            for (Map<String, Object> stmt : confirmedStatements) {
                String customerCode = String.valueOf(stmt.get("customer_code"));
                String month = String.valueOf(stmt.get("statement_month"));
                syncArOrderPaymentFromFinanceConfirmedRows(customerCode, month, loginUser.getUsername());
                syncArOrderPaymentFromFinanceConfirmedReturnRows(customerCode, month, loginUser.getUsername());
                count++;
            }

            return ResponseResult.success("成功同步 " + count + " 份对账单到收款明细");
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "同步失败: " + e.getMessage(), null);
        }
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

    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
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

    @SuppressWarnings("unused")
    private BigDecimal sumInvoiceAmountByMonth(Object historyRowsObj, String month) {
        if (!hasText(month)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal total = BigDecimal.ZERO;
        List<SalesStatementHistory> histories = castHistoryList(historyRowsObj);
        for (SalesStatementHistory history : histories) {
            if (history == null || !hasText(history.getStatementMonth())) {
                continue;
            }
            if (!month.trim().equals(history.getStatementMonth().trim())) {
                continue;
            }
            total = total.add(defaultDecimal(history.getInvoiceAmount()));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private List<Customer> loadAccessibleCustomersForOverview() {
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<Customer>()
                .eq(Customer::getIsDeleted, 0)
                .orderByAsc(Customer::getCustomerCode);
        List<Customer> allCustomers = customerMapper.selectList(wrapper);

        LoginUser loginUser = getLoginUser();
        if (loginUser == null || hasRole(loginUser, "admin") || isFinanceUser(loginUser)) {
            return allCustomers == null ? Collections.emptyList() : allCustomers;
        }

        Long uid = getCurrentUserId(loginUser);
        if (uid == null || allCustomers == null || allCustomers.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> allowedCodes = new LinkedHashSet<>();
        List<String> codeList = customerMapper.selectCustomerCodesByOwner(uid);
        if (codeList != null) {
            for (String code : codeList) {
                if (hasText(code)) {
                    allowedCodes.add(code.trim());
                }
            }
        }

        List<Customer> accessible = new ArrayList<>();
        for (Customer customer : allCustomers) {
            if (customer == null || !hasText(customer.getCustomerCode())) {
                continue;
            }
            if (allowedCodes.contains(customer.getCustomerCode().trim())) {
                accessible.add(customer);
            }
        }
        return accessible;
    }

    private LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return (LoginUser) authentication.getPrincipal();
        }
        return null;
    }

    private boolean isFinanceUser(LoginUser loginUser) {
        return hasRole(loginUser, "finance") || hasRole(loginUser, "财务");
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
        if (loginUser == null || hasRole(loginUser, "admin") || isFinanceUser(loginUser)) {
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
