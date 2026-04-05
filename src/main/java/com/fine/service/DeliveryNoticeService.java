package com.fine.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.modle.DeliveryNotice;

import java.util.Map;

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

    /**
     * 更新发货通知单（包含明细）
     * @param deliveryNotice 发货通知单信息
     * @return 更新后的发货通知单
     */
    DeliveryNotice updateDeliveryNotice(DeliveryNotice deliveryNotice);

    /**
     * 删除发货通知单（逻辑删除主表，物理删除明细）
     * @param id 发货通知单ID
     * @return 是否删除成功
     */
    boolean deleteDeliveryNotice(Long id);

    /**
     * 查询发货单物流轨迹
     * @param id 发货单ID
     * @return 物流结果
     */
    Map<String, Object> queryLogistics(Long id);
}
