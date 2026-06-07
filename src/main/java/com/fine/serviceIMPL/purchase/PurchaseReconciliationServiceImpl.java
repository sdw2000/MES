package com.fine.serviceIMPL.purchase;

import com.fine.Utils.ResponseResult;
import com.fine.modle.LoginUser;
import com.fine.modle.purchase.PurchaseReconciliationConfirmRequest;
import com.fine.modle.purchase.PurchaseReconciliationHistoryRequest;
import com.fine.service.purchase.PurchaseReconciliationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PurchaseReconciliationServiceImpl implements PurchaseReconciliationService {

    private static final Pattern MONTH_PATTERN = Pattern.compile("^\\d{4}-\\d{2}$");
    private static final Pattern RULE_PRICING_UNIT_PATTERN = Pattern.compile("(?:pricingUnit|priceUnit|计价单位)\\s*[:=：]\\s*([A-Za-z0-9㎡²mM]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RULE_DENSITY_PATTERN = Pattern.compile("(?:density|dens|密度)\\s*[:=：]\\s*([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);
    private volatile boolean historyTableChecked = false;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public ResponseResult<?> getStatement(String supplier, String month) {
        try {
            String safeSupplier = trimToEmpty(supplier);
            if (!StringUtils.hasText(safeSupplier)) {
                return ResponseResult.error("请选择供应商");
            }
            String safeMonth = normalizeMonth(month);
            List<Map<String, Object>> detailRows = queryStatementRows(safeSupplier, safeMonth);

            BigDecimal totalQty = BigDecimal.ZERO;
            BigDecimal totalAmount = BigDecimal.ZERO;
            int confirmedCount = 0;

            for (Map<String, Object> row : detailRows) {
                BigDecimal qty = toDecimal(row.get("quantity"));
                BigDecimal amount = toDecimal(row.get("amount"));
                totalQty = totalQty.add(qty);
                totalAmount = totalAmount.add(amount);

                boolean confirmed = toInt(row.get("confirmed")) == 1;
                row.put("reconciliationStatus", confirmed ? "MATCHED" : "UNRECONCILED");
                row.put("reconciliationStatusLabel", confirmed ? "已对账" : "未对账");
                if (confirmed) {
                    confirmedCount++;
                }
            }

            String status = resolveStatus(confirmedCount, detailRows.size());
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("itemCount", detailRows.size());
            summary.put("totalQty", totalQty.setScale(4, BigDecimal.ROUND_HALF_UP));
            summary.put("totalAmount", totalAmount.setScale(2, BigDecimal.ROUND_HALF_UP));
            summary.put("reconciledItemCount", confirmedCount);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("supplier", safeSupplier);
            data.put("month", safeMonth);
            data.put("reconciliationStatus", status);
            data.put("reconciliationStatusLabel", statusLabel(status));
            data.put("detailRows", detailRows);
            data.put("historyRows", queryHistoryRows(safeSupplier));
            data.put("summary", summary);
            return ResponseResult.success(data);
        } catch (Exception e) {
            log.error("查询采购对账明细失败 supplier={}, month={}", supplier, month, e);
            return ResponseResult.error("查询采购对账明细失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseResult<?> getOverview(String month,
                                         String supplierKeyword,
                                         String reconciledStatus,
                                         Integer current,
                                         Integer size,
                                         String sortProp,
                                         String sortOrder) {
        try {
            String safeMonth = normalizeMonth(month);
            String keyword = trimToEmpty(supplierKeyword);

            List<Map<String, Object>> groupedRows = queryOverviewRows(safeMonth, keyword);
            String statusFilter = trimToEmpty(reconciledStatus).toUpperCase(Locale.ROOT);
            if (StringUtils.hasText(statusFilter)) {
                groupedRows = groupedRows.stream()
                        .filter(row -> statusFilter.equals(String.valueOf(row.getOrDefault("reconciliationStatus", ""))))
                        .collect(Collectors.toList());
            }

            sortOverviewRows(groupedRows, sortProp, sortOrder);

            int safeCurrent = current == null || current < 1 ? 1 : current;
            int safeSize = size == null || size < 1 ? 20 : Math.min(size, 200);
            int total = groupedRows.size();
            int fromIndex = Math.max(0, (safeCurrent - 1) * safeSize);
            int toIndex = Math.min(total, fromIndex + safeSize);
            List<Map<String, Object>> pageRows = fromIndex >= total ? new ArrayList<>() : groupedRows.subList(fromIndex, toIndex);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("rows", pageRows);
            data.put("total", total);
            data.put("current", safeCurrent);
            data.put("size", safeSize);
            return ResponseResult.success(data);
        } catch (Exception e) {
            log.error("查询采购对账汇总失败 month={}, supplierKeyword={}", month, supplierKeyword, e);
            return ResponseResult.error("查询采购对账汇总失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> confirmDetails(PurchaseReconciliationConfirmRequest request) {
        try {
            if (request == null) {
                return ResponseResult.error("请求参数不能为空");
            }
            String supplier = trimToEmpty(request.getSupplier());
            if (!StringUtils.hasText(supplier)) {
                return ResponseResult.error("请选择供应商");
            }
            String month = normalizeMonth(request.getMonth());

            List<Map<String, Object>> scopedRows = queryStatementRows(supplier, month);
            Set<Long> scopedIds = scopedRows.stream()
                    .map(x -> toLong(x.get("receiptItemId")))
                    .filter(v -> v != null && v > 0)
                    .collect(Collectors.toSet());
            if (scopedIds.isEmpty()) {
                return ResponseResult.error("当前月份没有可对账明细");
            }

            Set<Long> targetIds = new HashSet<>();
            if (request.getDetailIds() != null && !request.getDetailIds().isEmpty()) {
                for (Long id : request.getDetailIds()) {
                    if (id != null && scopedIds.contains(id)) {
                        targetIds.add(id);
                    }
                }
            }
            if (targetIds.isEmpty()) {
                targetIds.addAll(scopedIds);
            }

            String operator = getCurrentUsername();
            String upsertSql = "INSERT INTO purchase_statement_receipt_confirm " +
                    "(receipt_item_id, statement_month, confirmed, confirmed_by, confirmed_at, updated_by, updated_at, is_deleted) " +
                    "VALUES (?, ?, ?, ?, NOW(), ?, NOW(), 0) " +
                    "ON DUPLICATE KEY UPDATE statement_month = VALUES(statement_month), " +
                    "confirmed = VALUES(confirmed), confirmed_by = VALUES(confirmed_by), confirmed_at = NOW(), " +
                    "updated_by = VALUES(updated_by), updated_at = NOW(), is_deleted = 0";

            int reconciled = 0;
            int unreconciled = 0;

            for (Long id : scopedIds) {
                boolean isConfirmed = targetIds.contains(id);
                jdbcTemplate.update(upsertSql, id, month, isConfirmed ? 1 : 0, operator, operator);
                if (isConfirmed) {
                    reconciled++;
                } else {
                    unreconciled++;
                }
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("supplier", supplier);
            data.put("month", month);
            data.put("total", scopedIds.size());
            data.put("reconciled", reconciled);
            data.put("unreconciled", unreconciled);
            return ResponseResult.success("采购对账确认成功", data);
        } catch (Exception e) {
            log.error("采购对账确认失败 request={}", request, e);
            return ResponseResult.error("采购对账确认失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> carryToNextMonth(PurchaseReconciliationConfirmRequest request) {
        try {
            if (request == null) {
                return ResponseResult.error("请求参数不能为空");
            }
            String supplier = trimToEmpty(request.getSupplier());
            if (!StringUtils.hasText(supplier)) {
                return ResponseResult.error("请选择供应商");
            }
            String month = normalizeMonth(request.getMonth());

            List<Map<String, Object>> scopedRows = queryStatementRows(supplier, month);
            Set<Long> scopedIds = scopedRows.stream()
                    .map(x -> toLong(x.get("receiptItemId")))
                    .filter(v -> v != null && v > 0)
                    .collect(Collectors.toSet());
            if (scopedIds.isEmpty()) {
                return ResponseResult.error("当前月份没有可顺延明细");
            }

            Set<Long> targetIds = new HashSet<>();
            if (request.getDetailIds() != null && !request.getDetailIds().isEmpty()) {
                targetIds = request.getDetailIds().stream()
                        .filter(id -> id != null && scopedIds.contains(id))
                        .collect(Collectors.toSet());
            }
            if (targetIds.isEmpty()) {
                targetIds.addAll(scopedIds);
            }
            if (targetIds.isEmpty()) {
                return ResponseResult.error("未找到可顺延的有效明细");
            }

            String nextMonth = getNextMonth(month);
            String operator = getCurrentUsername();
            String upsertSql = "INSERT INTO purchase_statement_receipt_confirm " +
                    "(receipt_item_id, statement_month, confirmed, confirmed_by, confirmed_at, updated_by, updated_at, is_deleted) " +
                    "VALUES (?, ?, 0, ?, NOW(), ?, NOW(), 0) " +
                    "ON DUPLICATE KEY UPDATE statement_month = VALUES(statement_month), " +
                    "confirmed = VALUES(confirmed), confirmed_by = VALUES(confirmed_by), confirmed_at = VALUES(confirmed_at), " +
                    "updated_by = VALUES(updated_by), updated_at = NOW(), is_deleted = 0";

            int moved = 0;
            for (Long id : targetIds) {
                moved += jdbcTemplate.update(upsertSql, id, nextMonth, operator, operator);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("supplier", supplier);
            data.put("month", month);
            data.put("nextMonth", nextMonth);
            data.put("movedCount", targetIds.size());
            data.put("affectedRows", moved);
            return ResponseResult.success("已顺延到下月", data);
        } catch (Exception e) {
            log.error("采购对账顺延下月失败 request={}", request, e);
            return ResponseResult.error("采购对账顺延下月失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseResult<?> getCarryInCandidates(String supplier, String month, String keyword) {
        try {
            String safeSupplier = trimToEmpty(supplier);
            if (!StringUtils.hasText(safeSupplier)) {
                return ResponseResult.error("请选择供应商");
            }
            String targetMonth = normalizeMonth(month);
            List<Map<String, Object>> rows = queryCarryInCandidateRows(safeSupplier, targetMonth, keyword);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("supplier", safeSupplier);
            data.put("targetMonth", targetMonth);
            data.put("rows", rows);
            data.put("hint", rows.isEmpty() ? "没有找到可加入本月的历史未对账收货明细" : "");
            return ResponseResult.success(data);
        } catch (Exception e) {
            log.error("查询采购历史未对账候选失败 supplier={}, month={}, keyword={}", supplier, month, keyword, e);
            return ResponseResult.error("查询采购历史未对账候选失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> moveToMonth(PurchaseReconciliationConfirmRequest request) {
        try {
            if (request == null) {
                return ResponseResult.error("请求参数不能为空");
            }
            String supplier = trimToEmpty(request.getSupplier());
            if (!StringUtils.hasText(supplier)) {
                return ResponseResult.error("请选择供应商");
            }
            String targetMonth = normalizeMonth(StringUtils.hasText(request.getTargetMonth()) ? request.getTargetMonth() : request.getMonth());
            if (request.getDetailIds() == null || request.getDetailIds().isEmpty()) {
                return ResponseResult.error("请选择要加入本月的明细");
            }

            Set<Long> requestedIds = request.getDetailIds().stream()
                    .filter(id -> id != null && id > 0)
                    .collect(Collectors.toSet());
            if (requestedIds.isEmpty()) {
                return ResponseResult.error("请选择有效明细");
            }

            Set<Long> movableIds = queryMovableReceiptItemIds(supplier, targetMonth, requestedIds);
            if (movableIds.isEmpty()) {
                return ResponseResult.error("未找到可移动的未对账明细，已对账或已在本月的明细不能重复加入");
            }

            String operator = getCurrentUsername();
            String upsertSql = "INSERT INTO purchase_statement_receipt_confirm " +
                    "(receipt_item_id, statement_month, confirmed, confirmed_by, confirmed_at, updated_by, updated_at, is_deleted) " +
                    "VALUES (?, ?, 0, ?, NOW(), ?, NOW(), 0) " +
                    "ON DUPLICATE KEY UPDATE statement_month = VALUES(statement_month), confirmed = 0, " +
                    "confirmed_by = VALUES(confirmed_by), confirmed_at = VALUES(confirmed_at), " +
                    "updated_by = VALUES(updated_by), updated_at = NOW(), is_deleted = 0";

            int affectedRows = 0;
            for (Long id : movableIds) {
                affectedRows += jdbcTemplate.update(upsertSql, id, targetMonth, operator, operator);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("supplier", supplier);
            data.put("targetMonth", targetMonth);
            data.put("requestedCount", requestedIds.size());
            data.put("movedCount", movableIds.size());
            data.put("affectedRows", affectedRows);
            return ResponseResult.success("已加入本月对账", data);
        } catch (Exception e) {
            log.error("采购对账移动到指定月份失败 request={}", request, e);
            return ResponseResult.error("采购对账移动到指定月份失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseResult<?> getHistory(String supplier) {
        try {
            String safeSupplier = trimToEmpty(supplier);
            if (!StringUtils.hasText(safeSupplier)) {
                return ResponseResult.error("请选择供应商");
            }
            List<Map<String, Object>> rows = queryHistoryRows(safeSupplier);
            return ResponseResult.success(rows);
        } catch (Exception e) {
            log.error("查询采购对账历史失败 supplier={}", supplier, e);
            return ResponseResult.error("查询采购对账历史失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> saveHistory(PurchaseReconciliationHistoryRequest request) {
        try {
            ensureHistoryTable();
            if (request == null) {
                return ResponseResult.error("请求参数不能为空");
            }
            String supplier = trimToEmpty(request.getSupplier());
            if (!StringUtils.hasText(supplier)) {
                return ResponseResult.error("请选择供应商");
            }
            String statementMonth = normalizeMonth(request.getStatementMonth());
            BigDecimal unpaidAmount = toDecimal(request.getUnpaidAmount()).setScale(2, BigDecimal.ROUND_HALF_UP);
            BigDecimal paidAmount = toDecimal(request.getPaidAmount()).setScale(2, BigDecimal.ROUND_HALF_UP);
            String paymentDate = trimToEmpty(request.getPaymentDate());
            if (StringUtils.hasText(paymentDate)) {
                LocalDate.parse(paymentDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } else {
                paymentDate = null;
            }
            String remark = trimToEmpty(request.getRemark());
            String operator = getCurrentUsername();

            if (request.getId() == null || request.getId() <= 0) {
                String insertSql = "INSERT INTO purchase_statement_history " +
                        "(supplier, statement_month, unpaid_amount, paid_amount, payment_date, remark, created_by, created_at, updated_by, updated_at, is_deleted) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), ?, NOW(), 0)";
                jdbcTemplate.update(insertSql, supplier, statementMonth, unpaidAmount, paidAmount, paymentDate, remark, operator, operator);
            } else {
                Integer exists = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM purchase_statement_history WHERE id = ? AND is_deleted = 0",
                        Integer.class,
                        request.getId()
                );
                if (exists == null || exists <= 0) {
                    return ResponseResult.error("历史记录不存在");
                }
                String updateSql = "UPDATE purchase_statement_history SET supplier = ?, statement_month = ?, unpaid_amount = ?, paid_amount = ?, " +
                        "payment_date = ?, remark = ?, updated_by = ?, updated_at = NOW() WHERE id = ? AND is_deleted = 0";
                jdbcTemplate.update(updateSql, supplier, statementMonth, unpaidAmount, paidAmount, paymentDate, remark, operator, request.getId());
            }

            List<Map<String, Object>> rows = queryHistoryRows(supplier);
            return ResponseResult.success("保存成功", rows);
        } catch (Exception e) {
            log.error("保存采购对账历史失败 request={}", request, e);
            return ResponseResult.error("保存采购对账历史失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> deleteHistory(Long id) {
        try {
            ensureHistoryTable();
            if (id == null || id <= 0) {
                return ResponseResult.error("ID不能为空");
            }
            String operator = getCurrentUsername();
            int updated = jdbcTemplate.update(
                    "UPDATE purchase_statement_history SET is_deleted = 1, updated_by = ?, updated_at = NOW() WHERE id = ? AND is_deleted = 0",
                    operator,
                    id
            );
            if (updated <= 0) {
                return ResponseResult.error("历史记录不存在");
            }
            return ResponseResult.success("删除成功");
        } catch (Exception e) {
            log.error("删除采购对账历史失败 id={}", id, e);
            return ResponseResult.error("删除采购对账历史失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseResult<?> getSupplierOptions(String keyword) {
        try {
            String safeKeyword = trimToEmpty(keyword);
            String sql = "SELECT DISTINCT pr.supplier AS supplier " +
                    "FROM purchase_receipts pr " +
                    "WHERE pr.is_deleted = 0 " +
                    (StringUtils.hasText(safeKeyword) ? "AND pr.supplier LIKE ? " : "") +
                    "ORDER BY pr.supplier ASC LIMIT 200";

            List<Map<String, Object>> rows;
            if (StringUtils.hasText(safeKeyword)) {
                rows = jdbcTemplate.queryForList(sql, "%" + safeKeyword + "%");
            } else {
                rows = jdbcTemplate.queryForList(sql);
            }

            List<Map<String, Object>> list = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String supplier = trimToEmpty(row.get("supplier"));
                if (!StringUtils.hasText(supplier)) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("supplier", supplier);
                item.put("label", supplier);
                item.put("value", supplier);
                list.add(item);
            }
            return ResponseResult.success(list);
        } catch (Exception e) {
            log.error("查询采购对账供应商选项失败 keyword={}", keyword, e);
            return ResponseResult.error("查询供应商失败: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> queryStatementRows(String supplier, String month) {
        String sql = "SELECT " +
                " pri.id AS receiptItemId, " +
                " pr.id AS receiptId, " +
                " pr.receipt_no AS receiptNo, " +
                " pr.purchase_order_no AS purchaseOrderNo, " +
                " pr.supplier AS supplier, " +
                " COALESCE(pr.received_date, pr.expected_date) AS bizDate, " +
                " pri.material_code AS materialCode, " +
                " pri.material_name AS materialName, " +
                " COALESCE(NULLIF(pri.specification, ''), poi_ref.specification, '') AS specification, " +
                " COALESCE(NULLIF(pri.stock_qty, 0), NULLIF(pri.purchase_qty, 0), NULLIF(pri.price_qty, 0), poi_ref.orderQty, 0) AS quantity, " +
                " COALESCE(NULLIF(pri.stock_uom_code, ''), NULLIF(pri.purchase_uom_code, ''), NULLIF(pri.price_uom_code, ''), NULLIF(pri.unit, ''), poi_ref.qtyUnit, '') AS qtyUnit, " +
                " COALESCE(NULLIF(pri.price_qty, 0), NULLIF(pri.stock_qty, 0), NULLIF(pri.purchase_qty, 0), NULLIF(poi_ref.priceQty, 0), poi_ref.orderQty, 0) AS priceQuantity, " +
                " COALESCE(NULLIF(pri.price_uom_code, ''), NULLIF(poi_ref.priceUnit, ''), NULLIF(pri.stock_uom_code, ''), NULLIF(pri.purchase_uom_code, ''), NULLIF(pri.unit, ''), poi_ref.qtyUnit, '') AS priceUnit, " +
                " COALESCE(NULLIF(pri.unit_price, 0), NULLIF(poi_ref.unit_price, 0), 0) AS unitPrice, " +
                " COALESCE( " +
                "   NULLIF(pri.amount, 0), " +
                "   COALESCE(NULLIF(pri.price_qty, 0), NULLIF(pri.stock_qty, 0), NULLIF(pri.purchase_qty, 0), NULLIF(poi_ref.priceQty, 0), poi_ref.orderQty, 0) * " +
                "   COALESCE(NULLIF(pri.unit_price, 0), NULLIF(poi_ref.unit_price, 0), 0), " +
                "   NULLIF(poi_ref.amount, 0), 0 " +
                " ) AS amount, " +
                " COALESCE(ps.statement_month, DATE_FORMAT(COALESCE(pr.received_date, pr.expected_date), '%Y-%m')) AS statementMonth, " +
                " COALESCE(ps.confirmed, 0) AS confirmed " +
                "FROM purchase_receipt_items pri " +
                "INNER JOIN purchase_receipts pr ON pr.id = pri.receipt_id " +
                "LEFT JOIN (" +
                "  SELECT po.order_no AS order_no, poi.material_code AS material_code, " +
                "         COALESCE(NULLIF(poi.raw_spec, ''), '') AS specification, " +
                "         COALESCE(NULLIF(poi.price_qty, 0), NULLIF(poi.stock_qty, 0), NULLIF(poi.purchase_qty, 0), 0) AS orderQty, " +
                "         COALESCE(NULLIF(poi.price_qty, 0), 0) AS priceQty, " +
                "         COALESCE(NULLIF(poi.price_uom_code, ''), NULLIF(poi.stock_uom_code, ''), NULLIF(poi.purchase_uom_code, ''), '') AS qtyUnit, " +
                "         COALESCE(NULLIF(poi.price_uom_code, ''), '') AS priceUnit, " +
                "         COALESCE(poi.unit_price, 0) AS unit_price, " +
                "         COALESCE(poi.amount, COALESCE(NULLIF(poi.price_qty, 0), NULLIF(poi.stock_qty, 0), NULLIF(poi.purchase_qty, 0), 0) * COALESCE(poi.unit_price, 0), 0) AS amount " +
                "  FROM purchase_order_items poi " +
                "  INNER JOIN purchase_orders po ON po.id = poi.order_id " +
                "  INNER JOIN (" +
                "    SELECT po2.order_no AS order_no, poi2.material_code AS material_code, MAX(poi2.id) AS latest_item_id " +
                "    FROM purchase_order_items poi2 " +
                "    INNER JOIN purchase_orders po2 ON po2.id = poi2.order_id " +
                "    WHERE COALESCE(po2.is_deleted, 0) = 0 AND COALESCE(poi2.is_deleted, 0) = 0 " +
                "    GROUP BY po2.order_no, poi2.material_code " +
                "  ) latest ON latest.latest_item_id = poi.id " +
                "  WHERE COALESCE(po.is_deleted, 0) = 0 AND COALESCE(poi.is_deleted, 0) = 0 " +
                ") poi_ref ON poi_ref.order_no = COALESCE(pri.purchase_order_no, pr.purchase_order_no) " +
                "            AND poi_ref.material_code = pri.material_code " +
                "LEFT JOIN purchase_statement_receipt_confirm ps ON ps.receipt_item_id = pri.id AND ps.is_deleted = 0 " +
                "WHERE pri.is_deleted = 0 AND pr.is_deleted = 0 " +
                "  AND pr.supplier = ? " +
                "  AND COALESCE(ps.statement_month, DATE_FORMAT(COALESCE(pr.received_date, pr.expected_date), '%Y-%m')) = ? " +
                "  AND COALESCE(pr.status, '') <> 'cancelled' " +
                "ORDER BY COALESCE(pr.received_date, pr.expected_date) DESC, pri.id DESC";

        return jdbcTemplate.queryForList(sql, supplier, month);
    }

    private List<Map<String, Object>> queryCarryInCandidateRows(String supplier, String targetMonth, String keyword) {
        String safeKeyword = trimToEmpty(keyword);
        boolean hasKeyword = StringUtils.hasText(safeKeyword);
        String sql = "SELECT " +
                " pri.id AS receiptItemId, " +
                " pr.id AS receiptId, " +
                " pr.receipt_no AS receiptNo, " +
                " pr.purchase_order_no AS purchaseOrderNo, " +
                " pr.supplier AS supplier, " +
                " COALESCE(pr.received_date, pr.expected_date) AS bizDate, " +
                " pri.material_code AS materialCode, " +
                " pri.material_name AS materialName, " +
                " COALESCE(NULLIF(pri.specification, ''), poi_ref.specification, '') AS specification, " +
                " COALESCE(NULLIF(pri.stock_qty, 0), NULLIF(pri.purchase_qty, 0), NULLIF(pri.price_qty, 0), poi_ref.orderQty, 0) AS quantity, " +
                " COALESCE(NULLIF(pri.stock_uom_code, ''), NULLIF(pri.purchase_uom_code, ''), NULLIF(pri.price_uom_code, ''), NULLIF(pri.unit, ''), poi_ref.qtyUnit, '') AS qtyUnit, " +
                " COALESCE(NULLIF(pri.price_qty, 0), NULLIF(pri.stock_qty, 0), NULLIF(pri.purchase_qty, 0), NULLIF(poi_ref.priceQty, 0), poi_ref.orderQty, 0) AS priceQuantity, " +
                " COALESCE(NULLIF(pri.price_uom_code, ''), NULLIF(poi_ref.priceUnit, ''), NULLIF(pri.stock_uom_code, ''), NULLIF(pri.purchase_uom_code, ''), NULLIF(pri.unit, ''), poi_ref.qtyUnit, '') AS priceUnit, " +
                " COALESCE(NULLIF(pri.unit_price, 0), NULLIF(poi_ref.unit_price, 0), 0) AS unitPrice, " +
                " COALESCE( " +
                "   NULLIF(pri.amount, 0), " +
                "   COALESCE(NULLIF(pri.price_qty, 0), NULLIF(pri.stock_qty, 0), NULLIF(pri.purchase_qty, 0), NULLIF(poi_ref.priceQty, 0), poi_ref.orderQty, 0) * " +
                "   COALESCE(NULLIF(pri.unit_price, 0), NULLIF(poi_ref.unit_price, 0), 0), " +
                "   NULLIF(poi_ref.amount, 0), 0 " +
                " ) AS amount, " +
                " COALESCE(ps.statement_month, DATE_FORMAT(COALESCE(pr.received_date, pr.expected_date), '%Y-%m')) AS statementMonth, " +
                " COALESCE(ps.confirmed, 0) AS confirmed " +
                "FROM purchase_receipt_items pri " +
                "INNER JOIN purchase_receipts pr ON pr.id = pri.receipt_id " +
                "LEFT JOIN (" +
                "  SELECT po.order_no AS order_no, poi.material_code AS material_code, " +
                "         COALESCE(NULLIF(poi.raw_spec, ''), '') AS specification, " +
                "         COALESCE(NULLIF(poi.price_qty, 0), NULLIF(poi.stock_qty, 0), NULLIF(poi.purchase_qty, 0), 0) AS orderQty, " +
                "         COALESCE(NULLIF(poi.price_qty, 0), 0) AS priceQty, " +
                "         COALESCE(NULLIF(poi.price_uom_code, ''), NULLIF(poi.stock_uom_code, ''), NULLIF(poi.purchase_uom_code, ''), '') AS qtyUnit, " +
                "         COALESCE(NULLIF(poi.price_uom_code, ''), '') AS priceUnit, " +
                "         COALESCE(poi.unit_price, 0) AS unit_price, " +
                "         COALESCE(poi.amount, COALESCE(NULLIF(poi.price_qty, 0), NULLIF(poi.stock_qty, 0), NULLIF(poi.purchase_qty, 0), 0) * COALESCE(poi.unit_price, 0), 0) AS amount " +
                "  FROM purchase_order_items poi " +
                "  INNER JOIN purchase_orders po ON po.id = poi.order_id " +
                "  INNER JOIN (" +
                "    SELECT po2.order_no AS order_no, poi2.material_code AS material_code, MAX(poi2.id) AS latest_item_id " +
                "    FROM purchase_order_items poi2 " +
                "    INNER JOIN purchase_orders po2 ON po2.id = poi2.order_id " +
                "    WHERE COALESCE(po2.is_deleted, 0) = 0 AND COALESCE(poi2.is_deleted, 0) = 0 " +
                "    GROUP BY po2.order_no, poi2.material_code " +
                "  ) latest ON latest.latest_item_id = poi.id " +
                "  WHERE COALESCE(po.is_deleted, 0) = 0 AND COALESCE(poi.is_deleted, 0) = 0 " +
                ") poi_ref ON poi_ref.order_no = COALESCE(pri.purchase_order_no, pr.purchase_order_no) " +
                "            AND poi_ref.material_code = pri.material_code " +
                "LEFT JOIN purchase_statement_receipt_confirm ps ON ps.receipt_item_id = pri.id AND ps.is_deleted = 0 " +
                "WHERE pri.is_deleted = 0 AND pr.is_deleted = 0 " +
                "  AND pr.supplier = ? " +
                "  AND COALESCE(pr.status, '') <> 'cancelled' " +
                "  AND COALESCE(ps.confirmed, 0) = 0 " +
                "  AND COALESCE(ps.statement_month, DATE_FORMAT(COALESCE(pr.received_date, pr.expected_date), '%Y-%m')) < ? " +
                (hasKeyword ? "  AND (pr.receipt_no LIKE ? OR pr.purchase_order_no LIKE ? OR pri.material_code LIKE ? OR pri.material_name LIKE ?) " : "") +
                "ORDER BY statementMonth DESC, COALESCE(pr.received_date, pr.expected_date) DESC, pri.id DESC LIMIT 500";

        if (!hasKeyword) {
            return jdbcTemplate.queryForList(sql, supplier, targetMonth);
        }
        String like = "%" + safeKeyword + "%";
        return jdbcTemplate.queryForList(sql, supplier, targetMonth, like, like, like, like);
    }

    private Set<Long> queryMovableReceiptItemIds(String supplier, String targetMonth, Set<Long> requestedIds) {
        if (requestedIds == null || requestedIds.isEmpty()) {
            return new HashSet<>();
        }
        String placeholders = buildPlaceholders(requestedIds.size());
        String sql = "SELECT pri.id AS receiptItemId " +
                "FROM purchase_receipt_items pri " +
                "INNER JOIN purchase_receipts pr ON pr.id = pri.receipt_id " +
                "LEFT JOIN purchase_statement_receipt_confirm ps ON ps.receipt_item_id = pri.id AND ps.is_deleted = 0 " +
                "WHERE pri.is_deleted = 0 AND pr.is_deleted = 0 " +
                "  AND COALESCE(pr.status, '') <> 'cancelled' " +
                "  AND pr.supplier = ? " +
                "  AND pri.id IN (" + placeholders + ") " +
                "  AND COALESCE(ps.confirmed, 0) = 0 " +
                "  AND COALESCE(ps.statement_month, DATE_FORMAT(COALESCE(pr.received_date, pr.expected_date), '%Y-%m')) <> ?";
        List<Object> args = new ArrayList<>();
        args.add(supplier);
        args.addAll(requestedIds);
        args.add(targetMonth);
        return jdbcTemplate.queryForList(sql, args.toArray()).stream()
                .map(row -> toLong(row.get("receiptItemId")))
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
    }

    private List<Map<String, Object>> queryHistoryRows(String supplier) {
        ensureHistoryTable();
        String sql = "SELECT id, supplier, statement_month AS statementMonth, unpaid_amount AS unpaidAmount, " +
                "paid_amount AS paidAmount, payment_date AS paymentDate, remark, created_by AS createdBy, created_at AS createdAt, " +
                "updated_by AS updatedBy, updated_at AS updatedAt " +
                "FROM purchase_statement_history WHERE supplier = ? AND is_deleted = 0 " +
                "ORDER BY statement_month DESC, payment_date DESC, id DESC";
        return jdbcTemplate.queryForList(sql, supplier);
    }

    private synchronized void ensureHistoryTable() {
        if (historyTableChecked) {
            return;
        }
        String ddl = "CREATE TABLE IF NOT EXISTS purchase_statement_history (" +
                "id BIGINT NOT NULL AUTO_INCREMENT," +
                "supplier VARCHAR(128) NOT NULL," +
                "statement_month VARCHAR(7) NOT NULL," +
                "unpaid_amount DECIMAL(18,2) DEFAULT 0," +
                "paid_amount DECIMAL(18,2) DEFAULT 0," +
                "payment_date DATE NULL," +
                "remark VARCHAR(255) NULL," +
                "created_by VARCHAR(64) NULL," +
                "created_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP," +
                "updated_by VARCHAR(64) NULL," +
                "updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "is_deleted TINYINT(1) NOT NULL DEFAULT 0," +
                "PRIMARY KEY (id)," +
                "KEY idx_supplier_month (supplier, statement_month)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        jdbcTemplate.execute(ddl);
        historyTableChecked = true;
    }

    private List<Map<String, Object>> queryOverviewRows(String month, String supplierKeyword) {
        String supplierSql = "SELECT DISTINCT pr.supplier AS supplier " +
                "FROM purchase_receipt_items pri " +
                "INNER JOIN purchase_receipts pr ON pr.id = pri.receipt_id " +
                "LEFT JOIN purchase_statement_receipt_confirm ps ON ps.receipt_item_id = pri.id AND ps.is_deleted = 0 " +
                "WHERE pri.is_deleted = 0 AND pr.is_deleted = 0 " +
                "  AND COALESCE(pr.status, '') <> 'cancelled' " +
                "  AND COALESCE(ps.statement_month, DATE_FORMAT(COALESCE(pr.received_date, pr.expected_date), '%Y-%m')) = ? " +
                (StringUtils.hasText(supplierKeyword) ? "  AND pr.supplier LIKE ? " : "") +
                "ORDER BY pr.supplier";

        List<Map<String, Object>> supplierRows = StringUtils.hasText(supplierKeyword)
                ? jdbcTemplate.queryForList(supplierSql, month, "%" + supplierKeyword + "%")
                : jdbcTemplate.queryForList(supplierSql, month);

        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, String> supplierCodeCache = new LinkedHashMap<>();
        Map<String, String> supplierNameByCodeCache = new LinkedHashMap<>();
        Map<String, Map<String, String>> pricingRuleCache = new LinkedHashMap<>();
        Map<String, String> quotationUnitCache = new LinkedHashMap<>();
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> supplierRow : supplierRows) {
            String supplier = trimToEmpty(supplierRow.get("supplier"));
            if (!StringUtils.hasText(supplier)) {
                continue;
            }
            List<Map<String, Object>> detailRows = queryStatementRows(supplier, month);

            int itemCount = detailRows.size();
            int reconciledItemCount = 0;
            BigDecimal totalQty = BigDecimal.ZERO;
            BigDecimal totalAmount = BigDecimal.ZERO;
            for (Map<String, Object> detail : detailRows) {
                totalQty = totalQty.add(toDecimal(detail.get("quantity")));
                totalAmount = totalAmount.add(toDecimal(detail.get("amount")));
                if (toInt(detail.get("confirmed")) == 1) {
                    reconciledItemCount++;
                }
            }

            String canonicalSupplier = resolveCanonicalSupplierName(supplier, supplierCodeCache, supplierNameByCodeCache);
            Map<String, Object> one = grouped.get(canonicalSupplier);
            if (one == null) {
                one = new LinkedHashMap<>();
                one.put("supplier", canonicalSupplier);
                one.put("supplierQuery", supplier);
                one.put("month", month);
                one.put("itemCount", 0);
                one.put("reconciledItemCount", 0);
                one.put("totalQty", BigDecimal.ZERO.setScale(4, BigDecimal.ROUND_HALF_UP));
                one.put("totalAmount", BigDecimal.ZERO.setScale(2, BigDecimal.ROUND_HALF_UP));
                grouped.put(canonicalSupplier, one);
            }
            one.put("itemCount", toInt(one.get("itemCount")) + itemCount);
            one.put("reconciledItemCount", toInt(one.get("reconciledItemCount")) + reconciledItemCount);
            one.put("totalQty", toDecimal(one.get("totalQty")).add(totalQty).setScale(4, BigDecimal.ROUND_HALF_UP));
            one.put("totalAmount", toDecimal(one.get("totalAmount")).add(totalAmount).setScale(2, BigDecimal.ROUND_HALF_UP));
        }

        for (Map<String, Object> one : grouped.values()) {
            int itemCount = toInt(one.get("itemCount"));
            int reconciledItemCount = toInt(one.get("reconciledItemCount"));
            String status = resolveStatus(reconciledItemCount, itemCount);
            one.put("reconciliationStatus", status);
            one.put("reconciliationStatusLabel", statusLabel(status));
            result.add(one);
        }
        return result;
    }

    private String resolveCanonicalSupplierName(String supplierName,
                                                Map<String, String> supplierCodeCache,
                                                Map<String, String> supplierNameByCodeCache) {
        String input = trimToEmpty(supplierName);
        if (!StringUtils.hasText(input)) {
            return input;
        }
        String supplierCode = resolveSupplierCodeByName(input, supplierCodeCache);
        if (!StringUtils.hasText(supplierCode)) {
            return input;
        }
        String canonical = resolveSupplierNameByCode(supplierCode, supplierNameByCodeCache);
        return StringUtils.hasText(canonical) ? canonical : input;
    }

    private String resolveSupplierNameByCode(String supplierCode,
                                             Map<String, String> cache) {
        String code = trimToEmpty(supplierCode).toUpperCase(Locale.ROOT);
        if (!StringUtils.hasText(code)) {
            return "";
        }
        if (cache.containsKey(code)) {
            return trimToEmpty(cache.get(code));
        }
        String sql = "SELECT ps.supplier_name FROM purchase_suppliers ps " +
                "WHERE COALESCE(ps.is_deleted, 0) = 0 AND UPPER(ps.supplier_code) = ? " +
                "ORDER BY ps.id DESC LIMIT 1";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, code);
        String name = rows.isEmpty() ? "" : trimToEmpty(rows.get(0).get("supplier_name"));
        cache.put(code, name);
        return name;
    }

    private void applySpecialPricingRule(Map<String, Object> row,
                                         Map<String, String> supplierCodeCache,
                                         Map<String, Map<String, String>> pricingRuleCache,
                                         Map<String, String> quotationUnitCache) {
        if (row == null) {
            return;
        }
        String supplier = trimToEmpty(row.get("supplier"));
        String materialCode = trimToEmpty(row.get("materialCode")).toUpperCase(Locale.ROOT);
        if (!StringUtils.hasText(supplier) || !StringUtils.hasText(materialCode)) {
            return;
        }

        String supplierCode = resolveSupplierCodeByName(supplier, supplierCodeCache);
        if (!StringUtils.hasText(supplierCode)) {
            return;
        }
        Map<String, String> rule = resolveSupplierMaterialPricingRule(supplierCode, materialCode, pricingRuleCache);
        String configuredPricingUnit = normalizePricingUnit(rule == null ? null : rule.get("pricingUnit"));
        String quotePricingUnit = resolveLatestQuotationPricingUnit(supplier, materialCode, quotationUnitCache);
        String qtyUnit = trimToEmpty(row.get("qtyUnit")).toUpperCase(Locale.ROOT);
        String priceUnit = trimToEmpty(row.get("priceUnit")).toUpperCase(Locale.ROOT);
        boolean isAreaPricing = "M2".equals(priceUnit) || "㎡".equals(priceUnit) || "M²".equals(priceUnit) || "SQM".equals(priceUnit);
        boolean qtyIsKg = "KG".equals(qtyUnit) || "KGS".equals(qtyUnit) || "公斤".equals(qtyUnit) || "千克".equals(qtyUnit);

        boolean shouldUseKg = "KG".equals(configuredPricingUnit) || "KG".equals(quotePricingUnit);
        // 兜底：如果收货数量单位已是KG、但计价单位仍是㎡，按KG口径重算，避免把公斤单价当平方米单价
        if (!shouldUseKg && qtyIsKg && isAreaPricing) {
            shouldUseKg = true;
        }
        if (!shouldUseKg) {
            return;
        }

        if (!isAreaPricing && !qtyIsKg) {
            return;
        }

        BigDecimal kg = BigDecimal.ZERO;
        if (qtyIsKg) {
            kg = firstPositive(toDecimal(row.get("quantity")), toDecimal(row.get("priceQuantity")));
        }
        if (kg.compareTo(BigDecimal.ZERO) <= 0) {
            BigDecimal area = firstPositive(
                    toDecimal(row.get("priceQuantity")),
                    toDecimal(row.get("quantity"))
            );
            if (area.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }

            BigDecimal thicknessUm = resolveThicknessUm(row);
            if (thicknessUm.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }

            BigDecimal density = toDecimal(rule == null ? null : rule.get("density"));
            if (density.compareTo(BigDecimal.ZERO) <= 0) {
                density = inferFilmDensity(row); // g/cm³
            }
            // kg = area(㎡) * thickness(μm) * density / 1000
            kg = area.multiply(thicknessUm)
                    .multiply(density)
                    .divide(new BigDecimal("1000"), 4, BigDecimal.ROUND_HALF_UP);
        }

        if (kg.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal unitPrice = toDecimal(row.get("unitPrice"));
        BigDecimal amount = kg.multiply(unitPrice).setScale(2, BigDecimal.ROUND_HALF_UP);

        row.put("quantity", kg);
        row.put("qtyUnit", "KG");
        row.put("priceQuantity", kg);
        row.put("priceUnit", "KG");
        row.put("amount", amount);
    }

    private String resolveLatestQuotationPricingUnit(String supplier,
                                                     String materialCode,
                                                     Map<String, String> cache) {
        String supplierKey = trimToEmpty(supplier);
        String materialKey = trimToEmpty(materialCode).toUpperCase(Locale.ROOT);
        if (!StringUtils.hasText(supplierKey) || !StringUtils.hasText(materialKey)) {
            return "";
        }
        String cacheKey = supplierKey + "__" + materialKey;
        if (cache.containsKey(cacheKey)) {
            return trimToEmpty(cache.get(cacheKey));
        }

        String sql = "SELECT qi.unit AS unit " +
                "FROM purchase_quotation_items qi " +
                "INNER JOIN purchase_quotations q ON q.id = qi.quotation_id " +
                "WHERE COALESCE(q.is_deleted, 0) = 0 AND COALESCE(qi.is_deleted, 0) = 0 " +
                "  AND q.supplier = ? AND qi.material_code = ? " +
                "ORDER BY COALESCE(qi.updated_at, qi.created_at) DESC, qi.id DESC LIMIT 1";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, supplierKey, materialKey);
        String unit = rows.isEmpty() ? "" : normalizePricingUnit(trimToEmpty(rows.get(0).get("unit")));
        cache.put(cacheKey, unit);
        return unit;
    }

    private String resolveSupplierCodeByName(String supplierName, Map<String, String> cache) {
        String key = trimToEmpty(supplierName);
        if (!StringUtils.hasText(key)) {
            return "";
        }
        if (cache.containsKey(key)) {
            return trimToEmpty(cache.get(key));
        }
        String sql = "SELECT ps.supplier_code FROM purchase_suppliers ps " +
                "WHERE COALESCE(ps.is_deleted, 0) = 0 " +
                "  AND (ps.supplier_name = ? OR ps.short_name = ? OR ps.supplier_name LIKE ? OR ps.short_name LIKE ?) " +
                "ORDER BY CASE WHEN ps.supplier_name = ? OR ps.short_name = ? THEN 0 ELSE 1 END, ps.id DESC LIMIT 1";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                key,
                key,
                "%" + key + "%",
                "%" + key + "%",
                key,
                key);
        String code = rows.isEmpty() ? "" : trimToEmpty(rows.get(0).get("supplier_code"));
        cache.put(key, code);
        return code;
    }

    private Map<String, String> resolveSupplierMaterialPricingRule(String supplierCode,
                                                                   String materialCode,
                                                                   Map<String, Map<String, String>> cache) {
        String sCode = trimToEmpty(supplierCode).toUpperCase(Locale.ROOT);
        String mCode = trimToEmpty(materialCode).toUpperCase(Locale.ROOT);
        if (!StringUtils.hasText(sCode) || !StringUtils.hasText(mCode)) {
            return new LinkedHashMap<>();
        }
        String cacheKey = sCode + "__" + mCode;
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }

        String sql = "SELECT remark FROM purchase_supplier_material_mapping " +
                "WHERE COALESCE(is_active, 0) = 1 AND supplier_code = ? AND material_code = ? " +
                "ORDER BY id DESC LIMIT 1";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, sCode, mCode);
        Map<String, String> parsed = rows.isEmpty()
                ? new LinkedHashMap<>()
                : parsePricingRuleFromRemark(trimToEmpty(rows.get(0).get("remark")));
        cache.put(cacheKey, parsed);
        return parsed;
    }

    private Map<String, String> parsePricingRuleFromRemark(String remark) {
        Map<String, String> rule = new LinkedHashMap<>();
        String text = trimToEmpty(remark);
        if (!StringUtils.hasText(text)) {
            return rule;
        }
        java.util.regex.Matcher pricingMatcher = RULE_PRICING_UNIT_PATTERN.matcher(text);
        if (pricingMatcher.find() && pricingMatcher.group(1) != null) {
            rule.put("pricingUnit", pricingMatcher.group(1).trim());
        }
        java.util.regex.Matcher densityMatcher = RULE_DENSITY_PATTERN.matcher(text);
        if (densityMatcher.find() && densityMatcher.group(1) != null) {
            rule.put("density", densityMatcher.group(1).trim());
        }
        return rule;
    }

    private String normalizePricingUnit(String unitText) {
        String text = trimToEmpty(unitText).toUpperCase(Locale.ROOT);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        if ("KG".equals(text) || "KGS".equals(text) || "公斤".equals(text) || "千克".equals(text)) {
            return "KG";
        }
        if ("M2".equals(text) || "M²".equals(text) || "㎡".equals(text) || "SQM".equals(text)) {
            return "M2";
        }
        return text;
    }

    private BigDecimal resolveThicknessUm(Map<String, Object> row) {
        String[] candidates = new String[]{
                trimToEmpty(row.get("specification")),
                trimToEmpty(row.get("materialName")),
                trimToEmpty(row.get("materialCode"))
        };
        for (String text : candidates) {
            if (!StringUtils.hasText(text)) {
                continue;
            }
            String normalized = text.replace("μ", "u").replace("µ", "u");
            java.util.regex.Matcher um = java.util.regex.Pattern
                    .compile("(\\d+(?:\\.\\d+)?)\\s*(?:um|u m|μm|µm)", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(normalized);
            if (um.find()) {
                return toDecimal(um.group(1));
            }

            java.util.regex.Matcher leading = java.util.regex.Pattern
                    .compile("^(\\d+(?:\\.\\d+)?)")
                    .matcher(normalized.trim());
            if (leading.find()) {
                return toDecimal(leading.group(1));
            }
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal inferFilmDensity(Map<String, Object> row) {
        String text = (trimToEmpty(row.get("materialCode")) + " " + trimToEmpty(row.get("materialName"))).toUpperCase(Locale.ROOT);
        if (text.contains("BOPP")) {
            return new BigDecimal("0.90");
        }
        if (text.contains("OPS") || text.contains("PS")) {
            return new BigDecimal("1.05");
        }
        if (text.contains("PET") || text.contains("PI")) {
            return new BigDecimal("1.40");
        }
        return new BigDecimal("1.05");
    }

    private BigDecimal firstPositive(BigDecimal... values) {
        if (values == null) {
            return BigDecimal.ZERO;
        }
        for (BigDecimal value : values) {
            if (value != null && value.compareTo(BigDecimal.ZERO) > 0) {
                return value;
            }
        }
        return BigDecimal.ZERO;
    }

    private void sortOverviewRows(List<Map<String, Object>> rows, String sortProp, String sortOrder) {
        if (rows == null || rows.size() <= 1) {
            return;
        }
        String prop = trimToEmpty(sortProp);
        boolean asc = "ascending".equalsIgnoreCase(trimToEmpty(sortOrder)) || "asc".equalsIgnoreCase(trimToEmpty(sortOrder));

        Comparator<Map<String, Object>> comparator;
        switch (prop) {
            case "supplier":
                comparator = Comparator.comparing(x -> trimToEmpty(x.get("supplier")), String.CASE_INSENSITIVE_ORDER);
                break;
            case "itemCount":
                comparator = Comparator.comparingInt(x -> toInt(x.get("itemCount")));
                break;
            case "reconciledItemCount":
                comparator = Comparator.comparingInt(x -> toInt(x.get("reconciledItemCount")));
                break;
            case "totalQty":
                comparator = Comparator.comparing(x -> toDecimal(x.get("totalQty")));
                break;
            case "reconciliationStatus":
                comparator = Comparator.comparingInt(x -> {
                    String status = String.valueOf(x.getOrDefault("reconciliationStatus", "UNRECONCILED"));
                    if ("MATCHED".equalsIgnoreCase(status)) return 2;
                    if ("PARTIAL".equalsIgnoreCase(status)) return 1;
                    return 0;
                });
                break;
            case "totalAmount":
            default:
                comparator = Comparator.comparing(x -> toDecimal(x.get("totalAmount")));
                break;
        }

        if (!asc) {
            comparator = comparator.reversed();
        }
        rows.sort(comparator.thenComparing(x -> trimToEmpty(x.get("supplier")), String.CASE_INSENSITIVE_ORDER));
    }

    private String resolveStatus(int reconciledCount, int totalCount) {
        if (totalCount <= 0) {
            return "UNRECONCILED";
        }
        if (reconciledCount >= totalCount) {
            return "MATCHED";
        }
        if (reconciledCount > 0) {
            return "PARTIAL";
        }
        return "UNRECONCILED";
    }

    private String statusLabel(String status) {
        if ("MATCHED".equalsIgnoreCase(status)) {
            return "已对账";
        }
        if ("PARTIAL".equalsIgnoreCase(status)) {
            return "部分对账";
        }
        return "未对账";
    }

    private String normalizeMonth(String month) {
        String raw = trimToEmpty(month);
        if (!StringUtils.hasText(raw)) {
            YearMonth ym = YearMonth.now().minusMonths(1);
            return ym.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        }
        if (!MONTH_PATTERN.matcher(raw).matches()) {
            throw new IllegalArgumentException("月份格式错误，应为 yyyy-MM");
        }
        LocalDate.parse(raw + "-01", DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return raw;
    }

    private String getNextMonth(String month) {
        YearMonth ym = YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyy-MM"));
        return ym.plusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    private String buildPlaceholders(int size) {
        if (size <= 0) {
            return "?";
        }
        return java.util.stream.IntStream.range(0, size)
                .mapToObj(i -> "?")
                .collect(Collectors.joining(","));
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return ((LoginUser) authentication.getPrincipal()).getUsername();
        }
        return "system";
    }

    private String trimToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ex) {
            return 0;
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            if (value instanceof BigDecimal) {
                return (BigDecimal) value;
            }
            if (value instanceof Number) {
                return BigDecimal.valueOf(((Number) value).doubleValue());
            }
            return new BigDecimal(String.valueOf(value).trim());
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }
}
