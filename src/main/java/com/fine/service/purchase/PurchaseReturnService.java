package com.fine.service.purchase;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fine.modle.purchase.PurchaseReturnOrder;

public interface PurchaseReturnService extends IService<PurchaseReturnOrder> {
    void saveReturn(PurchaseReturnOrder returnOrder);
}
