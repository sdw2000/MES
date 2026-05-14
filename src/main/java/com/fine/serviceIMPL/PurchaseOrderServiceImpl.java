package com.fine.serviceIMPL;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.purchase.PurchaseOrderItemMapper;
import com.fine.Dao.purchase.PurchaseOrderMapper;
import com.fine.Dao.purchase.PurchaseReceiptItemMapper;
import com.fine.Dao.purchase.PurchaseReceiptMapper;
import com.fine.Dao.rd.TapeSpecMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.LoginUser;
import com.fine.modle.PurchaseOrder;
import com.fine.modle.PurchaseOrderItem;
import com.fine.modle.purchase.PurchaseReceipt;
import com.fine.modle.purchase.PurchaseReceiptItem;
import com.fine.modle.rd.TapeSpec;
import com.fine.service.PurchaseOrderService;

import javax.servlet.http.HttpServletResponse;

@Service
public class PurchaseOrderServiceImpl extends ServiceImpl<PurchaseOrderMapper, PurchaseOrder> implements PurchaseOrderService {

    @Autowired
    private PurchaseOrderMapper purchaseOrderMapper;

    @Autowired
    private PurchaseOrderItemMapper purchaseOrderItemMapper;

    @Autowired
    private TapeSpecMapper tapeSpecMapper;

    @Autowired
    private PurchaseReceiptMapper purchaseReceiptMapper;

    @Autowired
    private PurchaseReceiptItemMapper purchaseReceiptItemMapper;

    @Override
    public ResponseResult<?> getAllOrders(Integer pageNum, Integer pageSize, String orderNo, String supplier, String startDate, String endDate, String reconciliationStatus) {
        try {
            Page<PurchaseOrder> page = new Page<>(pageNum != null ? pageNum : 1, pageSize != null ? pageSize : 10);
            IPage<PurchaseOrder> pageResult = purchaseOrderMapper.selectOrdersWithSupplierSearch(page, orderNo, supplier, startDate, endDate, reconciliationStatus);
            return new ResponseResult<>(200, "success", pageResult);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "Failed to get purchase orders: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> createOrder(PurchaseOrder purchaseOrder) {
        try {
            String username = getCurrentUsername();

            if (purchaseOrder.getOrderNo() == null || purchaseOrder.getOrderNo().isEmpty()) {
                purchaseOrder.setOrderNo(generateOrderNo());
            } else {
                String incomingOrderNo = purchaseOrder.getOrderNo().trim();
                purchaseOrder.setOrderNo(incomingOrderNo);
                if (purchaseOrderMapper.countByOrderNo(incomingOrderNo) > 0) {
                    purchaseOrder.setOrderNo(generateOrderNo());
                }
            }

            int retry = 0;
            while (purchaseOrderMapper.countByOrderNo(purchaseOrder.getOrderNo()) > 0 && retry < 5) {
                purchaseOrder.setOrderNo(generateOrderNo());
                retry++;
            }
            if (purchaseOrder.getStatus() == null || purchaseOrder.getStatus().isEmpty()) {
                purchaseOrder.setStatus("pending");
            }

            purchaseOrder.setCreatedBy(username);
            purchaseOrder.setUpdatedBy(username);
            purchaseOrder.setCreatedAt(new Date());
            purchaseOrder.setUpdatedAt(new Date());
            purchaseOrder.setIsDeleted(0);
            calculateOrderTotals(purchaseOrder);
            enrichItemsWithSpecInfo(purchaseOrder.getItems());
            normalizeItems(purchaseOrder.getItems());
            initializeReconciliationStatus(purchaseOrder);

            purchaseOrderMapper.insert(purchaseOrder);

            if (purchaseOrder.getItems() != null && !purchaseOrder.getItems().isEmpty()) {
                for (PurchaseOrderItem item : purchaseOrder.getItems()) {
                    item.setOrderId(purchaseOrder.getId());
                    item.setCreatedBy(username);
                    item.setUpdatedBy(username);
                    item.setCreatedAt(new Date());
                    item.setUpdatedAt(new Date());
                    item.setIsDeleted(0);
                    calculateItemAmounts(item);
                    purchaseOrderItemMapper.insert(item);
                }
            }

            Map<String, Object> data = new HashMap<>();
            data.put("data", purchaseOrder);
            return new ResponseResult<>(200, "创建采购订单成功", data);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "创建采购订单失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> updateOrder(PurchaseOrder purchaseOrder) {
        try {
            String username = getCurrentUsername();
            LambdaQueryWrapper<PurchaseOrder> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(PurchaseOrder::getOrderNo, purchaseOrder.getOrderNo())
                        .eq(PurchaseOrder::getIsDeleted, 0);
            PurchaseOrder existing = purchaseOrderMapper.selectOne(queryWrapper);
            if (existing == null) {
                return new ResponseResult<>(404, "采购订单不存在");
            }

            purchaseOrder.setId(existing.getId());
            purchaseOrder.setCreatedBy(existing.getCreatedBy());
            purchaseOrder.setCreatedAt(existing.getCreatedAt());
            purchaseOrder.setUpdatedBy(username);
            purchaseOrder.setUpdatedAt(new Date());
            purchaseOrder.setIsDeleted(0);
            calculateOrderTotals(purchaseOrder);
            enrichItemsWithSpecInfo(purchaseOrder.getItems());
            normalizeItems(purchaseOrder.getItems());
            initializeReconciliationStatus(purchaseOrder);

            purchaseOrderMapper.updateById(purchaseOrder);

            Set<Long> newItemIds = new HashSet<>();
            if (purchaseOrder.getItems() != null) {
                for (PurchaseOrderItem item : purchaseOrder.getItems()) {
                    if (item.getId() != null) {
                        newItemIds.add(item.getId());
                    }
                }
            }

            purchaseOrderItemMapper.logicDeleteMissingItems(existing.getId(),
                    newItemIds.isEmpty() ? null : new java.util.ArrayList<>(newItemIds),
                    username);

            if (purchaseOrder.getItems() != null && !purchaseOrder.getItems().isEmpty()) {
                for (PurchaseOrderItem item : purchaseOrder.getItems()) {
                    item.setOrderId(purchaseOrder.getId());
                    item.setUpdatedBy(username);
                    item.setUpdatedAt(new Date());
                    item.setIsDeleted(0);
                    calculateItemAmounts(item);
                    if (item.getId() != null && item.getId() > 0) {
                        purchaseOrderItemMapper.updateById(item);
                    } else {
                        item.setCreatedBy(username);
                        item.setCreatedAt(new Date());
                        purchaseOrderItemMapper.insert(item);
                    }
                }
            }

            // 按业务要求：采购订单更新后不再自动同步到收货通知明细
            // 如需同步，改为由人工在收货通知页维护。

            Map<String, Object> data = new HashMap<>();
            data.put("data", purchaseOrder);
            return new ResponseResult<>(200, "更新采购订单成功", data);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "更新采购订单失败: " + e.getMessage());
        }
    }

    private void enrichItemsWithSpecInfo(List<PurchaseOrderItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (PurchaseOrderItem item : items) {
            if (item.getMaterialCode() != null) {
                TapeSpec spec = tapeSpecMapper.selectByMaterialCode(item.getMaterialCode());
                if (spec != null) {
                    item.setColorCode(spec.getColorCode());
                    if (item.getThickness() == null) {
                        item.setThickness(spec.getTotalThickness());
                    }
                }
            }
        }
    }

    private void calculateOrderTotals(PurchaseOrder purchaseOrder) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalQty = BigDecimal.ZERO;
        if (purchaseOrder.getItems() != null) {
            for (PurchaseOrderItem item : purchaseOrder.getItems()) {
                calculateItemAmounts(item);
                if (item.getAmount() != null) {
                    totalAmount = totalAmount.add(item.getAmount());
                }
                if (item.getStockQty() != null) {
                    totalQty = totalQty.add(item.getStockQty());
                } else if (item.getSqm() != null) {
                    totalQty = totalQty.add(item.getSqm());
                }
            }
        }
        purchaseOrder.setTotalAmount(totalAmount);
        // 兼容现有前端字段：totalArea字段承载“总数量”
        purchaseOrder.setTotalArea(totalQty);
    }

    private void calculateItemAmounts(PurchaseOrderItem item) {
        if (item == null) {
            return;
        }
        normalizeSingleItem(item);
        if (item.getPriceQty() != null && item.getUnitPrice() != null) {
            item.setAmount(item.getPriceQty().multiply(item.getUnitPrice()).setScale(2, BigDecimal.ROUND_HALF_UP));
            return;
        }
        if (item.getWidth() != null && item.getLength() != null && item.getRolls() != null) {
            BigDecimal widthM = item.getWidth().divide(new BigDecimal(1000), 6, BigDecimal.ROUND_HALF_UP);
            BigDecimal lengthM = item.getLength();
            BigDecimal area = widthM.multiply(lengthM).multiply(new BigDecimal(item.getRolls()));
            if (item.getSqm() == null) {
                item.setSqm(area.setScale(2, BigDecimal.ROUND_HALF_UP));
            }
            if (item.getUnitPrice() != null) {
                item.setAmount(area.multiply(item.getUnitPrice()).setScale(2, BigDecimal.ROUND_HALF_UP));
            }
            return;
        }

        // 其他原材料：前端将总重传入sqm，后端按 总重 * 单价 计算金额
        if (item.getSqm() != null && item.getUnitPrice() != null) {
            item.setAmount(item.getSqm().multiply(item.getUnitPrice()).setScale(2, BigDecimal.ROUND_HALF_UP));
        }
    }

    private void normalizeItems(List<PurchaseOrderItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (PurchaseOrderItem item : items) {
            normalizeSingleItem(item);
        }
    }

    private void normalizeSingleItem(PurchaseOrderItem item) {
        if (item == null) {
            return;
        }
        boolean looksLikeFilm = item.getWidth() != null && item.getLength() != null;
        if (looksLikeFilm) {
            if (item.getPurchaseQty() == null && item.getRolls() != null) {
                item.setPurchaseQty(BigDecimal.valueOf(item.getRolls()));
            }
            if (item.getPurchaseUomCode() == null || item.getPurchaseUomCode().isEmpty()) {
                item.setPurchaseUomCode("ROLL");
            }
            if (item.getStockQty() == null && item.getSqm() != null) {
                item.setStockQty(item.getSqm());
            }
            if (item.getStockUomCode() == null || item.getStockUomCode().isEmpty()) {
                item.setStockUomCode("M2");
            }
            if (item.getPriceQty() == null) {
                item.setPriceQty(item.getStockQty());
            }
            if (item.getPriceUomCode() == null || item.getPriceUomCode().isEmpty()) {
                item.setPriceUomCode(item.getStockUomCode());
            }
            if (item.getConversionRate() == null && item.getPurchaseQty() != null && item.getStockQty() != null && item.getPurchaseQty().compareTo(BigDecimal.ZERO) > 0) {
                item.setConversionRate(item.getStockQty().divide(item.getPurchaseQty(), 8, BigDecimal.ROUND_HALF_UP));
            }
            if (item.getSqm() == null && item.getStockQty() != null) {
                item.setSqm(item.getStockQty());
            }
            if (item.getRolls() == null && item.getPurchaseQty() != null) {
                item.setRolls(item.getPurchaseQty().intValue());
            }
            return;
        }

        if (item.getPurchaseQty() == null && item.getRolls() != null) {
            item.setPurchaseQty(BigDecimal.valueOf(item.getRolls()));
        }
        if (item.getPurchaseUomCode() == null || item.getPurchaseUomCode().isEmpty()) {
            item.setPurchaseUomCode("DRUM");
        }
        if (item.getStockQty() == null && item.getSqm() != null) {
            item.setStockQty(item.getSqm());
        }
        if (item.getStockUomCode() == null || item.getStockUomCode().isEmpty()) {
            item.setStockUomCode("KG");
        }
        if (item.getPriceQty() == null) {
            item.setPriceQty(item.getStockQty());
        }
        if (item.getPriceUomCode() == null || item.getPriceUomCode().isEmpty()) {
            item.setPriceUomCode(item.getStockUomCode());
        }
        if (item.getConversionRate() == null && item.getPurchaseQty() != null && item.getStockQty() != null && item.getPurchaseQty().compareTo(BigDecimal.ZERO) > 0) {
            item.setConversionRate(item.getStockQty().divide(item.getPurchaseQty(), 8, BigDecimal.ROUND_HALF_UP));
        }
        if (item.getSqm() == null && item.getStockQty() != null) {
            item.setSqm(item.getStockQty());
        }
        if (item.getRolls() == null && item.getPurchaseQty() != null) {
            item.setRolls(item.getPurchaseQty().intValue());
        }
    }

    private void initializeReconciliationStatus(PurchaseOrder purchaseOrder) {
        if (purchaseOrder == null) {
            return;
        }
        purchaseOrder.setReconciliationStatus("UNRECONCILED");
        if (purchaseOrder.getItems() != null) {
            for (PurchaseOrderItem item : purchaseOrder.getItems()) {
                item.setReconciliationStatus("UNRECONCILED");
            }
        }
    }

    @SuppressWarnings("unused")
    private void syncReceiptItemsFromOrder(PurchaseOrder purchaseOrder, String username) {
        if (purchaseOrder == null || purchaseOrder.getId() == null || purchaseOrder.getOrderNo() == null || purchaseOrder.getOrderNo().trim().isEmpty()) {
            return;
        }

        LambdaQueryWrapper<PurchaseReceipt> receiptQuery = new LambdaQueryWrapper<>();
        receiptQuery.eq(PurchaseReceipt::getPurchaseOrderNo, purchaseOrder.getOrderNo().trim())
                .eq(PurchaseReceipt::getIsDeleted, 0)
                .and(q -> q.eq(PurchaseReceipt::getStatus, "planned")
                        .or()
                        .eq(PurchaseReceipt::getStatus, "receiving"));
        List<PurchaseReceipt> receipts = purchaseReceiptMapper.selectList(receiptQuery);
        if (receipts == null || receipts.isEmpty()) {
            return;
        }

        List<PurchaseOrderItem> orderItems = purchaseOrderItemMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrderItem>()
                        .eq(PurchaseOrderItem::getOrderId, purchaseOrder.getId())
                        .eq(PurchaseOrderItem::getIsDeleted, 0)
        );
        if (orderItems == null) {
            orderItems = new java.util.ArrayList<>();
        }

        Map<String, PurchaseOrderItem> orderItemByCode = new HashMap<>();
        for (PurchaseOrderItem orderItem : orderItems) {
            if (orderItem == null || orderItem.getMaterialCode() == null || orderItem.getMaterialCode().trim().isEmpty()) {
                continue;
            }
            String codeKey = buildReceiptSyncKey(orderItem.getMaterialCode(), resolveOrderItemSpec(orderItem, null));
            if (!orderItemByCode.containsKey(codeKey)) {
                orderItemByCode.put(codeKey, orderItem);
            }
        }

        for (PurchaseReceipt receipt : receipts) {
            if (receipt == null || receipt.getId() == null) {
                continue;
            }
            List<PurchaseReceiptItem> receiptItems = purchaseReceiptItemMapper.selectByReceiptId(receipt.getId());
            Map<String, PurchaseReceiptItem> receiptItemByCode = new HashMap<>();
            if (receiptItems != null) {
                for (PurchaseReceiptItem receiptItem : receiptItems) {
                    if (receiptItem == null || receiptItem.getMaterialCode() == null || receiptItem.getMaterialCode().trim().isEmpty()) {
                        continue;
                    }
                    String receiptKey = buildReceiptSyncKey(receiptItem.getMaterialCode(), receiptItem.getSpecification());
                    if (!receiptItemByCode.containsKey(receiptKey)) {
                        receiptItemByCode.put(receiptKey, receiptItem);
                    }
                }
            }

            for (Map.Entry<String, PurchaseOrderItem> entry : orderItemByCode.entrySet()) {
                String materialCode = entry.getKey();
                PurchaseOrderItem orderItem = entry.getValue();
                PurchaseReceiptItem receiptItem = receiptItemByCode.get(materialCode);

                if (receiptItem == null) {
                    PurchaseReceiptItem newItem = buildReceiptItemFromOrderItem(receipt, orderItem, username);
                    purchaseReceiptItemMapper.insert(newItem);
                } else {
                    applyOrderItemToReceiptItem(receiptItem, orderItem, receipt, username);
                    purchaseReceiptItemMapper.updateById(receiptItem);
                }
            }

            if (receiptItems != null && !receiptItems.isEmpty()) {
                for (PurchaseReceiptItem receiptItem : receiptItems) {
                    if (receiptItem == null || receiptItem.getId() == null) {
                        continue;
                    }
                    String materialCode = buildReceiptSyncKey(receiptItem.getMaterialCode(), receiptItem.getSpecification());
                    if (!materialCode.isEmpty() && !orderItemByCode.containsKey(materialCode)) {
                        PurchaseReceiptItem toDelete = new PurchaseReceiptItem();
                        toDelete.setId(receiptItem.getId());
                        toDelete.setIsDeleted(1);
                        toDelete.setUpdatedAt(LocalDateTime.now());
                        purchaseReceiptItemMapper.updateById(toDelete);
                    }
                }
            }
        }
    }

    private String buildReceiptSyncKey(String materialCode, String specification) {
        String code = materialCode == null ? "" : materialCode.trim();
        String spec = specification == null ? "" : specification.trim();
        if (code.isEmpty() && spec.isEmpty()) {
            return "";
        }
        return code + "||" + spec;
    }

    private PurchaseReceiptItem buildReceiptItemFromOrderItem(PurchaseReceipt receipt, PurchaseOrderItem orderItem, String username) {
        PurchaseReceiptItem receiptItem = new PurchaseReceiptItem();
        receiptItem.setReceiptId(receipt.getId());
        receiptItem.setPurchaseOrderNo(receipt.getPurchaseOrderNo());
        receiptItem.setCreatedAt(LocalDateTime.now());
        receiptItem.setUpdatedAt(LocalDateTime.now());
        receiptItem.setIsDeleted(0);
        applyOrderItemToReceiptItem(receiptItem, orderItem, receipt, username);
        return receiptItem;
    }

    private void applyOrderItemToReceiptItem(PurchaseReceiptItem receiptItem,
                                             PurchaseOrderItem orderItem,
                                             PurchaseReceipt receipt,
                                             String username) {
        if (receiptItem == null || orderItem == null) {
            return;
        }
        receiptItem.setPurchaseOrderNo(receipt == null ? receiptItem.getPurchaseOrderNo() : receipt.getPurchaseOrderNo());
        receiptItem.setMaterialCode(orderItem.getMaterialCode());
        receiptItem.setMaterialName(orderItem.getMaterialName());
        receiptItem.setSpecification(resolveOrderItemSpec(orderItem, receiptItem.getSpecification()));
        receiptItem.setPurchaseQty(orderItem.getPurchaseQty());
        receiptItem.setPurchaseUomCode(orderItem.getPurchaseUomCode());
        receiptItem.setPriceQty(orderItem.getPriceQty());
        receiptItem.setPriceUomCode(orderItem.getPriceUomCode());
        receiptItem.setStockQty(orderItem.getStockQty() != null ? orderItem.getStockQty() : orderItem.getSqm());
        receiptItem.setStockUomCode(orderItem.getStockUomCode());
        receiptItem.setConversionRate(orderItem.getConversionRate());
        receiptItem.setExpectedQty(toIntValue(orderItem.getPurchaseQty()));
        if (receipt != null && "planned".equalsIgnoreCase(receipt.getStatus())) {
            receiptItem.setReceivedQty(toIntValue(orderItem.getPurchaseQty()));
        }
        receiptItem.setUnit(resolveDisplayUnit(orderItem));
        receiptItem.setUnitPrice(orderItem.getUnitPrice());
        receiptItem.setAmount(orderItem.getAmount());
        receiptItem.setUpdatedAt(LocalDateTime.now());
        if (username != null && !username.trim().isEmpty()) {
            String patchRemark = "[AUTO_SYNC_FROM_PURCHASE_ORDER]";
            String originalRemark = receiptItem.getRemark() == null ? "" : receiptItem.getRemark();
            if (!originalRemark.contains(patchRemark)) {
                receiptItem.setRemark((originalRemark + " " + patchRemark).trim());
            }
        }
    }

    private String resolveOrderItemSpec(PurchaseOrderItem orderItem, String fallback) {
        if (orderItem == null) {
            return fallback;
        }
        if (orderItem.getFilmSpecRaw() != null && !orderItem.getFilmSpecRaw().trim().isEmpty()) {
            return orderItem.getFilmSpecRaw().trim();
        }
        if (orderItem.getRawSpec() != null && !orderItem.getRawSpec().trim().isEmpty()) {
            return orderItem.getRawSpec().trim();
        }
        return fallback;
    }

    private Integer toIntValue(BigDecimal value) {
        if (value == null) {
            return 0;
        }
        return value.setScale(0, BigDecimal.ROUND_HALF_UP).intValue();
    }

    private String resolveDisplayUnit(PurchaseOrderItem orderItem) {
        if (orderItem == null) {
            return "";
        }
        String code = orderItem.getPurchaseUomCode();
        if (code == null || code.trim().isEmpty()) {
            code = orderItem.getStockUomCode();
        }
        if (code == null) {
            return "";
        }
        String normalized = code.trim().toUpperCase();
        switch (normalized) {
            case "DRUM":
            case "DRUN":
                return "桶";
            case "KG":
                return "kg";
            case "M2":
                return "㎡";
            case "ROLL":
                return "卷";
            case "PCS":
            case "EA":
            case "PC":
                return "支";
            default:
                return code;
        }
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return ((LoginUser) authentication.getPrincipal()).getUsername();
        }
        return "system";
    }

    @Override
    public String generateOrderNo() {
        String dateCode = new SimpleDateFormat("yyMMdd").format(new Date());
        String prefix = "CD" + dateCode;

        String lastOrderNo = purchaseOrderMapper.selectLastOrderNoByPrefix(prefix);
        int nextSeq = 1;
        if (lastOrderNo != null && lastOrderNo.length() > prefix.length()) {
            String seqPart = lastOrderNo.substring(prefix.length());
            try {
                nextSeq = Integer.parseInt(seqPart) + 1;
            } catch (Exception ignored) {
                nextSeq = 1;
            }
        }

        // 序号按2位起，不足补0（如 01、02...）
        return prefix + String.format("%02d", nextSeq);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> deleteOrder(String orderNo) {
        try {
            if (orderNo == null || orderNo.trim().isEmpty()) {
                return new ResponseResult<>(400, "采购单号不能为空");
            }
            orderNo = orderNo.trim();
            String username = getCurrentUsername();

            LambdaQueryWrapper<PurchaseOrder> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(PurchaseOrder::getOrderNo, orderNo)
                    .eq(PurchaseOrder::getIsDeleted, 0);
            PurchaseOrder order = purchaseOrderMapper.selectOne(queryWrapper);
            if (order == null) {
                return new ResponseResult<>(404, "采购订单不存在或已删除: " + orderNo);
            }

            purchaseOrderItemMapper.logicDeleteByOrderNo(orderNo, username);
            int affectedOrder = purchaseOrderMapper.logicDeleteByOrderNo(orderNo, username);
            if (affectedOrder <= 0) {
                return new ResponseResult<>(500, "删除失败，未更新到采购单: " + orderNo);
            }
            return new ResponseResult<>(200, "删除成功");
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "删除失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseResult<?> getOrderByOrderNo(String orderNo) {
        LambdaQueryWrapper<PurchaseOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(PurchaseOrder::getOrderNo, orderNo)
            .eq(PurchaseOrder::getIsDeleted, 0);
        PurchaseOrder order = purchaseOrderMapper.selectOne(queryWrapper);
        if (order != null) {
            LambdaQueryWrapper<PurchaseOrderItem> itemWrapper = new LambdaQueryWrapper<>();
            itemWrapper.eq(PurchaseOrderItem::getOrderId, order.getId())
                       .eq(PurchaseOrderItem::getIsDeleted, 0);
            List<PurchaseOrderItem> items = purchaseOrderItemMapper.selectList(itemWrapper);
            enrichItemsWithSpecInfo(items);
            order.setItems(items);
        }
        return new ResponseResult<>(200, "success", order);
    }

    @Override
    public ResponseResult<?> searchOrders(String keyword, String status) {
        try {
            LambdaQueryWrapper<PurchaseOrder> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(PurchaseOrder::getIsDeleted, 0);
            if (keyword != null && !keyword.isEmpty()) {
                queryWrapper.and(wrapper -> wrapper.like(PurchaseOrder::getOrderNo, keyword)
                        .or()
                        .like(PurchaseOrder::getSupplier, keyword));
            }
            if (status != null && !status.isEmpty()) {
                queryWrapper.eq(PurchaseOrder::getStatus, status);
            }
            queryWrapper.orderByDesc(PurchaseOrder::getCreatedAt).last("LIMIT 20");
            List<PurchaseOrder> orders = purchaseOrderMapper.selectList(queryWrapper);
            return new ResponseResult<>(200, "success", orders);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "搜索采购订单失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseResult<?> getRawSpecHistory(String supplier, String materialCode) {
        try {
            String code = materialCode == null ? "" : materialCode.trim();
            if (code.isEmpty()) {
                return new ResponseResult<>(400, "materialCode不能为空");
            }
            String supplierKeyword = supplier == null ? "" : supplier.trim();
            List<String> rawList = purchaseOrderItemMapper.selectRawSpecHistory(supplierKeyword, code);
            LinkedHashSet<String> dedup = new LinkedHashSet<>();
            if (rawList != null) {
                for (String value : rawList) {
                    if (value == null) {
                        continue;
                    }
                    String text = value.trim();
                    if (!text.isEmpty()) {
                        dedup.add(text);
                    }
                }
            }
            return new ResponseResult<>(200, "success", dedup);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "获取历史规格失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseResult<?> getReconciliationSummary(String orderNo) {
        try {
            PurchaseOrder order = purchaseOrderMapper.selectByOrderNo(orderNo);
            if (order == null) {
                return new ResponseResult<>(404, "采购订单不存在");
            }
            LambdaQueryWrapper<PurchaseOrderItem> orderItemQuery = new LambdaQueryWrapper<>();
            orderItemQuery.eq(PurchaseOrderItem::getOrderId, order.getId())
                    .eq(PurchaseOrderItem::getIsDeleted, 0);
            List<PurchaseOrderItem> orderItems = purchaseOrderItemMapper.selectList(orderItemQuery);
            normalizeItems(orderItems);

            LambdaQueryWrapper<PurchaseReceipt> receiptQuery = new LambdaQueryWrapper<>();
            receiptQuery.eq(PurchaseReceipt::getPurchaseOrderNo, orderNo)
                    .eq(PurchaseReceipt::getIsDeleted, 0);
            List<PurchaseReceipt> receipts = purchaseReceiptMapper.selectList(receiptQuery);

            BigDecimal orderAmount = BigDecimal.ZERO;
            BigDecimal receiptAmount = BigDecimal.ZERO;
            BigDecimal orderQty = BigDecimal.ZERO;
            BigDecimal receiptQty = BigDecimal.ZERO;

            Map<String, Map<String, Object>> lineMap = new HashMap<>();

            if (orderItems != null) {
                for (PurchaseOrderItem item : orderItems) {
                    BigDecimal qty = item.getPriceQty() != null ? item.getPriceQty() : item.getStockQty();
                    BigDecimal amount = item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO;
                    orderAmount = orderAmount.add(amount);
                    if (qty != null) {
                        orderQty = orderQty.add(qty);
                    }
                    String key = String.valueOf(item.getMaterialCode() == null ? item.getId() : item.getMaterialCode());
                    Map<String, Object> line = lineMap.computeIfAbsent(key, k -> new HashMap<>());
                    line.put("materialCode", item.getMaterialCode());
                    line.put("materialName", item.getMaterialName());
                    line.put("purchaseUomCode", item.getPurchaseUomCode());
                    line.put("priceUomCode", item.getPriceUomCode());
                    line.put("orderQty", ((BigDecimal) line.getOrDefault("orderQty", BigDecimal.ZERO)).add(qty == null ? BigDecimal.ZERO : qty));
                    line.put("orderAmount", ((BigDecimal) line.getOrDefault("orderAmount", BigDecimal.ZERO)).add(amount));
                }
            }

            if (receipts != null) {
                for (PurchaseReceipt receipt : receipts) {
                    List<PurchaseReceiptItem> receiptItems = purchaseReceiptItemMapper.selectByReceiptId(receipt.getId());
                    for (PurchaseReceiptItem item : receiptItems) {
                        BigDecimal qty = item.getPriceQty() != null ? item.getPriceQty() : item.getStockQty();
                        BigDecimal amount = item.getAmount() != null ? item.getAmount() : BigDecimal.ZERO;
                        receiptAmount = receiptAmount.add(amount);
                        if (qty != null) {
                            receiptQty = receiptQty.add(qty);
                        }
                        String key = String.valueOf(item.getMaterialCode() == null ? item.getId() : item.getMaterialCode());
                        Map<String, Object> line = lineMap.computeIfAbsent(key, k -> new HashMap<>());
                        line.put("materialCode", item.getMaterialCode());
                        line.put("materialName", item.getMaterialName());
                        line.put("purchaseUomCode", item.getPurchaseUomCode());
                        line.put("priceUomCode", item.getPriceUomCode());
                        line.put("receiptQty", ((BigDecimal) line.getOrDefault("receiptQty", BigDecimal.ZERO)).add(qty == null ? BigDecimal.ZERO : qty));
                        line.put("receiptAmount", ((BigDecimal) line.getOrDefault("receiptAmount", BigDecimal.ZERO)).add(amount));
                    }
                }
            }

            List<Map<String, Object>> lines = new java.util.ArrayList<>(lineMap.values());
            for (Map<String, Object> line : lines) {
                BigDecimal oq = (BigDecimal) line.getOrDefault("orderQty", BigDecimal.ZERO);
                BigDecimal rq = (BigDecimal) line.getOrDefault("receiptQty", BigDecimal.ZERO);
                BigDecimal oa = (BigDecimal) line.getOrDefault("orderAmount", BigDecimal.ZERO);
                BigDecimal ra = (BigDecimal) line.getOrDefault("receiptAmount", BigDecimal.ZERO);
                line.put("qtyDiff", oq.subtract(rq));
                line.put("amountDiff", oa.subtract(ra));
                line.put("reconciliationStatus", oq.compareTo(rq) == 0 ? "MATCHED" : (rq.compareTo(BigDecimal.ZERO) > 0 ? "PARTIAL" : "UNRECONCILED"));
            }

            Map<String, Object> result = new HashMap<>();
            result.put("orderNo", orderNo);
            result.put("orderAmount", orderAmount.setScale(2, BigDecimal.ROUND_HALF_UP));
            result.put("receiptAmount", receiptAmount.setScale(2, BigDecimal.ROUND_HALF_UP));
            result.put("amountDiff", orderAmount.subtract(receiptAmount).setScale(2, BigDecimal.ROUND_HALF_UP));
            result.put("orderQty", orderQty.setScale(4, BigDecimal.ROUND_HALF_UP));
            result.put("receiptQty", receiptQty.setScale(4, BigDecimal.ROUND_HALF_UP));
            result.put("qtyDiff", orderQty.subtract(receiptQty).setScale(4, BigDecimal.ROUND_HALF_UP));
            result.put("lineItems", lines);
            result.put("reconciliationStatus", orderQty.compareTo(receiptQty) == 0 && orderAmount.compareTo(receiptAmount) == 0 ? "MATCHED" : "PARTIAL");
            return ResponseResult.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "获取对账汇总失败: " + e.getMessage());
        }
    }

    @Override
    public void exportOrders(HttpServletResponse response) {
        // 预留导出扩展点
    }

    @Override
    public ResponseResult<?> importOrders(MultipartFile file, String username) {
        return new ResponseResult<>(200, "Not implemented yet");
    }

    @Override
    public void downloadTemplate(HttpServletResponse response) {
        // 预留模板下载扩展点
    }
}
