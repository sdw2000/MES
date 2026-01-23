package com.fine.serviceIMPL.purchase;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.purchase.PurchaseSupplierContactMapper;
import com.fine.Dao.purchase.PurchaseSupplierMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.purchase.PurchaseSupplier;
import com.fine.modle.purchase.PurchaseSupplierContact;
import com.fine.service.purchase.PurchaseSupplierService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

@Service
public class PurchaseSupplierServiceImpl extends ServiceImpl<PurchaseSupplierMapper, PurchaseSupplier> implements PurchaseSupplierService {

    private final PurchaseSupplierContactMapper contactMapper;

    public PurchaseSupplierServiceImpl(PurchaseSupplierContactMapper contactMapper) {
        this.contactMapper = contactMapper;
    }

    @Override
    public ResponseResult<?> listSuppliers(String keyword, Integer page, Integer size) {
        Page<PurchaseSupplier> p = new Page<>(page == null ? 1 : page, size == null ? 20 : size);
        LambdaQueryWrapper<PurchaseSupplier> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PurchaseSupplier::getIsDeleted, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(PurchaseSupplier::getSupplierName, keyword)
                    .or().like(PurchaseSupplier::getSupplierCode, keyword)
                    .or().like(PurchaseSupplier::getShortName, keyword));
        }
        wrapper.orderByDesc(PurchaseSupplier::getCreatedAt);
        Page<PurchaseSupplier> result = this.page(p, wrapper);
        return ResponseResult.success(result);
    }

    @Override
    public ResponseResult<?> saveSupplier(PurchaseSupplier supplier) {
        Date now = new Date();
        if (supplier.getId() == null) {
            supplier.setCreatedAt(now);
            supplier.setUpdatedAt(now);
            supplier.setIsDeleted(0);
            this.save(supplier);
        } else {
            supplier.setUpdatedAt(now);
            this.updateById(supplier);
        }

        // 保存联系人（先清除旧的再插入新的）
        if (supplier.getId() != null && supplier.getContacts() != null) {
            contactMapper.deleteBySupplierId(supplier.getId());
            for (PurchaseSupplierContact c : supplier.getContacts()) {
                c.setSupplierId(supplier.getId());
                c.setIsDeleted(0);
                c.setCreatedAt(now);
                c.setUpdatedAt(now);
                if (c.getIsPrimary() == null) c.setIsPrimary(0);
                if (c.getIsDecisionMaker() == null) c.setIsDecisionMaker(0);
                contactMapper.insert(c);
            }
        }

        return ResponseResult.success(supplier);
    }

    @Override
    public ResponseResult<?> deleteSupplier(Long id) {
        PurchaseSupplier supplier = this.getById(id);
        if (supplier == null) {
            return new ResponseResult<>(404, "供应商不存在");
        }
        supplier.setIsDeleted(1);
        this.updateById(supplier);
        return ResponseResult.success();
    }

    @Override
    public ResponseResult<?> getSupplierDetail(Long id) {
        PurchaseSupplier supplier = this.getById(id);
        if (supplier == null || (supplier.getIsDeleted() != null && supplier.getIsDeleted() == 1)) {
            return new ResponseResult<>(404, "供应商不存在");
        }
        List<PurchaseSupplierContact> contacts = contactMapper.selectBySupplierId(id);
        supplier.setContacts(contacts);
        return ResponseResult.success(supplier);
    }
}
