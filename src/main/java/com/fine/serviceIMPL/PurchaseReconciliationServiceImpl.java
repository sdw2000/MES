package com.fine.serviceIMPL;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fine.Dao.PurchaseStatementHistoryMapper;
import com.fine.Dao.purchase.PurchaseSupplierMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.purchase.PurchaseReconciliationConfirmRequest;
import com.fine.modle.purchase.PurchaseStatementHistory;
import com.fine.modle.purchase.PurchaseSupplier;
import com.fine.service.PurchaseReconciliationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PurchaseReconciliationServiceImpl implements PurchaseReconciliationService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PurchaseSupplierMapper purchaseSupplierMapper;

    @Autowired
    private PurchaseStatementHistoryMapper purchaseStatementHistoryMapper;

    private boolean hasText(String str) {
        return str != null && !str.trim().isEmpty();
    }

    @Override
    public ResponseResult<?> getStatement(String supplierCode, String month, Integer current, Integer size, String sortProp, String sortOrder) {
        try {
            ensureReceiptSplitTable();
            if (supplierCode == null || supplierCode.trim().isEmpty()) {
                return new ResponseResult<>(400, "供应商不能为空", null);
            }
            if (month == null || !month.matches("\\d{4}-\\d{2}")) {
                return new ResponseResult<>(400, "月份格式应为yyyy-MM", null);
            }

            PurchaseSupplier supplier = purchaseSupplierMapper.selectOne(
                    new LambdaQueryWrapper<PurchaseSupplier>().eq(PurchaseSupplier::getSupplierCode, supplierCode.trim())
            );

            YearMonth yearMonth = YearMonth.parse(month);
            LocalDate start = yearMonth.atDay(1);
            LocalDate end = yearMonth.atEndOfMonth();

            List<Map<String, Object>> rawRows = fetchCandidateRows(supplierCode, month, start, end);
            Map<Long, String> confirmedMonthMap = loadReceiptConfirmMonthMap(rawRows);
            Map<Long, List<Map<String, Object>>> splitMap = loadReceiptSplitRows(rawRows);

            List<Map<String, Object>> processedRows = new ArrayList<>();
            for (Map<String, Object> row : rawRows) {
                Long receiptItemId = findRowId(row);
                String defaultMonth = resolveDefaultMonth(String.valueOf(row.get("bizDate")));
                String confirmedMonth = (receiptItemId == null) ? null : confirmedMonthMap.get(receiptItemId);
                String bizType = String.valueOf(row.get("bizType"));

                if ("return".equals(bizType)) {
                    String targetMonth = hasText(confirmedMonth) ? confirmedMonth : defaultMonth;
                    if (month.equals(targetMonth)) {
                        row.put("reconcileTargetMonth", targetMonth);
                        row.put("isConfirmed", hasText(confirmedMonth));
                        row.put("includeInCurrentStatement", true);
                        processedRows.add(row);
                    }
                    continue;
                }

                List<Map<String, Object>> splits = (receiptItemId == null) ? null : splitMap.get(receiptItemId);
                if (splits == null || splits.isEmpty()) {
                    String targetMonth = hasText(confirmedMonth) ? confirmedMonth : defaultMonth;
                    if (month.equals(targetMonth)) {
                        row.put("reconcileTargetMonth", targetMonth);
                        row.put("isConfirmed", hasText(confirmedMonth));
                        row.put("includeInCurrentStatement", true);
                        processedRows.add(row);
                    }
                } else {
                    for (Map<String, Object> split : splits) {
                        String splitMonth = String.valueOf(split.get("statement_month"));
                        if (month.equals(splitMonth)) {
                            Map<String, Object> splitRow = new HashMap<>(row);
                            splitRow.put("quantity", toDecimal(split.get("split_quantity")));
                            splitRow.put("amount", toDecimal(split.get("split_amount")));
                            splitRow.put("reconcileTargetMonth", splitMonth);
                            splitRow.put("isConfirmed", true);
                            splitRow.put("isSplit", true);
                            splitRow.put("includeInCurrentStatement", true);
                            processedRows.add(splitRow);
                        }
                    }
                }
            }

            BigDecimal totalReceipt = BigDecimal.ZERO;
            BigDecimal totalReturn = BigDecimal.ZERO;
            for (Map<String, Object> row : processedRows) {
                BigDecimal amt = toDecimal(row.get("amount"));
                if ("return".equals(row.get("bizType"))) {
                    totalReturn = totalReturn.add(amt);
                } else if ("receipt".equals(row.get("bizType"))) {
                    totalReceipt = totalReceipt.add(amt);
                }
            }

            sortProcessedRows(processedRows, sortProp, sortOrder);
            
            int total = processedRows.size();
            int fromIndex = (current != null && size != null) ? (current - 1) * size : 0;
            int toIndex = (current != null && size != null) ? Math.min(fromIndex + size, total) : total;
            List<Map<String, Object>> pagedRows = (fromIndex < total) ? new ArrayList<>(processedRows.subList(fromIndex, toIndex)) : new ArrayList<>();

            Map<String, Object> summary = new HashMap<>();
            summary.put("receiptAmount", totalReceipt.setScale(2, RoundingMode.HALF_UP));
            summary.put("returnAmount", totalReturn.setScale(2, RoundingMode.HALF_UP).abs().negate());
            summary.put("totalAmount", totalReceipt.add(totalReturn).setScale(2, RoundingMode.HALF_UP));

            List<PurchaseStatementHistory> histories = purchaseStatementHistoryMapper.selectList(
                    new LambdaQueryWrapper<PurchaseStatementHistory>()
                            .eq(PurchaseStatementHistory::getSupplierCode, supplierCode.trim())
                            .eq(PurchaseStatementHistory::getIsDeleted, 0)
                            .orderByDesc(PurchaseStatementHistory::getStatementMonth)
            );

            Map<String, Object> data = new HashMap<>();
            data.put("supplierCode", supplierCode);
            data.put("supplierName", supplier != null ? supplier.getSupplierName() : supplierCode);
            data.put("month", month);
            data.put("detailRows", pagedRows);
            data.put("total", total);
            data.put("historyRows", histories);
            data.put("summary", summary);
            data.putAll(loadMonthConfirmInfo(supplierCode, month));

            return ResponseResult.success(data);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error("查询失败: " + e.getMessage());
        }
    }

    private Long findRowId(Map<String, Object> row) {
        Object id = row.get("detailId");
        if (id == null) return null;
        return toLong(id);
    }

    private List<Map<String, Object>> fetchCandidateRows(String supplierCode, String month, LocalDate start, LocalDate end) {
        String sql = "SELECT * FROM (" +
                "  SELECT pri.id as detailId, pri.receipt_id as mainId, pr.receipt_no as documentNo, pr.received_date as bizDate, " +
                "  pri.material_code as materialCode, pri.material_name as materialName, COALESCE(poi.raw_spec, poi.film_spec_raw, pri.specification) as spec, " +
                "  COALESCE(pri.price_qty, pri.received_qty) as quantity, " +
                "  COALESCE(pri.price_uom_code, pri.unit) as unit, " +
                "  COALESCE(poi.unit_price, pri.unit_price) as unit_price, " +
                "  (COALESCE(pri.price_qty, pri.received_qty) * COALESCE(poi.unit_price, pri.unit_price)) as amount, " +
                "  pr.purchase_order_no as orderNo, " +
                "  'receipt' as bizType " +
                "  FROM purchase_receipt_items pri " +
                "  JOIN purchase_receipts pr ON pri.receipt_id = pr.id " +
                "  JOIN purchase_suppliers s ON (pr.supplier = s.supplier_name OR pr.supplier = s.supplier_code) " +
                "  LEFT JOIN purchase_orders po ON pr.purchase_order_no = po.order_no " +
                "  LEFT JOIN purchase_order_items poi ON po.id = poi.order_id AND (pri.material_code = poi.material_code OR (pri.material_code IS NULL AND pri.material_name = poi.material_name)) " +
                "  WHERE s.supplier_code = ? AND (pr.received_date BETWEEN ? AND ? OR pri.id IN (SELECT receipt_item_id FROM purchase_statement_receipt_confirm WHERE statement_month = ? AND is_deleted=0) OR pri.id IN (SELECT receipt_item_id FROM purchase_statement_receipt_split WHERE statement_month = ? AND is_deleted=0)) " +
                "  AND pr.is_deleted = 0 AND pri.is_deleted = 0 " +
                "  UNION ALL " +
                "  SELECT pri.id as detailId, pri.return_id as mainId, pr.return_no as documentNo, pr.return_date as bizDate, " +
                "  pri.material_code as materialCode, pri.material_name as materialName, COALESCE(poi.raw_spec, poi.film_spec_raw) as spec, " +
                "  pri.sqm as quantity, " +
                "  pri.price_unit as unit, " +
                "  COALESCE(poi.unit_price, pri.unit_price) as unit_price, " +
                "  -(pri.sqm * COALESCE(poi.unit_price, pri.unit_price)) as amount, " +
                "  pri.purchase_order_no as orderNo, " +
                "  'return' as bizType " +
                "  FROM purchase_return_items pri " +
                "  JOIN purchase_return_orders pr ON pri.return_id = pr.id " +
                "  JOIN purchase_suppliers s ON (pr.supplier = s.supplier_name OR pr.supplier = s.supplier_code) " +
                "  LEFT JOIN purchase_orders po ON pri.purchase_order_no = po.order_no " +
                "  LEFT JOIN purchase_order_items poi ON po.id = poi.order_id AND (pri.material_code = poi.material_code OR (pri.material_code IS NULL AND pri.material_name = poi.material_name)) " +
                "  WHERE s.supplier_code = ? AND (pr.return_date BETWEEN ? AND ? OR pri.id IN (SELECT receipt_item_id FROM purchase_statement_receipt_confirm WHERE statement_month = ? AND is_deleted=0)) " +
                "  AND pr.is_deleted = 0 AND pri.is_deleted = 0 " +
                ") as t";
        return jdbcTemplate.queryForList(sql, supplierCode, start.toString(), end.toString(), month, month, supplierCode, start.toString(), end.toString(), month);
    }

    private void sortProcessedRows(List<Map<String, Object>> rows, String prop, String order) {
        String safeProp = (prop == null || prop.isEmpty()) ? "bizDate" : prop;
        boolean desc = "desc".equalsIgnoreCase(order);
        rows.sort((a, b) -> {
            Object v1 = a.get(safeProp);
            Object v2 = b.get(safeProp);
            if (v1 == null) return 1;
            if (v2 == null) return -1;
            int cmp;
            if (v1 instanceof Comparable) {
                cmp = ((Comparable) v1).compareTo(v2);
            } else {
                cmp = String.valueOf(v1).compareTo(String.valueOf(v2));
            }
            return desc ? -cmp : cmp;
        });
    }

    private Map<Long, String> loadReceiptConfirmMonthMap(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return Collections.emptyMap();
        List<Long> ids = rows.stream().map(this::findRowId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) return Collections.emptyMap();
        String placeholders = ids.stream().map(i -> "?").collect(Collectors.joining(","));
        List<Map<String, Object>> confirms = jdbcTemplate.queryForList(
            "SELECT receipt_item_id, statement_month FROM purchase_statement_receipt_confirm WHERE receipt_item_id IN (" + placeholders + ") AND is_deleted = 0",
            ids.toArray()
        );
        Map<Long, String> map = new HashMap<>();
        for (Map<String, Object> c : confirms) {
            map.put(toLong(c.get("receipt_item_id")), String.valueOf(c.get("statement_month")));
        }
        return map;
    }

    private Map<Long, List<Map<String, Object>>> loadReceiptSplitRows(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return Collections.emptyMap();
        List<Long> ids = rows.stream().map(this::findRowId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) return Collections.emptyMap();
        String placeholders = ids.stream().map(i -> "?").collect(Collectors.joining(","));
        List<Map<String, Object>> splits = jdbcTemplate.queryForList(
            "SELECT * FROM purchase_statement_receipt_split WHERE receipt_item_id IN (" + placeholders + ") AND is_deleted = 0",
            ids.toArray()
        );
        return splits.stream().collect(Collectors.groupingBy(s -> toLong(s.get("receipt_item_id"))));
    }

    private String resolveDefaultMonth(String bizDate) {
        if (bizDate == null || bizDate.length() < 7) return "";
        return bizDate.substring(0, 7);
    }

    @Override
    public ResponseResult<?> getStatementOverview(String month, String supplierCode, String reconciledStatus, Integer current, Integer size, String sortProp, String sortOrder) {
        try {
            ensureReceiptSplitTable();
            YearMonth yearMonth = YearMonth.parse(month);
            LocalDate start = yearMonth.atDay(1);
            LocalDate end = yearMonth.atEndOfMonth();

            String baseQuerySql = "FROM (" +
                    "  SELECT pr.supplier, 'receipt' as bizType, " +
                    "  (COALESCE(pri.price_qty, pri.received_qty) * COALESCE(poi.unit_price, pri.unit_price)) as amount " +
                    "  FROM purchase_receipt_items pri " +
                    "  JOIN purchase_receipts pr ON pri.receipt_id = pr.id " +
                    "  LEFT JOIN purchase_orders po ON pr.purchase_order_no = po.order_no " +
                    "  LEFT JOIN purchase_order_items poi ON po.id = poi.order_id AND (pri.material_code = poi.material_code OR (pri.material_code IS NULL AND pri.material_name = poi.material_name)) " +
                    "  WHERE (pr.received_date BETWEEN ? AND ? OR pri.id IN (SELECT receipt_item_id FROM purchase_statement_receipt_confirm WHERE statement_month = ? AND is_deleted=0) OR pri.id IN (SELECT receipt_item_id FROM purchase_statement_receipt_split WHERE statement_month = ? AND is_deleted=0)) " +
                    "  AND pr.is_deleted = 0 AND pri.is_deleted = 0 " +
                    "  UNION ALL " +
                    "  SELECT pr.supplier, 'return' as bizType, " +
                    "  -(pri.sqm * COALESCE(poi.unit_price, pri.unit_price)) as amount " +
                    "  FROM purchase_return_items pri " +
                    "  JOIN purchase_return_orders pr ON pri.return_id = pr.id " +
                    "  LEFT JOIN purchase_orders po ON pri.purchase_order_no = po.order_no " +
                    "  LEFT JOIN purchase_order_items poi ON po.id = poi.order_id AND (pri.material_code = poi.material_code OR (pri.material_code IS NULL AND pri.material_name = poi.material_name)) " +
                    "  WHERE (pr.return_date BETWEEN ? AND ? OR pri.id IN (SELECT receipt_item_id FROM purchase_statement_receipt_confirm WHERE statement_month = ? AND is_deleted=0)) " +
                    "  AND pr.is_deleted = 0 AND pri.is_deleted = 0 " +
                    ") t " +
                    "JOIN purchase_suppliers s ON (t.supplier = s.supplier_name OR t.supplier = s.supplier_code) " +
                    "LEFT JOIN purchase_statement_month_confirm mc ON s.supplier_code = mc.supplier_code AND mc.statement_month = ? " +
                    "WHERE 1=1 ";

            List<Object> params = new ArrayList<>();
            params.addAll(Arrays.asList(start.toString(), end.toString(), month, month, start.toString(), end.toString(), month, month));

            if (supplierCode != null && !supplierCode.isEmpty()) {
                baseQuerySql += " AND (s.supplier_code LIKE ? OR s.supplier_name LIKE ?) ";
                params.add("%" + supplierCode + "%");
                params.add("%" + supplierCode + "%");
            }

            String aggregateSql = "SELECT s.supplier_code as supplierCode, s.supplier_name as supplierName, " +
                    "SUM(CASE WHEN bizType = 'receipt' THEN amount ELSE 0 END) as receiptAmount, " +
                    "SUM(CASE WHEN bizType = 'return' THEN amount ELSE 0 END) as returnAmount, " +
                    "SUM(amount) as totalAmount, " +
                    "COALESCE(mc.status, 'PENDING') as status, " +
                    "mc.purchase_confirmed_by as purchaseConfirmedBy, " +
                    "mc.purchase_confirmed_at as purchaseConfirmedAt " +
                    baseQuerySql +
                    "GROUP BY s.supplier_code, s.supplier_name, status, mc.purchase_confirmed_by, mc.purchase_confirmed_at ";

            String finalSql = "SELECT * FROM (" + aggregateSql + ") as final_t WHERE 1=1 ";
            List<Object> finalParams = new ArrayList<>(params);
            if (reconciledStatus != null && !reconciledStatus.isEmpty()) {
                finalSql += " AND status = ? ";
                finalParams.add(reconciledStatus);
            }

            String orderSql = " ORDER BY supplierCode ASC";
            if (sortProp != null && !sortProp.isEmpty()) {
                String dir = "desc".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";
                if (Arrays.asList("supplierCode", "receiptAmount", "returnAmount", "totalAmount", "status").contains(sortProp)) {
                    orderSql = " ORDER BY " + sortProp + " " + dir;
                }
            }

            Integer total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM (" + finalSql + ") as count_t", Integer.class, finalParams.toArray());
            String limitSql = (current != null && size != null) ? " LIMIT " + (current - 1) * size + ", " + size : "";
            List<Map<String, Object>> records = jdbcTemplate.queryForList(finalSql + orderSql + limitSql, finalParams.toArray());

            for (Map<String, Object> row : records) {
                row.put("receiptAmount", toDecimal(row.get("receiptAmount")).setScale(2, RoundingMode.HALF_UP));
                row.put("returnAmount", toDecimal(row.get("returnAmount")).setScale(2, RoundingMode.HALF_UP));
                row.put("totalAmount", toDecimal(row.get("totalAmount")).setScale(2, RoundingMode.HALF_UP));
                row.put("month", month);
            }
            Map<String, Object> result = new HashMap<>();
            result.put("records", records);
            result.put("total", total);
            return ResponseResult.success(result);
        } catch (Exception e) {
            return ResponseResult.error("查询总览失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseResult<?> getHistory(String supplierCode) {
        List<PurchaseStatementHistory> list = purchaseStatementHistoryMapper.selectList(
                new LambdaQueryWrapper<PurchaseStatementHistory>()
                        .eq(PurchaseStatementHistory::getSupplierCode, supplierCode)
                        .eq(PurchaseStatementHistory::getIsDeleted, 0)
                        .orderByDesc(PurchaseStatementHistory::getStatementMonth)
        );
        return ResponseResult.success(list);
    }

    @Override
    public ResponseResult<?> saveHistory(PurchaseStatementHistory history) {
        if (history.getId() != null) purchaseStatementHistoryMapper.updateById(history);
        else purchaseStatementHistoryMapper.insert(history);
        return ResponseResult.success("保存成功");
    }

    @Override
    public ResponseResult<?> deleteHistory(Long id) {
        purchaseStatementHistoryMapper.deleteById(id);
        return ResponseResult.success("删除成功");
    }

    @Override
    @Transactional
    public ResponseResult<?> confirmStatementDetails(PurchaseReconciliationConfirmRequest request) {
        if (request == null || !hasText(request.getSupplierCode()) || !hasText(request.getMonth())) return ResponseResult.error("参数不完整");
        String operator = getCurrentUsername();
        if (request.getDetails() != null) {
            Map<Long, Map<String, PurchaseReconciliationConfirmRequest.ReceiptConfirmItem>> grouped = new LinkedHashMap<>();
            for (PurchaseReconciliationConfirmRequest.ReceiptConfirmItem item : request.getDetails()) {
                if (item.getReceiptItemId() == null || !hasText(item.getTargetMonth())) continue;
                grouped.computeIfAbsent(item.getReceiptItemId(), k -> new LinkedHashMap<>()).put(item.getTargetMonth(), item);
            }
            for (Map.Entry<Long, Map<String, PurchaseReconciliationConfirmRequest.ReceiptConfirmItem>> entry : grouped.entrySet()) {
                Long itemId = entry.getKey();
                Map<String, PurchaseReconciliationConfirmRequest.ReceiptConfirmItem> months = entry.getValue();
                if (months.size() == 1) {
                    String target = months.keySet().iterator().next();
                    jdbcTemplate.update("INSERT INTO purchase_statement_receipt_confirm (receipt_item_id, statement_month, updated_by, updated_at, is_deleted) VALUES (?, ?, ?, NOW(), 0) ON DUPLICATE KEY UPDATE statement_month = VALUES(statement_month), updated_by = VALUES(updated_by), updated_at = NOW(), is_deleted = 0", itemId, target, operator);
                    jdbcTemplate.update("UPDATE purchase_statement_receipt_split SET is_deleted = 1 WHERE receipt_item_id = ?", itemId);
                } else {
                    jdbcTemplate.update("UPDATE purchase_statement_receipt_confirm SET is_deleted = 1 WHERE receipt_item_id = ?", itemId);
                    jdbcTemplate.update("UPDATE purchase_statement_receipt_split SET is_deleted = 1 WHERE receipt_item_id = ?", itemId);
                    for (PurchaseReconciliationConfirmRequest.ReceiptConfirmItem s : months.values()) {
                        jdbcTemplate.update("INSERT INTO purchase_statement_receipt_split (receipt_item_id, statement_month, split_quantity, split_amount, updated_by, updated_at, is_deleted) VALUES (?, ?, ?, ?, ?, NOW(), 0) ON DUPLICATE KEY UPDATE split_quantity = VALUES(split_quantity), split_amount = VALUES(split_amount), updated_by = VALUES(updated_by), updated_at = NOW(), is_deleted = 0", itemId, s.getTargetMonth(), s.getSplitQuantity(), s.getSplitAmount(), operator);
                    }
                }
            }
        }
        jdbcTemplate.update("INSERT INTO purchase_statement_month_confirm (supplier_code, statement_month, purchase_confirmed_by, purchase_confirmed_at, status) VALUES (?, ?, ?, NOW(), 'CONFIRMED') ON DUPLICATE KEY UPDATE purchase_confirmed_by = VALUES(purchase_confirmed_by), purchase_confirmed_at = NOW(), status = 'CONFIRMED'", request.getSupplierCode(), request.getMonth(), operator);
        return ResponseResult.success("确认成功");
    }

    private Map<String, Object> loadMonthConfirmInfo(String supplierCode, String month) {
        List<Map<String, Object>> list = jdbcTemplate.queryForList("SELECT * FROM purchase_statement_month_confirm WHERE supplier_code = ? AND statement_month = ?", supplierCode, month);
        if (list.isEmpty()) { Map<String, Object> m = new HashMap<>(); m.put("status", "PENDING"); return m; }
        return list.get(0);
    }

    private void ensureReceiptSplitTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS purchase_statement_receipt_split (id BIGINT PRIMARY KEY AUTO_INCREMENT, receipt_item_id BIGINT NOT NULL, statement_month VARCHAR(7) NOT NULL, split_quantity DECIMAL(18,2) NOT NULL DEFAULT 0, split_amount DECIMAL(18,2) NOT NULL DEFAULT 0, updated_by VARCHAR(64) DEFAULT NULL, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, is_deleted TINYINT(1) NOT NULL DEFAULT 0, UNIQUE KEY uk_receipt_month (receipt_item_id, statement_month), INDEX idx_receipt_item (receipt_item_id), INDEX idx_statement_month (statement_month)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    private String getCurrentUsername() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
    private Long toLong(Object val) { if (val == null) return null; if (val instanceof Number) return ((Number) val).longValue(); try { return Long.parseLong(String.valueOf(val)); } catch (Exception e) { return null; } }
    private BigDecimal toDecimal(Object val) { if (val == null) return BigDecimal.ZERO; if (val instanceof BigDecimal) return (BigDecimal) val; try { return new BigDecimal(String.valueOf(val)); } catch (Exception e) { return BigDecimal.ZERO; } }
}
