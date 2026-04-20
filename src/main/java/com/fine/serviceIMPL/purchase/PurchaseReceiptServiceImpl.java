package com.fine.serviceIMPL.purchase;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.purchase.PurchaseReceiptItemMapper;
import com.fine.Dao.purchase.PurchaseReceiptMapper;
import com.fine.Dao.purchase.PurchaseOrderItemMapper;
import com.fine.Dao.purchase.PurchaseOrderMapper;
import com.fine.Dao.rd.TapeFormulaMapper;
import com.fine.Dao.stock.TapeInboundRequestMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.PurchaseOrder;
import com.fine.modle.PurchaseOrderItem;
import com.fine.modle.rd.TapeRawMaterial;
import com.fine.modle.purchase.PurchaseReceipt;
import com.fine.modle.purchase.PurchaseReceiptItem;
import com.fine.modle.stock.TapeInboundRequest;
import com.fine.service.purchase.PurchaseReceiptService;
import com.fine.service.system.SystemMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    @Autowired
    private TapeFormulaMapper tapeFormulaMapper;

    @Autowired
    private SystemMessageService systemMessageService;

    @Autowired
    private TapeInboundRequestMapper tapeInboundRequestMapper;

    private static final String TEST_TAG = "[TEST_DATA_PURCHASE_RECEIPT]";
    private static final String INBOUND_SOURCE_TAG = "[PURCHASE_RECEIPT]";
    private static final Pattern SPEC_NUMBER_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");

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
        } else {
            receipt.setReceiptNo(receipt.getReceiptNo().trim());
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
        syncInboundRequestsFromReceipt(receipt.getId());
        refreshReconciliationStatus(receipt.getPurchaseOrderNo());
        notifyWarehouseForArrival(receipt, null);
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
        syncInboundRequestsFromReceipt(receipt.getId());
        refreshReconciliationStatus(receipt.getPurchaseOrderNo());
        notifyWarehouseForArrival(receipt, existing.getStatus());
        return ResponseResult.success(receipt);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> deleteReceipt(Long id) {
        if (id == null || id <= 0) {
            return new ResponseResult<>(400, "收货通知ID不能为空");
        }
        PurchaseReceipt receipt = receiptMapper.selectById(id);
        if (receipt == null || Integer.valueOf(1).equals(receipt.getIsDeleted())) {
            return new ResponseResult<>(404, "收货通知不存在");
        }
        int affected = receiptMapper.logicDeleteById(id, LocalDateTime.now());
        if (affected <= 0) {
            return new ResponseResult<>(500, "删除失败，请稍后重试");
        }
        itemMapper.deleteByReceiptId(id);
        refreshReconciliationStatus(receipt.getPurchaseOrderNo());
        return ResponseResult.success();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> seedTestData(Integer count) {
        int safeCount = (count == null || count <= 0) ? 3 : Math.min(count, 20);
        List<String> receiptNos = new ArrayList<>();
        for (int i = 1; i <= safeCount; i++) {
            PurchaseReceipt receipt = new PurchaseReceipt();
            receipt.setReceiptNo("TEST-PR-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + "-" + i);
            receipt.setPurchaseOrderNo("TEST-PO-" + DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDate.now()));
            receipt.setSupplier("测试供应商-" + i);
            receipt.setContactName("测试联系人");
            receipt.setContactPhone("13800000000");
            receipt.setReceiveAddress("测试收货地址");
            receipt.setExpectedDate(LocalDate.now());
            receipt.setReceivedDate(LocalDate.now());
            receipt.setStatus(i % 2 == 0 ? "received" : "planned");
            receipt.setRemark(TEST_TAG + " 自动生成测试数据");
            receipt.setReconciliationStatus("UNRECONCILED");
            receipt.setIsDeleted(0);
            receipt.setCreatedAt(LocalDateTime.now());
            receipt.setUpdatedAt(LocalDateTime.now());
            receiptMapper.insert(receipt);

            PurchaseReceiptItem item = new PurchaseReceiptItem();
            item.setReceiptId(receipt.getId());
            item.setPurchaseOrderNo(receipt.getPurchaseOrderNo());
            item.setMaterialCode("TEST-MAT-" + i);
            item.setMaterialName("测试物料-" + i);
            item.setSpecification("50μm*1000mm*100m");
            item.setPurchaseQty(new BigDecimal("10"));
            item.setPurchaseUomCode("ROLL");
            item.setPriceQty(new BigDecimal("10"));
            item.setPriceUomCode("ROLL");
            item.setStockQty(new BigDecimal("10"));
            item.setStockUomCode("ROLL");
            item.setConversionRate(new BigDecimal("1"));
            item.setExpectedQty(10);
            item.setReceivedQty(i % 2 == 0 ? 10 : 0);
            item.setUnit("卷");
            item.setUnitPrice(new BigDecimal("1"));
            item.setAmount(new BigDecimal("10"));
            item.setRemark(TEST_TAG);
            item.setIsDeleted(0);
            item.setCreatedAt(LocalDateTime.now());
            item.setUpdatedAt(LocalDateTime.now());
            itemMapper.insert(item);

            notifyWarehouseForArrival(receipt, null);
            receiptNos.add(receipt.getReceiptNo());
        }
        Map<String, Object> data = new HashMap<>();
        data.put("count", safeCount);
        data.put("receiptNos", receiptNos);
        return ResponseResult.success("测试数据生成成功", data);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> cleanupTestData() {
        List<PurchaseReceipt> testReceipts = receiptMapper.selectList(
                new LambdaQueryWrapper<PurchaseReceipt>()
                        .eq(PurchaseReceipt::getIsDeleted, 0)
                        .like(PurchaseReceipt::getRemark, TEST_TAG)
        );
        int receiptCount = 0;
        int itemCount = 0;
        if (!CollectionUtils.isEmpty(testReceipts)) {
            for (PurchaseReceipt receipt : testReceipts) {
                if (receipt == null || receipt.getId() == null) {
                    continue;
                }
                itemCount += itemMapper.deleteByReceiptId(receipt.getId());
                receipt.setIsDeleted(1);
                receipt.setUpdatedAt(LocalDateTime.now());
                receiptMapper.updateById(receipt);
                receiptCount++;
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("receiptCount", receiptCount);
        data.put("itemCount", itemCount);
        return ResponseResult.success("测试数据清理完成", data);
    }

    private String generateReceiptNo() {
        return "PR"
                + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now())
                + ThreadLocalRandom.current().nextInt(10, 100);
    }

    private void syncInboundRequestsFromReceipt(Long receiptId) {
        if (receiptId == null) {
            return;
        }
        PurchaseReceipt receipt = receiptMapper.selectById(receiptId);
        if (receipt == null || Integer.valueOf(1).equals(receipt.getIsDeleted())) {
            return;
        }
        List<PurchaseReceiptItem> items = itemMapper.selectByReceiptId(receiptId);
        if (CollectionUtils.isEmpty(items)) {
            return;
        }
        for (PurchaseReceiptItem item : items) {
            if (item == null || item.getId() == null) {
                continue;
            }
            upsertInboundRequestByReceiptItem(receipt, item);
        }
    }

    private void upsertInboundRequestByReceiptItem(PurchaseReceipt receipt, PurchaseReceiptItem item) {
        String token = buildInboundSourceToken(receipt.getId(), item.getId());
        LambdaQueryWrapper<TapeInboundRequest> existingQuery = new LambdaQueryWrapper<>();
        existingQuery.like(TapeInboundRequest::getRemark, token)
                .orderByDesc(TapeInboundRequest::getId)
                .last("LIMIT 1");
        TapeInboundRequest inbound = tapeInboundRequestMapper.selectOne(existingQuery);

        Integer targetRolls = resolveInboundRolls(item);
        if (targetRolls == null || targetRolls <= 0) {
            targetRolls = 1;
        }

        String materialDisplayName = resolveMaterialDisplayName(item, new HashMap<>());

        if (inbound != null) {
            if (inbound.getStatus() != null && inbound.getStatus().intValue() == TapeInboundRequest.STATUS_APPROVED) {
                return;
            }
            inbound.setMaterialCode(defaultString(item.getMaterialCode()));
            inbound.setProductName(defaultString(materialDisplayName));
            inbound.setBatchNo(buildBatchNo(receipt, item));
            inbound.setRolls(targetRolls);
            inbound.setLocation(defaultString(receipt.getReceiveAddress(), "待上架"));
            inbound.setApplicant(defaultString(receipt.getContactName(), "采购收货提交"));
            inbound.setApplyDept("采购收货");
            applySpecificationToInbound(inbound, item);
            inbound.setRemark(buildInboundRemark(receipt, item));
            inbound.setStatus(TapeInboundRequest.STATUS_PENDING);
            inbound.setApplyTime(LocalDateTime.now());
            tapeInboundRequestMapper.updateById(inbound);
            return;
        }

        TapeInboundRequest request = new TapeInboundRequest();
        String requestNo = tapeInboundRequestMapper.generateRequestNo();
        if (!StringUtils.hasText(requestNo)) {
            requestNo = "IN" + DateTimeFormatter.ofPattern("yyyyMMdd").format(LocalDate.now()) + "0001";
        }
        request.setRequestNo(requestNo);
        request.setMaterialCode(defaultString(item.getMaterialCode()));
        request.setProductName(defaultString(materialDisplayName));
        request.setBatchNo(buildBatchNo(receipt, item));
        request.setRolls(targetRolls);
        request.setLocation(defaultString(receipt.getReceiveAddress(), "待上架"));
        request.setApplicant(defaultString(receipt.getContactName(), "采购收货提交"));
        request.setApplyDept("采购收货");
        request.setApplyTime(LocalDateTime.now());
        request.setStatus(TapeInboundRequest.STATUS_PENDING);
        applySpecificationToInbound(request, item);
        request.setRemark(buildInboundRemark(receipt, item));
        tapeInboundRequestMapper.insert(request);
    }

    private void applySpecificationToInbound(TapeInboundRequest inbound, PurchaseReceiptItem item) {
        if (inbound == null || item == null) {
            return;
        }
        String spec = StringUtils.hasText(item.getSpecification()) ? item.getSpecification().trim() : "";
        if (!StringUtils.hasText(spec)) {
            return;
        }

        List<Integer> dims = parseSpecificationNumbers(spec);
        if (dims.size() >= 3) {
            if (inbound.getThickness() == null || inbound.getThickness() <= 0) {
                inbound.setThickness(dims.get(0));
            }
            if (inbound.getWidth() == null || inbound.getWidth() <= 0) {
                inbound.setWidth(dims.get(1));
            }
            if (inbound.getLength() == null || inbound.getLength() <= 0) {
                inbound.setLength(dims.get(2));
            }
            if (inbound.getThickness() != null && inbound.getWidth() != null && inbound.getLength() != null) {
                inbound.setSpecDesc(inbound.getThickness() + "μm*" + inbound.getWidth() + "mm*" + inbound.getLength() + "m");
                return;
            }
        }

        if (!StringUtils.hasText(inbound.getSpecDesc())) {
            inbound.setSpecDesc(spec);
        }
    }

    private List<Integer> parseSpecificationNumbers(String spec) {
        List<Integer> result = new ArrayList<>();
        if (!StringUtils.hasText(spec)) {
            return result;
        }
        Matcher matcher = SPEC_NUMBER_PATTERN.matcher(spec);
        while (matcher.find() && result.size() < 3) {
            String val = matcher.group(1);
            try {
                int parsed = new BigDecimal(val).setScale(0, BigDecimal.ROUND_HALF_UP).intValue();
                if (parsed > 0) {
                    result.add(parsed);
                }
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private Integer resolveInboundRolls(PurchaseReceiptItem item) {
        if (item == null) {
            return 0;
        }
        if (item.getReceivedQty() != null && item.getReceivedQty() > 0) {
            return item.getReceivedQty();
        }
        if (item.getExpectedQty() != null && item.getExpectedQty() > 0) {
            return item.getExpectedQty();
        }
        if (item.getPurchaseQty() != null && item.getPurchaseQty().compareTo(BigDecimal.ZERO) > 0) {
            return item.getPurchaseQty().setScale(0, BigDecimal.ROUND_HALF_UP).intValue();
        }
        return 0;
    }

    private String buildInboundSourceToken(Long receiptId, Long itemId) {
        return INBOUND_SOURCE_TAG + "|receiptId=" + receiptId + "|itemId=" + itemId;
    }

    private String buildInboundRemark(PurchaseReceipt receipt, PurchaseReceiptItem item) {
        String token = buildInboundSourceToken(receipt.getId(), item.getId());
        return token
                + "|receiptNo=" + defaultString(receipt.getReceiptNo(), "-")
                + "|purchaseOrderNo=" + defaultString(receipt.getPurchaseOrderNo(), "-")
                + "|supplier=" + defaultString(receipt.getSupplier(), "-");
    }

    private String buildBatchNo(PurchaseReceipt receipt, PurchaseReceiptItem item) {
        String receiptNo = defaultString(receipt.getReceiptNo(), "PR");
        String materialCode = defaultString(item.getMaterialCode(), "MAT");
        String itemId = item.getId() == null ? "0" : String.valueOf(item.getId());
        return receiptNo + "-" + materialCode + "-" + itemId;
    }

    private String defaultString(String value) {
        return defaultString(value, "");
    }

    private String defaultString(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
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

    private void notifyWarehouseForArrival(PurchaseReceipt receipt, String oldStatus) {
        if (receipt == null || receipt.getId() == null) {
            return;
        }
        String newStatus = receipt.getStatus();
        if (!isArrivalStatus(newStatus)) {
            return;
        }
        if (isArrivalStatus(oldStatus) && StringUtils.hasText(oldStatus) && oldStatus.equalsIgnoreCase(newStatus)) {
            return;
        }

        List<PurchaseReceiptItem> items = receipt.getItems();
        if (CollectionUtils.isEmpty(items)) {
            items = itemMapper.selectByReceiptId(receipt.getId());
        }

        String supplierCode = resolveSupplierCode(receipt.getSupplier());
        String supplierDisplayName = supplierCode;
        String receiptNo = StringUtils.hasText(receipt.getReceiptNo()) ? receipt.getReceiptNo() : "-";
        String bizType = "PURCHASE_RECEIPT_ARRIVAL";
        String routePath = "/stock/inbound";

        Map<String, String> materialNameCache = new HashMap<>();

        if (CollectionUtils.isEmpty(items)) {
            String title = "收货提醒";
            String content = supplierDisplayName + "的到货信息已更新，请查收。";
            String bizId = receipt.getId() + "_" + newStatus + "_summary";
            String routeQueryJson = "{\"source\":\"purchase-receipt\",\"receiptId\":" + receipt.getId()
                    + ",\"receiptNo\":\"" + escapeJsonText(receiptNo) + "\"}";
            systemMessageService.createRoleMessage(
                    "warehouse",
                    title,
                    content,
                    bizType,
                    bizId,
                    routePath,
                    routeQueryJson,
                    "system"
            );
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            PurchaseReceiptItem item = items.get(i);
            if (item == null) {
                continue;
            }
            String materialCode = StringUtils.hasText(item.getMaterialCode()) ? item.getMaterialCode().trim() : "";
            String materialName = resolveMaterialDisplayName(item, materialNameCache);
            String qtyText = resolveArrivedQtyText(item);

            String title = "收货提醒";
            String content = supplierDisplayName + "的" + materialName + "货到了" + qtyText + "，请查收。";

            String itemBizKey = item.getId() == null ? String.valueOf(i) : String.valueOf(item.getId());
            String bizId = receipt.getId() + "_" + newStatus + "_" + itemBizKey;
            String routeQueryJson = "{\"source\":\"purchase-receipt\",\"receiptId\":" + receipt.getId()
                    + ",\"receiptNo\":\"" + escapeJsonText(receiptNo) + "\""
                    + ",\"itemId\":\"" + escapeJsonText(itemBizKey) + "\""
                    + ",\"materialCode\":\"" + escapeJsonText(materialCode) + "\""
                    + ",\"materialName\":\"" + escapeJsonText(materialName) + "\"}"
                    ;

            systemMessageService.createRoleMessage(
                    "warehouse",
                    title,
                    content,
                    bizType,
                    bizId,
                    routePath,
                    routeQueryJson,
                    "system"
            );
        }
    }

    private String resolveArrivedQtyText(PurchaseReceiptItem item) {
        if (item == null) {
            return "未知数量";
        }
        BigDecimal qty = null;
        if (item.getReceivedQty() != null) {
            qty = BigDecimal.valueOf(item.getReceivedQty());
        } else if (item.getPriceQty() != null) {
            qty = item.getPriceQty();
        } else if (item.getStockQty() != null) {
            qty = item.getStockQty();
        } else if (item.getPurchaseQty() != null) {
            qty = item.getPurchaseQty();
        } else if (item.getExpectedQty() != null) {
            qty = BigDecimal.valueOf(item.getExpectedQty());
        }
        String qtyText = qty == null ? "未知数量" : qty.stripTrailingZeros().toPlainString();
        String unit = StringUtils.hasText(item.getUnit()) ? item.getUnit() : "";
        return StringUtils.hasText(unit) ? (qtyText + unit) : qtyText;
    }

    private String resolveSupplierCode(String supplierCodeOrName) {
        if (!StringUtils.hasText(supplierCodeOrName)) {
            return "供应商";
        }
        String value = supplierCodeOrName.trim();
        String inner = extractInnerAlias(value);
        if (StringUtils.hasText(inner)) {
            return inner;
        }
        return value;
    }

    private String extractInnerAlias(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String value = text.trim();
        int left = Math.max(value.lastIndexOf('('), value.lastIndexOf('（'));
        int right = Math.max(value.lastIndexOf(')'), value.lastIndexOf('）'));
        if (left >= 0 && right > left) {
            return value.substring(left + 1, right).trim();
        }
        return "";
    }

    private String resolveMaterialDisplayName(PurchaseReceiptItem item, Map<String, String> materialNameCache) {
        if (item == null) {
            return "物料";
        }

        String materialCode = StringUtils.hasText(item.getMaterialCode()) ? item.getMaterialCode().trim() : "";
        String itemMaterialName = StringUtils.hasText(item.getMaterialName()) ? item.getMaterialName().trim() : "";

        if (!materialCode.isEmpty() && !itemMaterialName.isEmpty() && !materialCode.equalsIgnoreCase(itemMaterialName)) {
            return itemMaterialName;
        }

        if (!materialCode.isEmpty()) {
            String cached = materialNameCache.get(materialCode);
            if (StringUtils.hasText(cached)) {
                return cached;
            }
            try {
                TapeRawMaterial rawMaterial = tapeFormulaMapper.selectRawMaterialByCode(materialCode);
                if (rawMaterial != null && StringUtils.hasText(rawMaterial.getMaterialName())) {
                    String name = rawMaterial.getMaterialName().trim();
                    materialNameCache.put(materialCode, name);
                    return name;
                }
            } catch (Exception ignored) {
            }
        }

        if (!itemMaterialName.isEmpty()) {
            return itemMaterialName;
        }
        return materialCode.isEmpty() ? "物料" : materialCode;
    }

    private String escapeJsonText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean isArrivalStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        return "receiving".equalsIgnoreCase(status)
                || "received".equalsIgnoreCase(status)
                || "partial".equalsIgnoreCase(status);
    }
}
