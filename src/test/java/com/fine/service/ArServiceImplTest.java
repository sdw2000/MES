package com.fine.service;

import com.fine.Dao.ArInvoiceItemMapper;
import com.fine.Dao.ArInvoiceMapper;
import com.fine.Dao.GlEntryMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.ArInvoice;
import com.fine.serviceIMPL.ArServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ArInvoiceMapper arInvoiceMapper;

    @Mock
    private ArInvoiceItemMapper arInvoiceItemMapper;

    @Mock
    private GlEntryMapper glEntryMapper;

    private ArServiceImpl arService;

    @BeforeEach
    void setUp() {
        arService = new ArServiceImpl();
        ReflectionTestUtils.setField(Objects.requireNonNull(arService), "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(Objects.requireNonNull(arService), "arInvoiceMapper", arInvoiceMapper);
        ReflectionTestUtils.setField(Objects.requireNonNull(arService), "arInvoiceItemMapper", arInvoiceItemMapper);
        ReflectionTestUtils.setField(Objects.requireNonNull(arService), "glEntryMapper", glEntryMapper);
    }

    @Test
    void createInvoice_shouldRejectNullPayload() {
        ResponseResult<?> result = arService.createInvoice(null);

        assertEquals(400, result.getCode());
        assertEquals("payload required", result.getMsg());
        verifyNoInteractions(jdbcTemplate, arInvoiceMapper, arInvoiceItemMapper, glEntryMapper);
    }

    @Test
    void createInvoice_shouldRejectInvalidDate() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("invoice_no", "AR-001");
        payload.put("customer_code", "C001");
        payload.put("invoice_date", "bad-date");
        payload.put("total_amount", 100);

        ResponseResult<?> result = arService.createInvoice(payload);

        assertEquals(400, result.getCode());
        assertEquals("invalid invoice_date", result.getMsg());
        verifyNoInteractions(jdbcTemplate, arInvoiceMapper, arInvoiceItemMapper, glEntryMapper);
    }

    @Test
    void createAndPostInvoice_shouldCreateInvoiceItemsAndGlEntries() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("invoice_no", "AR-POST-001");
        payload.put("customer_code", "C001");
        payload.put("invoice_date", "2026-05-01");
        payload.put("total_amount", 300.50);
        payload.put("debit_account_id", 101L);
        payload.put("credit_account_id", 201L);
        payload.put("items", Arrays.asList(
                item("SO001", "MAT-01", 2, 50),
                item("SO002", "MAT-02", 1, 200.5)
        ));

        doAnswer((InvocationOnMock invocation) -> {
            ArInvoice invoice = invocation.getArgument(0);
            invoice.setId(99L);
            return 1;
        }).when(arInvoiceMapper).insert(any(ArInvoice.class));
        when(arInvoiceItemMapper.insert(any())).thenReturn(1);
        when(glEntryMapper.insert(any())).thenReturn(1);

        ResponseResult<?> result = arService.createAndPostInvoice(payload);

        assertEquals(200, result.getCode());
        assertEquals("created_and_posted", result.getMsg());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals(99L, data.get("invoiceId"));
        verify(arInvoiceMapper, times(1)).insert(any(ArInvoice.class));
        verify(arInvoiceItemMapper, times(2)).insert(any());
        verify(glEntryMapper, times(2)).insert(any());
    }

    @Test
    void createAndPostInvoice_shouldRejectNonPositiveAmount() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("invoice_no", "AR-POST-002");
        payload.put("customer_code", "C001");
        payload.put("invoice_date", "2026-05-01");
        payload.put("total_amount", 0);
        payload.put("debit_account_id", 101L);
        payload.put("credit_account_id", 201L);

        ResponseResult<?> result = arService.createAndPostInvoice(payload);

        assertEquals(400, result.getCode());
        assertTrue(result.getMsg().contains("positive"));
        verifyNoInteractions(jdbcTemplate, arInvoiceMapper, arInvoiceItemMapper, glEntryMapper);
    }

    private Map<String, Object> item(String orderNo, String materialCode, double quantity, double unitPrice) {
        Map<String, Object> item = new HashMap<>();
        item.put("order_no", orderNo);
        item.put("material_code", materialCode);
        item.put("quantity", quantity);
        item.put("unit_price", unitPrice);
        item.put("description", materialCode + " desc");
        return item;
    }
}