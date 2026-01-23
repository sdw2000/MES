package com.fine.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.modle.DeliveryNotice;

public interface DeliveryNoticeService extends IService<DeliveryNotice> {
    
    /**
     * 创建发货通知单
     * @param deliveryNotice 发货通知单信息
     * @return 创建后的发货通知单
     */
    DeliveryNotice createDeliveryNotice(DeliveryNotice deliveryNotice);
    
    /**
     * 获取发货通知单详情（包含明细）
     * @param id 发货通知单ID
     * @return 发货通知单详情
     */
    DeliveryNotice getDeliveryNoticeDetail(Long id);
}
