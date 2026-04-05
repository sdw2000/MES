package com.fine.serviceIMPL.purchase;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.purchase.PurchaseQuotationItemMapper;
import com.fine.Dao.purchase.PurchaseQuotationMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.purchase.PurchaseQuotation;
import com.fine.modle.purchase.PurchaseQuotationItem;
import com.fine.service.purchase.PurchaseQuotationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Service
public class PurchaseQuotationServiceImpl extends ServiceImpl<PurchaseQuotationMapper, PurchaseQuotation> implements PurchaseQuotationService {

    @Autowired
    private PurchaseQuotationMapper quotationMapper;
    @Autowired
    private PurchaseQuotationItemMapper quotationItemMapper;

    @Override
    public ResponseResult<?> list(Integer pageNum, Integer pageSize, String supplier, String status) {
        Page<PurchaseQuotation> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);
        IPage<PurchaseQuotation> result = quotationMapper.selectPaged(page, supplier, status);
        return ResponseResult.success(result);
    }

    @Override
    public ResponseResult<?> detail(Long id) {
        PurchaseQuotation quotation = quotationMapper.selectById(id);
        if (quotation == null || quotation.getIsDeleted() == 1) {
            return new ResponseResult<>(404, "报价单不存在");
        }
        LambdaQueryWrapper<PurchaseQuotationItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PurchaseQuotationItem::getQuotationId, id).eq(PurchaseQuotationItem::getIsDeleted, 0);
        List<PurchaseQuotationItem> items = quotationItemMapper.selectList(wrapper);
        quotation.setItems(items);
        return ResponseResult.success(quotation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> create(PurchaseQuotation quotation) {
        if (!StringUtils.hasText(quotation.getQuotationNo())) {
            quotation.setQuotationNo(generateQuotationNo());
        }
        if (!StringUtils.hasText(quotation.getStatus())) {
            quotation.setStatus("draft");
        }
        quotation.setIsDeleted(0);
        quotation.setCreatedAt(new Date());
        quotation.setUpdatedAt(new Date());
        calculateTotals(quotation);
        quotationMapper.insert(quotation);

        if (!CollectionUtils.isEmpty(quotation.getItems())) {
            for (PurchaseQuotationItem item : quotation.getItems()) {
                item.setQuotationId(quotation.getId());
                item.setIsDeleted(0);
                item.setCreatedAt(new Date());
                item.setUpdatedAt(new Date());
                calculateItem(item);
                quotationItemMapper.insert(item);
            }
        }
        return ResponseResult.success(quotation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> updateQuotation(PurchaseQuotation quotation) {
        PurchaseQuotation existing = quotationMapper.selectById(quotation.getId());
        if (existing == null || existing.getIsDeleted() == 1) {
            return new ResponseResult<>(404, "报价单不存在");
        }
        quotation.setCreatedAt(existing.getCreatedAt());
        quotation.setUpdatedAt(new Date());
        quotation.setIsDeleted(0);
        calculateTotals(quotation);
        quotationMapper.updateById(quotation);

        LambdaQueryWrapper<PurchaseQuotationItem> del = new LambdaQueryWrapper<>();
        del.eq(PurchaseQuotationItem::getQuotationId, quotation.getId());
        quotationItemMapper.delete(del);

        if (!CollectionUtils.isEmpty(quotation.getItems())) {
            for (PurchaseQuotationItem item : quotation.getItems()) {
                item.setId(null);
                item.setQuotationId(quotation.getId());
                item.setIsDeleted(0);
                item.setCreatedAt(new Date());
                item.setUpdatedAt(new Date());
                calculateItem(item);
                quotationItemMapper.insert(item);
            }
        }
        return ResponseResult.success(quotation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> deleteQuotation(Long id) {
        PurchaseQuotation quotation = quotationMapper.selectById(id);
        if (quotation == null) {
            return new ResponseResult<>(404, "报价单不存在");
        }
        quotation.setIsDeleted(1);
        quotationMapper.updateById(quotation);
        LambdaQueryWrapper<PurchaseQuotationItem> del = new LambdaQueryWrapper<>();
        del.eq(PurchaseQuotationItem::getQuotationId, id);
        quotationItemMapper.delete(del);
        return ResponseResult.success();
    }

    @Override
    public String generateQuotationNo() {
        String prefix = "PQ-" + new SimpleDateFormat("yyyyMMdd").format(new Date()) + "-";
        String seq = new SimpleDateFormat("HHmmss").format(new Date());
        return prefix + seq;
    }

    private void calculateTotals(PurchaseQuotation quotation) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalArea = BigDecimal.ZERO;
        if (!CollectionUtils.isEmpty(quotation.getItems())) {
            for (PurchaseQuotationItem item : quotation.getItems()) {
                calculateItem(item);
                if (item.getAmount() != null) totalAmount = totalAmount.add(item.getAmount());
                // 仅薄膜类（有宽度+长度）累计面积；其他原材料sqm承载总重
                if (item.getSqm() != null && item.getWidth() != null && item.getLength() != null) {
                    totalArea = totalArea.add(item.getSqm());
                }
            }
        }
        quotation.setTotalAmount(totalAmount);
        quotation.setTotalArea(totalArea);
    }

    private void calculateItem(PurchaseQuotationItem item) {
        if (item.getWidth() != null && item.getLength() != null && item.getQuantity() != null) {
            BigDecimal sqm = item.getWidth().divide(new BigDecimal(1000), 6, BigDecimal.ROUND_HALF_UP)
                    .multiply(item.getLength()).multiply(new BigDecimal(item.getQuantity()));
            item.setSqm(sqm.setScale(2, BigDecimal.ROUND_HALF_UP));
            if (item.getUnitPrice() != null) {
                item.setAmount(sqm.multiply(item.getUnitPrice()).setScale(2, BigDecimal.ROUND_HALF_UP));
            }
            return;
        }

        // 其他原材料：sqm承载总重，金额=总重*单价
        if (item.getSqm() != null && item.getUnitPrice() != null) {
            item.setAmount(item.getSqm().multiply(item.getUnitPrice()).setScale(2, BigDecimal.ROUND_HALF_UP));
        }
    }
}
