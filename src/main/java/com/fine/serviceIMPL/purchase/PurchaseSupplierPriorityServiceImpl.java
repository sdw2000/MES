package com.fine.serviceIMPL.purchase;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.purchase.PurchaseSupplierPriorityMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.purchase.PurchaseSupplierPriority;
import com.fine.service.purchase.PurchaseSupplierPriorityService;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class PurchaseSupplierPriorityServiceImpl extends ServiceImpl<PurchaseSupplierPriorityMapper, PurchaseSupplierPriority> implements PurchaseSupplierPriorityService {
    @Override
    public ResponseResult<?> listAll() {
        LambdaQueryWrapper<PurchaseSupplierPriority> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PurchaseSupplierPriority::getIsDeleted, 0).orderByDesc(PurchaseSupplierPriority::getScore);
        List<PurchaseSupplierPriority> list = this.list(wrapper);
        return ResponseResult.success(list);
    }

    @Override
    public ResponseResult<?> list(String keyword, Integer page, Integer size) {
        Page<PurchaseSupplierPriority> p = new Page<>(page == null ? 1 : page, size == null ? 20 : size);
        LambdaQueryWrapper<PurchaseSupplierPriority> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PurchaseSupplierPriority::getIsDeleted, 0);
        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.and(w -> w.like(PurchaseSupplierPriority::getSupplierName, keyword)
                    .or().like(PurchaseSupplierPriority::getSupplierCode, keyword));
        }
        wrapper.orderByDesc(PurchaseSupplierPriority::getScore);
        Page<PurchaseSupplierPriority> result = this.page(p, wrapper);
        return ResponseResult.success(result);
    }

    @Override
    public ResponseResult<?> upsert(PurchaseSupplierPriority priority) {
        Date now = new Date();
        if (priority.getId() == null) {
            priority.setCreatedAt(now);
        }
        priority.setUpdatedAt(now);
        priority.setIsDeleted(0);
        this.saveOrUpdate(priority);
        return ResponseResult.success(priority);
    }

    @Override
    public ResponseResult<?> deleteById(Long id) {
        PurchaseSupplierPriority p = this.getById(id);
        if (p == null) {
            return new ResponseResult<>(404, "记录不存在");
        }
        p.setIsDeleted(1);
        this.updateById(p);
        return ResponseResult.success();
    }
}
