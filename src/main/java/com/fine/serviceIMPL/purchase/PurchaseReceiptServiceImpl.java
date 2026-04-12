package com.fine.serviceIMPL.purchase;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.purchase.PurchaseReceiptItemMapper;
import com.fine.Dao.purchase.PurchaseReceiptMapper;
import com.fine.Dao.purchase.PurchaseOrderItemMapper;
import com.fine.Dao.purchase.PurchaseOrderMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.PurchaseOrder;
import com.fine.modle.PurchaseOrderItem;
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
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

@Service
public class PurchaseReceiptServiceImpl extends ServiceImpl<PurchaseReceiptMapper, PurchaseReceipt> 
        implements PurchaseReceiptService {

    @Autowired
    private PurchaseReceiptMapper receiptMapper;
    
    @Autowired
    private PurchaseReceiptItemMapper itemMapper;

    @Autowired
    private PurchaseOrderMapper purchaseOrderMapper;

    @Autowired
    private PurchaseOrderItemMapper purchaseOrderItemMapper;

    @Override
    public ResponseResult<?> list(Integer pageNum, Integer pageSize, String supplier, String status, String reconciliationStatus) {
        Page<PurchaseReceipt> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 20 : pageSize);
        IPage<PurchaseReceipt> result = receiptMapper.selectPaged(page, supplier, status, reconciliationStatus);
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
        normalizeReceipt(receipt);
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
        refreshReconciliationStatus(receipt.getPurchaseOrderNo());
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
        normalizeReceipt(receipt);
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
        refreshReconciliationStatus(receipt.getPurchaseOrderNo());
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
        refreshReconciliationStatus(receipt.getPurchaseOrderNo());
        return ResponseResult.success();
    }

    private String generateReceiptNo() {
        return "PR" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
    }

    private void normalizeReceipt(PurchaseReceipt receipt) {
        if (receipt == null || CollectionUtils.isEmpty(receipt.getItems())) {
            return;
        }
        for (PurchaseReceiptItem item : receipt.getItems()) {
            item.setPurchaseOrderNo(receipt.getPurchaseOrderNo());
            if (!StringUtils.hasText(item.getPurchaseUomCode()) && StringUtils.hasText(receipt.getPurchaseOrderNo())) {
                item.setPurchaseUomCode("EA");
            }
            if (!StringUtils.hasText(item.getPriceUomCode()) && item.getPurchaseUomCode() != null) {
                item.setPriceUomCode(item.getPurchaseUomCode());
            }
            if (item.getPriceQty() == null && item.getPurchaseQty() != null) {
                item.setPriceQty(item.getPurchaseQty());
            }
            if (item.getStockQty() == null && item.getPriceQty() != null) {
                item.setStockQty(item.getPriceQty());
            }
            if (!StringUtils.hasText(item.getStockUomCode()) && item.getPriceUomCode() != null) {
                item.setStockUomCode(item.getPriceUomCode());
            }
            if (item.getConversionRate() == null && item.getPurchaseQty() != null && item.getStockQty() != null && item.getPurchaseQty().compareTo(java.math.BigDecimal.ZERO) > 0) {
                item.setConversionRate(item.getStockQty().divide(item.getPurchaseQty(), 8, java.math.BigDecimal.ROUND_HALF_UP));
            }
        }
    }

    private void refreshReconciliationStatus(String purchaseOrderNo) {
        if (!StringUtils.hasText(purchaseOrderNo)) {
            return;
        }
        PurchaseOrder order = purchaseOrderMapper.selectByOrderNo(purchaseOrderNo);
        if (order == null) {
            return;
        }

        List<PurchaseOrderItem> orderItems = purchaseOrderItemMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrderItem>()
                        .eq(PurchaseOrderItem::getOrderId, order.getId())
                        .eq(PurchaseOrderItem::getIsDeleted, 0)
        );

        List<PurchaseReceipt> receipts = receiptMapper.selectList(
                new LambdaQueryWrapper<PurchaseReceipt>()
                        .eq(PurchaseReceipt::getPurchaseOrderNo, purchaseOrderNo)
                        .eq(PurchaseReceipt::getIsDeleted, 0)
        );

        Map<String, BigDecimal> receiptQtyByKey = new HashMap<>();
        Map<String, BigDecimal> receiptAmountByKey = new HashMap<>();
        BigDecimal receiptQtyTotal = BigDecimal.ZERO;
        BigDecimal receiptAmountTotal = BigDecimal.ZERO;

        if (!CollectionUtils.isEmpty(receipts)) {
            for (PurchaseReceipt receipt : receipts) {
                List<PurchaseReceiptItem> receiptItems = itemMapper.selectByReceiptId(receipt.getId());
                if (CollectionUtils.isEmpty(receiptItems)) {
                    continue;
                }
                for (PurchaseReceiptItem item : receiptItems) {
                    BigDecimal qty = item.getPriceQty() != null ? item.getPriceQty() : item.getStockQty();
                    BigDecimal amount = item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO;
                    String key = item.getMaterialCode() == null ? String.valueOf(item.getId()) : item.getMaterialCode();
                    receiptQtyByKey.put(key, receiptQtyByKey.getOrDefault(key, BigDecimal.ZERO).add(qty == null ? BigDecimal.ZERO : qty));
                    receiptAmountByKey.put(key, receiptAmountByKey.getOrDefault(key, BigDecimal.ZERO).add(amount));
                    receiptQtyTotal = receiptQtyTotal.add(qty == null ? BigDecimal.ZERO : qty);
                    receiptAmountTotal = receiptAmountTotal.add(amount);
                }
            }
        }

        BigDecimal orderQtyTotal = BigDecimal.ZERO;
        BigDecimal orderAmountTotal = BigDecimal.ZERO;
        boolean allMatched = !CollectionUtils.isEmpty(orderItems);
        boolean anyReceived = false;

        if (!CollectionUtils.isEmpty(orderItems)) {
            for (PurchaseOrderItem item : orderItems) {
                BigDecimal qty = item.getPriceQty() != null ? item.getPriceQty() : item.getStockQty();
                BigDecimal amount = item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO;
                String key = item.getMaterialCode() == null ? String.valueOf(item.getId()) : item.getMaterialCode();
                BigDecimal receivedQty = receiptQtyByKey.getOrDefault(key, BigDecimal.ZERO);
                BigDecimal receivedAmount = receiptAmountByKey.getOrDefault(key, BigDecimal.ZERO);
                item.setReconciliationStatus(receivedQty.compareTo(BigDecimal.ZERO) <= 0 ? "UNRECONCILED" : (qty != null && receivedQty.compareTo(qty) >= 0 && receivedAmount.compareTo(amount) >= 0 ? "MATCHED" : "PARTIAL"));
                purchaseOrderItemMapper.updateById(item);
                orderQtyTotal = orderQtyTotal.add(qty == null ? BigDecimal.ZERO : qty);
                orderAmountTotal = orderAmountTotal.add(amount);
                if (receivedQty.compareTo(BigDecimal.ZERO) > 0 || receivedAmount.compareTo(BigDecimal.ZERO) > 0) {
                    anyReceived = true;
                }
                if (!"MATCHED".equals(item.getReconciliationStatus())) {
                    allMatched = false;
                }
            }
        }

        order.setReconciliationStatus(!anyReceived ? "UNRECONCILED" : (allMatched && orderQtyTotal.compareTo(receiptQtyTotal) == 0 && orderAmountTotal.compareTo(receiptAmountTotal) == 0 ? "MATCHED" : "PARTIAL"));
        purchaseOrderMapper.updateById(order);
    }
}
