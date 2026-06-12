package com.fine.serviceIMPL;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fine.Dao.ArInvoiceItemMapper;
import com.fine.Dao.ArInvoiceMapper;
import com.fine.Dao.BankAccountMapper;
import com.fine.Dao.GlEntryMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.ArInvoice;
import com.fine.modle.ArInvoiceItem;
import com.fine.modle.BankAccount;
import com.fine.modle.GlEntry;
import com.fine.modle.LoginUser;
import com.fine.service.ArService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ArServiceImpl implements ArService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ArInvoiceMapper arInvoiceMapper;

    @Autowired
    private ArInvoiceItemMapper arInvoiceItemMapper;

    @Autowired
    private GlEntryMapper glEntryMapper;

    @Autowired
    private BankAccountMapper bankAccountMapper;

    @Override
    public ResponseResult<?> listInvoices(Map<String, Object> params) {
        String sql = "SELECT a.id, a.invoice_no AS invoiceNo, a.customer_code AS customerCode, " +
                "c.customer_name AS customerName, " +
                "DATE_FORMAT(a.invoice_date, '%Y-%m-%d') AS invoiceDate, " +
                "a.total_amount AS totalAmount, a.status " +
                "FROM ar_invoice a " +
                "LEFT JOIN customers c ON c.customer_code COLLATE utf8mb4_unicode_ci = a.customer_code COLLATE utf8mb4_unicode_ci AND c.is_deleted = 0 " +
                "WHERE a.is_deleted = 0 " +
                "ORDER BY a.invoice_date DESC LIMIT 200";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        return new ResponseResult<>(200, "OK", rows);
    }

    @Override
    public ResponseResult<?> listUnpaidDetails(Map<String, Object> params) {
        ensureOrderPaymentTables();

        final String shipmentExpr = "IFNULL(s.shipment_amount, IFNULL(s.order_amount,0))";
        final String effectiveUnpaidExpr = "GREATEST(" + shipmentExpr + " - IFNULL(s.paid_amount,0), 0)";

        int current = parseInt(params == null ? null : params.get("current"), 1);
        int size = parseInt(params == null ? null : params.get("size"), 20);
        if (current < 1) current = 1;
        if (size < 1) size = 20;
        if (size > 500) size = 500;

        String keyword = params == null ? "" : stringValue(params.get("keyword"));
        String customerCode = params == null ? "" : stringValue(params.get("customerCode"));
        String payStatus = params == null ? "" : stringValue(params.get("payStatus")).toUpperCase();
        String matchStatus = params == null ? "" : stringValue(params.get("matchStatus")).toUpperCase();
        String sortProp = params == null ? "" : stringValue(params.get("sortProp"));
        String sortOrder = params == null ? "" : stringValue(params.get("sortOrder"));

        StringBuilder where = new StringBuilder(" WHERE s.is_deleted = 0 ");
        List<Object> args = new ArrayList<>();

        if (StringUtils.hasText(keyword)) {
            where.append(" AND (s.order_no LIKE ? OR s.customer_code LIKE ?) ");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }

        if (StringUtils.hasText(customerCode)) {
            where.append(" AND s.customer_code = ? ");
            args.add(customerCode.trim());
        }

        if ("UNPAID".equals(payStatus)) {
            where.append(" AND ").append(effectiveUnpaidExpr).append(" > 0 ");
        } else if ("PAID".equals(payStatus)) {
            where.append(" AND ").append(effectiveUnpaidExpr).append(" <= 0 ");
        }

        if ("MATCHED".equals(matchStatus)) {
            where.append(" AND IFNULL(s.order_item_id,0) > 0 ");
        } else if ("UNMATCHED".equals(matchStatus)) {
            where.append(" AND IFNULL(s.order_item_id,0) <= 0 ");
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM finance_ar_order_payment_status s " + where,
                Long.class,
                args.toArray()
        );

        BigDecimal unpaidAmountTotal = jdbcTemplate.queryForObject(
            "SELECT IFNULL(SUM(" + effectiveUnpaidExpr + "),0) FROM finance_ar_order_payment_status s " + where,
            BigDecimal.class,
            args.toArray()
        );

        String dataSql =
            "SELECT s.id, s.customer_code AS customerCode, IFNULL(NULLIF(c.customer_name,''), IFNULL(s.import_customer_name,'')) AS customerName, " +
                "s.order_no AS orderNo, DATE_FORMAT(s.order_date, '%Y-%m-%d') AS orderDate, " +
                "s.order_item_id AS orderItemId, " +
                "s.order_amount AS orderAmount, " + shipmentExpr + " AS shipmentAmount, IFNULL(s.paid_amount,0) AS receiptAmount, s.paid_amount AS paidAmount, " + effectiveUnpaidExpr + " AS unpaidAmount, " +
            "DATE_FORMAT(s.import_shipment_date, '%Y-%m-%d') AS shipmentDate, " +
            "IFNULL(NULLIF(s.import_order_detail, ''), IFNULL(soi.material_code, '')) AS orderDetail, " +
            "IFNULL(NULLIF(s.import_product_name, ''), IFNULL((SELECT tss.product_name FROM tape_spec tss WHERE tss.material_code = soi.material_code LIMIT 1), '')) AS productName, " +
            "IFNULL(NULLIF(s.import_order_spec, ''), IFNULL(NULLIF(CONCAT(IFNULL(soi.thickness, ''), '*', IFNULL(soi.width, ''), '*', IFNULL(soi.length, '')), '**'), '')) AS orderSpec, " +
            "IFNULL(s.import_order_rolls, IFNULL(soi.rolls,0)) AS orderRolls, IFNULL(s.import_order_sqm, IFNULL(soi.sqm,0)) AS orderSqm, IFNULL(s.import_unit_price, IFNULL(soi.unit_price,0)) AS unitPrice, " +
                "CASE WHEN IFNULL(s.order_item_id,0) > 0 THEN 'MATCHED' ELSE 'UNMATCHED' END AS matchStatus, " +
                "CASE WHEN " + effectiveUnpaidExpr + " <= 0 THEN 'PAID' WHEN IFNULL(s.paid_amount,0) > 0 THEN 'PARTIAL' ELSE 'INIT' END AS detailStatus, " +
                "DATE_FORMAT(s.updated_at, '%Y-%m-%d %H:%i:%s') AS updatedAt " +
                "FROM finance_ar_order_payment_status s " +
                "LEFT JOIN customers c ON c.customer_code COLLATE utf8mb4_unicode_ci = s.customer_code COLLATE utf8mb4_unicode_ci AND c.is_deleted = 0 " +
                "LEFT JOIN sales_order_items soi ON soi.id = s.order_item_id AND soi.is_deleted = 0 " +
                where +
            " ORDER BY " + buildUnpaidDetailsOrderBy(sortProp, sortOrder, shipmentExpr, effectiveUnpaidExpr) + " LIMIT ? OFFSET ?";

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((current - 1) * size);

        List<Map<String, Object>> records = jdbcTemplate.queryForList(dataSql, pageArgs.toArray());
        Map<String, Object> data = new HashMap<>();
        data.put("records", records);
        data.put("total", total == null ? 0L : total);
        data.put("unpaidAmountTotal", unpaidAmountTotal == null ? BigDecimal.ZERO : unpaidAmountTotal.setScale(2, RoundingMode.HALF_UP));
        data.put("current", current);
        data.put("size", size);
        return new ResponseResult<>(200, "OK", data);
    }

    private String buildUnpaidDetailsOrderBy(String sortProp, String sortOrder, String shipmentExpr, String effectiveUnpaidExpr) {
        String direction = "descending".equalsIgnoreCase(sortOrder) ? "DESC" : "ASC";
        String column;
        switch (sortProp) {
            case "customerCode":
                column = "s.customer_code";
                break;
            case "customerName":
                column = "IFNULL(NULLIF(c.customer_name,''), IFNULL(s.import_customer_name,''))";
                break;
            case "orderNo":
                column = "s.order_no";
                break;
            case "orderItemId":
                column = "IFNULL(s.order_item_id,0)";
                break;
            case "orderDetail":
                column = "IFNULL(NULLIF(s.import_order_detail, ''), IFNULL(soi.material_code, ''))";
                break;
            case "productName":
                column = "IFNULL(NULLIF(s.import_product_name, ''), IFNULL((SELECT tss.product_name FROM tape_spec tss WHERE tss.material_code = soi.material_code LIMIT 1), ''))";
                break;
            case "orderSpec":
                column = "IFNULL(NULLIF(s.import_order_spec, ''), IFNULL(NULLIF(CONCAT(IFNULL(soi.thickness, ''), '*', IFNULL(soi.width, ''), '*', IFNULL(soi.length, '')), '**'), ''))";
                break;
            case "orderRolls":
                column = "IFNULL(s.import_order_rolls, IFNULL(soi.rolls,0))";
                break;
            case "orderSqm":
                column = "IFNULL(s.import_order_sqm, IFNULL(soi.sqm,0))";
                break;
            case "unitPrice":
                column = "IFNULL(s.import_unit_price, IFNULL(soi.unit_price,0))";
                break;
            case "orderDate":
                column = "s.order_date";
                break;
            case "shipmentDate":
                column = "s.import_shipment_date";
                break;
            case "orderAmount":
                column = "IFNULL(s.order_amount,0)";
                break;
            case "shipmentAmount":
                column = shipmentExpr;
                break;
            case "receiptAmount":
                column = "IFNULL(s.paid_amount,0)";
                break;
            case "paidAmount":
                column = "IFNULL(s.paid_amount,0)";
                break;
            case "unpaidAmount":
                column = effectiveUnpaidExpr;
                break;
            case "detailStatus":
                column = "CASE WHEN " + effectiveUnpaidExpr + " <= 0 THEN 3 WHEN IFNULL(s.paid_amount,0) > 0 THEN 2 ELSE 1 END";
                break;
            case "updatedAt":
                column = "s.updated_at";
                break;
            case "matchStatus":
                column = "CASE WHEN IFNULL(s.order_item_id,0) > 0 THEN 2 ELSE 1 END";
                break;
            default:
                return effectiveUnpaidExpr + " DESC, s.order_date DESC, s.id DESC";
        }
        return column + " " + direction + ", s.id DESC";
    }

    @Override
    public ResponseResult<?> listReceipts(Map<String, Object> params) {
        ensureReceiptTable();
        int current = parseInt(params == null ? null : params.get("current"), 1);
        int size = parseInt(params == null ? null : params.get("size"), 20);
        if (current < 1) current = 1;
        if (size < 1) size = 20;
        if (size > 200) size = 200;
        String keyword = params == null ? "" : stringValue(params.get("keyword"));
        String startDate = params == null ? "" : stringValue(params.get("startDate"));
        String endDate = params == null ? "" : stringValue(params.get("endDate"));
        Boolean onlyFull = parseBoolean(params == null ? null : params.get("onlyFull"));
        Boolean showAll = parseBoolean(params == null ? null : params.get("showAll"));

        String where = " WHERE r.is_deleted = 0 ";
        List<Object> args = new ArrayList<>();
        if (StringUtils.hasText(keyword)) {
            where += " AND (r.customer_code LIKE ? OR IFNULL(r.customer_name,'') LIKE ?) ";
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        if (StringUtils.hasText(startDate)) {
            where += " AND r.pay_date >= ? ";
            args.add(startDate.trim());
        }
        if (StringUtils.hasText(endDate)) {
            where += " AND r.pay_date <= ? ";
            args.add(endDate.trim());
        }
        if (Boolean.TRUE.equals(onlyFull)) {
            where += " AND IFNULL(r.reconcile_status,'UNRECONCILED') = 'FULL' ";
        } else if (!Boolean.TRUE.equals(showAll)) {
            where += " AND IFNULL(r.unallocated_amount, IFNULL(r.amount,0)) > 0 ";
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM finance_ar_receipt r " + where,
                Long.class,
                args.toArray()
        );

        String dataSql = "SELECT r.id, r.customer_code AS customerCode, " +
                "IFNULL(NULLIF(c.customer_name,''), IFNULL(r.customer_name, '')) AS customerName, " +
                "r.amount AS receiptAmount, " +
            "r.bank_account_id AS bankAccountId, " +
            "IFNULL(r.bank_name, '') AS bankName, " +
            "IFNULL(r.bank_account, '') AS bankAccount, " +
                "CONCAT(IFNULL(r.bank_name,''), ' ', IFNULL(r.bank_account,'')) AS paymentAccount, " +
                "DATE_FORMAT(r.pay_date, '%Y-%m-%d') AS payDate, " +
                "IFNULL(r.reconcile_status, 'UNRECONCILED') AS reconcileStatus, " +
                "IFNULL(r.allocated_amount, 0) AS allocatedAmount, " +
                "IFNULL(r.unallocated_amount, IFNULL(r.amount,0)) AS unallocatedAmount, " +
                "DATE_FORMAT(r.reconciled_at, '%Y-%m-%d %H:%i:%s') AS reconciledAt, " +
                "IFNULL(r.reconciled_by, '') AS reconciledBy, " +
            "IFNULL(NULLIF(r.created_by, ''), IFNULL(r.registrar, '')) AS registrar " +
                "FROM finance_ar_receipt r " +
                "LEFT JOIN customers c ON c.customer_code COLLATE utf8mb4_unicode_ci = r.customer_code COLLATE utf8mb4_unicode_ci AND c.is_deleted = 0 " +
                where +
                " ORDER BY r.pay_date DESC, r.id DESC LIMIT ? OFFSET ?";

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((current - 1) * size);
        List<Map<String, Object>> records = jdbcTemplate.queryForList(dataSql, pageArgs.toArray());

        Map<String, Object> data = new HashMap<>();
        data.put("records", records);
        data.put("total", total == null ? 0L : total);
        data.put("current", current);
        data.put("size", size);
        return new ResponseResult<>(200, "OK", data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> createReceipt(Map<String, Object> payload) {
        ensureReceiptTable();
        ensureOrderPaymentTables();
        if (payload == null) {
            return new ResponseResult<>(400, "payload required", null);
        }
        String customerCode = stringValue(payload.get("customerCode"));
        Long bankAccountId = parseLong(payload.get("bankAccountId"));
        BigDecimal amount = toDecimal(payload.get("amount"));
        String payDateText = stringValue(payload.get("payDate"));

        if (!StringUtils.hasText(customerCode) || amount == null) {
            return new ResponseResult<>(400, "缺少必填字段", null);
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return new ResponseResult<>(400, "收款金额必须大于0", null);
        }

        Date payDate;
        try {
            payDate = StringUtils.hasText(payDateText) ? Date.valueOf(payDateText) : Date.valueOf(LocalDate.now());
        } catch (Exception ex) {
            return new ResponseResult<>(400, "付款日期格式错误", null);
        }

        Map<String, Object> customerInfo = jdbcTemplate.query(
                "SELECT customer_name, bank_name, bank_account FROM customers WHERE is_deleted = 0 AND customer_code = ? LIMIT 1",
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    Map<String, Object> m = new HashMap<>();
                    m.put("customerName", rs.getString("customer_name"));
                    m.put("bankName", rs.getString("bank_name"));
                    m.put("bankAccount", rs.getString("bank_account"));
                    return m;
                },
                customerCode
        );
        if (customerInfo == null) {
            return new ResponseResult<>(400, "客户不存在", null);
        }

        String customerName = stringValue(customerInfo.get("customerName"));
        String bankName = stringValue(customerInfo.get("bankName"));
        String bankNo = stringValue(customerInfo.get("bankAccount"));

        if (bankAccountId != null) {
            BankAccount bankAccount = bankAccountMapper.selectOne(new LambdaQueryWrapper<BankAccount>()
                    .eq(BankAccount::getId, bankAccountId)
                    .eq(BankAccount::getIsDeleted, 0)
                    .last("LIMIT 1"));
            if (bankAccount == null) {
                return new ResponseResult<>(400, "付款账户不存在", null);
            }
            bankName = bankAccount.getBankName();
            bankNo = bankAccount.getAccountNo();
        }
        final String finalBankName = bankName;
        final String finalBankNo = bankNo;

        Timestamp now = new Timestamp(System.currentTimeMillis());
        String creator = getCurrentUsername();
        final String finalRegistrar = creator;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO finance_ar_receipt(" +
                    "customer_code, customer_name, amount, bank_account_id, bank_name, bank_account, pay_date, registrar, created_by, created_at, updated_at, is_deleted" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, customerCode);
            ps.setString(2, customerName);
            ps.setBigDecimal(3, amount.setScale(2, RoundingMode.HALF_UP));
            if (bankAccountId == null) {
                ps.setNull(4, java.sql.Types.BIGINT);
            } else {
                ps.setLong(4, bankAccountId);
            }
            ps.setString(5, finalBankName);
            ps.setString(6, finalBankNo);
            ps.setDate(7, payDate);
            ps.setString(8, finalRegistrar);
            ps.setString(9, creator);
            ps.setTimestamp(10, now);
            ps.setTimestamp(11, now);
            return ps;
        }, keyHolder);

        Number generatedKey = keyHolder.getKey();
        Long receiptId = generatedKey == null ? null : generatedKey.longValue();
        Map<String, Object> allocation = allocateReceiptToOrders(
            customerCode,
            receiptId,
            amount.setScale(2, RoundingMode.HALF_UP),
            payDate,
            creator
        );

        BigDecimal allocatedAmount = normalizeMoney(toDecimal(allocation.get("allocatedAmount")));
        BigDecimal unallocatedAmount = normalizeMoney(toDecimal(allocation.get("unallocatedAmount")));
        String reconcileStatus = determineReconcileStatus(
            amount.setScale(2, RoundingMode.HALF_UP),
            allocatedAmount,
            unallocatedAmount
        );
        jdbcTemplate.update(
            "UPDATE finance_ar_receipt SET allocated_amount = ?, unallocated_amount = ?, reconcile_status = ?, reconciled_at = NOW(), reconciled_by = ?, updated_at = NOW() WHERE id = ?",
            allocatedAmount,
            unallocatedAmount,
            reconcileStatus,
            creator,
            receiptId
        );

        Map<String, Object> result = new HashMap<>();
        result.put("receiptId", receiptId);
        result.put("reconcileStatus", reconcileStatus);
        result.putAll(allocation);
        return new ResponseResult<>(200, "创建收款成功", result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> reconcileReceipt(Long id) {
        System.err.println("AR_DEBUG: reconcileReceipt START for id=" + id);
        ensureReceiptTable();
        ensureOrderPaymentTables();
        if (id == null || id <= 0) {
            return new ResponseResult<>(400, "id required", null);
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, customer_code AS customerCode, amount, pay_date AS payDate, " +
                "IFNULL(allocated_amount,0) AS allocatedAmount, IFNULL(unallocated_amount, IFNULL(amount,0)) AS unallocatedAmount " +
                "FROM finance_ar_receipt WHERE id = ? AND is_deleted = 0 LIMIT 1",
            id
        );
        if (rows.isEmpty()) {
            System.err.println("AR_DEBUG: Receipt not found or deleted for id=" + id);
            return new ResponseResult<>(404, "收款记录不存在", null);
        }

        String operator = getCurrentUsername();
        Map<String, Object> summary = reconcileOneReceipt(rows.get(0), operator);
        BigDecimal allocatedNow = normalizeMoney(toDecimal(summary.get("allocatedNow")));
        System.err.println("AR_DEBUG: reconcileOneReceipt returned allocatedNow=" + allocatedNow);
        if (allocatedNow.compareTo(BigDecimal.ZERO) <= 0) {
            return new ResponseResult<>(200, "无需扣账（暂无可分配未收金额）", summary);
        }
        return new ResponseResult<>(200, "收款扣账成功", summary);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> reverseReceipt(Long id) {
        ensureReceiptTable();
        ensureOrderPaymentTables();
        if (id == null || id <= 0) {
            return new ResponseResult<>(400, "id required", null);
        }

        List<Map<String, Object>> receiptRows = jdbcTemplate.queryForList(
                "SELECT id, customer_code AS customerCode, IFNULL(amount,0) AS amount, " +
                        "IFNULL(allocated_amount,0) AS allocatedAmount, IFNULL(unallocated_amount, IFNULL(amount,0)) AS unallocatedAmount, " +
                        "IFNULL(reconcile_status,'UNRECONCILED') AS reconcileStatus " +
                        "FROM finance_ar_receipt WHERE id = ? AND is_deleted = 0 LIMIT 1",
                id
        );
        if (receiptRows.isEmpty()) {
            return new ResponseResult<>(404, "收款记录不存在", null);
        }

        Map<String, Object> receipt = receiptRows.get(0);
        BigDecimal amount = normalizeMoney(toDecimal(receipt.get("amount")));
        BigDecimal allocatedAmount = normalizeMoney(toDecimal(receipt.get("allocatedAmount")));
        if (amount == null) {
            amount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (allocatedAmount == null || allocatedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            jdbcTemplate.update(
                    "UPDATE finance_ar_receipt SET allocated_amount = 0, unallocated_amount = IFNULL(amount,0), reconcile_status = 'REVERSED', reconciled_at = NOW(), reconciled_by = ?, updated_at = NOW() WHERE id = ?",
                    getCurrentUsername(),
                    id
            );
            Map<String, Object> data = new HashMap<>();
            data.put("receiptId", id);
            data.put("reversedAmount", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            data.put("affectedOrders", 0);
            return new ResponseResult<>(200, "收款单无已扣金额，已标记为冲销", data);
        }

        List<Map<String, Object>> allocations = jdbcTemplate.queryForList(
                "SELECT order_no AS orderNo, IFNULL(SUM(allocation_amount),0) AS allocAmount " +
                        "FROM finance_ar_order_payment_allocation WHERE receipt_id = ? GROUP BY order_no",
                id
        );

        Set<String> touchedOrders = new HashSet<>();
        for (Map<String, Object> alloc : allocations) {
            String orderNo = stringValue(alloc.get("orderNo"));
            BigDecimal rollbackAmount = normalizeMoney(toDecimal(alloc.get("allocAmount")));
            if (!StringUtils.hasText(orderNo) || rollbackAmount == null || rollbackAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            rollbackOrderPaymentAllocation(orderNo, rollbackAmount);
            touchedOrders.add(orderNo);
        }

        Map<String, Object> historyRollback = rollbackHistoryByReceipt(id, getCurrentUsername());
        int affectedHistoryRows = parseInt(historyRollback.get("affectedRows"), 0);

        jdbcTemplate.update("DELETE FROM finance_ar_order_payment_allocation WHERE receipt_id = ?", id);
        jdbcTemplate.update(
                "UPDATE finance_ar_receipt SET allocated_amount = 0, unallocated_amount = IFNULL(amount,0), reconcile_status = 'REVERSED', reconciled_at = NOW(), reconciled_by = ?, updated_at = NOW() WHERE id = ?",
                getCurrentUsername(),
                id
        );

        String operator = getCurrentUsername();
        for (String orderNo : touchedOrders) {
            syncSalesOrderPaymentLifecycle(orderNo, null, null, operator);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("receiptId", id);
        data.put("reversedAmount", allocatedAmount);
        data.put("affectedOrders", touchedOrders.size());
        data.put("affectedHistoryRows", affectedHistoryRows);
        return new ResponseResult<>(200, "冲销成功", data);
    }

    private void rollbackOrderPaymentAllocation(String orderNo, BigDecimal rollbackAmount) {
        BigDecimal remain = normalizeMoney(rollbackAmount);
        if (!StringUtils.hasText(orderNo) || remain == null || remain.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, IFNULL(paid_amount,0) AS paidAmount, " +
                        "GREATEST(IFNULL(shipment_amount, IFNULL(order_amount,0)) - IFNULL(paid_amount,0), 0) AS unpaidAmount " +
                        "FROM finance_ar_order_payment_status " +
                        "WHERE is_deleted = 0 AND order_no = ? AND IFNULL(paid_amount,0) > 0 " +
                        "ORDER BY IFNULL(import_shipment_date, order_date) DESC, id DESC",
                orderNo
        );

        for (Map<String, Object> row : rows) {
            if (remain.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            Long rowId = parseLong(row.get("id"));
            BigDecimal paid = normalizeMoney(toDecimal(row.get("paidAmount")));
            BigDecimal unpaid = normalizeMoney(toDecimal(row.get("unpaidAmount")));
            if (rowId == null || paid == null || unpaid == null || paid.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal rollback = paid.min(remain);
            BigDecimal newPaid = normalizeMoney(paid.subtract(rollback));
            BigDecimal newUnpaid = normalizeMoney(unpaid.add(rollback));
            int paidFlag = newUnpaid.compareTo(BigDecimal.ZERO) <= 0 ? 1 : 0;

            jdbcTemplate.update(
                    "UPDATE finance_ar_order_payment_status SET paid_amount = ?, unpaid_amount = ?, is_paid = ?, updated_at = NOW() WHERE id = ?",
                    newPaid,
                    newUnpaid,
                    paidFlag,
                    rowId
            );

            remain = normalizeMoney(remain.subtract(rollback));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> batchReconcileReceipts(Map<String, Object> payload) {
        ensureReceiptTable();
        ensureOrderPaymentTables();

        int limit = parseInt(payload == null ? null : payload.get("limit"), 2000);
        if (limit < 1) limit = 1;
        if (limit > 10000) limit = 10000;

        List<Map<String, Object>> receipts = jdbcTemplate.queryForList(
            "SELECT id, customer_code AS customerCode, amount, pay_date AS payDate, " +
                "IFNULL(allocated_amount,0) AS allocatedAmount, IFNULL(unallocated_amount, IFNULL(amount,0)) AS unallocatedAmount " +
                "FROM finance_ar_receipt " +
                "WHERE is_deleted = 0 AND IFNULL(amount,0) > 0 AND IFNULL(unallocated_amount, IFNULL(amount,0)) > 0 " +
                "ORDER BY pay_date ASC, id ASC LIMIT ?",
            limit
        );

        String operator = getCurrentUsername();
        int processed = 0;
        int skipped = 0;
        int affectedOrders = 0;
        BigDecimal totalAllocatedNow = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (Map<String, Object> receipt : receipts) {
            Map<String, Object> summary = reconcileOneReceipt(receipt, operator);
            BigDecimal allocatedNow = normalizeMoney(toDecimal(summary.get("allocatedNow")));
            Integer affected = parseInt(summary.get("affectedOrders"), 0);
            if (allocatedNow.compareTo(BigDecimal.ZERO) > 0) {
                processed++;
                totalAllocatedNow = normalizeMoney(totalAllocatedNow.add(allocatedNow));
                affectedOrders += affected == null ? 0 : affected;
            } else {
                skipped++;
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("totalCandidates", receipts.size());
        data.put("processedReceipts", processed);
        data.put("skippedReceipts", skipped);
        data.put("affectedOrders", affectedOrders);
        data.put("allocatedAmount", totalAllocatedNow);
        return new ResponseResult<>(200, "批量扣账完成", data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> batchReconcileReceiptHistory(Map<String, Object> payload) {
        ensureReceiptTable();
        ensureHistoryAllocationTable();
        if (!tableExists("sales_statement_history")) {
            return new ResponseResult<>(400, "历史欠账台账不存在", null);
        }

        String startDateText = payload == null ? "" : stringValue(payload.get("startDate"));
        String endDateText = payload == null ? "" : stringValue(payload.get("endDate"));
        if (!StringUtils.hasText(startDateText)) {
            return new ResponseResult<>(400, "startDate required", null);
        }

        Date startDate;
        Date endDate = null;
        try {
            startDate = Date.valueOf(startDateText.trim());
            if (StringUtils.hasText(endDateText)) {
                endDate = Date.valueOf(endDateText.trim());
            }
        } catch (Exception ex) {
            return new ResponseResult<>(400, "日期格式错误", null);
        }

        int limit = parseInt(payload == null ? null : payload.get("limit"), 5000);
        if (limit < 1) limit = 1;
        if (limit > 20000) limit = 20000;

        StringBuilder sql = new StringBuilder(
            "SELECT id, customer_code AS customerCode, IFNULL(amount,0) AS amount, pay_date AS payDate, " +
                "IFNULL(allocated_amount,0) AS allocatedAmount, IFNULL(unallocated_amount, IFNULL(amount,0)) AS unallocatedAmount " +
                "FROM finance_ar_receipt WHERE is_deleted = 0 AND IFNULL(amount,0) > 0 AND pay_date >= ?"
        );
        List<Object> params = new ArrayList<>();
        params.add(startDate);
        if (endDate != null) {
            sql.append(" AND pay_date <= ?");
            params.add(endDate);
        }
        sql.append(" ORDER BY pay_date ASC, id ASC LIMIT ?");
        params.add(limit);

        List<Map<String, Object>> receipts = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        String operator = getCurrentUsername();
        int processed = 0;
        int rolledBack = 0;
        int reallocated = 0;
        int affectedHistoryRows = 0;
        BigDecimal totalRollbackAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAllocatedNow = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (Map<String, Object> receipt : receipts) {
            Long receiptId = parseLong(receipt.get("id"));
            if (receiptId == null || receiptId <= 0) {
                continue;
            }

            String customerCode = stringValue(receipt.get("customerCode"));
            BigDecimal amount = normalizeMoney(toDecimal(receipt.get("amount")));
            BigDecimal allocatedAmount = normalizeMoney(toDecimal(receipt.get("allocatedAmount")));

            Map<String, Object> rollback = rollbackHistoryByReceipt(receiptId, operator);
            BigDecimal rollbackAmount = normalizeMoney(toDecimal(rollback.get("rollbackAmount")));
            int rollbackRows = parseInt(rollback.get("affectedRows"), 0);
            if (rollbackAmount.compareTo(BigDecimal.ZERO) > 0) {
                rolledBack++;
                totalRollbackAmount = normalizeMoney(totalRollbackAmount.add(rollbackAmount));
            }
            affectedHistoryRows += rollbackRows;

            BigDecimal allocatedAfterRollback = normalizeMoney(allocatedAmount.subtract(rollbackAmount));
            if (allocatedAfterRollback.compareTo(BigDecimal.ZERO) < 0) {
                allocatedAfterRollback = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            BigDecimal unallocatedAfterRollback = normalizeMoney(amount.subtract(allocatedAfterRollback));
            if (unallocatedAfterRollback.compareTo(BigDecimal.ZERO) < 0) {
                unallocatedAfterRollback = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }

            String reconcileStatus = determineReconcileStatus(amount, allocatedAfterRollback, unallocatedAfterRollback);
            jdbcTemplate.update(
                "UPDATE finance_ar_receipt SET allocated_amount = ?, unallocated_amount = ?, reconcile_status = ?, reconciled_at = NOW(), reconciled_by = ?, updated_at = NOW() WHERE id = ?",
                allocatedAfterRollback,
                unallocatedAfterRollback,
                reconcileStatus,
                operator,
                receiptId
            );

            if (unallocatedAfterRollback.compareTo(BigDecimal.ZERO) > 0 && StringUtils.hasText(customerCode)) {
                Map<String, Object> historyAlloc = allocateReceiptToHistoryWithResult(customerCode, receiptId, unallocatedAfterRollback, operator);
                BigDecimal allocatedNow = normalizeMoney(toDecimal(historyAlloc.get("allocatedAmount")));
                if (allocatedNow.compareTo(BigDecimal.ZERO) > 0) {
                    reallocated++;
                    totalAllocatedNow = normalizeMoney(totalAllocatedNow.add(allocatedNow));
                }
                BigDecimal allocatedTotal = normalizeMoney(allocatedAfterRollback.add(allocatedNow));
                BigDecimal unallocatedTotal = normalizeMoney(unallocatedAfterRollback.subtract(allocatedNow));
                if (unallocatedTotal.compareTo(BigDecimal.ZERO) < 0) {
                    unallocatedTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                }
                String finalStatus = determineReconcileStatus(amount, allocatedTotal, unallocatedTotal);
                jdbcTemplate.update(
                    "UPDATE finance_ar_receipt SET allocated_amount = ?, unallocated_amount = ?, reconcile_status = ?, reconciled_at = NOW(), reconciled_by = ?, updated_at = NOW() WHERE id = ?",
                    allocatedTotal,
                    unallocatedTotal,
                    finalStatus,
                    operator,
                    receiptId
                );
            }

            processed++;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("totalCandidates", receipts.size());
        data.put("processedReceipts", processed);
        data.put("rolledBackReceipts", rolledBack);
        data.put("reallocatedReceipts", reallocated);
        data.put("affectedHistoryRows", affectedHistoryRows);
        data.put("rollbackAmount", totalRollbackAmount);
        data.put("allocatedAmount", totalAllocatedNow);
        return new ResponseResult<>(200, "历史欠款批量回退重扣完成", data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> updateReceipt(Map<String, Object> payload) {
        ensureReceiptTable();
        if (payload == null) {
            return new ResponseResult<>(400, "payload required", null);
        }
        Long id = parseLong(payload.get("id"));
        if (id == null || id <= 0) {
            return new ResponseResult<>(400, "id required", null);
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, IFNULL(reconcile_status, 'UNRECONCILED') AS reconcileStatus, IFNULL(allocated_amount,0) AS allocatedAmount " +
                "FROM finance_ar_receipt WHERE id = ? AND is_deleted = 0 LIMIT 1",
                id
        );
        if (rows.isEmpty()) {
            return new ResponseResult<>(404, "收款记录不存在", null);
        }
        Map<String, Object> currentReceipt = rows.get(0);
        String reconcileStatus = stringValue(currentReceipt.get("reconcileStatus"));
        BigDecimal allocatedAmount = normalizeMoney(toDecimal(currentReceipt.get("allocatedAmount")));
        if (("FULL".equalsIgnoreCase(reconcileStatus) || "PARTIAL".equalsIgnoreCase(reconcileStatus))
            && allocatedAmount != null
            && allocatedAmount.compareTo(BigDecimal.ZERO) > 0) {
            return new ResponseResult<>(400, "该收款单已扣账，请先冲销后再修改", null);
        }

        String customerCode = stringValue(payload.get("customerCode"));
        Long bankAccountId = parseLong(payload.get("bankAccountId"));
        BigDecimal amount = toDecimal(payload.get("amount"));
        String payDateText = stringValue(payload.get("payDate"));

        if (!StringUtils.hasText(customerCode) || amount == null) {
            return new ResponseResult<>(400, "缺少必填字段", null);
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return new ResponseResult<>(400, "收款金额必须大于0", null);
        }

        Date payDate;
        try {
            payDate = StringUtils.hasText(payDateText) ? Date.valueOf(payDateText) : Date.valueOf(LocalDate.now());
        } catch (Exception ex) {
            return new ResponseResult<>(400, "付款日期格式错误", null);
        }

        Map<String, Object> customerInfo = jdbcTemplate.query(
                "SELECT customer_name, bank_name, bank_account FROM customers WHERE is_deleted = 0 AND customer_code = ? LIMIT 1",
                rs -> {
                    if (!rs.next()) {
                        return null;
                    }
                    Map<String, Object> m = new HashMap<>();
                    m.put("customerName", rs.getString("customer_name"));
                    m.put("bankName", rs.getString("bank_name"));
                    m.put("bankAccount", rs.getString("bank_account"));
                    return m;
                },
                customerCode
        );
        if (customerInfo == null) {
            return new ResponseResult<>(400, "客户不存在", null);
        }

        String customerName = stringValue(customerInfo.get("customerName"));
        String bankName = stringValue(customerInfo.get("bankName"));
        String bankNo = stringValue(customerInfo.get("bankAccount"));

        if (bankAccountId != null) {
            BankAccount bankAccount = bankAccountMapper.selectOne(new LambdaQueryWrapper<BankAccount>()
                    .eq(BankAccount::getId, bankAccountId)
                    .eq(BankAccount::getIsDeleted, 0)
                    .last("LIMIT 1"));
            if (bankAccount == null) {
                return new ResponseResult<>(400, "付款账户不存在", null);
            }
            bankName = bankAccount.getBankName();
            bankNo = bankAccount.getAccountNo();
        }

        String operator = getCurrentUsername();
        jdbcTemplate.update(
                "UPDATE finance_ar_receipt SET customer_code = ?, customer_name = ?, amount = ?, bank_account_id = ?, bank_name = ?, bank_account = ?, pay_date = ?, updated_at = NOW() " +
                        "WHERE id = ? AND is_deleted = 0",
                customerCode,
                customerName,
                amount.setScale(2, RoundingMode.HALF_UP),
                bankAccountId,
                bankName,
                bankNo,
                payDate,
                id
        );

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("updatedBy", operator);
        return new ResponseResult<>(200, "修改收款成功", result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> deleteReceipt(Long id) {
        ensureReceiptTable();
        if (id == null || id <= 0) {
            return new ResponseResult<>(400, "id required", null);
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT IFNULL(reconcile_status, 'UNRECONCILED') AS reconcileStatus, IFNULL(allocated_amount,0) AS allocatedAmount " +
                "FROM finance_ar_receipt WHERE id = ? AND is_deleted = 0 LIMIT 1",
            id
        );
        if (rows.isEmpty()) {
            return new ResponseResult<>(404, "收款记录不存在", null);
        }

        Map<String, Object> currentReceipt = rows.get(0);
        String reconcileStatus = stringValue(currentReceipt.get("reconcileStatus"));
        BigDecimal allocatedAmount = normalizeMoney(toDecimal(currentReceipt.get("allocatedAmount")));
        if (("FULL".equalsIgnoreCase(reconcileStatus) || "PARTIAL".equalsIgnoreCase(reconcileStatus))
            && allocatedAmount != null
            && allocatedAmount.compareTo(BigDecimal.ZERO) > 0) {
            return new ResponseResult<>(400, "该收款单已扣账，请先冲销后再删除", null);
        }

        int affected = jdbcTemplate.update(
                "UPDATE finance_ar_receipt SET is_deleted = 1, updated_at = NOW() WHERE id = ? AND is_deleted = 0",
                id
        );
        if (affected <= 0) {
            return new ResponseResult<>(404, "收款记录不存在", null);
        }
        return new ResponseResult<>(200, "删除收款成功", null);
    }

    private Map<String, Object> reconcileOneReceipt(Map<String, Object> receipt,
                                                    String operator) {
        Long receiptId = parseLong(receipt.get("id"));
        String customerCode = stringValue(receipt.get("customerCode"));
        BigDecimal amount = normalizeMoney(toDecimal(receipt.get("amount")));
        System.err.println("AR_DEBUG: reconcileOneReceipt for ID=" + receiptId + " customer=" + customerCode + " amount=" + amount);
        Date payDate = receipt.get("payDate") instanceof Date ? (Date) receipt.get("payDate") : Date.valueOf(LocalDate.now());
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            System.err.println("AR_DEBUG: amount <= 0, SKIPPING");
            Map<String, Object> empty = new HashMap<>();
            empty.put("receiptId", receiptId);
            empty.put("allocatedNow", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            empty.put("affectedOrders", 0);
            empty.put("allocatedAmount", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            empty.put("unallocatedAmount", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            empty.put("reconcileStatus", "UNRECONCILED");
            return empty;
        }

        BigDecimal allocatedExisting = normalizeMoney(toDecimal(receipt.get("allocatedAmount")));
        BigDecimal remaining = normalizeMoney(toDecimal(receipt.get("unallocatedAmount")));
        System.err.println("AR_DEBUG: allocatedExisting=" + allocatedExisting + " remaining=" + remaining);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            remaining = normalizeMoney(amount.subtract(allocatedExisting));
            System.err.println("AR_DEBUG: adjusted remaining to amount-allocatedExisting=" + remaining);
        }
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            remaining = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal remainingForOrders = remaining;
        int affectedHistoryRows = 0;
        BigDecimal historyAllocatedTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        // 1. 优先冲销历史欠款 (History First)
        if (remaining.compareTo(BigDecimal.ZERO) > 0 && StringUtils.hasText(customerCode)) {
            System.err.println("AR_DEBUG: Trying history allocation first for " + customerCode);
            Map<String, Object> historyAlloc = allocateReceiptToHistoryWithResult(customerCode, receiptId, remaining, operator);
            historyAllocatedTotal = normalizeMoney(toDecimal(historyAlloc.get("allocatedAmount")));
            affectedHistoryRows = parseInt(historyAlloc.get("affectedRows"), 0);
            remainingForOrders = normalizeMoney(remaining.subtract(historyAllocatedTotal));
            System.err.println("AR_DEBUG: History allocated " + historyAllocatedTotal + ", remaining for orders: " + remainingForOrders);
        }

        // 2. 剩余金额冲销当前订单 (Current Orders)
        Map<String, Object> allocation;
        if (remainingForOrders.compareTo(BigDecimal.ZERO) > 0 && StringUtils.hasText(customerCode)) {
            System.err.println("AR_DEBUG: Calling allocateReceiptToOrders for " + customerCode + " with remaining " + remainingForOrders);
            allocation = allocateReceiptToOrders(customerCode, receiptId, remainingForOrders, payDate, operator);
        } else {
            allocation = new HashMap<>();
            allocation.put("allocatedAmount", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            allocation.put("unallocatedAmount", remainingForOrders);
            allocation.put("affectedOrders", 0);
        }

        BigDecimal orderAllocatedNow = normalizeMoney(toDecimal(allocation.get("allocatedAmount")));
        BigDecimal allocatedNow = normalizeMoney(historyAllocatedTotal.add(orderAllocatedNow));
        
        System.err.println("AR_DEBUG: Total allocated now (Hist+Order)=" + allocatedNow);
        BigDecimal allocatedTotal = normalizeMoney(allocatedExisting.add(allocatedNow));
        BigDecimal unallocatedTotal = normalizeMoney(remaining.subtract(allocatedNow));
        if (unallocatedTotal.compareTo(BigDecimal.ZERO) < 0) {
            unallocatedTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        String reconcileStatus = determineReconcileStatus(amount, allocatedTotal, unallocatedTotal);
        System.err.println("AR_DEBUG: Final allocatedTotal=" + allocatedTotal + " unallocatedTotal=" + unallocatedTotal + " status=" + reconcileStatus);

        jdbcTemplate.update(
            "UPDATE finance_ar_receipt SET allocated_amount = ?, unallocated_amount = ?, reconcile_status = ?, reconciled_at = NOW(), reconciled_by = ?, updated_at = NOW() WHERE id = ?",
            allocatedTotal,
            unallocatedTotal,
            reconcileStatus,
            operator,
            receiptId
        );

        Map<String, Object> result = new HashMap<>();
        result.put("receiptId", receiptId);
        result.put("allocatedNow", allocatedNow);
        result.put("allocatedAmount", allocatedTotal);
        result.put("unallocatedAmount", unallocatedTotal);
        result.put("affectedOrders", parseInt(allocation.get("affectedOrders"), 0));
        result.put("affectedHistoryRows", affectedHistoryRows);
        result.put("reconcileStatus", reconcileStatus);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> reconcileReceiptHistory(Long id, Map<String, Object> payload) {
        ensureReceiptTable();
        ensureHistoryAllocationTable();
        if (!tableExists("sales_statement_history")) {
            return new ResponseResult<>(400, "历史欠账台账不存在", null);
        }
        if (id == null || id <= 0) {
            return new ResponseResult<>(400, "id required", null);
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, customer_code AS customerCode, IFNULL(amount,0) AS amount, " +
                "IFNULL(allocated_amount,0) AS allocatedAmount, IFNULL(unallocated_amount, IFNULL(amount,0)) AS unallocatedAmount " +
                "FROM finance_ar_receipt WHERE id = ? AND is_deleted = 0 LIMIT 1",
            id
        );
        if (rows.isEmpty()) {
            return new ResponseResult<>(404, "收款记录不存在", null);
        }

        Map<String, Object> receipt = rows.get(0);
        String customerCode = stringValue(receipt.get("customerCode"));
        if (!StringUtils.hasText(customerCode)) {
            return new ResponseResult<>(400, "客户信息缺失", null);
        }

        BigDecimal amount = normalizeMoney(toDecimal(receipt.get("amount")));
        BigDecimal allocatedExisting = normalizeMoney(toDecimal(receipt.get("allocatedAmount")));
        BigDecimal remaining = normalizeMoney(toDecimal(receipt.get("unallocatedAmount")));
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("receiptId", id);
            empty.put("allocatedNow", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            empty.put("allocatedAmount", allocatedExisting);
            empty.put("unallocatedAmount", remaining);
            return new ResponseResult<>(200, "无需扣历史欠款（无可用余额）", empty);
        }

        BigDecimal requested = payload == null ? null : normalizeMoney(toDecimal(payload.get("amount")));
        BigDecimal allocateAmount = (requested == null || requested.compareTo(BigDecimal.ZERO) <= 0)
            ? remaining
            : requested.min(remaining);
        if (allocateAmount.compareTo(BigDecimal.ZERO) <= 0) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("receiptId", id);
            empty.put("allocatedNow", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            empty.put("allocatedAmount", allocatedExisting);
            empty.put("unallocatedAmount", remaining);
            return new ResponseResult<>(200, "无需扣历史欠款（可用余额为0）", empty);
        }

        String operator = getCurrentUsername();
        Map<String, Object> historyAlloc = allocateReceiptToHistoryWithResult(customerCode, id, allocateAmount, operator);
        BigDecimal allocatedNow = normalizeMoney(toDecimal(historyAlloc.get("allocatedAmount")));
        BigDecimal allocatedTotal = normalizeMoney(allocatedExisting.add(allocatedNow));
        BigDecimal unallocatedTotal = normalizeMoney(remaining.subtract(allocatedNow));
        if (unallocatedTotal.compareTo(BigDecimal.ZERO) < 0) {
            unallocatedTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        String reconcileStatus = determineReconcileStatus(amount, allocatedTotal, unallocatedTotal);

        jdbcTemplate.update(
            "UPDATE finance_ar_receipt SET allocated_amount = ?, unallocated_amount = ?, reconcile_status = ?, reconciled_at = NOW(), reconciled_by = ?, updated_at = NOW() WHERE id = ?",
            allocatedTotal,
            unallocatedTotal,
            reconcileStatus,
            operator,
            id
        );

        Map<String, Object> result = new HashMap<>();
        result.put("receiptId", id);
        result.put("allocatedNow", allocatedNow);
        result.put("allocatedAmount", allocatedTotal);
        result.put("unallocatedAmount", unallocatedTotal);
        result.put("affectedHistoryRows", parseInt(historyAlloc.get("affectedRows"), 0));
        result.put("reconcileStatus", reconcileStatus);
        return new ResponseResult<>(200, "历史欠款扣账成功", result);
    }

    private Map<String, Object> allocateReceiptToHistoryWithResult(String customerCode, Long receiptId, BigDecimal allocateAmount, String operator) {
        Map<String, Object> result = new HashMap<>();
        result.put("allocatedAmount", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        result.put("affectedRows", 0);
        if (!StringUtils.hasText(customerCode) || receiptId == null || allocateAmount == null || allocateAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return result;
        }
        ensureHistoryAllocationTable();
        if (!tableExists("sales_statement_history")) {
            return result;
        }

        BigDecimal remain = normalizeMoney(allocateAmount);
        int affected = 0;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, statement_month AS statementMonth, IFNULL(unpaid_amount,0) AS unpaidAmount " +
                        "FROM sales_statement_history WHERE is_deleted = 0 AND customer_code = ? AND IFNULL(unpaid_amount,0) > 0 " +
                        "ORDER BY statement_month ASC, id ASC",
                customerCode
        );

        for (Map<String, Object> row : rows) {
            if (remain.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            Long historyId = parseLong(row.get("id"));
            String statementMonth = stringValue(row.get("statementMonth"));
            BigDecimal unpaid = normalizeMoney(toDecimal(row.get("unpaidAmount")));
            if (historyId == null || unpaid == null || unpaid.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal alloc = remain.min(unpaid);
            BigDecimal newUnpaid = normalizeMoney(unpaid.subtract(alloc));

            jdbcTemplate.update(
                    "UPDATE sales_statement_history SET unpaid_amount = ?, updated_by = ?, updated_at = NOW() WHERE id = ? AND is_deleted = 0",
                    newUnpaid,
                    operator,
                    historyId
            );
            jdbcTemplate.update(
                    "INSERT INTO finance_ar_history_allocation(receipt_id, history_id, customer_code, statement_month, allocation_amount, created_by, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, NOW())",
                    receiptId,
                    historyId,
                    customerCode,
                    statementMonth,
                    alloc,
                    operator
            );

            remain = normalizeMoney(remain.subtract(alloc));
            affected++;
        }

        BigDecimal allocated = normalizeMoney(allocateAmount.subtract(remain));
        result.put("allocatedAmount", allocated);
        result.put("affectedRows", affected);
        return result;
    }

    private Map<String, Object> rollbackHistoryByReceipt(Long receiptId, String operator) {
        Map<String, Object> result = new HashMap<>();
        result.put("affectedRows", 0);
        result.put("rollbackAmount", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

        if (receiptId == null || receiptId <= 0) {
            return result;
        }
        ensureHistoryAllocationTable();
        if (!tableExists("sales_statement_history")) {
            jdbcTemplate.update("DELETE FROM finance_ar_history_allocation WHERE receipt_id = ?", receiptId);
            return result;
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT history_id AS historyId, IFNULL(SUM(allocation_amount),0) AS allocAmount " +
                        "FROM finance_ar_history_allocation WHERE receipt_id = ? GROUP BY history_id",
                receiptId
        );
        int affected = 0;
        BigDecimal rollbackAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (Map<String, Object> row : rows) {
            Long historyId = parseLong(row.get("historyId"));
            BigDecimal allocAmount = normalizeMoney(toDecimal(row.get("allocAmount")));
            if (historyId == null || allocAmount == null || allocAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            int n = jdbcTemplate.update(
                    "UPDATE sales_statement_history SET unpaid_amount = IFNULL(unpaid_amount,0) + ?, updated_by = ?, updated_at = NOW() WHERE id = ? AND is_deleted = 0",
                    allocAmount,
                    operator,
                    historyId
            );
            if (n > 0) {
                affected++;
                rollbackAmount = normalizeMoney(rollbackAmount.add(allocAmount));
            }
        }

        jdbcTemplate.update("DELETE FROM finance_ar_history_allocation WHERE receipt_id = ?", receiptId);
        result.put("affectedRows", affected);
        result.put("rollbackAmount", rollbackAmount);
        return result;
    }

    private void ensureHistoryAllocationTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS finance_ar_history_allocation (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "receipt_id BIGINT NOT NULL," +
                "history_id BIGINT NOT NULL," +
                "customer_code VARCHAR(100) NOT NULL," +
                "statement_month VARCHAR(7) NOT NULL," +
                "allocation_amount DECIMAL(18,2) NOT NULL DEFAULT 0," +
                "created_by VARCHAR(64) NULL," +
                "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "KEY idx_receipt_id (receipt_id)," +
                "KEY idx_history_id (history_id)," +
                "KEY idx_customer_month (customer_code, statement_month)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    private boolean tableExists(String tableName) {
        if (!StringUtils.hasText(tableName)) {
            return false;
        }
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                    Integer.class,
                    tableName.trim()
            );
            return count != null && count > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public ResponseResult<?> searchCustomers(Map<String, Object> params) {
        String keyword = params == null ? "" : stringValue(params.get("keyword"));
        List<Map<String, Object>> rows;
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim() + "%";
            rows = jdbcTemplate.queryForList(
                "SELECT customer_code AS customerCode, customer_name AS customerName, short_name AS shortName, " +
                    "bank_name AS bankName, bank_account AS bankAccount " +
                            "FROM customers WHERE is_deleted = 0 AND (customer_code LIKE ? OR customer_name LIKE ? OR short_name LIKE ?) " +
                            "ORDER BY update_time DESC, id DESC LIMIT 20",
                    like, like, like
            );
        } else {
            rows = jdbcTemplate.queryForList(
                "SELECT customer_code AS customerCode, customer_name AS customerName, short_name AS shortName, " +
                    "bank_name AS bankName, bank_account AS bankAccount " +
                            "FROM customers WHERE is_deleted = 0 ORDER BY update_time DESC, id DESC LIMIT 20"
            );
        }
        return new ResponseResult<>(200, "OK", rows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> importOrderPaymentStatuses(Map<String, Object> payload) {
        ensureOrderPaymentTables();
        if (payload == null) {
            return new ResponseResult<>(400, "payload required", null);
        }

        Object ordersObj = payload.get("orders");
        if (!(ordersObj instanceof List)) {
            return new ResponseResult<>(400, "orders 必须是数组", null);
        }

        String defaultCustomerCode = stringValue(payload.get("customerCode"));
        String operator = getCurrentUsername();
        Timestamp now = new Timestamp(System.currentTimeMillis());

        int success = 0;
        int skipped = 0;
        int duplicateCount = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        Map<String, List<Map<String, Object>>> orderItemsCache = new HashMap<>();
        Map<String, Set<Long>> requestUsedItemIds = new HashMap<>();
        Map<String, Set<Long>> persistedBoundItemIdsCache = new HashMap<>();
        Set<String> touchedOrderNos = new HashSet<>();
        int unmatchedCount = 0;

        @SuppressWarnings("unchecked")
        List<Object> orders = (List<Object>) ordersObj;

        Boolean updateShipmentOnly = parseBoolean(payload.get("updateShipmentOnly"));
        if (Boolean.TRUE.equals(updateShipmentOnly)) {
            return importShipmentAmountsOnly(orders, defaultCustomerCode, operator);
        }

        for (int i = 0; i < orders.size(); i++) {
            Object rowObj = orders.get(i);
            try {
                if (!(rowObj instanceof Map)) {
                    skipped++;
                    Map<String, Object> err = new HashMap<>();
                    err.put("index", i);
                    err.put("orderNo", "");
                    err.put("reason", "行数据格式错误");
                    errors.add(err);
                    continue;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> row = (Map<String, Object>) rowObj;
                String orderNo = stringValue(row.get("orderNo"));
                String customerCode = stringValue(row.get("customerCode"));
                if (!StringUtils.hasText(customerCode)) {
                    customerCode = defaultCustomerCode;
                }
                if (!StringUtils.hasText(orderNo)) {
                    Map<String, Object> err = new HashMap<>();
                    err.put("index", i);
                    err.put("orderNo", orderNo);
                    err.put("reason", "orderNo 不能为空");
                    errors.add(err);
                    continue;
                }

                BigDecimal orderAmount = normalizeMoney(toDecimal(row.get("orderAmount")));
                BigDecimal shipmentAmount = normalizeMoney(toDecimal(row.get("shipmentAmount")));
                BigDecimal paidAmount = null;
                BigDecimal unpaidAmount = null;
                Boolean isPaid = Boolean.FALSE;
                Date orderDate = parseDate(row.get("orderDate"));
                Object shipmentDateRaw = firstRawValue(
                    row,
                    "shipmentDate",
                    "shipment_date",
                    "deliveryDate",
                    "delivery_date",
                    "shipDate",
                    "ship_date",
                    "发货日期",
                    "出货日期"
                );
                Date importShipmentDate = parseDateWithReferenceYear(shipmentDateRaw, orderDate);
                Long providedOrderItemId = null;
                String importCustomerName = stringValue(row.get("customerName"));
                String importProductName = stringValue(row.get("productName"));
                String importOrderDetail = stringValue(row.get("orderDetail"));
                String importOrderSpec = stringValue(row.get("orderSpec"));
                BigDecimal importOrderRolls = normalizeScale(toDecimal(row.get("orderRolls")), 4);
                BigDecimal importOrderSqm = normalizeScale(toDecimal(row.get("orderSqm")), 4);
                BigDecimal importUnitPrice = normalizeScale(toDecimal(row.get("unitPrice")), 4);

                List<Map<String, Object>> orderMetaRows = jdbcTemplate.queryForList(
                    "SELECT id AS orderId, customer AS customerCode, total_amount AS orderAmount, order_date AS orderDate " +
                                "FROM sales_orders WHERE is_deleted = 0 AND order_no = ? LIMIT 1",
                        orderNo
                );
                Map<String, Object> orderMeta = orderMetaRows.isEmpty() ? null : orderMetaRows.get(0);

                if (!StringUtils.hasText(customerCode)) {
                    customerCode = orderMeta == null ? "" : stringValue(orderMeta.get("customerCode"));
                }
                if (orderAmount == null) {
                    orderAmount = orderMeta == null ? null : normalizeMoney(toDecimal(orderMeta.get("orderAmount")));
                }
                if (orderDate == null) {
                    orderDate = orderMeta == null ? null : parseDate(orderMeta.get("orderDate"));
                }
                Long orderId = orderMeta == null ? null : parseLong(orderMeta.get("orderId"));

                if (!StringUtils.hasText(customerCode)) {
                    customerCode = "UNKNOWN";
                }

                if (orderAmount == null) {
                    orderAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                }
                if (shipmentAmount == null) {
                    shipmentAmount = orderAmount;
                }
                if (orderDate == null) {
                    orderDate = Date.valueOf(LocalDate.now());
                }

                BigDecimal amountForBinding = orderAmount;
                if (amountForBinding == null) {
                    amountForBinding = unpaidAmount != null ? unpaidAmount : paidAmount;
                }

                Long orderItemId = null;
                String matchStatus = orderMeta == null ? "UNMATCHED" : "MATCHED";

                if (paidAmount == null && unpaidAmount == null) {
                    if (Boolean.TRUE.equals(isPaid)) {
                        paidAmount = shipmentAmount;
                        unpaidAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    } else {
                        paidAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                        unpaidAmount = shipmentAmount;
                    }
                } else if (paidAmount == null) {
                    // 初始化/手工导入场景：若未明确提供已收金额，默认按0处理，
                    // 避免将“订单金额-未收金额”自动推导为已收金额，造成初始化数据非0。
                    paidAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    if (unpaidAmount == null) {
                        unpaidAmount = shipmentAmount;
                    }
                } else if (unpaidAmount == null) {
                    unpaidAmount = normalizeMoney(shipmentAmount.subtract(paidAmount));
                }

                if (paidAmount.compareTo(BigDecimal.ZERO) < 0) {
                    paidAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                }
                if (unpaidAmount.compareTo(BigDecimal.ZERO) < 0) {
                    unpaidAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                }

                if (isPaid == null) {
                    isPaid = unpaidAmount.compareTo(BigDecimal.ZERO) <= 0;
                }

                Long existingId = null;
                if (orderItemId != null && orderItemId > 0) {
                    existingId = jdbcTemplate.query(
                        "SELECT id FROM finance_ar_order_payment_status " +
                            "WHERE is_deleted = 0 AND order_no = ? AND order_item_id = ? " +
                            "ORDER BY id DESC LIMIT 1",
                        rs -> rs.next() ? rs.getLong("id") : null,
                        orderNo,
                        orderItemId
                    );
                }

                if (existingId != null) {
                    jdbcTemplate.update(
                        "UPDATE finance_ar_order_payment_status SET " +
                            "customer_code = ?, order_no = ?, order_item_id = ?, order_date = ?, " +
                            "order_amount = ?, shipment_amount = ?, paid_amount = ?, unpaid_amount = ?, is_paid = ?, " +
                            "import_customer_name = ?, import_product_name = ?, " +
                            "import_order_detail = ?, import_order_spec = ?, import_order_rolls = ?, import_order_sqm = ?, import_unit_price = ?, import_shipment_date = ?, " +
                            "created_by = ?, updated_at = NOW() " +
                            "WHERE id = ?",
                        customerCode,
                        orderNo,
                        orderItemId,
                        orderDate,
                        orderAmount,
                        shipmentAmount,
                        paidAmount,
                        unpaidAmount,
                        Boolean.TRUE.equals(isPaid) ? 1 : 0,
                        StringUtils.hasText(importCustomerName) ? importCustomerName : null,
                        StringUtils.hasText(importProductName) ? importProductName : null,
                        StringUtils.hasText(importOrderDetail) ? importOrderDetail : null,
                        StringUtils.hasText(importOrderSpec) ? importOrderSpec : null,
                        importOrderRolls,
                        importOrderSqm,
                        importUnitPrice,
                        importShipmentDate,
                        operator,
                        existingId
                    );
                } else {
                    jdbcTemplate.update(
                        "INSERT INTO finance_ar_order_payment_status(" +
                            "customer_code, order_no, order_item_id, order_date, order_amount, shipment_amount, paid_amount, unpaid_amount, is_paid, import_customer_name, import_product_name, import_order_detail, import_order_spec, import_order_rolls, import_order_sqm, import_unit_price, import_shipment_date, created_by, created_at, updated_at, is_deleted" +
                            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                        customerCode,
                        orderNo,
                        orderItemId,
                        orderDate,
                        orderAmount,
                        shipmentAmount,
                        paidAmount,
                        unpaidAmount,
                        Boolean.TRUE.equals(isPaid) ? 1 : 0,
                        StringUtils.hasText(importCustomerName) ? importCustomerName : null,
                        StringUtils.hasText(importProductName) ? importProductName : null,
                        StringUtils.hasText(importOrderDetail) ? importOrderDetail : null,
                        StringUtils.hasText(importOrderSpec) ? importOrderSpec : null,
                        importOrderRolls,
                        importOrderSqm,
                        importUnitPrice,
                        importShipmentDate,
                        operator,
                        now,
                        now
                    );
                }

                if ("MATCHED".equals(matchStatus)) {
                    touchedOrderNos.add(orderNo);
                } else {
                    unmatchedCount++;
                }
                success++;
            } catch (Exception ex) {
                Map<String, Object> err = new HashMap<>();
                err.put("index", i);
                String orderNo = "";
                if (rowObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> row = (Map<String, Object>) rowObj;
                    orderNo = stringValue(row.get("orderNo"));
                }
                err.put("orderNo", orderNo);
                String msg = ex.getMessage();
                if (!StringUtils.hasText(msg)) {
                    msg = "未知异常";
                }
                if (msg.length() > 120) {
                    msg = msg.substring(0, 120);
                }
                err.put("reason", "导入异常：" + msg);
                errors.add(err);
            }
        }

        for (String orderNo : touchedOrderNos) {
            syncSalesOrderPaymentLifecycle(orderNo, null, null, operator);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", orders.size());
        result.put("success", success);
        result.put("skipped", skipped);
        result.put("duplicateCount", duplicateCount);
        result.put("unmatchedCount", unmatchedCount);
        result.put("errorCount", errors.size());
        result.put("errors", errors);
        return new ResponseResult<>(200, "导入完成", result);
    }

    private ResponseResult<?> importShipmentAmountsOnly(List<Object> orders,
                                                        String defaultCustomerCode,
                                                        String operator) {
        int total = orders == null ? 0 : orders.size();
        int updatedRows = 0;
        int success = 0;
        int skipped = 0;
        int notFound = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        Set<String> touchedOrderNos = new HashSet<>();

        if (orders == null || orders.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("total", 0);
            result.put("success", 0);
            result.put("updatedRows", 0);
            result.put("skipped", 0);
            result.put("notFound", 0);
            result.put("errorCount", 0);
            result.put("errors", errors);
            result.put("mode", "SHIPMENT_ONLY");
            return new ResponseResult<>(200, "导入完成", result);
        }

        for (int i = 0; i < orders.size(); i++) {
            Object rowObj = orders.get(i);
            try {
                if (!(rowObj instanceof Map)) {
                    skipped++;
                    Map<String, Object> err = new HashMap<>();
                    err.put("index", i);
                    err.put("orderNo", "");
                    err.put("reason", "行数据格式错误");
                    errors.add(err);
                    continue;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> row = (Map<String, Object>) rowObj;
                String orderNo = stringValue(row.get("orderNo"));
                Long orderItemId = parseLong(row.get("orderItemId"));
                BigDecimal shipmentAmount = normalizeMoney(toDecimal(row.get("shipmentAmount")));

                if (!StringUtils.hasText(orderNo)) {
                    Map<String, Object> err = new HashMap<>();
                    err.put("index", i);
                    err.put("orderNo", "");
                    err.put("reason", "orderNo 不能为空");
                    errors.add(err);
                    skipped++;
                    continue;
                }

                if (shipmentAmount == null) {
                    Map<String, Object> err = new HashMap<>();
                    err.put("index", i);
                    err.put("orderNo", orderNo);
                    err.put("reason", "shipmentAmount/出货金额 不能为空");
                    errors.add(err);
                    skipped++;
                    continue;
                }

                if (shipmentAmount.compareTo(BigDecimal.ZERO) < 0) {
                    shipmentAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                }

                StringBuilder sql = new StringBuilder(
                        "SELECT id, order_no, IFNULL(paid_amount,0) AS paidAmount FROM finance_ar_order_payment_status " +
                                "WHERE is_deleted = 0 AND order_no = ?");
                List<Object> args = new ArrayList<>();
                args.add(orderNo);
                if (orderItemId != null && orderItemId > 0) {
                    sql.append(" AND order_item_id = ?");
                    args.add(orderItemId);
                }

                List<Map<String, Object>> targets = jdbcTemplate.queryForList(sql.toString(), args.toArray());
                if (targets == null || targets.isEmpty()) {
                    notFound++;
                    Map<String, Object> err = new HashMap<>();
                    err.put("index", i);
                    err.put("orderNo", orderNo);
                    err.put("reason", "未找到可更新记录（请确认订单号/明细ID）");
                    errors.add(err);
                    continue;
                }

                for (Map<String, Object> target : targets) {
                    Long id = parseLong(target.get("id"));
                    if (id == null || id <= 0) {
                        continue;
                    }
                    BigDecimal paidAmount = normalizeMoney(toDecimal(target.get("paidAmount")));
                    if (paidAmount == null) {
                        paidAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    }
                    BigDecimal unpaidAmount = normalizeMoney(shipmentAmount.subtract(paidAmount));
                    if (unpaidAmount == null || unpaidAmount.compareTo(BigDecimal.ZERO) < 0) {
                        unpaidAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    }
                    int isPaid = unpaidAmount.compareTo(BigDecimal.ZERO) <= 0 ? 1 : 0;

                    String customerCode = stringValue(row.get("customerCode"));
                    if (!StringUtils.hasText(customerCode)) {
                        customerCode = defaultCustomerCode;
                    }

                    if (StringUtils.hasText(customerCode)) {
                        jdbcTemplate.update(
                                "UPDATE finance_ar_order_payment_status " +
                                        "SET shipment_amount = ?, unpaid_amount = ?, is_paid = ?, customer_code = ?, updated_at = NOW() " +
                                        "WHERE id = ? AND is_deleted = 0",
                                shipmentAmount,
                                unpaidAmount,
                                isPaid,
                                customerCode,
                                id
                        );
                    } else {
                        jdbcTemplate.update(
                                "UPDATE finance_ar_order_payment_status " +
                                        "SET shipment_amount = ?, unpaid_amount = ?, is_paid = ?, updated_at = NOW() " +
                                        "WHERE id = ? AND is_deleted = 0",
                                shipmentAmount,
                                unpaidAmount,
                                isPaid,
                                id
                        );
                    }
                    updatedRows++;
                }

                touchedOrderNos.add(orderNo);
                success++;
            } catch (Exception ex) {
                Map<String, Object> err = new HashMap<>();
                err.put("index", i);
                String orderNo = "";
                if (rowObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> row = (Map<String, Object>) rowObj;
                    orderNo = stringValue(row.get("orderNo"));
                }
                err.put("orderNo", orderNo);
                String msg = ex.getMessage();
                if (!StringUtils.hasText(msg)) {
                    msg = "未知异常";
                }
                if (msg.length() > 120) {
                    msg = msg.substring(0, 120);
                }
                err.put("reason", "导入异常：" + msg);
                errors.add(err);
            }
        }

        for (String orderNo : touchedOrderNos) {
            syncSalesOrderPaymentLifecycle(orderNo, null, null, operator);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("success", success);
        result.put("updatedRows", updatedRows);
        result.put("skipped", skipped);
        result.put("notFound", notFound);
        result.put("errorCount", errors.size());
        result.put("errors", errors);
        result.put("mode", "SHIPMENT_ONLY");
        return new ResponseResult<>(200, "导入完成", result);
    }

    @Override
    public ResponseResult<?> listOrderItemCandidates(Map<String, Object> params) {
        String orderNo = params == null ? "" : stringValue(params.get("orderNo"));
        if (!StringUtils.hasText(orderNo)) {
            return new ResponseResult<>(400, "orderNo required", Collections.emptyList());
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT soi.id AS orderItemId, soi.material_code AS materialCode, " +
                        "CONCAT(IFNULL(soi.thickness,''),'*',IFNULL(soi.width,''),'*',IFNULL(soi.length,'')) AS spec, " +
                        "soi.rolls AS rolls, soi.sqm AS sqm, soi.amount AS amount " +
                        "FROM sales_order_items soi " +
                        "INNER JOIN sales_orders so ON so.id = soi.order_id " +
                        "WHERE soi.is_deleted = 0 AND so.is_deleted = 0 AND so.order_no = ? " +
                        "ORDER BY soi.id ASC",
                orderNo.trim()
        );
        return new ResponseResult<>(200, "OK", rows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> updateOrderPaymentRow(Long id, Map<String, Object> payload) {
        ensureOrderPaymentTables();
        if (id == null || id <= 0) {
            return new ResponseResult<>(400, "id required", null);
        }
        if (payload == null) {
            return new ResponseResult<>(400, "payload required", null);
        }

        List<Map<String, Object>> currentRows = jdbcTemplate.queryForList(
                "SELECT id, customer_code AS customerCode, order_no AS orderNo, order_item_id AS orderItemId, order_date AS orderDate, " +
                "order_amount AS orderAmount, shipment_amount AS shipmentAmount, paid_amount AS paidAmount, unpaid_amount AS unpaidAmount, is_paid AS isPaid, " +
                        "import_customer_name AS importCustomerName, import_product_name AS importProductName, " +
                        "import_order_detail AS importOrderDetail, import_order_spec AS importOrderSpec, " +
                    "import_order_rolls AS importOrderRolls, import_order_sqm AS importOrderSqm, import_unit_price AS importUnitPrice, import_shipment_date AS importShipmentDate " +
                        "FROM finance_ar_order_payment_status WHERE id = ? AND is_deleted = 0 LIMIT 1",
                id
        );
        if (currentRows.isEmpty()) {
            return new ResponseResult<>(404, "记录不存在", null);
        }

        Map<String, Object> current = currentRows.get(0);

        String customerCode = StringUtils.hasText(stringValue(payload.get("customerCode")))
                ? stringValue(payload.get("customerCode"))
                : stringValue(current.get("customerCode"));
        String orderNo = StringUtils.hasText(stringValue(payload.get("orderNo")))
                ? stringValue(payload.get("orderNo"))
                : stringValue(current.get("orderNo"));

        Date orderDate = parseDate(payload.get("orderDate"));
        if (orderDate == null) {
            orderDate = parseDate(current.get("orderDate"));
        }
        if (orderDate == null) {
            orderDate = Date.valueOf(LocalDate.now());
        }

        BigDecimal orderAmount = normalizeMoney(toDecimal(payload.get("orderAmount")));
        if (orderAmount == null) {
            orderAmount = normalizeMoney(toDecimal(current.get("orderAmount")));
        }
        if (orderAmount == null) {
            orderAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal shipmentAmount = normalizeMoney(toDecimal(payload.get("shipmentAmount")));
        if (shipmentAmount == null) {
            shipmentAmount = normalizeMoney(toDecimal(current.get("shipmentAmount")));
        }
        if (shipmentAmount == null) {
            shipmentAmount = orderAmount;
        }

        BigDecimal paidAmount = normalizeMoney(toDecimal(payload.get("paidAmount")));
        BigDecimal unpaidAmount = normalizeMoney(toDecimal(payload.get("unpaidAmount")));
        if (paidAmount == null && unpaidAmount == null) {
            paidAmount = normalizeMoney(toDecimal(current.get("paidAmount")));
            unpaidAmount = normalizeMoney(toDecimal(current.get("unpaidAmount")));
        } else if (paidAmount == null) {
            paidAmount = normalizeMoney(shipmentAmount.subtract(unpaidAmount));
        } else if (unpaidAmount == null) {
            unpaidAmount = normalizeMoney(shipmentAmount.subtract(paidAmount));
        }

        if (paidAmount == null) {
            paidAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (unpaidAmount == null) {
            unpaidAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (paidAmount.compareTo(BigDecimal.ZERO) < 0) {
            paidAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (unpaidAmount.compareTo(BigDecimal.ZERO) < 0) {
            unpaidAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        Boolean isPaid = parseBoolean(payload.get("isPaid"));
        if (isPaid == null) {
            isPaid = unpaidAmount.compareTo(BigDecimal.ZERO) <= 0;
        }

        String importCustomerName = payload.containsKey("customerName")
                ? stringValue(payload.get("customerName"))
                : stringValue(current.get("importCustomerName"));
        String importProductName = payload.containsKey("productName")
                ? stringValue(payload.get("productName"))
                : stringValue(current.get("importProductName"));
        String importOrderDetail = payload.containsKey("orderDetail")
                ? stringValue(payload.get("orderDetail"))
                : stringValue(current.get("importOrderDetail"));
        String importOrderSpec = payload.containsKey("orderSpec")
                ? stringValue(payload.get("orderSpec"))
                : stringValue(current.get("importOrderSpec"));
        BigDecimal importOrderRolls = payload.containsKey("orderRolls")
                ? normalizeScale(toDecimal(payload.get("orderRolls")), 4)
                : normalizeScale(toDecimal(current.get("importOrderRolls")), 4);
        BigDecimal importOrderSqm = payload.containsKey("orderSqm")
                ? normalizeScale(toDecimal(payload.get("orderSqm")), 4)
                : normalizeScale(toDecimal(current.get("importOrderSqm")), 4);
        BigDecimal importUnitPrice = payload.containsKey("unitPrice")
                ? normalizeScale(toDecimal(payload.get("unitPrice")), 4)
                : normalizeScale(toDecimal(current.get("importUnitPrice")), 4);
        Date importShipmentDate = payload.containsKey("shipmentDate")
            ? parseDate(payload.get("shipmentDate"))
            : parseDate(current.get("importShipmentDate"));

        Long currentOrderItemId = parseLong(current.get("orderItemId"));
        String oldOrderNo = stringValue(current.get("orderNo"));
        boolean orderNoChanged = StringUtils.hasText(orderNo) && !orderNo.equals(oldOrderNo);
        if (currentOrderItemId != null && currentOrderItemId > 0 && orderNoChanged) {
            return new ResponseResult<>(400, "已匹配记录不允许修改订单号，请先执行“修正”流程", null);
        }
        Long nextOrderItemId = currentOrderItemId;
        if (orderNoChanged && currentOrderItemId != null && currentOrderItemId > 0) {
            nextOrderItemId = null;
        }

        jdbcTemplate.update(
                "UPDATE finance_ar_order_payment_status SET " +
                "customer_code = ?, order_no = ?, order_item_id = ?, order_date = ?, order_amount = ?, shipment_amount = ?, paid_amount = ?, unpaid_amount = ?, is_paid = ?, " +
                        "import_customer_name = ?, import_product_name = ?, import_order_detail = ?, import_order_spec = ?, import_order_rolls = ?, import_order_sqm = ?, import_unit_price = ?, import_shipment_date = ?, " +
                    "updated_at = NOW() " +
                        "WHERE id = ? AND is_deleted = 0",
                customerCode,
                orderNo,
                nextOrderItemId,
                orderDate,
                orderAmount,
            shipmentAmount,
                paidAmount,
                unpaidAmount,
                Boolean.TRUE.equals(isPaid) ? 1 : 0,
                StringUtils.hasText(importCustomerName) ? importCustomerName : null,
                StringUtils.hasText(importProductName) ? importProductName : null,
                StringUtils.hasText(importOrderDetail) ? importOrderDetail : null,
                StringUtils.hasText(importOrderSpec) ? importOrderSpec : null,
                importOrderRolls,
                importOrderSqm,
                importUnitPrice,
                importShipmentDate,
                id
        );

        if (StringUtils.hasText(orderNo)) {
            syncSalesOrderPaymentLifecycle(orderNo, null, null, getCurrentUsername());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("orderNo", orderNo);
        return new ResponseResult<>(200, "更新成功", result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> batchUpdateOrderPaymentCustomer(Map<String, Object> payload) {
        ensureOrderPaymentTables();
        if (payload == null) {
            return new ResponseResult<>(400, "payload required", null);
        }

        Object idsObj = payload.get("ids");
        if (!(idsObj instanceof List)) {
            return new ResponseResult<>(400, "ids 必须是数组", null);
        }
        @SuppressWarnings("unchecked")
        List<Object> rawIds = (List<Object>) idsObj;
        List<Long> ids = new ArrayList<>();
        for (Object obj : rawIds) {
            Long id = parseLong(obj);
            if (id != null && id > 0) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return new ResponseResult<>(400, "请选择要替换的明细", null);
        }

        String customerCode = stringValue(payload.get("customerCode"));
        if (!StringUtils.hasText(customerCode)) {
            return new ResponseResult<>(400, "customerCode required", null);
        }

        List<Map<String, Object>> customerRows = jdbcTemplate.queryForList(
                "SELECT customer_code AS customerCode, customer_name AS customerName FROM customers WHERE is_deleted = 0 AND customer_code = ? LIMIT 1",
                customerCode
        );
        String customerName = stringValue(payload.get("customerName"));
        if (!customerRows.isEmpty()) {
            customerName = stringValue(customerRows.get(0).get("customerName"));
        }
        if (!StringUtils.hasText(customerName)) {
            return new ResponseResult<>(400, "客户名称不能为空", null);
        }

        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = "UPDATE finance_ar_order_payment_status " +
                "SET customer_code = ?, import_customer_name = ?, updated_at = NOW() " +
                "WHERE is_deleted = 0 AND id IN (" + placeholders + ")";

        List<Object> args = new ArrayList<>();
        args.add(customerCode);
        args.add(customerName);
        args.addAll(ids);

        int affected = jdbcTemplate.update(sql, args.toArray());

        Map<String, Object> result = new HashMap<>();
        result.put("affected", affected);
        result.put("customerCode", customerCode);
        result.put("customerName", customerName);
        return new ResponseResult<>(200, "批量替换成功", result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> updateOrderPaymentMatch(Long id, Map<String, Object> payload) {
        ensureOrderPaymentTables();
        if (id == null || id <= 0) {
            return new ResponseResult<>(400, "id required", null);
        }
        if (payload == null) {
            return new ResponseResult<>(400, "payload required", null);
        }

        List<Map<String, Object>> currentRows = jdbcTemplate.queryForList(
                "SELECT id, order_no AS orderNo, order_amount AS orderAmount FROM finance_ar_order_payment_status " +
                        "WHERE id = ? AND is_deleted = 0 LIMIT 1",
                id
        );
        if (currentRows.isEmpty()) {
            return new ResponseResult<>(404, "记录不存在", null);
        }

        Map<String, Object> current = currentRows.get(0);
        String orderNo = stringValue(payload.get("orderNo"));
        if (!StringUtils.hasText(orderNo)) {
            orderNo = stringValue(current.get("orderNo"));
        }
        Long orderItemId = parseLong(payload.get("orderItemId"));
        if (orderItemId == null || orderItemId <= 0) {
            return new ResponseResult<>(400, "orderItemId required", null);
        }

        List<Map<String, Object>> itemRows = jdbcTemplate.queryForList(
                "SELECT soi.id, soi.amount, so.order_no AS orderNo FROM sales_order_items soi " +
                        "INNER JOIN sales_orders so ON so.id = soi.order_id " +
                        "WHERE soi.is_deleted = 0 AND so.is_deleted = 0 AND soi.id = ? LIMIT 1",
                orderItemId
        );
        if (itemRows.isEmpty()) {
            return new ResponseResult<>(400, "orderItemId不存在", null);
        }
        Map<String, Object> item = itemRows.get(0);
        String itemOrderNo = stringValue(item.get("orderNo"));
        if (!orderNo.equals(itemOrderNo)) {
            return new ResponseResult<>(400, "orderNo与orderItemId不一致", null);
        }

        jdbcTemplate.update(
            "UPDATE finance_ar_order_payment_status SET order_no = ?, order_item_id = ?, updated_at = NOW() " +
                        "WHERE id = ? AND is_deleted = 0",
                orderNo,
                orderItemId,
                id
        );
        syncSalesOrderPaymentLifecycle(orderNo, null, null, getCurrentUsername());

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("matchStatus", "MATCHED");
        result.put("matchReason", "");
        return new ResponseResult<>(200, "更新成功", result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> supplementOrderMissingItems(Map<String, Object> payload) {
        ensureOrderPaymentTables();
        if (payload == null) {
            return new ResponseResult<>(400, "payload required", null);
        }
        String orderNo = stringValue(payload.get("orderNo"));
        if (!StringUtils.hasText(orderNo)) {
            return new ResponseResult<>(400, "orderNo required", null);
        }

        List<Map<String, Object>> orderRows = jdbcTemplate.queryForList(
                "SELECT id AS orderId, customer AS customerCode, order_date AS orderDate " +
                        "FROM sales_orders WHERE is_deleted = 0 AND order_no = ? LIMIT 1",
                orderNo.trim()
        );
        if (orderRows.isEmpty()) {
            return new ResponseResult<>(404, "订单不存在", null);
        }

        Map<String, Object> order = orderRows.get(0);
        Long orderId = parseLong(order.get("orderId"));
        if (orderId == null || orderId <= 0) {
            return new ResponseResult<>(400, "订单ID无效", null);
        }

        List<Map<String, Object>> itemRows = jdbcTemplate.queryForList(
                "SELECT id, amount FROM sales_order_items WHERE is_deleted = 0 AND order_id = ? ORDER BY id ASC",
                orderId
        );
        if (itemRows.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("orderNo", orderNo.trim());
            result.put("totalItems", 0);
            result.put("inserted", 0);
            result.put("skipped", 0);
            return new ResponseResult<>(200, "订单无可补齐明细", result);
        }

        List<Map<String, Object>> existingRows = jdbcTemplate.queryForList(
                "SELECT DISTINCT order_item_id AS orderItemId FROM finance_ar_order_payment_status " +
                        "WHERE is_deleted = 0 AND order_no = ? AND order_item_id IS NOT NULL",
                orderNo.trim()
        );
        Set<Long> existingItemIds = new HashSet<>();
        for (Map<String, Object> row : existingRows) {
            Long id = parseLong(row.get("orderItemId"));
            if (id != null && id > 0) {
                existingItemIds.add(id);
            }
        }

        String customerCode = stringValue(order.get("customerCode"));
        if (!StringUtils.hasText(customerCode)) {
            customerCode = "UNKNOWN";
        }
        Date orderDate = parseDate(order.get("orderDate"));
        if (orderDate == null) {
            orderDate = Date.valueOf(LocalDate.now());
        }

        String operator = getCurrentUsername();
        Timestamp now = new Timestamp(System.currentTimeMillis());
        int inserted = 0;
        int skipped = 0;

        for (Map<String, Object> item : itemRows) {
            Long itemId = parseLong(item.get("id"));
            if (itemId == null || itemId <= 0) {
                skipped++;
                continue;
            }
            if (existingItemIds.contains(itemId)) {
                skipped++;
                continue;
            }

            BigDecimal amount = normalizeMoney(toDecimal(item.get("amount")));
            if (amount == null) {
                amount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
                BigDecimal shipmentAmount = amount;
            BigDecimal paidAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                BigDecimal unpaidAmount = shipmentAmount;

            jdbcTemplate.update(
                    "INSERT INTO finance_ar_order_payment_status(" +
                        "customer_code, order_no, order_item_id, order_date, order_amount, shipment_amount, paid_amount, unpaid_amount, is_paid, created_by, created_at, updated_at, is_deleted" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                    customerCode,
                    orderNo.trim(),
                    itemId,
                    orderDate,
                    amount,
                    shipmentAmount,
                    paidAmount,
                    unpaidAmount,
                    0,
                    operator,
                    now,
                    now
            );
            inserted++;
        }

        if (inserted > 0) {
            syncSalesOrderPaymentLifecycle(orderNo.trim(), null, null, operator);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("orderNo", orderNo.trim());
        result.put("totalItems", itemRows.size());
        result.put("inserted", inserted);
        result.put("skipped", skipped);
        return new ResponseResult<>(200, "补齐完成", result);
    }

    private Map<String, Object> resolveOrderItemBinding(String orderNo,
                                                        Long orderId,
                                                        Long providedOrderItemId,
                                                        BigDecimal amountForBinding,
                                                        Map<String, List<Map<String, Object>>> orderItemsCache,
                                                        Map<String, Set<Long>> requestUsedItemIds,
                                                        Map<String, Set<Long>> persistedBoundItemIdsCache) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("订单缺少有效ID，无法绑定订单明细");
        }

        List<Map<String, Object>> items = orderItemsCache.computeIfAbsent(orderNo, key -> jdbcTemplate.queryForList(
                "SELECT id, amount FROM sales_order_items WHERE is_deleted = 0 AND order_id = ? ORDER BY id ASC",
                orderId
        ));
        if (items.isEmpty()) {
            throw new IllegalArgumentException("订单没有可用明细，无法绑定");
        }

        Set<Long> usedInRequest = requestUsedItemIds.computeIfAbsent(orderNo, key -> new HashSet<>());
        Set<Long> persistedBound = persistedBoundItemIdsCache.computeIfAbsent(orderNo, key -> {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT DISTINCT order_item_id AS orderItemId FROM finance_ar_order_payment_status " +
                            "WHERE is_deleted = 0 AND order_no = ? AND order_item_id IS NOT NULL",
                    orderNo
            );
            Set<Long> bound = new HashSet<>();
            for (Map<String, Object> row : rows) {
                Long id = parseLong(row.get("orderItemId"));
                if (id != null) {
                    bound.add(id);
                }
            }
            return bound;
        });

        List<Map<String, Object>> candidates = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Long id = parseLong(item.get("id"));
            if (id == null || persistedBound.contains(id) || usedInRequest.contains(id)) {
                continue;
            }
            candidates.add(item);
        }
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("订单明细已全部绑定，无法继续导入");
        }

        if (providedOrderItemId != null && providedOrderItemId > 0) {
            Map<String, Object> matched = null;
            for (Map<String, Object> item : candidates) {
                Long id = parseLong(item.get("id"));
                if (providedOrderItemId.equals(id)) {
                    matched = item;
                    break;
                }
            }
            if (matched == null) {
                throw new IllegalArgumentException("指定orderItemId不属于该订单或已被绑定");
            }
            usedInRequest.add(providedOrderItemId);
            persistedBound.add(providedOrderItemId);
            return matched;
        }

        if (amountForBinding != null) {
            List<Map<String, Object>> amountMatched = new ArrayList<>();
            for (Map<String, Object> item : candidates) {
                BigDecimal itemAmount = normalizeMoney(toDecimal(item.get("amount")));
                if (itemAmount != null && itemAmount.compareTo(amountForBinding) == 0) {
                    amountMatched.add(item);
                }
            }
            if (amountMatched.size() == 1) {
                Long id = parseLong(amountMatched.get(0).get("id"));
                usedInRequest.add(id);
                persistedBound.add(id);
                return amountMatched.get(0);
            }
            if (amountMatched.size() > 1) {
                throw new IllegalArgumentException("同金额订单明细有多条，请补充orderItemId");
            }
            throw new IllegalArgumentException("未找到与金额匹配的订单明细，请补充orderItemId");
        }

        if (candidates.size() == 1) {
            Long id = parseLong(candidates.get(0).get("id"));
            usedInRequest.add(id);
            persistedBound.add(id);
            return candidates.get(0);
        }

        throw new IllegalArgumentException("缺少可唯一匹配条件，请补充orderItemId");
    }

    private Map<String, String> buildOrderItemSuggestions(Long orderId) {
        Map<String, String> result = new HashMap<>();
        if (orderId == null || orderId <= 0) {
            result.put("candidateOrderItemIds", "");
            result.put("candidateOrderItemDetails", "");
            return result;
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, IFNULL(material_code,'') AS materialCode, " +
                        "CONCAT(IFNULL(thickness,''),'*',IFNULL(width,''),'*',IFNULL(length,'')) AS spec, " +
                        "IFNULL(rolls,0) AS rolls, IFNULL(sqm,0) AS sqm, IFNULL(amount,0) AS amount " +
                        "FROM sales_order_items WHERE is_deleted = 0 AND order_id = ? ORDER BY id ASC",
                orderId
        );

        List<String> ids = new ArrayList<>();
        List<String> details = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long id = parseLong(row.get("id"));
            if (id == null) {
                continue;
            }
            ids.add(String.valueOf(id));
            details.add(id + ":" + stringValue(row.get("materialCode")) +
                    "|" + stringValue(row.get("spec")) +
                    "|卷=" + stringValue(row.get("rolls")) +
                    "|㎡=" + stringValue(row.get("sqm")) +
                    "|金额=" + stringValue(row.get("amount")));
        }

        result.put("candidateOrderItemIds", String.join(",", ids));
        result.put("candidateOrderItemDetails", String.join("；", details));
        return result;
    }

    @Override
    public ResponseResult<?> getInvoice(Long id) {
        if (id == null) return new ResponseResult<>(400, "id required", null);
        ArInvoice invoice = arInvoiceMapper.selectOne(new LambdaQueryWrapper<ArInvoice>()
                .eq(ArInvoice::getId, id)
                .eq(ArInvoice::getIsDeleted, 0)
                .last("LIMIT 1"));
        return new ResponseResult<>(200, "OK", invoice);
    }

    @Override
    public ResponseResult<?> createInvoice(Map<String, Object> payload) {
        if (payload == null) {
            return new ResponseResult<>(400, "payload required", null);
        }
        String invoiceNo = stringValue(payload.get("invoice_no"));
        String customer = stringValue(payload.get("customer_code"));
        String invoiceDate = stringValue(payload.get("invoice_date"));
        Double total = toDouble(payload.get("total_amount"));
        if (invoiceNo.isEmpty() || customer.isEmpty() || invoiceDate.isEmpty() || total == null) {
            return new ResponseResult<>(400, "missing fields", null);
        }
        if (total <= 0) {
            return new ResponseResult<>(400, "total_amount must be positive", null);
        }
        Date parsedInvoiceDate;
        try {
            parsedInvoiceDate = Date.valueOf(invoiceDate);
        } catch (Exception ex) {
            return new ResponseResult<>(400, "invalid invoice_date", null);
        }
        ArInvoice invoice = new ArInvoice();
        invoice.setInvoiceNo(invoiceNo);
        invoice.setCustomerCode(customer);
        invoice.setInvoiceDate(parsedInvoiceDate);
        invoice.setTotalAmount(BigDecimal.valueOf(total));
        invoice.setPaidAmount(BigDecimal.ZERO);
        invoice.setStatus("OPEN");
        invoice.setCurrency("CNY");
        invoice.setIsDeleted(0);
        arInvoiceMapper.insert(invoice);
        return new ResponseResult<>(200, "created", Collections.singletonMap("invoiceId", invoice.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> createAndPostInvoice(Map<String, Object> payload) {
        if (payload == null) {
            return new ResponseResult<>(400, "payload required", null);
        }
        String invoiceNo = String.valueOf(payload.getOrDefault("invoice_no", "")).trim();
        String customer = String.valueOf(payload.getOrDefault("customer_code", "")).trim();
        String invoiceDate = String.valueOf(payload.getOrDefault("invoice_date", "")).trim();
        Object totalObj = payload.get("total_amount");
        Object debitAcctObj = payload.get("debit_account_id");
        Object creditAcctObj = payload.get("credit_account_id");

        if (invoiceNo.isEmpty() || customer.isEmpty() || invoiceDate.isEmpty() || totalObj == null || debitAcctObj == null || creditAcctObj == null) {
            return new ResponseResult<>(400, "missing required fields", null);
        }
        double totalAmount;
        try {
            totalAmount = totalObj instanceof Number ? ((Number) totalObj).doubleValue() : Double.parseDouble(String.valueOf(totalObj));
        } catch (Exception ex) {
            return new ResponseResult<>(400, "invalid total_amount", null);
        }
        if (totalAmount <= 0) {
            return new ResponseResult<>(400, "total_amount must be positive", null);
        }
        long debitAccountId;
        long creditAccountId;
        try {
            debitAccountId = debitAcctObj instanceof Number ? ((Number) debitAcctObj).longValue() : Long.parseLong(String.valueOf(debitAcctObj));
            creditAccountId = creditAcctObj instanceof Number ? ((Number) creditAcctObj).longValue() : Long.parseLong(String.valueOf(creditAcctObj));
        } catch (Exception ex) {
            return new ResponseResult<>(400, "invalid account ids", null);
        }
        Date parsedInvoiceDate;
        try {
            parsedInvoiceDate = Date.valueOf(invoiceDate);
        } catch (Exception ex) {
            return new ResponseResult<>(400, "invalid invoice_date", null);
        }

        ArInvoice invoice = new ArInvoice();
        invoice.setInvoiceNo(invoiceNo);
        invoice.setCustomerCode(customer);
        invoice.setInvoiceDate(parsedInvoiceDate);
        invoice.setTotalAmount(BigDecimal.valueOf(totalAmount));
        invoice.setPaidAmount(BigDecimal.ZERO);
        invoice.setStatus("OPEN");
        invoice.setCurrency("CNY");
        invoice.setIsDeleted(0);
        arInvoiceMapper.insert(invoice);
        Long invoiceId = invoice.getId();

        Object itemsObj = payload.get("items");
        if (itemsObj instanceof java.util.List) {
            List<?> items = (List<?>) itemsObj;
            for (Object o : items) {
                if (!(o instanceof java.util.Map)) continue;
                java.util.Map<?,?> it = (java.util.Map<?,?>) o;
                Object orderNoObj = it.get("order_no");
                String orderNo = orderNoObj == null ? "" : String.valueOf(orderNoObj);
                Object materialObj = it.get("material_code");
                String material = materialObj == null ? "" : String.valueOf(materialObj);
                Object qtyObj = it.get("quantity");
                Object unitPriceObj = it.get("unit_price");
                double qty = qtyObj instanceof Number ? ((Number) qtyObj).doubleValue() : 0.0;
                double unitPrice = unitPriceObj instanceof Number ? ((Number) unitPriceObj).doubleValue() : 0.0;
                double amount = qty * unitPrice;
                Object descObj = it.get("description");
                String desc = descObj == null ? "" : String.valueOf(descObj);
                ArInvoiceItem item = new ArInvoiceItem();
                item.setInvoiceId(invoiceId);
                item.setOrderNo(orderNo);
                item.setMaterialCode(material);
                item.setDescription(desc);
                item.setQuantity(BigDecimal.valueOf(qty));
                item.setUnitPrice(BigDecimal.valueOf(unitPrice));
                item.setAmount(BigDecimal.valueOf(amount));
                item.setIsDeleted(0);
                arInvoiceItemMapper.insert(item);
            }
        }

        String voucherNo = "INV-" + invoiceNo;
            GlEntry debitEntry = new GlEntry();
            debitEntry.setVoucherNo(voucherNo);
            debitEntry.setEntryDate(parsedInvoiceDate);
            debitEntry.setGlAccountId(debitAccountId);
            debitEntry.setDebit(BigDecimal.valueOf(totalAmount));
            debitEntry.setCredit(BigDecimal.ZERO);
            debitEntry.setCurrency("CNY");
            debitEntry.setSourceType("sales_invoice");
            debitEntry.setSourceId(invoiceId);
            debitEntry.setIsDeleted(0);
            glEntryMapper.insert(debitEntry);

            GlEntry creditEntry = new GlEntry();
            creditEntry.setVoucherNo(voucherNo);
            creditEntry.setEntryDate(parsedInvoiceDate);
            creditEntry.setGlAccountId(creditAccountId);
            creditEntry.setDebit(BigDecimal.ZERO);
            creditEntry.setCredit(BigDecimal.valueOf(totalAmount));
            creditEntry.setCurrency("CNY");
            creditEntry.setSourceType("sales_invoice");
            creditEntry.setSourceId(invoiceId);
            creditEntry.setIsDeleted(0);
            glEntryMapper.insert(creditEntry);

        // audit log
        try {
            jdbcTemplate.update("INSERT INTO audit_log (username, action, target_table, target_id, after_json, created_at) VALUES (?, ?, ?, ?, ?, NOW())",
                    "system", "CREATE_INVOICE_AND_POST_GL", "ar_invoice", invoiceId, "{\"invoice_no\":\"" + invoiceNo + "\"}");
        } catch (Exception ignore) {
        }

        return new ResponseResult<>(200, "created_and_posted", java.util.Collections.singletonMap("invoiceId", invoiceId));
    }

    private Map<String, Object> allocateReceiptToOrders(String customerCode,
                                                        Long receiptId,
                                                        BigDecimal receiptAmount,
                                                        Date payDate,
                                                        String operator) {
        BigDecimal remaining = normalizeMoney(receiptAmount);
        int affectedOrders = 0;

        System.err.println("AR_DEBUG: allocateReceiptToOrders Querying for customer=" + customerCode + " amount=" + receiptAmount);
        List<Map<String, Object>> unpaidOrders = jdbcTemplate.queryForList(
                "SELECT id, order_no, shipment_amount, paid_amount, order_date, import_shipment_date " +
                        "FROM finance_ar_order_payment_status " +
                        "WHERE is_deleted = 0 AND customer_code = ? " +
                        "AND GREATEST(IFNULL(shipment_amount, IFNULL(order_amount,0)) - IFNULL(paid_amount,0), 0) > 0 " +
                        "ORDER BY IFNULL(import_shipment_date, order_date) ASC, id ASC",
                customerCode
        );
        System.err.println("AR_DEBUG: found " + unpaidOrders.size() + " unpaid orders");

        for (Map<String, Object> row : unpaidOrders) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                System.err.println("AR_DEBUG: remaining is 0, BREAKING loop");
                break;
            }
            Long id = parseLong(row.get("id"));
            String orderNo = stringValue(row.get("order_no"));
            BigDecimal paidAmount = normalizeMoney(toDecimal(row.get("paid_amount")));
            BigDecimal shipmentAmount = normalizeMoney(toDecimal(row.get("shipment_amount")));
            if (shipmentAmount == null) {
                shipmentAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            BigDecimal unpaidAmount = normalizeMoney(shipmentAmount.subtract(paidAmount));
            if (unpaidAmount.compareTo(BigDecimal.ZERO) < 0) {
                unpaidAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }

            System.err.println("AR_DEBUG: Processing order=" + orderNo + " unpaidAmount=" + unpaidAmount);

            if (id == null || !StringUtils.hasText(orderNo) || unpaidAmount.compareTo(BigDecimal.ZERO) <= 0) {
                System.err.println("AR_DEBUG: skipping order due to invalid ID/No or zero unpaid");
                continue;
            }

            BigDecimal alloc = remaining.min(unpaidAmount);
            BigDecimal newPaidAmount = normalizeMoney(paidAmount.add(alloc));
            BigDecimal newUnpaidAmount = normalizeMoney(unpaidAmount.subtract(alloc));
            int paidFlag = newUnpaidAmount.compareTo(BigDecimal.ZERO) <= 0 ? 1 : 0;

            System.err.println("AR_DEBUG: Allocating " + alloc + " to " + orderNo + ". NewPaid=" + newPaidAmount + " NewUnpaid=" + newUnpaidAmount);

            jdbcTemplate.update(
                    "UPDATE finance_ar_order_payment_status " +
                        "SET paid_amount = ?, unpaid_amount = ?, is_paid = ?, updated_at = NOW() " +
                            "WHERE id = ?",
                    newPaidAmount,
                    newUnpaidAmount,
                    paidFlag,
                    id
            );

            jdbcTemplate.update(
                    "INSERT INTO finance_ar_order_payment_allocation(" +
                            "receipt_id, customer_code, order_no, allocation_amount, pay_date, created_by, created_at" +
                            ") VALUES (?, ?, ?, ?, ?, ?, NOW())",
                    receiptId,
                    customerCode,
                    orderNo,
                    alloc,
                    payDate,
                    operator
            );

            syncSalesOrderPaymentLifecycle(orderNo, newPaidAmount, newUnpaidAmount, operator);
            remaining = normalizeMoney(remaining.subtract(alloc));
            affectedOrders++;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("allocatedAmount", normalizeMoney(receiptAmount.subtract(remaining)));
        result.put("unallocatedAmount", remaining);
        result.put("affectedOrders", affectedOrders);
        return result;
    }

    private void syncSalesOrderPaymentLifecycle(String orderNo,
                                                BigDecimal paidAmount,
                                                BigDecimal unpaidAmount,
                                                String operator) {
        if (!StringUtils.hasText(orderNo)) {
            return;
        }
        List<Map<String, Object>> aggRows = jdbcTemplate.queryForList(
                "SELECT IFNULL(SUM(paid_amount),0) AS paidAmount, IFNULL(SUM(unpaid_amount),0) AS unpaidAmount " +
                        "FROM finance_ar_order_payment_status WHERE is_deleted = 0 AND order_no = ?",
                orderNo
        );
        if (!aggRows.isEmpty()) {
            Map<String, Object> agg = aggRows.get(0);
            BigDecimal aggPaid = normalizeMoney(toDecimal(agg.get("paidAmount")));
            BigDecimal aggUnpaid = normalizeMoney(toDecimal(agg.get("unpaidAmount")));
            if (aggPaid != null) {
                paidAmount = aggPaid;
            }
            if (aggUnpaid != null) {
                unpaidAmount = aggUnpaid;
            }
        }

        String targetStatus;
        if (unpaidAmount != null && unpaidAmount.compareTo(BigDecimal.ZERO) <= 0) {
            targetStatus = "PAID";
        } else if (paidAmount != null && paidAmount.compareTo(BigDecimal.ZERO) > 0) {
            targetStatus = "PAYMENT_PARTIAL";
        } else {
            return;
        }

        jdbcTemplate.update(
                "UPDATE sales_orders SET status = ?, updated_by = ?, updated_at = NOW() " +
                        "WHERE is_deleted = 0 AND order_no = ? " +
                        "AND UPPER(IFNULL(status, '')) NOT IN ('CANCELLED','CANCELED')",
                targetStatus,
                operator,
                orderNo
        );
    }

    private String determineReconcileStatus(BigDecimal receiptAmount,
                                            BigDecimal allocatedAmount,
                                            BigDecimal unallocatedAmount) {
        BigDecimal total = normalizeMoney(receiptAmount == null ? BigDecimal.ZERO : receiptAmount);
        BigDecimal allocated = normalizeMoney(allocatedAmount == null ? BigDecimal.ZERO : allocatedAmount);
        BigDecimal unallocated = normalizeMoney(unallocatedAmount == null ? BigDecimal.ZERO : unallocatedAmount);

        if (allocated.compareTo(BigDecimal.ZERO) <= 0) {
            return "UNRECONCILED";
        }
        if (unallocated.compareTo(BigDecimal.ZERO) > 0 && allocated.compareTo(total) < 0) {
            return "PARTIAL";
        }
        return "FULL";
    }

    private void ensureOrderPaymentTables() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS finance_ar_order_payment_status (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "customer_code VARCHAR(100) NOT NULL," +
                "order_no VARCHAR(100) NOT NULL," +
            "order_item_id BIGINT NULL," +
                "order_date DATE NOT NULL," +
                "statement_month VARCHAR(7) NULL," +
                "order_amount DECIMAL(18,2) NOT NULL DEFAULT 0," +
                "shipment_amount DECIMAL(18,2) NOT NULL DEFAULT 0," +
                "paid_amount DECIMAL(18,2) NOT NULL DEFAULT 0," +
                "unpaid_amount DECIMAL(18,2) NOT NULL DEFAULT 0," +
                "is_paid TINYINT NOT NULL DEFAULT 0," +
                "sync_token VARCHAR(128) NULL," +
                "import_customer_name VARCHAR(200) NULL," +
                "import_product_name VARCHAR(255) NULL," +
                "import_order_detail VARCHAR(255) NULL," +
                "import_order_spec VARCHAR(255) NULL," +
                "import_order_rolls DECIMAL(18,4) NULL," +
                "import_order_sqm DECIMAL(18,4) NULL," +
                "import_unit_price DECIMAL(18,4) NULL," +
                "created_by VARCHAR(64) NULL," +
                "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "is_deleted TINYINT NOT NULL DEFAULT 0," +
                "KEY idx_ar_order_customer_date (customer_code, order_date)," +
                "KEY idx_ar_order_statement (customer_code, statement_month)," +
                "KEY idx_ar_order_unpaid (customer_code, is_deleted, unpaid_amount)," +
                "KEY idx_ar_order_item (order_item_id)," +
                "KEY idx_ar_sync_token (sync_token)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        try {
            Integer hasStatementMonthColumn = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'finance_ar_order_payment_status' " +
                            "AND column_name = 'statement_month'",
                    Integer.class
            );
            if (hasStatementMonthColumn == null || hasStatementMonthColumn == 0) {
                jdbcTemplate.execute("ALTER TABLE finance_ar_order_payment_status ADD COLUMN statement_month VARCHAR(7) NULL AFTER order_date");
            }
        } catch (Exception ignored) {
            // 忽略非关键DDL异常，避免影响主流程
        }

        try {
            Integer idxStatement = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.statistics " +
                            "WHERE table_schema = DATABASE() AND table_name = 'finance_ar_order_payment_status' " +
                            "AND index_name = 'idx_ar_order_statement'",
                    Integer.class
            );
            if (idxStatement == null || idxStatement == 0) {
                jdbcTemplate.execute("ALTER TABLE finance_ar_order_payment_status ADD INDEX idx_ar_order_statement (customer_code, statement_month)");
            }
        } catch (Exception ignored) {
            // 忽略非关键DDL异常，避免影响主流程
        }

        try {
            Integer hasShipmentAmountColumn = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'finance_ar_order_payment_status' " +
                            "AND column_name = 'shipment_amount'",
                    Integer.class
            );
            if (hasShipmentAmountColumn == null || hasShipmentAmountColumn == 0) {
                jdbcTemplate.execute("ALTER TABLE finance_ar_order_payment_status ADD COLUMN shipment_amount DECIMAL(18,2) NOT NULL DEFAULT 0 AFTER order_amount");
                jdbcTemplate.execute("UPDATE finance_ar_order_payment_status SET shipment_amount = order_amount WHERE is_deleted = 0 AND IFNULL(shipment_amount,0) = 0");
            }
        } catch (Exception ignored) {
            // 忽略非关键DDL异常，避免影响主流程
        }

        try {
            Integer hasOrderItemColumn = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'finance_ar_order_payment_status' " +
                            "AND column_name = 'order_item_id'",
                    Integer.class
            );
            if (hasOrderItemColumn == null || hasOrderItemColumn == 0) {
                jdbcTemplate.execute("ALTER TABLE finance_ar_order_payment_status ADD COLUMN order_item_id BIGINT NULL AFTER order_no");
            }
        } catch (Exception ignored) {
            // 忽略非关键DDL异常，避免影响主流程
        }

        try {
            Integer hasSyncTokenColumn = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'finance_ar_order_payment_status' " +
                            "AND column_name = 'sync_token'",
                    Integer.class
            );
            if (hasSyncTokenColumn == null || hasSyncTokenColumn == 0) {
                jdbcTemplate.execute("ALTER TABLE finance_ar_order_payment_status ADD COLUMN sync_token VARCHAR(128) NULL AFTER is_paid");
            }
        } catch (Exception ignored) {
            // 忽略非关键DDL异常，避免影响主流程
        }

        try {
            Integer hasMatchStatusColumn = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'finance_ar_order_payment_status' " +
                            "AND column_name = 'match_status'",
                    Integer.class
            );
            if (hasMatchStatusColumn != null && hasMatchStatusColumn > 0) {
                jdbcTemplate.execute("ALTER TABLE finance_ar_order_payment_status DROP COLUMN match_status");
            }
        } catch (Exception ignored) {
            // 忽略非关键DDL异常，避免影响主流程
        }

        try {
            Integer hasMatchReasonColumn = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'finance_ar_order_payment_status' " +
                            "AND column_name = 'match_reason'",
                    Integer.class
            );
            if (hasMatchReasonColumn != null && hasMatchReasonColumn > 0) {
                jdbcTemplate.execute("ALTER TABLE finance_ar_order_payment_status DROP COLUMN match_reason");
            }
        } catch (Exception ignored) {
            // 忽略非关键DDL异常，避免影响主流程
        }

        try {
            Integer hasSourceColumn = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'finance_ar_order_payment_status' " +
                            "AND column_name = 'source'",
                    Integer.class
            );
            if (hasSourceColumn != null && hasSourceColumn > 0) {
                jdbcTemplate.execute("ALTER TABLE finance_ar_order_payment_status DROP COLUMN source");
            }
        } catch (Exception ignored) {
            // 忽略非关键DDL异常，避免影响主流程
        }

        try {
            Integer ukExists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.statistics " +
                            "WHERE table_schema = DATABASE() AND table_name = 'finance_ar_order_payment_status' " +
                            "AND index_name = 'uk_ar_order_no'",
                    Integer.class
            );
            if (ukExists != null && ukExists > 0) {
                jdbcTemplate.execute("ALTER TABLE finance_ar_order_payment_status DROP INDEX uk_ar_order_no");
            }
        } catch (Exception ignored) {
            // 忽略非关键DDL异常，避免影响主流程
        }

        try {
            Integer hasImportCustomerNameColumn = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'finance_ar_order_payment_status' " +
                            "AND column_name = 'import_customer_name'",
                    Integer.class
            );
            if (hasImportCustomerNameColumn == null || hasImportCustomerNameColumn == 0) {
                jdbcTemplate.execute("ALTER TABLE finance_ar_order_payment_status ADD COLUMN import_customer_name VARCHAR(200) NULL AFTER sync_token");
            }
        } catch (Exception ignored) {
            // 忽略非关键DDL异常，避免影响主流程
        }

        try {
            Integer hasImportProductNameColumn = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'finance_ar_order_payment_status' " +
                            "AND column_name = 'import_product_name'",
                    Integer.class
            );
            if (hasImportProductNameColumn == null || hasImportProductNameColumn == 0) {
                jdbcTemplate.execute("ALTER TABLE finance_ar_order_payment_status ADD COLUMN import_product_name VARCHAR(255) NULL AFTER import_customer_name");
            }
        } catch (Exception ignored) {
            // 忽略非关键DDL异常，避免影响主流程
        }

        try {
            Integer hasImportOrderDetailColumn = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'finance_ar_order_payment_status' " +
                            "AND column_name = 'import_order_detail'",
                    Integer.class
            );
            if (hasImportOrderDetailColumn == null || hasImportOrderDetailColumn == 0) {
                jdbcTemplate.execute("ALTER TABLE finance_ar_order_payment_status ADD COLUMN import_order_detail VARCHAR(255) NULL AFTER import_product_name");
            }
        } catch (Exception ignored) {
            // 忽略非关键DDL异常，避免影响主流程
        }

        try {
            Integer hasImportOrderSpecColumn = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'finance_ar_order_payment_status' " +
                            "AND column_name = 'import_order_spec'",
                    Integer.class
            );
            if (hasImportOrderSpecColumn == null || hasImportOrderSpecColumn == 0) {
                jdbcTemplate.execute("ALTER TABLE finance_ar_order_payment_status ADD COLUMN import_order_spec VARCHAR(255) NULL AFTER import_order_detail");
            }
        } catch (Exception ignored) {
            // 忽略非关键DDL异常，避免影响主流程
        }

        try {
            Integer hasImportOrderRollsColumn = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'finance_ar_order_payment_status' " +
                            "AND column_name = 'import_order_rolls'",
                    Integer.class
            );
            if (hasImportOrderRollsColumn == null || hasImportOrderRollsColumn == 0) {
                jdbcTemplate.execute("ALTER TABLE finance_ar_order_payment_status ADD COLUMN import_order_rolls DECIMAL(18,4) NULL AFTER import_order_spec");
            }
        } catch (Exception ignored) {
            // 忽略非关键DDL异常，避免影响主流程
        }

        try {
            Integer hasImportOrderSqmColumn = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'finance_ar_order_payment_status' " +
                            "AND column_name = 'import_order_sqm'",
                    Integer.class
            );
            if (hasImportOrderSqmColumn == null || hasImportOrderSqmColumn == 0) {
                jdbcTemplate.execute("ALTER TABLE finance_ar_order_payment_status ADD COLUMN import_order_sqm DECIMAL(18,4) NULL AFTER import_order_rolls");
            }
        } catch (Exception ignored) {
            // 忽略非关键DDL异常，避免影响主流程
        }

        try {
            Integer hasImportUnitPriceColumn = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'finance_ar_order_payment_status' " +
                            "AND column_name = 'import_unit_price'",
                    Integer.class
            );
            if (hasImportUnitPriceColumn == null || hasImportUnitPriceColumn == 0) {
                jdbcTemplate.execute("ALTER TABLE finance_ar_order_payment_status ADD COLUMN import_unit_price DECIMAL(18,4) NULL AFTER import_order_sqm");
            }
        } catch (Exception ignored) {
            // 忽略非关键DDL异常，避免影响主流程
        }

        try {
            Integer hasImportShipmentDateColumn = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.columns " +
                            "WHERE table_schema = DATABASE() AND table_name = 'finance_ar_order_payment_status' " +
                            "AND column_name = 'import_shipment_date'",
                    Integer.class
            );
            if (hasImportShipmentDateColumn == null || hasImportShipmentDateColumn == 0) {
                jdbcTemplate.execute("ALTER TABLE finance_ar_order_payment_status ADD COLUMN import_shipment_date DATE NULL AFTER import_unit_price");
            }
        } catch (Exception ignored) {
            // 忽略非关键DDL异常，避免影响主流程
        }

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS finance_ar_order_payment_allocation (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "receipt_id BIGINT NULL," +
                "customer_code VARCHAR(100) NOT NULL," +
                "order_no VARCHAR(100) NOT NULL," +
                "allocation_amount DECIMAL(18,2) NOT NULL DEFAULT 0," +
                "pay_date DATE NULL," +
                "created_by VARCHAR(64) NULL," +
                "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "KEY idx_ar_alloc_receipt (receipt_id)," +
                "KEY idx_ar_alloc_order (order_no)," +
                "KEY idx_ar_alloc_customer_date (customer_code, pay_date)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeScale(BigDecimal value, int scale) {
        if (value == null) {
            return null;
        }
        return value.setScale(scale, RoundingMode.HALF_UP);
    }

    private Date parseDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date) {
            return (Date) value;
        }
        if (value instanceof java.util.Date) {
            return new Date(((java.util.Date) value).getTime());
        }
        if (value instanceof Number) {
            try {
                // Excel 序列日期（1900 日期系统）
                long days = ((Number) value).longValue();
                LocalDate base = LocalDate.of(1899, 12, 30);
                LocalDate d = base.plusDays(days);
                return Date.valueOf(d);
            } catch (Exception ignored) {
                // ignore
            }
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }

        if (text.matches("^\\d+(?:\\.0+)?$")) {
            try {
                long days = Long.parseLong(text.replaceAll("\\.0+$", ""));
                LocalDate base = LocalDate.of(1899, 12, 30);
                LocalDate d = base.plusDays(days);
                return Date.valueOf(d);
            } catch (Exception ignored) {
                // ignore
            }
        }

        String normalized = text.replace('.', '-').replace('/', '-').replace("年", "-").replace("月", "-").replace("日", "").trim();
        normalized = normalized.replaceAll("\\s+", "");
        // M-d / MM-dd 视为当年
        if (normalized.matches("^\\d{1,2}-\\d{1,2}$")) {
            normalized = LocalDate.now().getYear() + "-" + normalized;
        }
        // yyyy-M-d 归一
        if (normalized.matches("^\\d{4}-\\d{1,2}-\\d{1,2}$")) {
            String[] arr = normalized.split("-");
            normalized = arr[0] + "-" + String.format("%02d", Integer.parseInt(arr[1])) + "-" + String.format("%02d", Integer.parseInt(arr[2]));
        }

        try {
            return Date.valueOf(normalized);
        } catch (Exception ex) {
            return null;
        }
    }

    private Boolean parseBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        if ("1".equals(text) || "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text) || "y".equalsIgnoreCase(text)) {
            return true;
        }
        if ("0".equals(text) || "false".equalsIgnoreCase(text) || "no".equalsIgnoreCase(text) || "n".equalsIgnoreCase(text)) {
            return false;
        }
        return null;
    }

    private Date firstDateValue(Map<String, Object> row, String... keys) {
        if (row == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (!StringUtils.hasText(key)) {
                continue;
            }
            Object value = row.get(key);
            Date d = parseDate(value);
            if (d != null) {
                return d;
            }
        }
        return null;
    }

    private Object firstRawValue(Map<String, Object> row, String... keys) {
        if (row == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (!StringUtils.hasText(key)) {
                continue;
            }
            Object value = row.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof String && !StringUtils.hasText((String) value)) {
                continue;
            }
            return value;
        }
        return null;
    }

    private Date parseDateWithReferenceYear(Object value, Date referenceDate) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date || value instanceof java.util.Date || value instanceof Number) {
            return parseDate(value);
        }

        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text)) {
            return null;
        }

        String normalized = text.replace('.', '-').replace('/', '-').replace("年", "-").replace("月", "-").replace("日", "").trim();
        normalized = normalized.replaceAll("\\s+", "");

        if (normalized.matches("^\\d{1,2}-\\d{1,2}$")) {
            int year = LocalDate.now().getYear();
            if (referenceDate != null) {
                year = referenceDate.toLocalDate().getYear();
            }
            String[] arr = normalized.split("-");
            String fixed = year + "-" + String.format("%02d", Integer.parseInt(arr[0])) + "-" + String.format("%02d", Integer.parseInt(arr[1]));
            try {
                return Date.valueOf(fixed);
            } catch (Exception ex) {
                return null;
            }
        }
        return parseDate(value);
    }

    private Integer parseInt(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private Long parseLong(Object value) {
        if (value == null) return null;
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null) return "system";
            Object principal = authentication.getPrincipal();
            if (principal instanceof LoginUser) {
                LoginUser loginUser = (LoginUser) principal;
                if (loginUser.getUser() != null && StringUtils.hasText(loginUser.getUser().getUsername())) {
                    return loginUser.getUser().getUsername();
                }
            }
            String name = authentication.getName();
            return StringUtils.hasText(name) ? name : "system";
        } catch (Exception ex) {
            return "system";
        }
    }

    private void ensureReceiptTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS finance_ar_receipt (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "customer_code VARCHAR(100) NOT NULL," +
                "customer_name VARCHAR(200) NULL," +
                "amount DECIMAL(18,2) NOT NULL DEFAULT 0," +
                "bank_account_id BIGINT NULL," +
                "bank_name VARCHAR(100) NULL," +
                "bank_account VARCHAR(100) NULL," +
                "pay_date DATE NOT NULL," +
                "reconcile_status VARCHAR(16) NOT NULL DEFAULT 'UNRECONCILED'," +
                "allocated_amount DECIMAL(18,2) NOT NULL DEFAULT 0," +
                "unallocated_amount DECIMAL(18,2) NOT NULL DEFAULT 0," +
                "reconciled_at DATETIME NULL," +
                "reconciled_by VARCHAR(64) NULL," +
                "registrar VARCHAR(64) NULL," +
                "created_by VARCHAR(64) NULL," +
                "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "is_deleted TINYINT NOT NULL DEFAULT 0," +
                "KEY idx_ar_receipt_customer_date (customer_code, pay_date)," +
                "KEY idx_ar_receipt_deleted_date (is_deleted, pay_date)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            jdbcTemplate.execute("ALTER TABLE finance_ar_receipt MODIFY COLUMN bank_account_id BIGINT NULL");

            try {
                Integer hasReconcileStatus = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'finance_ar_receipt' AND column_name = 'reconcile_status'",
                        Integer.class
                );
                if (hasReconcileStatus == null || hasReconcileStatus == 0) {
                    jdbcTemplate.execute("ALTER TABLE finance_ar_receipt ADD COLUMN reconcile_status VARCHAR(16) NOT NULL DEFAULT 'UNRECONCILED' AFTER pay_date");
                }
            } catch (Exception ignored) {
            }

            try {
                Integer hasAllocatedAmount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'finance_ar_receipt' AND column_name = 'allocated_amount'",
                        Integer.class
                );
                if (hasAllocatedAmount == null || hasAllocatedAmount == 0) {
                    jdbcTemplate.execute("ALTER TABLE finance_ar_receipt ADD COLUMN allocated_amount DECIMAL(18,2) NOT NULL DEFAULT 0 AFTER reconcile_status");
                }
            } catch (Exception ignored) {
            }

            try {
                Integer hasUnallocatedAmount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'finance_ar_receipt' AND column_name = 'unallocated_amount'",
                        Integer.class
                );
                if (hasUnallocatedAmount == null || hasUnallocatedAmount == 0) {
                    jdbcTemplate.execute("ALTER TABLE finance_ar_receipt ADD COLUMN unallocated_amount DECIMAL(18,2) NOT NULL DEFAULT 0 AFTER allocated_amount");
                }
            } catch (Exception ignored) {
            }

            try {
                Integer hasReconciledAt = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'finance_ar_receipt' AND column_name = 'reconciled_at'",
                        Integer.class
                );
                if (hasReconciledAt == null || hasReconciledAt == 0) {
                    jdbcTemplate.execute("ALTER TABLE finance_ar_receipt ADD COLUMN reconciled_at DATETIME NULL AFTER unallocated_amount");
                }
            } catch (Exception ignored) {
            }

            try {
                Integer hasReconciledBy = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'finance_ar_receipt' AND column_name = 'reconciled_by'",
                        Integer.class
                );
                if (hasReconciledBy == null || hasReconciledBy == 0) {
                    jdbcTemplate.execute("ALTER TABLE finance_ar_receipt ADD COLUMN reconciled_by VARCHAR(64) NULL AFTER reconciled_at");
                }
            } catch (Exception ignored) {
            }

            try {
                jdbcTemplate.execute(
                        "UPDATE finance_ar_receipt SET unallocated_amount = amount " +
                                "WHERE IFNULL(allocated_amount,0)=0 " +
                                "AND IFNULL(unallocated_amount,0)=0 " +
                                "AND IFNULL(reconcile_status,'UNRECONCILED') IN ('UNRECONCILED','')"
                );
            } catch (Exception ignored) {
            }
    }
}
