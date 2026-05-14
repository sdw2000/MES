package com.fine.service;

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
import com.fine.serviceIMPL.BankServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private BankAccountMapper bankAccountMapper;

    @Mock
    private BankTransactionMapper bankTransactionMapper;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private ArInvoiceMapper arInvoiceMapper;

    @Mock
    private GlEntryMapper glEntryMapper;

    private BankServiceImpl bankService;

    @BeforeEach
    void setUp() {
        bankService = new BankServiceImpl();
        ReflectionTestUtils.setField(Objects.requireNonNull(bankService), "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(Objects.requireNonNull(bankService), "bankAccountMapper", bankAccountMapper);
        ReflectionTestUtils.setField(Objects.requireNonNull(bankService), "bankTransactionMapper", bankTransactionMapper);
        ReflectionTestUtils.setField(Objects.requireNonNull(bankService), "paymentMapper", paymentMapper);
        ReflectionTestUtils.setField(Objects.requireNonNull(bankService), "arInvoiceMapper", arInvoiceMapper);
        ReflectionTestUtils.setField(Objects.requireNonNull(bankService), "glEntryMapper", glEntryMapper);
    }

    @Test
    void applyPayment_shouldRejectNonPositiveAmount() {
        Map<String, Object> payload = basePayload();
        payload.put("amount", 0);

        ResponseResult<?> result = bankService.applyPayment(payload);

        assertEquals(400, result.getCode());
        assertEquals("amount must be positive", result.getMsg());
        verifyNoInteractions(jdbcTemplate, bankAccountMapper, bankTransactionMapper, paymentMapper, arInvoiceMapper, glEntryMapper);
    }

    @Test
    void applyPayment_shouldRejectInvalidRelatedInvoiceBeforeWrites() {
        Map<String, Object> payload = basePayload();
        payload.put("related_invoice_id", "bad-id");

        ResponseResult<?> result = bankService.applyPayment(payload);

        assertEquals(400, result.getCode());
        assertEquals("invalid related_invoice_id", result.getMsg());
        verifyNoInteractions(jdbcTemplate, bankAccountMapper, bankTransactionMapper, paymentMapper, arInvoiceMapper, glEntryMapper);
    }

    @Test
    void applyPayment_shouldUpdateBalanceAndMarkInvoicePaid() {
        Map<String, Object> payload = basePayload();
        payload.put("amount", 150);
        payload.put("related_invoice_id", 9L);

        doAnswer((InvocationOnMock invocation) -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(66L);
            return 1;
        }).when(paymentMapper).insert(any(Payment.class));
        when(bankAccountMapper.selectOne(any())).thenReturn(bankAccount(1L, "100.00"));
        when(arInvoiceMapper.selectOne(any())).thenReturn(invoice(9L, 300, 150));
        when(bankAccountMapper.updateById(any())).thenReturn(1);
        when(bankTransactionMapper.insert(any())).thenReturn(1);
        when(arInvoiceMapper.updateById(any())).thenReturn(1);
        when(glEntryMapper.insert(any())).thenReturn(1);

        ResponseResult<?> result = bankService.applyPayment(payload);

        assertEquals(200, result.getCode());
        assertEquals("payment_applied", result.getMsg());
        verify(bankAccountMapper).updateById(argThat(acct -> acct.getCurrentBalance().compareTo(new java.math.BigDecimal("250.00")) == 0));
        verify(arInvoiceMapper).updateById(argThat(inv -> "PAID".equals(inv.getStatus())));
        verify(bankTransactionMapper, times(1)).insert(any(BankTransaction.class));
        verify(glEntryMapper, times(2)).insert(any(GlEntry.class));
    }

    @Test
    void applyPayment_shouldMarkInvoicePartialWhenNotFullyPaid() {
        Map<String, Object> payload = basePayload();
        payload.put("related_invoice_id", 9L);

        doAnswer((InvocationOnMock invocation) -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(66L);
            return 1;
        }).when(paymentMapper).insert(any(Payment.class));
        when(bankAccountMapper.selectOne(any())).thenReturn(bankAccount(1L, "100.00"));
        when(arInvoiceMapper.selectOne(any())).thenReturn(invoice(9L, 500, 200));
        when(bankAccountMapper.updateById(any())).thenReturn(1);
        when(bankTransactionMapper.insert(any())).thenReturn(1);
        when(arInvoiceMapper.updateById(any())).thenReturn(1);
        when(glEntryMapper.insert(any())).thenReturn(1);

        ResponseResult<?> result = bankService.applyPayment(payload);

        assertEquals(200, result.getCode());
        verify(arInvoiceMapper).updateById(argThat(inv -> "PARTIAL".equals(inv.getStatus())));
        verify(arInvoiceMapper, never()).updateById(argThat(inv -> "PAID".equals(inv.getStatus())));
    }

    private Map<String, Object> basePayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("payment_no", "PAY-001");
        payload.put("payment_date", "2026-05-01");
        payload.put("amount", 100);
        payload.put("method", "bank_transfer");
        payload.put("bank_account_id", 1L);
        payload.put("debit_account_id", 101L);
        payload.put("credit_account_id", 201L);
        return payload;
    }

    private ArInvoice invoice(Long id, double totalAmount, double paidAmount) {
        ArInvoice invoice = new ArInvoice();
        invoice.setId(id);
        invoice.setTotalAmount(java.math.BigDecimal.valueOf(totalAmount));
        invoice.setPaidAmount(java.math.BigDecimal.valueOf(paidAmount));
        invoice.setStatus("OPEN");
        invoice.setIsDeleted(0);
        return invoice;
    }

    private BankAccount bankAccount(Long id, String currentBalance) {
        BankAccount account = new BankAccount();
        account.setId(id);
        account.setCurrentBalance(new java.math.BigDecimal(currentBalance));
        account.setIsDeleted(0);
        return account;
    }
}