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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

@Service
public class PurchaseSupplierServiceImpl extends ServiceImpl<PurchaseSupplierMapper, PurchaseSupplier> implements PurchaseSupplierService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseSupplierServiceImpl.class);

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
        // 仅当请求中传入了有效联系人数据时才操作联系人表，避免导入等场景因联系人表缺失导致失败
        if (supplier.getId() != null && hasValidContacts(supplier.getContacts())) {
            try {
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
            } catch (Exception ex) {
                String msg = ex.getMessage() == null ? "" : ex.getMessage();
                if (msg.contains("purchase_supplier_contacts") || msg.contains("doesn't exist")) {
                    log.warn("联系人表不存在，已跳过联系人保存。supplierId={}", supplier.getId());
                } else {
                    throw ex;
                }
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
        List<PurchaseSupplierContact> contacts;
        try {
            contacts = contactMapper.selectBySupplierId(id);
        } catch (Exception ex) {
            String msg = ex.getMessage() == null ? "" : ex.getMessage();
            if (msg.contains("purchase_supplier_contacts") || msg.contains("doesn't exist")) {
                log.warn("联系人表不存在，返回空联系人列表。supplierId={}", id);
                contacts = java.util.Collections.emptyList();
            } else {
                throw ex;
            }
        }
        supplier.setContacts(contacts);
        return ResponseResult.success(supplier);
    }

    private boolean hasValidContacts(List<PurchaseSupplierContact> contacts) {
        if (contacts == null || contacts.isEmpty()) {
            return false;
        }
        for (PurchaseSupplierContact c : contacts) {
            if (c == null) continue;
            if (StringUtils.hasText(c.getContactName())
                    || StringUtils.hasText(c.getContactPhone())
                    || StringUtils.hasText(c.getContactEmail())
                    || StringUtils.hasText(c.getContactWechat())) {
                return true;
            }
        }
        return false;
    }
}
