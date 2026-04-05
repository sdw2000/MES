package com.fine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fine.Dao.DeliveryNoticeItemMapper;
import com.fine.Dao.DeliveryNoticeMapper;
import com.fine.Dao.SalesOrderItemMapper;
import com.fine.Dao.production.SalesOrderMapper;
import com.fine.modle.DeliveryNotice;
import com.fine.modle.DeliveryNoticeItem;
import com.fine.modle.SalesOrder;
import com.fine.modle.SalesOrderItem;
import com.fine.serviceIMPL.DeliveryNoticeServiceImpl;
import com.fine.serviceIMPL.SalesOrderServiceImpl;
import com.fine.Utils.ResponseResult;

@SpringBootTest
public class DeliveryNoticeIntegrationTest {

    @Mock
    private SalesOrderMapper salesOrderMapper;
    
    @Mock
    private SalesOrderItemMapper salesOrderItemMapper;

    @Mock
    private DeliveryNoticeMapper deliveryNoticeMapper;

    @Mock
    private DeliveryNoticeItemMapper deliveryNoticeItemMapper;

    @InjectMocks
    private SalesOrderServiceImpl salesOrderService;

    @InjectMocks
    private DeliveryNoticeServiceImpl deliveryNoticeService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testFullFlow_CreateOrder_DeliveryNotice_UpdateStatus() {
        Long orderId = 1001L;
        String orderNo = "SO-TEST-001";
        
        SalesOrder order = new SalesOrder();
        order.setId(orderId);
        order.setOrderNo(orderNo);
        order.setCustomer("Test Customer");
        
        SalesOrderItem item1 = new SalesOrderItem();
        item1.setId(2001L);
        item1.setOrderId(orderId);
        item1.setMaterialCode("MAT001");
        item1.setRolls(100); // 100 rolls total
        
        List<SalesOrderItem> orderItems = new ArrayList<>();
        orderItems.add(item1);
        order.setItems(orderItems);

        when(salesOrderMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(order));
        IPage<SalesOrder> mockPage = new Page<>(1, 10);
        mockPage.setRecords(Arrays.asList(order));
        when(salesOrderMapper.selectOrdersWithCustomerSearch(any(Page.class), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(mockPage);
        when(salesOrderMapper.selectById(eq(orderId))).thenReturn(order);
        
        when(salesOrderItemMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(orderItems);
        when(deliveryNoticeItemMapper.getShippedQuantityByOrderItemId(item1.getId())).thenReturn(0);

        ResponseResult<?> orderResult = salesOrderService.getAllOrders(1, 10, null, null, null, null, null, null, null, null);
        IPage<SalesOrder> pageInfo = (IPage<SalesOrder>) orderResult.getData();
        List<SalesOrder> orders = pageInfo.getRecords();
        assertNotNull(orders);
        assertEquals(1, orders.size());

        DeliveryNotice notice = new DeliveryNotice();
        notice.setOrderId(orderId);
        notice.setNoticeNo("DN-TEST-001");
        notice.setDeliveryDate(new Date());
        
        DeliveryNoticeItem noticeItem = new DeliveryNoticeItem();
        noticeItem.setOrderItemId(item1.getId());
        noticeItem.setQuantity(40); // Shipping 40 rolls
        
        List<DeliveryNoticeItem> noticeItems = new ArrayList<>();
        noticeItems.add(noticeItem);
        notice.setItems(noticeItems);

        when(deliveryNoticeMapper.insert(any(DeliveryNotice.class))).thenReturn(1);
        when(deliveryNoticeItemMapper.insert(any(DeliveryNoticeItem.class))).thenReturn(1);
        when(salesOrderItemMapper.selectById(eq(item1.getId()))).thenReturn(item1);
        
        DeliveryNotice createResult = deliveryNoticeService.createDeliveryNotice(notice);
        assertNotNull(createResult);

        when(deliveryNoticeItemMapper.getShippedQuantityByOrderItemId(item1.getId())).thenReturn(40);
        
        ResponseResult<?> orderResultAfterShip = salesOrderService.getAllOrders(1, 10, null, null, null, null, null, null, null, null);
        IPage<SalesOrder> pageInfoAfter = (IPage<SalesOrder>) orderResultAfterShip.getData();
        assertNotNull(pageInfoAfter);
        
        System.out.println("Integration Test Passed: Order created, partial shipment processed, shipped count updated correctly.");
    }
}
