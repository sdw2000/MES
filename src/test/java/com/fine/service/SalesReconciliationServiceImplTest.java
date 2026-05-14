package com.fine.service;

import com.fine.Utils.ResponseResult;
import com.fine.serviceIMPL.SalesReconciliationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesReconciliationServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private SalesReconciliationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SalesReconciliationServiceImpl();
        ReflectionTestUtils.setField(Objects.requireNonNull(service), "jdbcTemplate", jdbcTemplate);
    }

    @Test
    void adminDiagnoseDeletedConfirms_shouldRejectInvalidIds() {
        ResponseResult<?> result = service.adminDiagnoseDeletedConfirms("abc,def");

        assertEquals(400, result.getCode());
        assertEquals("no valid noticeItemId provided", result.getMsg());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void adminDiagnoseDeletedConfirms_withoutIdsShouldReturnRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(java.util.Collections.singletonMap("notice_item_id", 101L));
        when(jdbcTemplate.queryForList("SELECT notice_item_id, statement_month, is_deleted, updated_by, updated_at FROM sales_statement_delivery_confirm WHERE is_deleted != 0 ORDER BY updated_at DESC LIMIT 200"))
                .thenReturn(rows);

        ResponseResult<?> result = service.adminDiagnoseDeletedConfirms(null);

        assertEquals(200, result.getCode());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals(rows, data.get("rows"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void adminClearOverviewCache_shouldClearEntries() {
        Object fieldValue = ReflectionTestUtils.getField(Objects.requireNonNull(service), "overviewCache");
        assertNotNull(fieldValue);
        ConcurrentMap<String, Object> cache = (ConcurrentMap<String, Object>) fieldValue;
        cache.put("C001_2026-05", new Object());

        ResponseResult<?> result = service.adminClearOverviewCache();

        assertEquals(200, result.getCode());
        assertTrue(cache.isEmpty());
    }
}