package com.fine.serviceIMPL;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fine.Dao.ArInvoiceMapper;
import com.fine.Dao.BankAccountMapper;
import com.fine.Dao.BankTransactionMapper;
import com.fine.Dao.GlEntryMapper;
import com.fine.Dao.PaymentMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.ArInvoice;
import com.fine.modle.BankAccount;
import com.fine.modle.BankTransaction;
import com.fine.modle.GlEntry;
import com.fine.modle.Payment;
import com.fine.service.BankService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.HashMap;

import java.util.List;
import java.util.Map;

@Service
public class BankServiceImpl implements BankService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BankAccountMapper bankAccountMapper;

    @Autowired
    private BankTransactionMapper bankTransactionMapper;

    @Autowired
    private PaymentMapper paymentMapper;

    @Autowired
    private ArInvoiceMapper arInvoiceMapper;

    @Autowired
    private GlEntryMapper glEntryMapper;

    @Override
    public ResponseResult<?> listAccounts(Map<String, Object> params) {
        List<BankAccount> rows = bankAccountMapper.selectList(new LambdaQueryWrapper<BankAccount>()
                .eq(BankAccount::getIsDeleted, 0)
                .orderByAsc(BankAccount::getId));
        return new ResponseResult<>(200, "OK", rows);
    }

    @Override
    public ResponseResult<?> listTransactions(Long accountId, Map<String, Object> params) {
        if (accountId == null) return new ResponseResult<>(400, "accountId required", null);
        List<BankTransaction> rows = bankTransactionMapper.selectList(new LambdaQueryWrapper<BankTransaction>()
                .eq(BankTransaction::getBankAccountId, accountId)
                .eq(BankTransaction::getIsDeleted, 0)
                .orderByDesc(BankTransaction::getTxnDate)
                .last("LIMIT 500"));
        return new ResponseResult<>(200, "OK", rows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> applyPayment(Map<String, Object> payload) {
        if (payload == null) return new ResponseResult<>(400, "payload required", null);
        String paymentNo = String.valueOf(payload.getOrDefault("payment_no", "")).trim();
        String paymentDate = String.valueOf(payload.getOrDefault("payment_date", "")).trim();
        Object amountObj = payload.get("amount");
        String method = String.valueOf(payload.getOrDefault("method", ""));
        Object bankAccountIdObj = payload.get("bank_account_id");
        Object debitAcctObj = payload.get("debit_account_id");
        Object creditAcctObj = payload.get("credit_account_id");
        Object relatedInvoiceObj = payload.get("related_invoice_id");

        if (paymentNo.isEmpty() || paymentDate.isEmpty() || amountObj == null || bankAccountIdObj == null || debitAcctObj == null || creditAcctObj == null) {
            return new ResponseResult<>(400, "missing required fields", null);
        }
        double amount;
        try {
            amount = amountObj instanceof Number ? ((Number) amountObj).doubleValue() : Double.parseDouble(String.valueOf(amountObj));
        } catch (Exception ex) {
            return new ResponseResult<>(400, "invalid amount", null);
        }
        if (amount <= 0) {
            return new ResponseResult<>(400, "amount must be positive", null);
        }
        long bankAccountId;
        long debitAccountId;
        long creditAccountId;
        try {
            bankAccountId = bankAccountIdObj instanceof Number ? ((Number) bankAccountIdObj).longValue() : Long.parseLong(String.valueOf(bankAccountIdObj));
            debitAccountId = debitAcctObj instanceof Number ? ((Number) debitAcctObj).longValue() : Long.parseLong(String.valueOf(debitAcctObj));
            creditAccountId = creditAcctObj instanceof Number ? ((Number) creditAcctObj).longValue() : Long.parseLong(String.valueOf(creditAcctObj));
        } catch (Exception ex) {
            return new ResponseResult<>(400, "invalid account ids", null);
        }
        Date parsedPaymentDate;
        try {
            parsedPaymentDate = Date.valueOf(paymentDate);
        } catch (Exception ex) {
            return new ResponseResult<>(400, "invalid payment_date", null);
        }
        Long relatedInvoiceId = null;
        if (relatedInvoiceObj != null) {
            try {
                relatedInvoiceId = relatedInvoiceObj instanceof Number
                        ? ((Number) relatedInvoiceObj).longValue()
                        : Long.parseLong(String.valueOf(relatedInvoiceObj));
            } catch (Exception ex) {
                return new ResponseResult<>(400, "invalid related_invoice_id", null);
            }
        }

        Payment payment = new Payment();
        payment.setPaymentNo(paymentNo);
        payment.setPaymentDate(parsedPaymentDate);
        payment.setPayer(valueOrNull(payload.get("payer")));
        payment.setPayee(valueOrNull(payload.get("payee")));
        payment.setAmount(BigDecimal.valueOf(amount));
        payment.setCurrency(String.valueOf(payload.getOrDefault("currency", "CNY")));
        payment.setMethod(method);
        payment.setBankAccountId(bankAccountId);
        payment.setRelatedInvoiceId(relatedInvoiceId);
        payment.setRelatedBillId(null);
        payment.setReference(valueOrNull(payload.get("reference")));
        payment.setStatus("POSTED");
        payment.setIsDeleted(0);
        paymentMapper.insert(payment);
        Long paymentId = payment.getId();

        BankAccount bankAccount = bankAccountMapper.selectOne(new LambdaQueryWrapper<BankAccount>()
            .eq(BankAccount::getId, bankAccountId)
            .eq(BankAccount::getIsDeleted, 0)
            .last("LIMIT 1"));
        if (bankAccount == null) {
            return new ResponseResult<>(400, "bank account not found", null);
        }
        BigDecimal currentBalance = bankAccount.getCurrentBalance() == null ? BigDecimal.ZERO : bankAccount.getCurrentBalance();
        BigDecimal updatedBalance = currentBalance.add(BigDecimal.valueOf(amount));
        bankAccount.setCurrentBalance(updatedBalance);
        bankAccountMapper.updateById(bankAccount);

        BankTransaction txn = new BankTransaction();
        txn.setBankAccountId(bankAccountId);
        txn.setTxnDate(parsedPaymentDate);
        txn.setAmount(BigDecimal.valueOf(amount));
        txn.setBalance(updatedBalance);
        txn.setTxnType("CREDIT");
        txn.setDescription(valueOrNull(payload.get("description")));
        txn.setExternalRef(valueOrNull(payload.get("external_ref")));
        txn.setIsReconciled(0);
        txn.setIsDeleted(0);
        bankTransactionMapper.insert(txn);

        if (relatedInvoiceId != null) {
            long invId = relatedInvoiceId;
            ArInvoice invoice = arInvoiceMapper.selectOne(new LambdaQueryWrapper<ArInvoice>()
                .eq(ArInvoice::getId, invId)
                .eq(ArInvoice::getIsDeleted, 0)
                .last("LIMIT 1"));
            if (invoice == null) {
            return new ResponseResult<>(400, "related invoice not found", null);
            }
            BigDecimal paidAmount = invoice.getPaidAmount() == null ? BigDecimal.ZERO : invoice.getPaidAmount();
            BigDecimal totalAmount = invoice.getTotalAmount() == null ? BigDecimal.ZERO : invoice.getTotalAmount();
            paidAmount = paidAmount.add(BigDecimal.valueOf(amount));
            invoice.setPaidAmount(paidAmount);
            String newStatus = paidAmount.compareTo(totalAmount) >= 0 ? "PAID"
                : (paidAmount.compareTo(BigDecimal.ZERO) > 0 ? "PARTIAL" : "OPEN");
            invoice.setStatus(newStatus);
            arInvoiceMapper.updateById(invoice);
        }

        String voucherNo = "PAY-" + paymentNo;
        GlEntry debitEntry = new GlEntry();
        debitEntry.setVoucherNo(voucherNo);
        debitEntry.setEntryDate(parsedPaymentDate);
        debitEntry.setGlAccountId(debitAccountId);
        debitEntry.setDebit(BigDecimal.valueOf(amount));
        debitEntry.setCredit(BigDecimal.ZERO);
        debitEntry.setCurrency("CNY");
        debitEntry.setSourceType("payment");
        debitEntry.setSourceId(paymentId);
        debitEntry.setIsDeleted(0);
        glEntryMapper.insert(debitEntry);

        GlEntry creditEntry = new GlEntry();
        creditEntry.setVoucherNo(voucherNo);
        creditEntry.setEntryDate(parsedPaymentDate);
        creditEntry.setGlAccountId(creditAccountId);
        creditEntry.setDebit(BigDecimal.ZERO);
        creditEntry.setCredit(BigDecimal.valueOf(amount));
        creditEntry.setCurrency("CNY");
        creditEntry.setSourceType("payment");
        creditEntry.setSourceId(paymentId);
        creditEntry.setIsDeleted(0);
        glEntryMapper.insert(creditEntry);

        // audit log
        try {
            jdbcTemplate.update("INSERT INTO audit_log (username, action, target_table, target_id, after_json, created_at) VALUES (?, ?, ?, ?, ?, NOW())",
                "system", "APPLY_PAYMENT", "payment", paymentId, "{\"payment_no\":\"" + paymentNo + "\"}");
        } catch (Exception ignore) {}

        Map<String, Object> res = new HashMap<>();
        res.put("paymentId", paymentId);
        res.put("paymentNo", paymentNo);
        return new ResponseResult<>(200, "payment_applied", res);
    }

    private String valueOrNull(Object val) {
        if (val == null) {
            return null;
        }
        String s = String.valueOf(val).trim();
        return s.isEmpty() ? null : s;
    }
}
