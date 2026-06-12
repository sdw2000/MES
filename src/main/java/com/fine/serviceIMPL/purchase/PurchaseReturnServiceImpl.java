package com.fine.serviceIMPL.purchase;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.purchase.PurchaseReturnItemMapper;
import com.fine.Dao.purchase.PurchaseReturnOrderMapper;
import com.fine.modle.purchase.PurchaseReturnItem;
import com.fine.modle.purchase.PurchaseReturnOrder;
import com.fine.service.purchase.PurchaseReturnService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PurchaseReturnServiceImpl extends ServiceImpl<PurchaseReturnOrderMapper, PurchaseReturnOrder> implements PurchaseReturnService {

    @Autowired
    private PurchaseReturnItemMapper itemMapper;

    @Override
    @Transactional
    public void saveReturn(PurchaseReturnOrder returnOrder) {
        if (returnOrder.getId() == null) {
            this.save(returnOrder);
        } else {
            this.updateById(returnOrder);
        }

        // Simple sync for items
        List<PurchaseReturnItem> items = returnOrder.getItems();
        if (items != null) {
            for (PurchaseReturnItem item : items) {
                item.setReturnId(returnOrder.getId());
                if (item.getId() == null) {
                    itemMapper.insert(item);
                } else {
                    itemMapper.updateById(item);
                }
            }
        }
    }
}
