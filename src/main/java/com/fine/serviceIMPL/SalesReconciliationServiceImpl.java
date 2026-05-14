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
    private static final long OVERVIEW_CACHE_TTL_MS = 60_000L;
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

            List<Map<String, Object>> detailRows = new ArrayList<>();
            detailRows.addAll(queryDeliveryRows(new ArrayList<>(customerKeys), month, periodStart, periodEnd, reconciliationDay, reconciliationBasis, rpNaturalMonthLocked));
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
            data.put("historyRows", histories);
            data.put("printHistoryRows", printHistories);
            data.put("summary", summary);
            data.put("reconciliationStatus", reconciliationStatus);
            data.put("reconciliationStatusLabel", reconciliationStatusLabel);
            data.put("currentDeliveryCount", currentDeliveryCount);
            data.put("confirmedDeliveryCount", currentConfirmedDeliveryCount);
            data.put("pendingDeliveryCount", currentPendingDeliveryCount);
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
            ensureReturnConfirmTable();
            if (!hasText(month) || !month.matches("\\d{4}-\\d{2}")) {
                return new ResponseResult<>(400, "月份格式应为yyyy-MM", null);
            }
            String normalizedCustomerCode = hasText(customerCode) ? customerCode.trim() : "";
            if (hasText(normalizedCustomerCode) && !canAccessCustomer(normalizedCustomerCode)) {
                return new ResponseResult<>(403, "无权限访问该客户", null);
            }

            int safeCurrent = (current == null || current <= 0) ? 1 : current;
            int safeSize = (size == null || size <= 0) ? 20 : Math.min(size, 200);
            String safeSortProp = hasText(sortProp) ? sortProp.trim() : "reconciliationStatus";
            String safeSortOrder = hasText(sortOrder) ? sortOrder.trim().toLowerCase(Locale.ROOT) : "ascending";
            boolean desc = "descending".equals(safeSortOrder) || "desc".equals(safeSortOrder);

            String normalizedMonth = month.trim();
            String cacheKey = buildOverviewCacheKey(normalizedMonth);
            List<Map<String, Object>> rows = getOrBuildOverviewRows(cacheKey, normalizedMonth);
            rows = new ArrayList<>(rows);
            if (hasText(normalizedCustomerCode)) {
                rows = rows.stream()
                        .filter(row -> normalizedCustomerCode.equals(String.valueOf(row.getOrDefault("customerCode", "")).trim()))
                        .collect(Collectors.toList());
            }
            if (hasText(reconciledStatus)) {
                rows = rows.stream()
                        .filter(row -> reconciledStatus.equals(String.valueOf(row.getOrDefault("reconciliationStatus", "")).trim()))
                        .collect(Collectors.toList());
            }

            Comparator<Map<String, Object>> comparator = buildOverviewComparator(safeSortProp, desc);
            rows.sort(comparator);

            int total = rows.size();
            int fromIndex = Math.max(0, (safeCurrent - 1) * safeSize);
            int toIndex = Math.min(total, fromIndex + safeSize);
            List<Map<String, Object>> pageRows = fromIndex >= total
                    ? Collections.emptyList()
                    : new ArrayList<>(rows.subList(fromIndex, toIndex));

            Map<String, Object> data = new HashMap<>();
            data.put("month", month.trim());
            data.put("current", safeCurrent);
            data.put("size", safeSize);
            data.put("total", total);
            data.put("sortProp", safeSortProp);
            data.put("sortOrder", desc ? "descending" : "ascending");
            data.put("customerCode", normalizedCustomerCode);
            data.put("rows", pageRows);
            data.put("records", pageRows);
            return new ResponseResult<>(200, "查询成功", data);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "查询对账总览失败: " + e.getMessage(), null);
        }
    }

    private String buildOverviewCacheKey(String month) {
        return getCurrentUsername() + "|" + month;
    }

    private List<Map<String, Object>> getOrBuildOverviewRows(String cacheKey, String month) {
        long now = System.currentTimeMillis();
        OverviewCacheEntry cached = overviewCache.get(cacheKey);
        if (cached != null && !cached.isExpired(now)) {
            return cached.rows;
        }

        List<Map<String, Object>> rebuilt = buildOverviewRows(month);
        overviewCache.put(cacheKey, new OverviewCacheEntry(rebuilt, now + OVERVIEW_CACHE_TTL_MS));
        return rebuilt;
    }

    private List<Map<String, Object>> buildOverviewRows(String month) {
        List<Customer> customers = loadAccessibleCustomersForOverview();
        if (customers == null || customers.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Customer> customerByCode = new LinkedHashMap<>();
        Map<String, String> identifierToCode = new LinkedHashMap<>();
        Set<String> identifiers = new LinkedHashSet<>();
        Set<String> customerCodes = new LinkedHashSet<>();
        Map<String, Integer> reconciliationDayMap = new HashMap<>();
        Map<String, String> reconciliationBasisMap = new HashMap<>();

        LocalDate globalStart = null;
        LocalDate globalEnd = null;
        for (Customer customer : customers) {
            if (customer == null || !hasText(customer.getCustomerCode())) {
                continue;
            }
            String code = customer.getCustomerCode().trim();
            customerByCode.put(code, customer);
            customerCodes.add(code);

            int reconciliationDay = resolveEffectiveReconciliationDay(customer, code);
            String reconciliationBasis = normalizeReconciliationBasis(customer.getReconciliationBasis());
            reconciliationDayMap.put(code, reconciliationDay);
            reconciliationBasisMap.put(code, reconciliationBasis);

            LocalDate periodStart = getStatementPeriodStart(month, reconciliationDay);
            LocalDate periodEnd = getStatementPeriodEnd(month, reconciliationDay);
            globalStart = globalStart == null || periodStart.isBefore(globalStart) ? periodStart : globalStart;
            globalEnd = globalEnd == null || periodEnd.isAfter(globalEnd) ? periodEnd : globalEnd;

            addCustomerIdentifier(code, code, identifiers, identifierToCode);
            addCustomerIdentifier(code, customer.getCustomerName(), identifiers, identifierToCode);
            addCustomerIdentifier(code, customer.getShortName(), identifiers, identifierToCode);
        }

        if (customerByCode.isEmpty() || identifiers.isEmpty() || globalStart == null || globalEnd == null) {
            return Collections.emptyList();
        }

        Map<String, OverviewAggregate> aggregateMap = new HashMap<>();
        for (String customerCode : customerByCode.keySet()) {
            aggregateMap.put(customerCode, new OverviewAggregate());
        }

        // 1) 批量拉取发货明细并在内存聚合
        String keyPlaceholders = buildPlaceholders(identifiers.size());
        List<Object> deliveryArgs = new ArrayList<>(identifiers);
        deliveryArgs.add(java.sql.Date.valueOf(globalStart));
        deliveryArgs.add(java.sql.Date.valueOf(globalEnd));
        deliveryArgs.add(month);
        String deliverySql = "SELECT dn.customer AS customerKey, " +
                "DATE_FORMAT(dn.delivery_date, '%Y-%m-%d') AS bizDate, " +
                "dni.id AS noticeItemId, " +
                "dn.status AS noticeStatus, " +
                "COALESCE(dni.quantity, 0) AS quantity, " +
                "COALESCE(dni.area_size, 0) AS areaSize, " +
                "COALESCE(NULLIF(soi.unit, ''), '㎡') AS priceUnit, " +
                "COALESCE(soi.unit_price, 0) AS unitPrice, " +
                "soi.length AS length, " +
                "soi.rolls AS orderItemRolls, " +
                "soi.sqm AS orderItemSqm, " +
                "soi.amount AS orderItemAmount, " +
                "c.statement_month AS confirmedMonth " +
                "FROM delivery_notice_items dni " +
                "INNER JOIN delivery_notices dn ON dn.id = dni.notice_id " +
                "LEFT JOIN sales_order_items soi ON dni.order_item_id = soi.id " +
                "LEFT JOIN sales_statement_delivery_confirm c ON c.notice_item_id = dni.id AND c.is_deleted = 0 " +
                "WHERE dn.is_deleted = 0 " +
                "AND (dn.status IS NULL OR dn.status NOT IN ('cancelled', '已作废')) " +
                "AND dn.customer IN (" + keyPlaceholders + ") " +
                "AND ((dn.delivery_date BETWEEN ? AND ?) OR c.statement_month = ?)";
        List<Map<String, Object>> deliveryRows = jdbcTemplate.queryForList(deliverySql, deliveryArgs.toArray());
        // 诊断：检查是否存在对应 notice_item_id 在确认表中但被标记为已删除（is_deleted != 0）
        try {
            List<Long> drIds = new ArrayList<>();
            for (Map<String, Object> r : deliveryRows) {
                Long id = getLong(r.get("noticeItemId"));
                if (id != null) drIds.add(id);
            }
            if (!drIds.isEmpty()) {
                String ph = buildPlaceholders(drIds.size());
                List<Object> qargs = new ArrayList<>(drIds);
                List<Map<String, Object>> bad = jdbcTemplate.queryForList(
                        "SELECT notice_item_id, statement_month, is_deleted FROM sales_statement_delivery_confirm WHERE notice_item_id IN (" + ph + ") AND is_deleted != 0",
                        qargs.toArray()
                );
                if (bad != null && !bad.isEmpty()) {
                    for (Map<String, Object> br : bad) {
                        logger.warn("overview: found deleted confirm row notice_item_id={} statement_month={} is_deleted={}", br.get("notice_item_id"), br.get("statement_month"), br.get("is_deleted"));
                    }
                }
            }
        } catch (Exception ex) {
            logger.warn("overview diagnostic query failed: {}", ex.getMessage());
        }

        for (Map<String, Object> row : deliveryRows) {
            String customerCode = resolveOverviewCustomerCode(row.get("customerKey"), identifierToCode);
            if (!hasText(customerCode)) {
                continue;
            }

            String reconciliationBasis = reconciliationBasisMap.getOrDefault(customerCode, RECON_BASIS_SHIPPED);
            if (RECON_BASIS_RECEIVED.equals(reconciliationBasis)
                    && !isReceivedDeliveryStatus(String.valueOf(row.getOrDefault("noticeStatus", "")))) {
                continue;
            }

            String bizDate = row.get("bizDate") == null ? "" : String.valueOf(row.get("bizDate"));
            if (!hasText(bizDate)) {
                continue;
            }
            int reconciliationDay = reconciliationDayMap.getOrDefault(customerCode, 25);
            String defaultMonth = resolveDefaultStatementMonth(bizDate, reconciliationDay);
            String confirmedMonth = row.get("confirmedMonth") == null ? "" : String.valueOf(row.get("confirmedMonth")).trim();
            boolean rpNaturalMonthLocked = isRpCustomerCode(customerCode);
            String targetMonth = rpNaturalMonthLocked ? defaultMonth : (hasText(confirmedMonth) ? confirmedMonth : defaultMonth);
            if (!month.equals(targetMonth)) {
                continue;
            }

            OverviewAggregate agg = aggregateMap.computeIfAbsent(customerCode, k -> new OverviewAggregate());
            agg.currentDeliveryCount++;
            if (hasText(confirmedMonth)) {
                agg.confirmedDeliveryCount++;
            }
            agg.deliveryAmount = agg.deliveryAmount.add(calculateDeliveryAmount(row));
        }

        // 2) 批量拉取当月退货金额并聚合
        List<Object> returnArgs = new ArrayList<>();
        returnArgs.add(month);
        returnArgs.add(month);
        returnArgs.addAll(identifiers);
        String returnSql = "SELECT sro.customer AS customerKey, -COALESCE(sri.amount, 0) AS amount " +
                "FROM sales_return_orders sro " +
                "INNER JOIN sales_return_items sri ON sro.id = sri.return_id " +
            "LEFT JOIN sales_statement_return_confirm rc ON rc.return_item_id = sri.id AND rc.is_deleted = 0 " +
                "WHERE sro.is_deleted = 0 AND sri.is_deleted = 0 " +
            "AND (sro.statement_month = ? OR rc.statement_month = ?) " +
                "AND sro.customer IN (" + keyPlaceholders + ")";
        List<Map<String, Object>> returnRows = jdbcTemplate.queryForList(returnSql, returnArgs.toArray());
        for (Map<String, Object> row : returnRows) {
            String customerCode = resolveOverviewCustomerCode(row.get("customerKey"), identifierToCode);
            if (!hasText(customerCode)) {
                continue;
            }
            OverviewAggregate agg = aggregateMap.computeIfAbsent(customerCode, k -> new OverviewAggregate());
            agg.returnAmount = agg.returnAmount.add(toDecimal(row.get("amount")));
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

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Customer> entry : customerByCode.entrySet()) {
            String customerCode = entry.getKey();
            Customer customer = entry.getValue();
            OverviewAggregate agg = aggregateMap.getOrDefault(customerCode, new OverviewAggregate());

            BigDecimal statementAmount = agg.deliveryAmount.add(agg.returnAmount).setScale(2, RoundingMode.HALF_UP);
            int pendingDeliveryCount = Math.max(0, agg.currentDeliveryCount - agg.confirmedDeliveryCount);
            String reconciliationStatus = (pendingDeliveryCount == 0) ? "RECONCILED" : "UNRECONCILED";

            Map<String, Object> row = new HashMap<>();
            row.put("customerCode", customerCode);
            row.put("customerName", hasText(customer.getShortName()) ? customer.getShortName() : customer.getCustomerName());
            row.put("month", month);
            row.put("statementAmount", statementAmount);
            row.put("invoiceAmount", invoiceMap.getOrDefault(customerCode, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)));
            row.put("receivedAmount", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            row.put("reconciliationStatus", reconciliationStatus);
            row.put("reconciliationStatusLabel", "RECONCILED".equals(reconciliationStatus) ? "已对账" : "未对账");
            row.put("pendingDeliveryCount", pendingDeliveryCount);
            row.put("confirmedDeliveryCount", agg.confirmedDeliveryCount);
            row.put("currentDeliveryCount", agg.currentDeliveryCount);
            rows.add(row);
        }
        return rows;
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
            List<SalesReconciliationConfirmRequest.DeliveryConfirmItem> details = request.getDetails();
            if (details == null || details.isEmpty()) {
                return new ResponseResult<>(200, "无变更", null);
            }

            String operator = getCurrentUsername();
            Set<String> touchedOrders = new LinkedHashSet<>();
            Map<Long, Map<String, SalesReconciliationConfirmRequest.DeliveryConfirmItem>> grouped = new LinkedHashMap<>();

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

            clearOverviewCache();
            return new ResponseResult<>(200, "确认成功", null);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "确认对账明细失败: " + e.getMessage(), null);
        }
    }

    @Override
    public ResponseResult<?> queryUnreconciledCandidates(String customerCode, String month, String orderNo) {
        try {
            ensureDeliveryConfirmTable();
            ensureDeliverySplitTable();
            if (!hasText(customerCode) || !hasText(month)) {
                return new ResponseResult<>(400, "客户和月份不能为空", null);
            }
            if (!month.matches("\\d{4}-\\d{2}")) {
                return new ResponseResult<>(400, "月份格式应为yyyy-MM", null);
            }
            if (!canAccessCustomer(customerCode)) {
                return new ResponseResult<>(403, "无权限操作该客户", null);
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
                    "COALESCE(ts.product_name, '') AS materialName, " +
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
                    "CONVERT(ts.material_code USING utf8mb4) COLLATE utf8mb4_unicode_ci = " +
                    "CONVERT(dni.material_code USING utf8mb4) COLLATE utf8mb4_unicode_ci " +
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
                    "COALESCE(ts.product_name, '') AS materialName, " +
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
                    "CONVERT(ts.material_code USING utf8mb4) COLLATE utf8mb4_unicode_ci = " +
                    "CONVERT(dni.material_code USING utf8mb4) COLLATE utf8mb4_unicode_ci " +
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
            if (!hasText(customerCode) || !hasText(month)) {
                return new ResponseResult<>(400, "客户和月份不能为空", null);
            }
            if (!month.matches("\\d{4}-\\d{2}")) {
                return new ResponseResult<>(400, "月份格式应为yyyy-MM", null);
            }
            if (!canAccessCustomer(customerCode)) {
                return new ResponseResult<>(403, "无权限操作该客户", null);
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

            String placeholders = buildPlaceholders(customerKeys.size());
            String sql = "SELECT dni.id AS noticeItemId FROM delivery_notice_items dni " +
                    "INNER JOIN delivery_notices dn ON dn.id = dni.notice_id " +
                    "LEFT JOIN sales_statement_delivery_confirm c ON c.notice_item_id = dni.id AND c.is_deleted = 0 " +
                    "LEFT JOIN sales_statement_delivery_split sp ON sp.notice_item_id = dni.id AND sp.is_deleted = 0 " +
                    "WHERE dn.is_deleted = 0 " +
                    "AND (dn.status IS NULL OR dn.status NOT IN ('cancelled', '已作废')) " +
                    "AND dn.customer IN (" + placeholders + ") " +
                    "AND dn.delivery_date <= ? " +
                    "AND c.notice_item_id IS NULL AND sp.notice_item_id IS NULL";
            List<Object> args = new ArrayList<>();
            args.addAll(customerKeys);
            args.add(java.sql.Date.valueOf(periodEnd));
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
            if (!hasText(customerCode) || !hasText(month) || detailId == null) {
                return new ResponseResult<>(400, "客户、月份和明细ID不能为空", null);
            }
            if (!month.matches("\\d{4}-\\d{2}")) {
                return new ResponseResult<>(400, "月份格式应为yyyy-MM", null);
            }
            if (!canAccessCustomer(customerCode)) {
                return new ResponseResult<>(403, "无权限操作该客户", null);
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
            return new ResponseResult<>(500, "删除对账明细失败: " + e.getMessage(), null);
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
            if (history == null || !hasText(history.getCustomerCode())) {
                return new ResponseResult<>(400, "客户不能为空", null);
            }
            if (!canAccessCustomer(history.getCustomerCode())) {
                return new ResponseResult<>(403, "无权限操作该客户", null);
            }
            if (!hasText(history.getStatementMonth()) || !history.getStatementMonth().matches("\\d{4}-\\d{2}")) {
                return new ResponseResult<>(400, "对账月份格式应为yyyy-MM", null);
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

    private List<Map<String, Object>> queryDeliveryRows(List<String> customerKeys, String month, LocalDate periodStart, LocalDate periodEnd, int reconciliationDay, String reconciliationBasis, boolean rpNaturalMonthLocked) {
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
                "COALESCE(ts.product_name, '') AS materialName, " +
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
            "CONVERT(ts.material_code USING utf8mb4) COLLATE utf8mb4_unicode_ci = " +
            "CONVERT(dni.material_code USING utf8mb4) COLLATE utf8mb4_unicode_ci " +
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

        // 诊断：检查 queryDeliveryRows 返回的 rows 中是否有对应的已被标记为已删除的确认记录
        try {
            List<Long> ids = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                Long id = getLong(r.get("noticeItemId"));
                if (id != null) ids.add(id);
            }
            if (!ids.isEmpty()) {
                String ph = buildPlaceholders(ids.size());
                List<Object> qargs = new ArrayList<>(ids);
                List<Map<String, Object>> deleted = jdbcTemplate.queryForList(
                        "SELECT notice_item_id, statement_month, is_deleted FROM sales_statement_delivery_confirm WHERE notice_item_id IN (" + ph + ") AND is_deleted != 0",
                        qargs.toArray()
                );
                if (deleted != null && !deleted.isEmpty()) {
                    for (Map<String, Object> d : deleted) {
                        logger.warn("queryDeliveryRows: found deleted confirm row notice_item_id={} statement_month={} is_deleted={}", d.get("notice_item_id"), d.get("statement_month"), d.get("is_deleted"));
                    }
                }
            }
        } catch (Exception ex) {
            logger.warn("queryDeliveryRows diagnostic failed: {}", ex.getMessage());
        }

        Map<Long, String> confirmedMonthMap = loadConfirmedStatementMonth(rows);
        Map<Long, List<Map<String, Object>>> splitMap = loadDeliverySplitRows(rows);
        List<Map<String, Object>> normalizedRows = new ArrayList<>();
        for (Map<String, Object> row : rows) {
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

    private SalesStatementHistory upsertHistoryByMonth(String customerCode,
                                                       String statementMonth,
                                                       BigDecimal unpaidAmount,
                                                       BigDecimal invoiceAmount,
                                                       LocalDate invoiceDate,
                                                       String remark,
                                                       String operator,
                                                       boolean appendRemark) {
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
        return text == null ? "" : text.trim();
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
        // 额外检查：如果存在同样的 notice_item_id 但被标记为已删除（is_deleted != 0），记录以便排查
        try {
            List<Map<String, Object>> deletedData = jdbcTemplate.queryForList(
                    "SELECT notice_item_id, statement_month, is_deleted FROM sales_statement_delivery_confirm WHERE notice_item_id IN (" + placeholders + ") AND is_deleted != 0",
                    args.toArray()
            );
            if (deletedData != null && !deletedData.isEmpty()) {
                for (Map<String, Object> drow : deletedData) {
                    Long nid = getLong(drow.get("notice_item_id"));
                    String sm = drow.get("statement_month") == null ? "" : String.valueOf(drow.get("statement_month"));
                    Object isdelObj = drow.get("is_deleted");
                    String isdel = isdelObj == null ? "null" : String.valueOf(isdelObj);
                    logger.warn("sales_statement_delivery_confirm has is_deleted!=0 for notice_item_id={} statement_month={} is_deleted={}", nid, sm, isdel);
                }
            }
        } catch (Exception ex) {
            logger.warn("failed to query deleted sales_statement_delivery_confirm rows for diagnostic: {}", ex.getMessage());
        }
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
        if (loginUser == null || hasRole(loginUser, "admin")) {
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
