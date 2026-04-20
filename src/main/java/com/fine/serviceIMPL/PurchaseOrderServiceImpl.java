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
