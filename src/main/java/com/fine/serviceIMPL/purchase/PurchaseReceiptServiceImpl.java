package com.fine.serviceIMPL.purchase;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.purchase.PurchaseReceiptItemMapper;
import com.fine.Dao.purchase.PurchaseReceiptMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.purchase.PurchaseReceipt;
import com.fine.modle.purchase.PurchaseReceiptItem;
import com.fine.service.purchase.PurchaseReceiptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PurchaseReceiptServiceImpl extends ServiceImpl<PurchaseReceiptMapper, PurchaseReceipt> 
        implements PurchaseReceiptService {

    @Autowired
    private PurchaseReceiptMapper receiptMapper;
    
    @Autowired
    private PurchaseReceiptItemMapper itemMapper;

    @Override
    public ResponseResult<?> list(Integer pageNum, Integer pageSize, String supplier, String status) {
        Page<PurchaseReceipt> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 20 : pageSize);
        IPage<PurchaseReceipt> result = receiptMapper.selectPaged(page, supplier, status);
        return ResponseResult.success(result);
    }

    @Override
    public ResponseResult<?> detail(Long id) {
        PurchaseReceipt receipt = receiptMapper.selectById(id);
        if (receipt == null || Integer.valueOf(1).equals(receipt.getIsDeleted())) {
            return new ResponseResult<>(404, "收货通知不存在");
        }
        List<PurchaseReceiptItem> items = itemMapper.selectByReceiptId(id);
        receipt.setItems(items);
        return ResponseResult.success(receipt);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> create(PurchaseReceipt receipt) {
        if (!StringUtils.hasText(receipt.getReceiptNo())) {
            receipt.setReceiptNo(generateReceiptNo());
        }
        if (!StringUtils.hasText(receipt.getStatus())) {
            receipt.setStatus("planned");
        }
        receipt.setIsDeleted(0);
        receipt.setCreatedAt(LocalDateTime.now());
        receipt.setUpdatedAt(LocalDateTime.now());
        receiptMapper.insert(receipt);

        if (!CollectionUtils.isEmpty(receipt.getItems())) {
            for (PurchaseReceiptItem item : receipt.getItems()) {
                item.setId(null);
                item.setReceiptId(receipt.getId());
                item.setIsDeleted(0);
                item.setCreatedAt(LocalDateTime.now());
                item.setUpdatedAt(LocalDateTime.now());
                itemMapper.insert(item);
            }
        }
        return ResponseResult.success(receipt);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> updateReceipt(PurchaseReceipt receipt) {
        PurchaseReceipt existing = receiptMapper.selectById(receipt.getId());
        if (existing == null || Integer.valueOf(1).equals(existing.getIsDeleted())) {
            return new ResponseResult<>(404, "收货通知不存在");
        }

        receipt.setCreatedAt(existing.getCreatedAt());
        receipt.setUpdatedAt(LocalDateTime.now());
        receipt.setIsDeleted(0);
        receiptMapper.updateById(receipt);

        itemMapper.deleteByReceiptId(receipt.getId());
        if (!CollectionUtils.isEmpty(receipt.getItems())) {
            for (PurchaseReceiptItem item : receipt.getItems()) {
                item.setId(null);
                item.setReceiptId(receipt.getId());
                item.setIsDeleted(0);
                item.setCreatedAt(LocalDateTime.now());
                item.setUpdatedAt(LocalDateTime.now());
                itemMapper.insert(item);
            }
        }
        return ResponseResult.success(receipt);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> deleteReceipt(Long id) {
        PurchaseReceipt receipt = receiptMapper.selectById(id);
        if (receipt == null || Integer.valueOf(1).equals(receipt.getIsDeleted())) {
            return new ResponseResult<>(404, "收货通知不存在");
        }
        receipt.setIsDeleted(1);
        receipt.setUpdatedAt(LocalDateTime.now());
        receiptMapper.updateById(receipt);
        itemMapper.deleteByReceiptId(id);
        return ResponseResult.success();
    }

    private String generateReceiptNo() {
        return "PR" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
    }
}
