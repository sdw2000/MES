package com.fine.serviceIMPL;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.DeliveryNoticeItemMapper;
import com.fine.Dao.DeliveryNoticeMapper;
import com.fine.Dao.SalesOrderItemMapper;
import com.fine.Dao.production.SalesOrderMapper;
import com.fine.modle.DeliveryNotice;
import com.fine.modle.DeliveryNoticeItem;
import com.fine.modle.SalesOrder;
import com.fine.modle.SalesOrderItem;
import com.fine.service.DeliveryNoticeService;

@Service
public class DeliveryNoticeServiceImpl extends ServiceImpl<DeliveryNoticeMapper, DeliveryNotice> implements DeliveryNoticeService {

    @Autowired
    private DeliveryNoticeMapper deliveryNoticeMapper;

    @Autowired
    private DeliveryNoticeItemMapper deliveryNoticeItemMapper;

    @Autowired
    private SalesOrderMapper salesOrderMapper;
    
    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryNotice createDeliveryNotice(DeliveryNotice deliveryNotice) {
        // 1. 验证订单是否存在
        SalesOrder order = null;
        if (deliveryNotice.getOrderId() != null) {
            order = salesOrderMapper.selectById(deliveryNotice.getOrderId());
        } else if (deliveryNotice.getOrderNo() != null) {
            // 尝试通过订单号查找
            List<SalesOrder> orders = salesOrderMapper.selectList(
                new QueryWrapper<SalesOrder>().eq("order_no", deliveryNotice.getOrderNo())
            );
            if (!orders.isEmpty()) {
                order = orders.get(0);
                deliveryNotice.setOrderId(order.getId());
            }
        }

        if (order == null) {
            throw new RuntimeException("关联销售订单不存在");
        }
        
        // 2. 生成发货单号 (DN + 年月日时分秒 + 随机数/ID)
        // 使用 String.format 确保格式正确
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int randomSuffix = (int)((Math.random() * 9 + 1) * 1000);
        String noticeNo = "DN" + timestamp + randomSuffix;
        
        deliveryNotice.setNoticeNo(noticeNo);
        // 如果前端没有传，使用订单的客户
        if (deliveryNotice.getCustomer() == null || deliveryNotice.getCustomer().isEmpty()) {
            deliveryNotice.setCustomer(order.getCustomer());
        }
        // 如果前端没有传，使用订单号
        if (deliveryNotice.getOrderNo() == null || deliveryNotice.getOrderNo().isEmpty()) {
            deliveryNotice.setOrderNo(order.getOrderNo());
        }
        
        deliveryNotice.setCreatedAt(new Date());
        deliveryNotice.setIsDeleted(0);
        
        // 设置默认状态为待发货
        if (deliveryNotice.getStatus() == null || deliveryNotice.getStatus().isEmpty()) {
            deliveryNotice.setStatus("待发货");
        }
        
        // 3. 保存主表
        deliveryNoticeMapper.insert(deliveryNotice);
        
        // 4. 保存明细表
        if (deliveryNotice.getItems() != null && !deliveryNotice.getItems().isEmpty()) {
            for (DeliveryNoticeItem item : deliveryNotice.getItems()) {
                item.setNoticeId(deliveryNotice.getId());
                item.setId(null); // 确保ID为null，触发自增
                
                // 补充明细信息，如物料名等（如果前端没传且关联了订单明细）
                if (item.getOrderItemId() != null) {
                    SalesOrderItem orderItem = salesOrderItemMapper.selectById(item.getOrderItemId());
                    if(orderItem != null) {
                        if(item.getMaterialCode() == null || item.getMaterialCode().isEmpty()) item.setMaterialCode(orderItem.getMaterialCode());
                        if(item.getMaterialName() == null || item.getMaterialName().isEmpty()) item.setMaterialName(orderItem.getMaterialName());
                        if(item.getSpec() == null || item.getSpec().isEmpty()) {
                            // 构造规格字符串，处理可能的null值
                            String width = orderItem.getWidth() != null ? orderItem.getWidth().toString() : "";
                            String thickness = orderItem.getThickness() != null ? orderItem.getThickness().toString() : "";
                            String length = orderItem.getLength() != null ? orderItem.getLength().toString() : "";
                            item.setSpec(width + "*" + thickness + "*" + length);
                        }
                    }
                }
                
                deliveryNoticeItemMapper.insert(item);
            }
        }
        
        return deliveryNotice;
    }

    @Override
    public DeliveryNotice getDeliveryNoticeDetail(Long id) {
        DeliveryNotice notice = deliveryNoticeMapper.selectById(id);
        if (notice != null) {
            List<DeliveryNoticeItem> items = deliveryNoticeItemMapper.selectByNoticeId(id);
            notice.setItems(items);
        }
        return notice;
    }
}
