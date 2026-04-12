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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
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

import com.fine.modle.SalesReconciliationConfirmRequest;

@Service
public class SalesReconciliationServiceImpl implements SalesReconciliationService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private SalesStatementHistoryMapper salesStatementHistoryMapper;

    private volatile boolean historyTableChecked = false;
    private static final String RECON_BASIS_SHIPPED = "SHIPPED";
    private static final String RECON_BASIS_RECEIVED = "RECEIVED";

    @Override
    public ResponseResult<?> getStatement(String customerCode, String month) {
        try {
            ensureHistoryTable();
            ensureDeliveryConfirmTable();
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

            int reconciliationDay = normalizeReconciliationDay(customer == null ? null : customer.getDefaultReconciliationDay());
            String reconciliationBasis = normalizeReconciliationBasis(customer == null ? null : customer.getReconciliationBasis());
            LocalDate periodStart = getStatementPeriodStart(month, reconciliationDay);
            LocalDate periodEnd = getStatementPeriodEnd(month, reconciliationDay);

            List<Map<String, Object>> detailRows = new ArrayList<>();
            detailRows.addAll(queryDeliveryRows(new ArrayList<>(customerKeys), month, periodStart, periodEnd, reconciliationDay, reconciliationBasis));
            detailRows.addAll(queryReturnRows(new ArrayList<>(customerKeys), month));
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

            Map<String, Object> data = new HashMap<>();
            data.put("customerCode", customerCode.trim());
            data.put("customerName", customer != null && hasText(customer.getCustomerName()) ? customer.getCustomerName() : customerCode.trim());
            data.put("customerShortName", customer != null ? customer.getShortName() : "");
            data.put("month", month);
            data.put("defaultReconciliationDay", reconciliationDay);
            data.put("reconciliationBasis", reconciliationBasis);
            data.put("periodStart", periodStart.toString());
            data.put("periodEnd", periodEnd.toString());
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> confirmStatementDetails(SalesReconciliationConfirmRequest request) {
        try {
            ensureDeliveryConfirmTable();
            if (request == null || !hasText(request.getCustomerCode()) || !hasText(request.getMonth())) {
                return new ResponseResult<>(400, "客户和月份不能为空", null);
            }
            if (!request.getMonth().matches("\\d{4}-\\d{2}")) {
                return new ResponseResult<>(400, "月份格式应为yyyy-MM", null);
            }
            if (!canAccessCustomer(request.getCustomerCode())) {
                return new ResponseResult<>(403, "无权限操作该客户", null);
            }
            List<SalesReconciliationConfirmRequest.DeliveryConfirmItem> details = request.getDetails();
            if (details == null || details.isEmpty()) {
                return new ResponseResult<>(200, "无变更", null);
            }

            String operator = getCurrentUsername();
            Set<String> touchedOrders = new LinkedHashSet<>();

            for (SalesReconciliationConfirmRequest.DeliveryConfirmItem item : details) {
                if (item == null || item.getNoticeItemId() == null || !hasText(item.getTargetMonth())) {
                    continue;
                }
                if (!item.getTargetMonth().matches("\\d{4}-\\d{2}")) {
                    continue;
                }

                Long noticeItemId = item.getNoticeItemId();
                String targetMonth = item.getTargetMonth().trim();

                jdbcTemplate.update(
                        "INSERT INTO sales_statement_delivery_confirm (notice_item_id, statement_month, updated_by, updated_at, is_deleted) " +
                                "VALUES (?, ?, ?, NOW(), 0) " +
                                "ON DUPLICATE KEY UPDATE statement_month = VALUES(statement_month), updated_by = VALUES(updated_by), updated_at = NOW(), is_deleted = 0",
                        noticeItemId, targetMonth, operator
                );

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
                        "SELECT COUNT(1) FROM delivery_notice_items dni " +
                                "INNER JOIN delivery_notices dn ON dn.id = dni.notice_id " +
                                "INNER JOIN sales_statement_delivery_confirm c ON c.notice_item_id = dni.id AND c.is_deleted = 0 " +
                                "WHERE dn.order_no = ? AND dn.is_deleted = 0 AND c.statement_month = ?",
                        Integer.class,
                        orderNo,
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

            return new ResponseResult<>(200, "确认成功", null);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "确认对账明细失败: " + e.getMessage(), null);
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

    private List<Map<String, Object>> queryDeliveryRows(List<String> customerKeys, String month, LocalDate periodStart, LocalDate periodEnd, int reconciliationDay, String reconciliationBasis) {
        if (customerKeys == null || customerKeys.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = buildPlaceholders(customerKeys.size());
        String deliveryStatusFilter = RECON_BASIS_RECEIVED.equals(reconciliationBasis)
                ? "AND UPPER(IFNULL(dn.status, '')) IN ('已收货', 'RECEIVED', '部分收货', 'PARTIAL_RECEIVED') "
                : "AND UPPER(IFNULL(dn.status, '')) IN ('已发货', 'SHIPPED', '已收货', 'RECEIVED', '部分收货', 'PARTIAL_RECEIVED') ";
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
                deliveryStatusFilter +
                "AND dn.customer IN (" + placeholders + ") " +
                "AND (" +
                "      (dn.delivery_date BETWEEN ? AND ?) " +
                "      OR dni.id IN (SELECT notice_item_id FROM sales_statement_delivery_confirm WHERE statement_month = ? AND is_deleted = 0) " +
                ") " +
                "ORDER BY dn.delivery_date ASC, dn.notice_no ASC";
        List<Object> args = new ArrayList<>();
        args.addAll(customerKeys);
        args.add(java.sql.Date.valueOf(periodStart));
        args.add(java.sql.Date.valueOf(periodEnd));
        args.add(month);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());

        Map<Long, String> confirmedMonthMap = loadConfirmedStatementMonth(rows);
        for (Map<String, Object> row : rows) {
            Long noticeItemId = getLong(row.get("noticeItemId"));
            String confirmedMonth = noticeItemId == null ? null : confirmedMonthMap.get(noticeItemId);
            String defaultMonth = resolveDefaultStatementMonth(String.valueOf(row.get("bizDate")), reconciliationDay);
            String statementMonth = hasText(confirmedMonth) ? confirmedMonth : defaultMonth;

            row.put("spec", resolveSpec(row));
            row.put("quantity", toDecimal(row.get("quantity")).setScale(0, RoundingMode.HALF_UP));
            row.put("areaSize", toDecimal(row.get("areaSize")).setScale(2, RoundingMode.HALF_UP));
            row.put("unitPrice", toDecimal(row.get("unitPrice")).setScale(4, RoundingMode.HALF_UP));
            row.put("amount", toDecimal(row.get("amount")).setScale(2, RoundingMode.HALF_UP));
            row.put("typeLabel", "发货");
            row.put("reconcileTargetMonth", statementMonth);
            row.put("defaultReconcileMonth", defaultMonth);
            row.put("includeInCurrentStatement", month.equals(statementMonth));
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
            row.put("reconcileTargetMonth", month);
            row.put("defaultReconcileMonth", month);
            row.put("includeInCurrentStatement", true);
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

    private void ensureDeliveryConfirmTable() {
        if (tableExists("sales_statement_delivery_confirm")) {
            return;
        }
        throw new IllegalStateException("对账模块缺少明细确认表，请先执行版本化脚本: sql/V20260410_01__sales_reconciliation_cycle.sql");
    }

    private int normalizeReconciliationDay(Integer day) {
        if (day == null) {
            return 25;
        }
        return Math.max(1, Math.min(31, day));
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
