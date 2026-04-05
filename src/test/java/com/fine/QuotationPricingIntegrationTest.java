package com.fine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fine.Dao.CustomerMapper;
import com.fine.Dao.QuotationItemMapper;
import com.fine.Dao.QuotationItemVersionMapper;
import com.fine.Dao.QuotationMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.LoginUser;
import com.fine.modle.Quotation;
import com.fine.modle.QuotationItem;
import com.fine.modle.QuotationItemVersion;
import com.fine.modle.User;
import com.fine.serviceIMPL.QuotationServiceImpl;

@SpringBootTest
public class QuotationPricingIntegrationTest {

    @Mock
    private QuotationMapper quotationMapper;

    @Mock
    private QuotationItemMapper quotationItemMapper;

    @Mock
    private QuotationItemVersionMapper quotationItemVersionMapper;

    @Mock
    private CustomerMapper customerMapper;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private QuotationServiceImpl quotationService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);

        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        LoginUser loginUser = new LoginUser(user, Collections.singletonList("admin"));
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        doNothing().when(jdbcTemplate).execute(anyString());
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(0);

        when(quotationMapper.insert(any(Quotation.class))).thenAnswer(invocation -> {
            Quotation q = invocation.getArgument(0);
            if (q.getId() == null) {
                q.setId(1L);
            }
            return 1;
        });

        when(quotationItemMapper.insert(any(QuotationItem.class))).thenAnswer(invocation -> {
            QuotationItem item = invocation.getArgument(0);
            if (item.getId() == null) {
                item.setId(10L);
            }
            return 1;
        });

        when(quotationItemVersionMapper.selectOne(any())).thenReturn(null);
        when(quotationItemVersionMapper.insert(any(QuotationItemVersion.class))).thenReturn(1);
    }

    @Test
    public void testCreateQuotation_PricingMetadataPersistedInResponse() {
        Quotation quotation = new Quotation();
        quotation.setCustomer("AHYC5001");
        quotation.setContactPerson("测试联系人");
        quotation.setContactPhone("13800000000");
        quotation.setQuotationDate(new Date());
        quotation.setValidUntil(new Date());
        quotation.setStatus("draft");
        quotation.setRemark("E2E测试报价");
        quotation.setPriceStatus("PENDING");
        quotation.setNeedsPricing(Boolean.TRUE);

        QuotationItem item = new QuotationItem();
        item.setMaterialCode("TEST-MAT-001");
        item.setMaterialName("测试物料");
        item.setColorCode("红");
        item.setThickness(new BigDecimal("100"));
        item.setWidth(new BigDecimal("50"));
        item.setLength(new BigDecimal("1000"));
        item.setUnit("㎡");
        item.setUnitPrice(null);
        item.setAppliedRuleId(12345L);
        item.setMatchPath("strict-spec");
        item.setRemark("E2E测试明细");
        quotation.setItems(Collections.singletonList(item));

        ResponseResult<?> result = quotationService.createQuotation(quotation);
        assertNotNull(result);
        assertEquals(200, result.getCode());

        Quotation saved = (Quotation) result.getData();
        assertNotNull(saved);
        assertEquals("PENDING", saved.getPriceStatus());
        assertEquals(Boolean.TRUE, saved.getNeedsPricing());
        assertNotNull(saved.getItems());
        assertEquals(1, saved.getItems().size());
        assertEquals(12345L, saved.getItems().get(0).getAppliedRuleId());
        assertEquals("strict-spec", saved.getItems().get(0).getMatchPath());
    }
}
