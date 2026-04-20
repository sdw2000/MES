package com.fine.serviceIMPL.purchase;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PurchaseQuotationServiceImpl extends ServiceImpl<PurchaseQuotationMapper, PurchaseQuotation> implements PurchaseQuotationService {

    @Autowired
    private PurchaseQuotationMapper quotationMapper;
    @Autowired
    private PurchaseQuotationItemMapper quotationItemMapper;

    @Override
    public ResponseResult<?> list(Integer pageNum, Integer pageSize, String supplier, String status, String materialCode) {
        Page<PurchaseQuotation> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize);
        IPage<PurchaseQuotation> result = quotationMapper.selectPaged(page, supplier, status, materialCode);
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
        if (!StringUtils.hasText(quotation.getStatus())) {
            quotation.setStatus("draft");
        }
        quotation.setIsDeleted(0);
        quotation.setCreatedAt(new Date());
        quotation.setUpdatedAt(new Date());

        int retryTimes = 5;
        for (int attempt = 0; attempt < retryTimes; attempt++) {
            if (!StringUtils.hasText(quotation.getQuotationNo()) || attempt > 0) {
                quotation.setQuotationNo(generateQuotationNo(attempt));
            }
            try {
                quotationMapper.insert(quotation);
                break;
            } catch (org.springframework.dao.DuplicateKeyException ex) {
                if (attempt == retryTimes - 1) {
                    throw ex;
                }
            }
        }

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

        LambdaUpdateWrapper<PurchaseQuotation> quotationDeleteWrapper = new LambdaUpdateWrapper<>();
        quotationDeleteWrapper
                .eq(PurchaseQuotation::getId, id)
                .eq(PurchaseQuotation::getIsDeleted, 0)
                .set(PurchaseQuotation::getIsDeleted, 1)
                .set(PurchaseQuotation::getUpdatedAt, new Date());
        int affected = quotationMapper.update(null, quotationDeleteWrapper);
        if (affected <= 0) {
            return new ResponseResult<>(409, "报价单删除失败或已被删除");
        }

        LambdaUpdateWrapper<PurchaseQuotationItem> itemDeleteWrapper = new LambdaUpdateWrapper<>();
        itemDeleteWrapper
                .eq(PurchaseQuotationItem::getQuotationId, id)
                .eq(PurchaseQuotationItem::getIsDeleted, 0)
                .set(PurchaseQuotationItem::getIsDeleted, 1)
                .set(PurchaseQuotationItem::getUpdatedAt, new Date());
        quotationItemMapper.update(null, itemDeleteWrapper);

        return ResponseResult.success();
    }

    @Override
    public String generateQuotationNo() {
        return generateQuotationNo(0);
    }

    private String generateQuotationNo(int attempt) {
        String datePart = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String timePart = new SimpleDateFormat("HHmmssSSS").format(new Date());
        int randomPart = ThreadLocalRandom.current().nextInt(1000, 10000);
        if (attempt > 0) {
            return String.format("PQ-%s-%s-%d-%d", datePart, timePart, attempt, randomPart);
        }
        return String.format("PQ-%s-%s-%d", datePart, timePart, randomPart);
    }

    @SuppressWarnings("unused")
    private void calculateTotals(PurchaseQuotation quotation) {
        BigDecimal totalArea = BigDecimal.ZERO;
        if (!CollectionUtils.isEmpty(quotation.getItems())) {
            for (PurchaseQuotationItem item : quotation.getItems()) {
                calculateItem(item);
                // 仅薄膜类（有宽度+长度）累计面积；其他原材料sqm承载总重
                if (item.getSqm() != null && item.getWidth() != null && item.getLength() != null) {
                    totalArea = totalArea.add(item.getSqm());
                }
            }
        }
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
