package com.fine.serviceIMPL;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fine.Dao.ArInvoiceItemMapper;
import com.fine.Dao.ArInvoiceMapper;
import com.fine.Dao.GlEntryMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.ArInvoice;
import com.fine.modle.ArInvoiceItem;
import com.fine.modle.GlEntry;
import com.fine.service.ArService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    @Override
    public ResponseResult<?> listInvoices(Map<String, Object> params) {
        LambdaQueryWrapper<ArInvoice> qw = new LambdaQueryWrapper<ArInvoice>()
                .eq(ArInvoice::getIsDeleted, 0)
                .orderByDesc(ArInvoice::getInvoiceDate)
                .last("LIMIT 200");
        List<ArInvoice> rows = arInvoiceMapper.selectList(qw);
        return new ResponseResult<>(200, "OK", rows);
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
}
