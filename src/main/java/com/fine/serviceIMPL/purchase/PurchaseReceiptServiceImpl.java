package com.fine.serviceIMPL.purchase;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.purchase.PurchaseReceiptItemMapper;
import com.fine.Dao.purchase.PurchaseReceiptMapper;
import com.fine.Dao.purchase.PurchaseSupplierMapper;
import com.fine.Dao.purchase.PurchaseOrderItemMapper;
import com.fine.Dao.purchase.PurchaseOrderMapper;
import com.fine.Dao.LabelPrintRecordMapper;
import com.fine.Dao.rd.TapeFormulaMapper;
import com.fine.Dao.stock.TapeInboundRequestMapper;
import com.fine.Utils.ResponseResult;
import com.fine.entity.LabelPrintRecord;
import com.fine.modle.PurchaseOrder;
import com.fine.modle.PurchaseOrderItem;
import com.fine.modle.rd.TapeRawMaterial;
import com.fine.modle.purchase.PurchaseReceipt;
import com.fine.modle.purchase.PurchaseReceiptItem;
import com.fine.modle.purchase.PurchaseSupplier;
import com.fine.modle.stock.TapeInboundRequest;
import com.fine.service.purchase.PurchaseReceiptService;
import com.fine.service.stock.TapeStockService;
import com.fine.service.system.SystemMessageService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Locale;
import java.util.Collections;

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
    private PurchaseSupplierMapper purchaseSupplierMapper;

    @Autowired
    private TapeFormulaMapper tapeFormulaMapper;

    @Autowired
    private SystemMessageService systemMessageService;

    @Autowired
    private TapeInboundRequestMapper tapeInboundRequestMapper;

    @Autowired
    private TapeStockService tapeStockService;

    @Autowired
    private LabelPrintRecordMapper labelPrintRecordMapper;

    private static final String TEST_TAG = "[TEST_DATA_PURCHASE_RECEIPT]";
    private static final String INBOUND_SOURCE_TAG = "[PURCHASE_RECEIPT]";
    private static final String PURCHASE_RECEIPT_LABEL_BIZ_TYPE = "PURCHASE_RECEIPT_LABEL";
    private static final Pattern SPEC_NUMBER_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)");
    private static final Pattern INBOUND_SOURCE_TOKEN_PATTERN = Pattern.compile("(\\[PURCHASE_RECEIPT\\]\\|receiptId=\\d+\\|itemId=\\d+)");

    @Override
    public ResponseResult<?> list(Integer pageNum, Integer pageSize, String supplier, String status, String reconciliationStatus,
                                  Boolean includeReceived, String sortField, String sortOrder) {
        Page<PurchaseReceipt> page = new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 20 : pageSize);
        String orderByClause = buildReceiptOrderByClause(sortField, sortOrder);
        Boolean includeReceivedSafe = includeReceived == null ? Boolean.FALSE : includeReceived;
        IPage<PurchaseReceipt> result = receiptMapper.selectPaged(page, supplier, status, reconciliationStatus, includeReceivedSafe, orderByClause);
        applySupplierShortNameDisplay(result);
        return ResponseResult.success(result);
    }

    private String buildReceiptOrderByClause(String sortField, String sortOrder) {
        String normalizedField = sortField == null ? "" : sortField.trim();
        String column;
        switch (normalizedField) {
            case "receiptNo":
                column = "receipt_no";
                break;
            case "supplier":
                column = "supplier";
                break;
            case "reconciliationStatus":
                column = "reconciliation_status";
                break;
            case "expectedDate":
                column = "expected_date";
                break;
            case "receivedDate":
                column = "received_date";
                break;
            case "status":
                column = "status";
                break;
            case "updatedAt":
                column = "updated_at";
                break;
            case "createdAt":
            default:
                column = "created_at";
                break;
        }
        String direction = normalizeSortDirection(sortOrder);
        return column + " " + direction + ", id DESC";
    }

    private String normalizeSortDirection(String sortOrder) {
        String v = sortOrder == null ? "" : sortOrder.trim().toLowerCase(Locale.ROOT);
        if ("asc".equals(v) || "ascending".equals(v) || "ascend".equals(v)) {
            return "ASC";
        }
        return "DESC";
    }

    private void applySupplierShortNameDisplay(IPage<PurchaseReceipt> pageData) {
        if (pageData == null || CollectionUtils.isEmpty(pageData.getRecords())) {
            return;
        }

        Set<String> supplierKeys = new LinkedHashSet<>();
        for (PurchaseReceipt receipt : pageData.getRecords()) {
            if (receipt != null && StringUtils.hasText(receipt.getSupplier())) {
                supplierKeys.add(receipt.getSupplier().trim());
            }
        }
        if (supplierKeys.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<PurchaseSupplier> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.in(PurchaseSupplier::getSupplierName, supplierKeys)
                .or().in(PurchaseSupplier::getSupplierCode, supplierKeys)
                .or().in(PurchaseSupplier::getShortName, supplierKeys));
        List<PurchaseSupplier> suppliers = purchaseSupplierMapper.selectList(wrapper);
        if (CollectionUtils.isEmpty(suppliers)) {
            return;
        }

        Map<String, String> aliasMap = new HashMap<>();
        for (PurchaseSupplier supplier : suppliers) {
            if (supplier == null) continue;
            String shortName = StringUtils.hasText(supplier.getShortName())
                    ? supplier.getShortName().trim()
                    : (StringUtils.hasText(supplier.getSupplierName()) ? supplier.getSupplierName().trim() : "");
            if (!StringUtils.hasText(shortName)) {
                continue;
            }
            putSupplierAlias(aliasMap, supplier.getSupplierCode(), shortName);
            putSupplierAlias(aliasMap, supplier.getSupplierName(), shortName);
            putSupplierAlias(aliasMap, supplier.getShortName(), shortName);
        }

        for (PurchaseReceipt receipt : pageData.getRecords()) {
            if (receipt == null || !StringUtils.hasText(receipt.getSupplier())) {
                continue;
            }
            String alias = aliasMap.get(normalizeSupplierKey(receipt.getSupplier()));
            if (StringUtils.hasText(alias)) {
                receipt.setSupplier(alias);
            }
        }
    }

    private void putSupplierAlias(Map<String, String> aliasMap, String key, String alias) {
        String normalized = normalizeSupplierKey(key);
        if (!StringUtils.hasText(normalized) || !StringUtils.hasText(alias)) {
            return;
        }
        aliasMap.put(normalized, alias.trim());
    }

    private String normalizeSupplierKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
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
        // 采购端仅维护预计到货信息：状态与实际到货日期由仓库入库动作驱动
        receipt.setStatus("planned");
        receipt.setReceivedDate(null);
        normalizeReceipt(receipt);
        String operator = getCurrentUsername();
        if (!StringUtils.hasText(receipt.getCreatedBy())) {
            receipt.setCreatedBy(operator);
        }
        receipt.setUpdatedBy(operator);
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

        boolean expectedDateChanged = (existing.getExpectedDate() == null && receipt.getExpectedDate() != null)
                || (existing.getExpectedDate() != null && !existing.getExpectedDate().equals(receipt.getExpectedDate()));

        receipt.setCreatedAt(existing.getCreatedAt());
        receipt.setCreatedBy(existing.getCreatedBy());
        receipt.setUpdatedBy(getCurrentUsername());
        receipt.setUpdatedAt(LocalDateTime.now());
        receipt.setIsDeleted(0);
        // 采购端不能修改实际到货状态：保持仓库回填结果
        receipt.setStatus(existing.getStatus());
        receipt.setReceivedDate(existing.getReceivedDate());
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
        String notifyOldStatus = existing.getStatus();
        if ("planned".equalsIgnoreCase(existing.getStatus()) && expectedDateChanged) {
            // 计划到货日期更新时，重新触发仓库可见提醒
            notifyOldStatus = null;
        }
        notifyWarehouseForArrival(receipt, notifyOldStatus);
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> syncInboundRequestsForAllActiveReceipts() {
        List<PurchaseReceipt> receipts = receiptMapper.selectList(
                new LambdaQueryWrapper<PurchaseReceipt>()
                        .eq(PurchaseReceipt::getIsDeleted, 0)
                        .orderByAsc(PurchaseReceipt::getId)
        );

        int total = receipts == null ? 0 : receipts.size();
        int syncedInbound = 0;
        int statusUpdated = 0;
        int skippedReceived = 0;

        Set<String> approvedOrderNos = collectApprovedPurchaseOrderNosFromInbound();
        String operator = getCurrentUsername();

        if (receipts != null && !CollectionUtils.isEmpty(receipts)) {
            for (PurchaseReceipt receipt : receipts) {
                if (receipt == null || receipt.getId() == null) {
                    continue;
                }

                String beforeStatus = defaultString(receipt.getStatus());
                boolean alreadyReceived = "received".equalsIgnoreCase(beforeStatus) || "已收货".equals(beforeStatus);

                // 未收货计划补齐到入库申请（仓库端可见）
                if (!alreadyReceived) {
                    syncInboundRequestsFromReceipt(receipt.getId());
                    syncedInbound++;
                } else {
                    skippedReceived++;
                }

                boolean changed = reconcileReceiptStatusFromInbound(receipt, operator);

                // 兜底：若同订单存在已审批采购入库，且当前单仍未收货，则回填为已收货
                if (!changed
                        && !alreadyReceived
                        && StringUtils.hasText(receipt.getPurchaseOrderNo())
                        && approvedOrderNos.contains(receipt.getPurchaseOrderNo().trim())) {
                    receipt.setStatus("received");
                    receipt.setReceivedDate(LocalDate.now());
                    receipt.setUpdatedAt(LocalDateTime.now());
                    receipt.setUpdatedBy(StringUtils.hasText(operator) ? operator : "warehouse");
                    receiptMapper.updateById(receipt);
                    changed = true;
                }

                if (changed) {
                    statusUpdated++;
                }
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("totalReceipts", total);
        data.put("inboundSyncedCount", syncedInbound);
        data.put("statusUpdatedCount", statusUpdated);
        data.put("skippedReceivedCount", skippedReceived);
        return ResponseResult.success("采购到货通知与仓库入库申请同步完成", data);
    }

    @Override
    public ResponseResult<?> listScanInboundDocuments(Integer pageNum, Integer pageSize, String keyword) {
        int current = pageNum == null || pageNum <= 0 ? 1 : pageNum;
        int size = pageSize == null || pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : "";

        LambdaQueryWrapper<PurchaseReceipt> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PurchaseReceipt::getIsDeleted, 0)
                .and(w -> w.isNull(PurchaseReceipt::getStatus)
                        .or().ne(PurchaseReceipt::getStatus, "received"))
                .orderByDesc(PurchaseReceipt::getCreatedAt)
                .orderByDesc(PurchaseReceipt::getId);
        if (StringUtils.hasText(kw)) {
            wrapper.and(w -> w.like(PurchaseReceipt::getReceiptNo, kw)
                    .or().like(PurchaseReceipt::getSupplier, kw)
                    .or().like(PurchaseReceipt::getPurchaseOrderNo, kw));
        }

        Page<PurchaseReceipt> page = new Page<>(current, size);
        IPage<PurchaseReceipt> result = receiptMapper.selectPage(page, wrapper);

        List<Map<String, Object>> rows = new ArrayList<>();
        if (result != null && !CollectionUtils.isEmpty(result.getRecords())) {
            for (PurchaseReceipt row : result.getRecords()) {
                if (row == null) {
                    continue;
                }
                Map<String, Object> m = new HashMap<>();
                String supplierCode = resolveSupplierCode(row.getSupplier());
                String supplierName = resolveSupplierDisplayName(row.getSupplier());
                m.put("id", row.getId());
                m.put("receiptNo", defaultString(row.getReceiptNo(), "-"));
                m.put("purchaseOrderNo", defaultString(row.getPurchaseOrderNo(), "-"));
                m.put("supplier", defaultString(supplierName, "-"));
                m.put("supplierName", defaultString(supplierName, "-"));
                m.put("supplierCode", defaultString(supplierCode, "-"));
                m.put("expectedDate", row.getExpectedDate() == null ? null : row.getExpectedDate().toString());
                m.put("status", defaultString(row.getStatus(), "planned"));
                rows.add(m);
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("records", rows);
        data.put("total", result == null ? 0 : result.getTotal());
        data.put("current", current);
        data.put("size", size);
        return ResponseResult.success("查询成功", data);
    }

    @Override
    public ResponseResult<?> getScanInboundDocument(String receiptNo, Long receiptId) {
        PurchaseReceipt receipt = findReceiptByNoOrId(receiptNo, receiptId);
        if (receipt == null && StringUtils.hasText(receiptNo)) {
            receipt = findReceiptByInboundScanCode(receiptNo);
            if (receipt == null) {
                receipt = findReceiptByPrintedLabel(receiptNo);
            }
        }
        if (receipt == null && StringUtils.hasText(receiptNo)) {
            return buildScanInboundDocumentByRequests(receiptNo);
        }
        if (receipt == null) {
            return ResponseResult.error("未找到收货单");
        }

        List<PurchaseReceiptItem> items = itemMapper.selectByReceiptId(receipt.getId());
        List<TapeInboundRequest> requests = listInboundRequestsByReceiptId(receipt.getId());
        Map<Long, TapeInboundRequest> latestByItemId = buildLatestInboundByItemId(requests);
        String supplierCode = resolveSupplierCode(receipt.getSupplier());
        String supplierName = resolveSupplierDisplayName(receipt.getSupplier());

        List<Map<String, Object>> itemRows = new ArrayList<>();
        List<String> validBatchNos = new ArrayList<>();
        List<String> alreadyInBatchNos = new ArrayList<>();
        int totalPlanQty = 0;
        int totalPrintedQty = 0;
        int totalScannedQty = 0;
        Map<String, Integer> printedQtyCache = new HashMap<>();

        Map<String, String> materialNameCache = new HashMap<>();
        if (!CollectionUtils.isEmpty(items)) {
            for (PurchaseReceiptItem item : items) {
                if (item == null) {
                    continue;
                }
                TapeInboundRequest req = latestByItemId.get(item.getId());

                int planQty = resolvePlanQty(item, req);
                int printedQty = resolvePrintedQty(req, printedQtyCache);
                int scannedQty = req != null && req.getStatus() != null
                    && req.getStatus().intValue() == TapeInboundRequest.STATUS_APPROVED
                    ? planQty : 0;
                totalPlanQty += planQty;
                totalPrintedQty += printedQty;
                totalScannedQty += scannedQty;

                String batchNo = "-";
                if (req != null) {
                    if (StringUtils.hasText(req.getCustomerBatchNo())) {
                        batchNo = req.getCustomerBatchNo().trim();
                    } else if (StringUtils.hasText(req.getBatchNo())) {
                        batchNo = req.getBatchNo().trim();
                    }
                }

                Integer reqStatus = req == null ? null : req.getStatus();
                if (StringUtils.hasText(batchNo) && !"-".equals(batchNo)) {
                    if (reqStatus != null && reqStatus.intValue() == TapeInboundRequest.STATUS_PENDING) {
                        validBatchNos.add(batchNo);
                    }
                    if (reqStatus != null && reqStatus.intValue() == TapeInboundRequest.STATUS_APPROVED) {
                        alreadyInBatchNos.add(batchNo);
                    }
                }

                Map<String, Object> row = new HashMap<>();
                row.put("itemId", item.getId());
                row.put("requestId", req == null ? null : req.getId());
                row.put("requestNo", req == null ? "-" : defaultString(req.getRequestNo(), "-"));
                row.put("requestStatus", reqStatus);
                row.put("supplier", defaultString(supplierName, "-"));
                row.put("supplierName", defaultString(supplierName, "-"));
                row.put("supplierCode", defaultString(supplierCode, "-"));
                row.put("materialCode", defaultString(item.getMaterialCode(), "-"));
                row.put("materialName", resolveMaterialDisplayName(item, materialNameCache));
                row.put("specification", defaultString(item.getSpecification(), "-"));
                row.put("incomingBatchNo", batchNo);
                row.put("productionDate", receipt.getExpectedDate() == null ? "-" : receipt.getExpectedDate().toString());
                row.put("planQty", planQty);
                row.put("printedQty", printedQty);
                row.put("scannedQty", scannedQty);
                row.put("unit", normalizeInboundDisplayUnit(item.getUnit()));
                itemRows.add(row);
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("planQty", totalPlanQty);
        summary.put("printedQty", totalPrintedQty);
        summary.put("scannedQty", totalScannedQty);

        Map<String, Object> data = new HashMap<>();
        data.put("receiptId", receipt.getId());
        data.put("receiptNo", defaultString(receipt.getReceiptNo(), "-"));
        data.put("purchaseOrderNo", defaultString(receipt.getPurchaseOrderNo(), "-"));
        data.put("supplier", defaultString(supplierName, "-"));
        data.put("supplierName", defaultString(supplierName, "-"));
        data.put("supplierCode", defaultString(supplierCode, "-"));
        data.put("status", defaultString(receipt.getStatus(), "planned"));
        data.put("summary", summary);
        data.put("items", itemRows);
        data.put("validBatchNos", validBatchNos);
        data.put("alreadyInBatchNos", alreadyInBatchNos);
        return ResponseResult.success("查询成功", data);
    }

    @Override
    public ResponseResult<?> submitScanInbound(String receiptNo, Long receiptId, List<String> scanCodes, String scannedLocation, String operator) {
        PurchaseReceipt receipt = findReceiptByNoOrId(receiptNo, receiptId);
        if (receipt == null && StringUtils.hasText(receiptNo)) {
            receipt = findReceiptByInboundScanCode(receiptNo);
            if (receipt == null) {
                receipt = findReceiptByPrintedLabel(receiptNo);
            }
        }

        List<String> normalizedCodes = new ArrayList<>();
        if (scanCodes != null) {
            for (String code : scanCodes) {
                if (StringUtils.hasText(code)) {
                    normalizedCodes.add(code.trim());
                }
            }
        }
        if (normalizedCodes.isEmpty()) {
            return ResponseResult.error("请先扫码");
        }

        List<TapeInboundRequest> requests = receipt == null ? new ArrayList<>() : listInboundRequestsByReceiptId(receipt.getId());
        if (CollectionUtils.isEmpty(requests)) {
            requests = collectInboundRequestsByScanCodes(normalizedCodes);
        }
        if (CollectionUtils.isEmpty(requests)) {
            return ResponseResult.error("当前单据无可入库明细");
        }

        Map<String, List<TapeInboundRequest>> requestByBatch = buildInboundByBatch(requests);
        Map<Long, TapeInboundRequest> pendingRequestMap = new LinkedHashMap<>();
        Map<Long, List<String>> scanCodesByRequestId = new HashMap<>();
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        int successCount = 0;

        for (String code : normalizedCodes) {
            if (!seen.add(code)) {
                failures.add(buildFailure(code, "重复码"));
                continue;
            }

            TapeInboundRequest matched = findInboundByScanCode(code, requestByBatch);
            if (matched == null) {
                matched = findInboundByPrintedLabelCode(code, requests);
            }
            if (matched == null) {
                matched = findInboundByScanCodeGlobal(code);
            }
            if (matched == null) {
                failures.add(buildFailure(code, "无效码"));
                continue;
            }

            Integer status = matched.getStatus();
            if (status == null || status.intValue() != TapeInboundRequest.STATUS_PENDING) {
                failures.add(buildFailure(code, "已入库码"));
                continue;
            }

            Long reqId = matched.getId();
            if (reqId == null || reqId <= 0) {
                failures.add(buildFailure(code, "无效入库申请"));
                continue;
            }
            pendingRequestMap.putIfAbsent(reqId, matched);
            scanCodesByRequestId.computeIfAbsent(reqId, k -> new ArrayList<>()).add(code);
        }

        String auditor = StringUtils.hasText(operator) ? operator.trim() : getCurrentUsername();
        String location = StringUtils.hasText(scannedLocation) ? scannedLocation.trim() : null;
        if (pendingRequestMap.size() > 0 && !StringUtils.hasText(location)) {
            return ResponseResult.error("请先扫码/填写库位后再提交入仓");
        }

        Map<Long, Integer> scannedIncrementByItemId = new HashMap<>();
        int approvedRequestCount = 0;
        if (!pendingRequestMap.isEmpty()) {
            for (Map.Entry<Long, TapeInboundRequest> entry : pendingRequestMap.entrySet()) {
                Long reqId = entry.getKey();
                TapeInboundRequest matched = entry.getValue();
                List<String> codesForReq = scanCodesByRequestId.getOrDefault(reqId, Collections.emptyList());
                if (reqId == null || reqId <= 0) {
                    continue;
                }
                try {
                    tapeStockService.approveInbound(reqId, true, auditor, "小程序扫码入仓", null, location);
                    approvedRequestCount++;
                    successCount += codesForReq.size();

                    Long itemId = parseLongSafe(extractInboundTokenValue(matched.getRemark(), "itemId"));
                    if (itemId != null && itemId > 0 && !codesForReq.isEmpty()) {
                        scannedIncrementByItemId.put(itemId, scannedIncrementByItemId.getOrDefault(itemId, 0) + codesForReq.size());
                    }
                } catch (Exception ex) {
                    String reason = "入库失败: " + ex.getMessage();
                    if (codesForReq == null || codesForReq.isEmpty()) {
                        failures.add(buildFailure(String.valueOf(reqId), reason));
                    } else {
                        for (String code : codesForReq) {
                            failures.add(buildFailure(code, reason));
                        }
                    }
                }
            }
        }

        if (!scannedIncrementByItemId.isEmpty()) {
            for (Map.Entry<Long, Integer> entry : scannedIncrementByItemId.entrySet()) {
                Long itemId = entry.getKey();
                Integer add = entry.getValue();
                if (itemId == null || itemId <= 0 || add == null || add <= 0) {
                    continue;
                }
                PurchaseReceiptItem item = itemMapper.selectById(itemId);
                if (item == null || Integer.valueOf(1).equals(item.getIsDeleted())) {
                    continue;
                }
                int current = item.getReceivedQty() == null ? 0 : Math.max(0, item.getReceivedQty());
                item.setReceivedQty(current + add);
                item.setUpdatedAt(LocalDateTime.now());
                itemMapper.updateById(item);
            }
        }

        if (receipt != null && approvedRequestCount > 0) {
            receipt.setStatus("SCANNED_IN");
            receipt.setReceivedDate(LocalDate.now());
            receipt.setUpdatedAt(LocalDateTime.now());
            receipt.setUpdatedBy(auditor);
            receiptMapper.updateById(receipt);
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("receiptId", receipt == null ? null : receipt.getId());
        summary.put("receiptNo", receipt == null ? defaultString(receiptNo, "-") : receipt.getReceiptNo());
        summary.put("status", approvedRequestCount > 0 ? "SCANNED_IN" : (receipt == null ? "planned" : defaultString(receipt.getStatus(), "planned")));
        summary.put("successCount", successCount);
        summary.put("failureCount", failures.size());
        summary.put("approvedRequestCount", approvedRequestCount);
        summary.put("failures", failures);
        return ResponseResult.success("扫码入仓完成", summary);
    }

    private ResponseResult<?> buildScanInboundDocumentByRequests(String scanCode) {
        List<TapeInboundRequest> requests = collectInboundRequestsByScanCodes(Collections.singletonList(scanCode));
        if (CollectionUtils.isEmpty(requests)) {
            return ResponseResult.error("未找到收货单");
        }

        List<Map<String, Object>> itemRows = new ArrayList<>();
        List<String> validBatchNos = new ArrayList<>();
        List<String> alreadyInBatchNos = new ArrayList<>();
        int totalPlanQty = 0;
        int totalPrintedQty = 0;
        int totalScannedQty = 0;
        Map<String, Integer> printedQtyCache = new HashMap<>();

        TapeInboundRequest first = requests.get(0);
        String firstRemark = first == null ? null : first.getRemark();
        Long receiptId = parseLongSafe(extractInboundTokenValue(firstRemark, "receiptId"));
        String receiptNo = extractInboundTokenValue(firstRemark, "receiptNo");
        String supplier = extractInboundTokenValue(firstRemark, "supplier");
        String supplierCode = resolveSupplierCode(supplier);
        String supplierName = resolveSupplierDisplayName(supplier);

        for (TapeInboundRequest req : requests) {
            if (req == null || req.getId() == null) {
                continue;
            }
            int planQty = req.getRolls() == null ? 0 : Math.max(0, req.getRolls());
            int printedQty = resolvePrintedQty(req, printedQtyCache);
            int scannedQty = req.getStatus() != null && req.getStatus().intValue() == TapeInboundRequest.STATUS_APPROVED ? planQty : 0;
            totalPlanQty += planQty;
            totalPrintedQty += printedQty;
            totalScannedQty += scannedQty;

            String batchNo = "-";
            if (StringUtils.hasText(req.getCustomerBatchNo())) {
                batchNo = req.getCustomerBatchNo().trim();
            } else if (StringUtils.hasText(req.getBatchNo())) {
                batchNo = req.getBatchNo().trim();
            }

            if (StringUtils.hasText(batchNo) && !"-".equals(batchNo)) {
                if (req.getStatus() != null && req.getStatus().intValue() == TapeInboundRequest.STATUS_PENDING) {
                    validBatchNos.add(batchNo);
                }
                if (req.getStatus() != null && req.getStatus().intValue() == TapeInboundRequest.STATUS_APPROVED) {
                    alreadyInBatchNos.add(batchNo);
                }
            }

            Map<String, Object> row = new HashMap<>();
            row.put("itemId", parseLongSafe(extractInboundTokenValue(req.getRemark(), "itemId")));
            row.put("requestId", req.getId());
            row.put("requestNo", defaultString(req.getRequestNo(), "-"));
            row.put("requestStatus", req.getStatus());
            row.put("supplier", defaultString(supplierName, "-"));
            row.put("supplierName", defaultString(supplierName, "-"));
            row.put("supplierCode", defaultString(supplierCode, "-"));
            row.put("materialCode", defaultString(req.getMaterialCode(), "-"));
            row.put("materialName", defaultString(req.getProductName(), defaultString(req.getMaterialCode(), "-")));
            row.put("specification", defaultString(req.getSpecDesc(), "-"));
            row.put("incomingBatchNo", batchNo);
            row.put("productionDate", "-");
            row.put("planQty", planQty);
            row.put("printedQty", printedQty);
            row.put("scannedQty", scannedQty);
            row.put("unit", normalizeInboundDisplayUnit(req.getQtyUnit()));
            itemRows.add(row);
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("planQty", totalPlanQty);
        summary.put("printedQty", totalPrintedQty);
        summary.put("scannedQty", totalScannedQty);

        Map<String, Object> data = new HashMap<>();
        data.put("receiptId", receiptId);
        data.put("receiptNo", defaultString(receiptNo, "-"));
        data.put("purchaseOrderNo", defaultString(extractInboundTokenValue(firstRemark, "purchaseOrderNo"), "-"));
        data.put("supplier", defaultString(supplierName, "-"));
        data.put("supplierName", defaultString(supplierName, "-"));
        data.put("supplierCode", defaultString(supplierCode, "-"));
        data.put("status", "planned");
        data.put("summary", summary);
        data.put("items", itemRows);
        data.put("validBatchNos", validBatchNos);
        data.put("alreadyInBatchNos", alreadyInBatchNos);
        return ResponseResult.success("查询成功", data);
    }

    private List<TapeInboundRequest> collectInboundRequestsByScanCodes(List<String> scanCodes) {
        List<TapeInboundRequest> result = new ArrayList<>();
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        if (CollectionUtils.isEmpty(scanCodes)) {
            return result;
        }
        for (String code : scanCodes) {
            TapeInboundRequest req = findInboundByScanCodeGlobal(code);
            if (req != null && req.getId() != null && seen.add(req.getId())) {
                result.add(req);
            }
        }
        return result;
    }

    private TapeInboundRequest findInboundByScanCodeGlobal(String code) {
        String raw = normalizeScanKey(code);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String batch = extractBatchFromLabelCode(raw);
        String batchToken = "%customerBatchNo=" + batch + "%";
        String rawToken = "%customerBatchNo=" + raw + "%";

        List<TapeInboundRequest> matched = tapeInboundRequestMapper.selectList(
                new LambdaQueryWrapper<TapeInboundRequest>()
                        .and(w -> w.eq(TapeInboundRequest::getRequestNo, raw)
                                .or().eq(TapeInboundRequest::getBatchNo, raw)
                                .or().eq(TapeInboundRequest::getCustomerBatchNo, raw)
                                .or().eq(TapeInboundRequest::getBatchNo, batch)
                                .or().eq(TapeInboundRequest::getCustomerBatchNo, batch)
                                .or().like(TapeInboundRequest::getRemark, batchToken)
                                .or().like(TapeInboundRequest::getRemark, rawToken))
                        .orderByDesc(TapeInboundRequest::getId)
                        .last("LIMIT 50")
        );
        return pickBestInbound(matched);
    }

    private int resolvePrintedQty(TapeInboundRequest req, Map<String, Integer> cache) {
        if (req == null) {
            return 0;
        }
        String cacheKey = buildPrintedQtyCacheKey(req);
        if (StringUtils.hasText(cacheKey) && cache != null && cache.containsKey(cacheKey)) {
            return Math.max(0, cache.get(cacheKey));
        }

        int counted = countPrintedQtyByInbound(req);
        if (counted <= 0) {
            counted = estimatePrintedQtyByInbound(req);
        }
        if (StringUtils.hasText(cacheKey) && cache != null) {
            cache.put(cacheKey, counted);
        }
        return counted;
    }

    /**
     * 打印日志缺失/上报失败时的兜底估算：
     * 采购来料标签在执行打印准备时会回写来料批次号，若存在批次号则按申请数量估算已打印数。
     */
    private int estimatePrintedQtyByInbound(TapeInboundRequest req) {
        if (req == null) {
            return 0;
        }
        String customerBatchNo = defaultString(req.getCustomerBatchNo()).trim();
        String batchNo = defaultString(req.getBatchNo()).trim();
        if (!StringUtils.hasText(customerBatchNo) && !StringUtils.hasText(batchNo)) {
            return 0;
        }
        Integer rolls = req.getRolls();
        return rolls == null ? 0 : Math.max(0, rolls);
    }

    private String buildPrintedQtyCacheKey(TapeInboundRequest req) {
        if (req == null) {
            return "";
        }
        String batch = normalizeScanKey(req.getCustomerBatchNo());
        if (StringUtils.hasText(batch)) {
            return "B:" + batch;
        }
        batch = normalizeScanKey(req.getBatchNo());
        if (StringUtils.hasText(batch)) {
            return "B:" + batch;
        }
        String requestNo = normalizeScanKey(req.getRequestNo());
        return StringUtils.hasText(requestNo) ? "R:" + requestNo : "ID:" + req.getId();
    }

    private int countPrintedQtyByInbound(TapeInboundRequest req) {
        if (req == null) {
            return 0;
        }
        String batchNo = StringUtils.hasText(req.getCustomerBatchNo()) ? req.getCustomerBatchNo().trim() :
                (StringUtils.hasText(req.getBatchNo()) ? req.getBatchNo().trim() : "");
        String requestNo = StringUtils.hasText(req.getRequestNo()) ? req.getRequestNo().trim() : "";

        List<LabelPrintRecord> records = new ArrayList<>();
        if (StringUtils.hasText(batchNo)) {
            records = labelPrintRecordMapper.selectList(
                    new LambdaQueryWrapper<LabelPrintRecord>()
                            .eq(LabelPrintRecord::getBizType, PURCHASE_RECEIPT_LABEL_BIZ_TYPE)
                            .eq(LabelPrintRecord::getPrintStatus, "SUCCESS")
                            .eq(LabelPrintRecord::getBatchNo, batchNo)
                            .orderByDesc(LabelPrintRecord::getId)
                            .last("LIMIT 500")
            );
        }

        if (CollectionUtils.isEmpty(records) && StringUtils.hasText(requestNo)) {
            records = labelPrintRecordMapper.selectList(
                    new LambdaQueryWrapper<LabelPrintRecord>()
                            .eq(LabelPrintRecord::getBizType, PURCHASE_RECEIPT_LABEL_BIZ_TYPE)
                            .eq(LabelPrintRecord::getPrintStatus, "SUCCESS")
                            .like(LabelPrintRecord::getPrintDataJson, requestNo)
                            .orderByDesc(LabelPrintRecord::getId)
                            .last("LIMIT 500")
            );
        }

        if (CollectionUtils.isEmpty(records)) {
            return 0;
        }

        int total = 0;
        for (LabelPrintRecord row : records) {
            if (row == null) {
                continue;
            }
            Integer copies = row.getCopies();
            total += (copies == null || copies <= 0) ? 1 : copies;
        }
        return Math.max(0, total);
    }

    private boolean reconcileReceiptStatusFromInbound(PurchaseReceipt receipt, String updater) {
        if (receipt == null || receipt.getId() == null) {
            return false;
        }

        String receiptToken = INBOUND_SOURCE_TAG + "|receiptId=" + receipt.getId() + "|";
        Long pendingCount = tapeInboundRequestMapper.selectCount(
                new LambdaQueryWrapper<TapeInboundRequest>()
                        .like(TapeInboundRequest::getRemark, receiptToken)
                        .eq(TapeInboundRequest::getStatus, TapeInboundRequest.STATUS_PENDING)
        );
        Long approvedCount = tapeInboundRequestMapper.selectCount(
                new LambdaQueryWrapper<TapeInboundRequest>()
                        .like(TapeInboundRequest::getRemark, receiptToken)
                        .eq(TapeInboundRequest::getStatus, TapeInboundRequest.STATUS_APPROVED)
        );

        long pending = pendingCount == null ? 0L : pendingCount;
        long approved = approvedCount == null ? 0L : approvedCount;

        String targetStatus;
        if (approved > 0 && pending > 0) {
            targetStatus = "receiving";
        } else if (approved > 0) {
            targetStatus = "received";
        } else {
            targetStatus = "planned";
        }

        String currentStatus = defaultString(receipt.getStatus());
        boolean changed = !targetStatus.equalsIgnoreCase(currentStatus)
                || (("received".equalsIgnoreCase(targetStatus)) != (receipt.getReceivedDate() != null));

        if (!changed) {
            return false;
        }

        receipt.setStatus(targetStatus);
        receipt.setReceivedDate("received".equalsIgnoreCase(targetStatus) ? LocalDate.now() : null);
        receipt.setUpdatedAt(LocalDateTime.now());
        receipt.setUpdatedBy(StringUtils.hasText(updater) ? updater : "warehouse");
        receiptMapper.updateById(receipt);
        return true;
    }

    private Set<String> collectApprovedPurchaseOrderNosFromInbound() {
        Set<String> orderNos = new HashSet<>();
        List<TapeInboundRequest> approved = tapeInboundRequestMapper.selectList(
                new LambdaQueryWrapper<TapeInboundRequest>()
                        .eq(TapeInboundRequest::getStatus, TapeInboundRequest.STATUS_APPROVED)
                        .like(TapeInboundRequest::getRemark, INBOUND_SOURCE_TAG)
                        .orderByDesc(TapeInboundRequest::getId)
        );
        if (CollectionUtils.isEmpty(approved)) {
            return orderNos;
        }
        for (TapeInboundRequest req : approved) {
            String orderNo = extractInboundTokenValue(req == null ? null : req.getRemark(), "purchaseOrderNo");
            if (StringUtils.hasText(orderNo)) {
                orderNos.add(orderNo.trim());
            }
        }
        return orderNos;
    }

    private String extractInboundTokenValue(String remark, String key) {
        if (!StringUtils.hasText(remark) || !StringUtils.hasText(key)) {
            return "";
        }
        String target = key.trim() + "=";
        String[] parts = remark.split("\\|");
        for (String part : parts) {
            String p = part == null ? "" : part.trim();
            if (!StringUtils.hasText(p)) {
                continue;
            }
            if (p.toLowerCase(Locale.ROOT).startsWith(target.toLowerCase(Locale.ROOT))) {
                return p.substring(target.length()).trim();
            }
        }
        return "";
    }

    private PurchaseReceipt findReceiptByNoOrId(String receiptNo, Long receiptId) {
        if (receiptId != null && receiptId > 0) {
            PurchaseReceipt byId = receiptMapper.selectById(receiptId);
            if (byId != null && !Integer.valueOf(1).equals(byId.getIsDeleted())) {
                return byId;
            }
        }
        if (!StringUtils.hasText(receiptNo)) {
            return null;
        }
        return receiptMapper.selectOne(new LambdaQueryWrapper<PurchaseReceipt>()
                .eq(PurchaseReceipt::getIsDeleted, 0)
                .eq(PurchaseReceipt::getReceiptNo, receiptNo.trim())
                .orderByDesc(PurchaseReceipt::getId)
                .last("LIMIT 1"));
    }

    private List<TapeInboundRequest> listInboundRequestsByReceiptId(Long receiptId) {
        if (receiptId == null || receiptId <= 0) {
            return new ArrayList<>();
        }
        String token = INBOUND_SOURCE_TAG + "|receiptId=" + receiptId + "|";
        List<TapeInboundRequest> rows = tapeInboundRequestMapper.selectList(
                new LambdaQueryWrapper<TapeInboundRequest>()
                        .like(TapeInboundRequest::getRemark, token)
                        .orderByDesc(TapeInboundRequest::getId)
        );
        return rows == null ? new ArrayList<>() : rows;
    }

    private Map<Long, TapeInboundRequest> buildLatestInboundByItemId(List<TapeInboundRequest> requests) {
        Map<Long, TapeInboundRequest> map = new HashMap<>();
        if (CollectionUtils.isEmpty(requests)) {
            return map;
        }
        for (TapeInboundRequest req : requests) {
            if (req == null || req.getId() == null) {
                continue;
            }
            Long itemId = parseLongSafe(extractInboundTokenValue(req.getRemark(), "itemId"));
            if (itemId == null || itemId <= 0 || map.containsKey(itemId)) {
                continue;
            }
            map.put(itemId, req);
        }
        return map;
    }

    private int resolvePlanQty(PurchaseReceiptItem item, TapeInboundRequest req) {
        if (req != null && req.getRolls() != null && req.getRolls() > 0) {
            return req.getRolls();
        }
        if (item != null && item.getExpectedQty() != null && item.getExpectedQty() > 0) {
            return item.getExpectedQty();
        }
        if (item != null && item.getPurchaseQty() != null && item.getPurchaseQty().compareTo(BigDecimal.ZERO) > 0) {
            return item.getPurchaseQty().setScale(0, BigDecimal.ROUND_HALF_UP).intValue();
        }
        return 0;
    }

    private Map<String, List<TapeInboundRequest>> buildInboundByBatch(List<TapeInboundRequest> requests) {
        Map<String, List<TapeInboundRequest>> map = new HashMap<>();
        if (CollectionUtils.isEmpty(requests)) {
            return map;
        }
        for (TapeInboundRequest req : requests) {
            if (req == null || req.getId() == null) {
                continue;
            }
            putInboundBatchKey(map, req == null ? null : req.getRequestNo(), req);
            putInboundBatchKey(map, req == null ? null : req.getBatchNo(), req);
            putInboundBatchKey(map, req == null ? null : req.getCustomerBatchNo(), req);
        }
        return map;
    }

    private PurchaseReceipt findReceiptByInboundScanCode(String scanCode) {
        String raw = normalizeScanKey(scanCode);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String batchCode = extractBatchFromLabelCode(raw);

        List<TapeInboundRequest> matched = tapeInboundRequestMapper.selectList(
                new LambdaQueryWrapper<TapeInboundRequest>()
                        .and(w -> w.eq(TapeInboundRequest::getRequestNo, raw)
                                .or().eq(TapeInboundRequest::getBatchNo, raw)
                                .or().eq(TapeInboundRequest::getCustomerBatchNo, raw)
                                .or().eq(TapeInboundRequest::getBatchNo, batchCode)
                                .or().eq(TapeInboundRequest::getCustomerBatchNo, batchCode))
                        .orderByDesc(TapeInboundRequest::getId)
        );
        TapeInboundRequest req = pickBestInbound(matched);
        if (req == null) {
            return null;
        }
        Long receiptId = parseLongSafe(extractInboundTokenValue(req.getRemark(), "receiptId"));
        if (receiptId == null || receiptId <= 0) {
            return null;
        }
        PurchaseReceipt receipt = receiptMapper.selectById(receiptId);
        if (receipt == null || Integer.valueOf(1).equals(receipt.getIsDeleted())) {
            return null;
        }
        return receipt;
    }

    private void putInboundBatchKey(Map<String, List<TapeInboundRequest>> map, String key, TapeInboundRequest req) {
        String k = normalizeScanKey(key);
        if (!StringUtils.hasText(k) || req == null) {
            return;
        }
        List<TapeInboundRequest> list = map.get(k);
        if (list == null) {
            list = new ArrayList<>();
            map.put(k, list);
        }
        list.add(req);
    }

    private TapeInboundRequest findInboundByScanCode(String code, Map<String, List<TapeInboundRequest>> requestByBatch) {
        String raw = normalizeScanKey(code);
        if (!StringUtils.hasText(raw) || requestByBatch == null || requestByBatch.isEmpty()) {
            return null;
        }
        TapeInboundRequest direct = pickBestInbound(requestByBatch.get(raw));
        if (direct != null) {
            return direct;
        }
        String batch = extractBatchFromLabelCode(raw);
        return pickBestInbound(requestByBatch.get(batch));
    }

    private PurchaseReceipt findReceiptByPrintedLabel(String scanCode) {
        TapeInboundRequest req = findInboundByPrintedLabelCode(scanCode, null);
        if (req == null) {
            return null;
        }
        Long receiptId = parseLongSafe(extractInboundTokenValue(req.getRemark(), "receiptId"));
        if (receiptId == null || receiptId <= 0) {
            return null;
        }
        PurchaseReceipt receipt = receiptMapper.selectById(receiptId);
        if (receipt == null || Integer.valueOf(1).equals(receipt.getIsDeleted())) {
            return null;
        }
        return receipt;
    }

    private TapeInboundRequest findInboundByPrintedLabelCode(String code, List<TapeInboundRequest> scopeRequests) {
        String raw = normalizeScanKey(code);
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String batch = extractBatchFromLabelCode(raw);
        List<LabelPrintRecord> records = listPrintedPurchaseLabels(raw, batch);
        if (CollectionUtils.isEmpty(records)) {
            return null;
        }

        for (LabelPrintRecord record : records) {
            if (record == null) {
                continue;
            }
            String dataJson = defaultString(record.getPrintDataJson());
            String requestNo = extractJsonTextValue(dataJson, "requestNo");
            String incomingBatchNo = extractJsonTextValue(dataJson, "incomingBatchNo");
            String customerBatchNo = extractJsonTextValue(dataJson, "customerBatchNo");
            String batchNo = extractJsonTextValue(dataJson, "batchNo");

            TapeInboundRequest req = findInboundByLabelMeta(scopeRequests, requestNo, incomingBatchNo, customerBatchNo, batchNo, raw, batch);
            if (req != null) {
                return req;
            }
        }
        return null;
    }

    private TapeInboundRequest findInboundByLabelMeta(List<TapeInboundRequest> scopeRequests,
                                                      String requestNo,
                                                      String incomingBatchNo,
                                                      String customerBatchNo,
                                                      String batchNo,
                                                      String raw,
                                                      String normalizedBatch) {
        if (!CollectionUtils.isEmpty(scopeRequests)) {
            List<TapeInboundRequest> candidates = new ArrayList<>();
            for (TapeInboundRequest req : scopeRequests) {
                if (req == null) {
                    continue;
                }
                String reqNo = normalizeScanKey(req.getRequestNo());
                String reqBatchNo = normalizeScanKey(req.getBatchNo());
                String reqCustomerBatchNo = normalizeScanKey(req.getCustomerBatchNo());

                if (equalsAnyIgnoreCase(reqNo, requestNo, raw)
                        || equalsAnyIgnoreCase(reqBatchNo, incomingBatchNo, batchNo, normalizedBatch, raw)
                        || equalsAnyIgnoreCase(reqCustomerBatchNo, customerBatchNo, incomingBatchNo, batchNo, normalizedBatch, raw)) {
                    candidates.add(req);
                }
            }
            return pickBestInbound(candidates);
        }

        String reqNoNorm = normalizeScanKey(requestNo);
        String incomingBatchNorm = normalizeScanKey(incomingBatchNo);
        String customerBatchNorm = normalizeScanKey(customerBatchNo);
        String batchNoNorm = normalizeScanKey(batchNo);

        List<TapeInboundRequest> matched = tapeInboundRequestMapper.selectList(
                new LambdaQueryWrapper<TapeInboundRequest>()
                        .and(w -> w.eq(StringUtils.hasText(reqNoNorm), TapeInboundRequest::getRequestNo, reqNoNorm)
                                .or().eq(StringUtils.hasText(incomingBatchNorm), TapeInboundRequest::getBatchNo, incomingBatchNorm)
                                .or().eq(StringUtils.hasText(customerBatchNorm), TapeInboundRequest::getCustomerBatchNo, customerBatchNorm)
                                .or().eq(StringUtils.hasText(batchNoNorm), TapeInboundRequest::getBatchNo, batchNoNorm)
                                .or().eq(StringUtils.hasText(batchNoNorm), TapeInboundRequest::getCustomerBatchNo, batchNoNorm)
                                .or().eq(StringUtils.hasText(normalizedBatch), TapeInboundRequest::getBatchNo, normalizedBatch)
                                .or().eq(StringUtils.hasText(normalizedBatch), TapeInboundRequest::getCustomerBatchNo, normalizedBatch)
                                .or().eq(StringUtils.hasText(raw), TapeInboundRequest::getRequestNo, raw)
                                .or().eq(StringUtils.hasText(raw), TapeInboundRequest::getBatchNo, raw)
                                .or().eq(StringUtils.hasText(raw), TapeInboundRequest::getCustomerBatchNo, raw))
                        .orderByDesc(TapeInboundRequest::getId)
                        .last("LIMIT 50")
        );
        return pickBestInbound(matched);
    }

    private List<LabelPrintRecord> listPrintedPurchaseLabels(String rawCode, String batchCode) {
        try {
            LinkedHashSet<Long> seen = new LinkedHashSet<>();
            List<LabelPrintRecord> merged = new ArrayList<>();

            List<LabelPrintRecord> byRaw = queryPurchasePrintedLabels(rawCode, 80);
            mergePrintRecords(merged, seen, byRaw);

            if (StringUtils.hasText(batchCode) && !batchCode.equalsIgnoreCase(rawCode)) {
                List<LabelPrintRecord> byBatch = queryPurchasePrintedLabels(batchCode, 80);
                mergePrintRecords(merged, seen, byBatch);
            }
            return merged;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private List<LabelPrintRecord> queryPurchasePrintedLabels(String token, int limit) {
        if (!StringUtils.hasText(token)) {
            return Collections.emptyList();
        }
        try {
            return labelPrintRecordMapper.selectList(
                    new LambdaQueryWrapper<LabelPrintRecord>()
                            .eq(LabelPrintRecord::getBizType, PURCHASE_RECEIPT_LABEL_BIZ_TYPE)
                            .eq(LabelPrintRecord::getPrintStatus, "SUCCESS")
                            .like(LabelPrintRecord::getPrintDataJson, token)
                            .orderByDesc(LabelPrintRecord::getId)
                            .last("LIMIT " + Math.max(10, Math.min(200, limit)))
            );
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private void mergePrintRecords(List<LabelPrintRecord> target, Set<Long> seen, List<LabelPrintRecord> source) {
        if (target == null || seen == null || CollectionUtils.isEmpty(source)) {
            return;
        }
        for (LabelPrintRecord row : source) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (seen.add(row.getId())) {
                target.add(row);
            }
        }
    }

    private String extractJsonTextValue(String json, String key) {
        if (!StringUtils.hasText(json) || !StringUtils.hasText(key)) {
            return "";
        }
        String escapedKey = Pattern.quote(key.trim());
        Pattern p = Pattern.compile("\\\"" + escapedKey + "\\\"\\s*:\\s*(\\\"([^\\\"]*)\\\"|(\\d+))", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(json);
        if (!m.find()) {
            return "";
        }
        String quoted = m.group(2);
        if (StringUtils.hasText(quoted)) {
            return quoted.trim();
        }
        String numeric = m.group(3);
        return StringUtils.hasText(numeric) ? numeric.trim() : "";
    }

    private boolean equalsAnyIgnoreCase(String base, String... values) {
        String left = normalizeScanKey(base);
        if (!StringUtils.hasText(left) || values == null || values.length == 0) {
            return false;
        }
        for (String v : values) {
            String right = normalizeScanKey(v);
            if (StringUtils.hasText(right) && left.equals(right)) {
                return true;
            }
        }
        return false;
    }

    private TapeInboundRequest pickBestInbound(List<TapeInboundRequest> list) {
        if (CollectionUtils.isEmpty(list)) {
            return null;
        }
        TapeInboundRequest approved = null;
        for (TapeInboundRequest req : list) {
            if (req == null || req.getId() == null) {
                continue;
            }
            Integer status = req.getStatus();
            if (status != null && status.intValue() == TapeInboundRequest.STATUS_PENDING) {
                return req;
            }
            if (approved == null && status != null && status.intValue() == TapeInboundRequest.STATUS_APPROVED) {
                approved = req;
            }
        }
        return approved == null ? list.get(0) : approved;
    }

    private String normalizeScanKey(String text) {
        return StringUtils.hasText(text) ? text.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String extractBatchFromLabelCode(String scanCode) {
        if (!StringUtils.hasText(scanCode)) {
            return "";
        }
        String code = scanCode.trim();
        int idx = code.lastIndexOf('-');
        if (idx > 0 && idx < code.length() - 1) {
            String suffix = code.substring(idx + 1);
            if (suffix.matches("\\d{1,4}")) {
                return code.substring(0, idx);
            }
        }
        return code;
    }

    private Long parseLongSafe(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> buildFailure(String code, String reason) {
        Map<String, Object> row = new HashMap<>();
        row.put("code", defaultString(code, "-"));
        row.put("reason", defaultString(reason, "失败"));
        return row;
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
            cancelObsoleteInboundRequests(receiptId, new HashSet<>());
            return;
        }

        Set<String> activeTokens = new LinkedHashSet<>();
        Set<String> dedupKeys = new HashSet<>();
        for (PurchaseReceiptItem item : items) {
            if (item == null || item.getId() == null) {
                continue;
            }

            String dedupKey = buildInboundDedupKey(item);
            if (!dedupKeys.add(dedupKey)) {
                // 同一收货单内完全重复的明细，仅保留第一条，避免入库申请翻倍
                continue;
            }

            activeTokens.add(buildInboundSourceToken(receipt.getId(), item.getId()));
            upsertInboundRequestByReceiptItem(receipt, item);
        }

        // 取消本收货单下未出现在本次有效明细中的待审批申请（包括被去重掉的重复项）
        cancelObsoleteInboundRequests(receiptId, activeTokens);
    }

    private String buildInboundDedupKey(PurchaseReceiptItem item) {
        String materialCode = defaultString(item == null ? null : item.getMaterialCode(), "-");
        String spec = normalizeSingleSpecText(item == null ? null : item.getSpecification());
        Integer receivedQty = item == null ? null : item.getReceivedQty();
        String unit = normalizeQtyUnit(item == null ? null : item.getUnit());
        String stockUom = normalizeQtyUnit(item == null ? null : item.getStockUomCode());
        String purchaseUom = normalizeQtyUnit(item == null ? null : item.getPurchaseUomCode());
        String priceUom = normalizeQtyUnit(item == null ? null : item.getPriceUomCode());
        return materialCode + "|" + spec + "|" + String.valueOf(receivedQty)
                + "|" + unit + "|" + stockUom + "|" + purchaseUom + "|" + priceUom;
    }

    private void cancelObsoleteInboundRequests(Long receiptId, Set<String> activeTokens) {
        if (receiptId == null) {
            return;
        }

        String receiptToken = INBOUND_SOURCE_TAG + "|receiptId=" + receiptId + "|";
        LambdaQueryWrapper<TapeInboundRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(TapeInboundRequest::getRemark, receiptToken)
                .orderByDesc(TapeInboundRequest::getId);
        List<TapeInboundRequest> requests = tapeInboundRequestMapper.selectList(wrapper);
        if (CollectionUtils.isEmpty(requests)) {
            return;
        }

        Set<String> safeTokens = activeTokens == null ? new HashSet<>() : activeTokens;
        for (TapeInboundRequest req : requests) {
            if (req == null || req.getId() == null) {
                continue;
            }
            String token = extractInboundSourceToken(req.getRemark());
            if (!StringUtils.hasText(token) || safeTokens.contains(token)) {
                continue;
            }
            if (req.getStatus() != null && req.getStatus().intValue() == TapeInboundRequest.STATUS_PENDING) {
                req.setStatus(TapeInboundRequest.STATUS_CANCELLED);
                req.setAuditRemark(defaultString(req.getAuditRemark()) + "[AUTO_CANCEL_DUPLICATE_RECEIPT_ITEM]");
                req.setUpdateTime(LocalDateTime.now());
                tapeInboundRequestMapper.updateById(req);
            }
        }
    }

    private String extractInboundSourceToken(String remark) {
        String text = defaultString(remark);
        if (!StringUtils.hasText(text)) {
            return "";
        }
        Matcher matcher = INBOUND_SOURCE_TOKEN_PATTERN.matcher(text);
        if (!matcher.find()) {
            return "";
        }
        String token = matcher.group(1);
        return token == null ? "" : token.trim();
    }

    private void upsertInboundRequestByReceiptItem(PurchaseReceipt receipt, PurchaseReceiptItem item) {
        String token = buildInboundSourceToken(receipt.getId(), item.getId());
        LambdaQueryWrapper<TapeInboundRequest> existingQuery = new LambdaQueryWrapper<>();
        existingQuery.like(TapeInboundRequest::getRemark, token)
                .orderByDesc(TapeInboundRequest::getId)
                .last("LIMIT 1");
        TapeInboundRequest inbound = tapeInboundRequestMapper.selectOne(existingQuery);

        PurchaseOrderItem poItem = findBestPurchaseOrderItem(item);

        Integer targetRolls = resolveInboundRolls(item, poItem);
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
            inbound.setCustomerBatchNo(buildBatchNo(receipt, item));
            inbound.setRolls(targetRolls);
            inbound.setQtyUnit(resolveInboundQtyUnit(item, poItem));
            inbound.setLocation(defaultString(receipt.getReceiveAddress(), "待上架"));
            inbound.setApplicant(resolveInboundApplicantAccount(receipt));
            inbound.setApplyDept("采购收货");
            applySpecificationToInbound(inbound, item, poItem);
            inbound.setRemark(buildInboundRemark(receipt, item, poItem));
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
        request.setCustomerBatchNo(buildBatchNo(receipt, item));
        request.setRolls(targetRolls);
        request.setQtyUnit(resolveInboundQtyUnit(item, poItem));
        request.setLocation(defaultString(receipt.getReceiveAddress(), "待上架"));
        request.setApplicant(resolveInboundApplicantAccount(receipt));
        request.setApplyDept("采购收货");
        request.setApplyTime(LocalDateTime.now());
        request.setStatus(TapeInboundRequest.STATUS_PENDING);
        applySpecificationToInbound(request, item, poItem);
        request.setRemark(buildInboundRemark(receipt, item, poItem));
        tapeInboundRequestMapper.insert(request);
    }

    @SuppressWarnings("unused")
    private boolean shouldSyncToTapeInbound(String materialCode) {
        String code = StringUtils.hasText(materialCode) ? materialCode.trim() : "";
        if (!StringUtils.hasText(code)) {
            return true;
        }
        try {
            TapeRawMaterial rawMaterial = tapeFormulaMapper.selectRawMaterialByCode(code);
            if (rawMaterial == null) {
                // 不在原材料主数据中，按胶带物料处理
                return true;
            }
            String category = normalizeLower(rawMaterial.getMaterialCategory());
            String categoryRaw = normalizeLower(rawMaterial.getMaterialCategoryRaw());
            String type = normalizeLower(rawMaterial.getMaterialType());
            String unit = normalizeLower(rawMaterial.getUnit());

            if (containsAny(category, "chemical", "化工", "film", "薄膜", "原膜")
                    || containsAny(categoryRaw, "chemical", "化工", "film", "薄膜", "原膜")
                    || containsAny(type, "solvent", "additive", "resin", "curing", "溶剂", "助剂", "树脂", "固化剂", "胶水")
                    || containsAny(unit, "kg", "公斤", "千克", "m²")) {
                return false;
            }
            return true;
        } catch (Exception ignored) {
            // 主数据查询异常时保持兼容：仍按原逻辑同步，避免中断收货流程
            return true;
        }
    }

    private String normalizeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private boolean containsAny(String source, String... keywords) {
        if (!StringUtils.hasText(source) || keywords == null || keywords.length == 0) {
            return false;
        }
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && source.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private void applySpecificationToInbound(TapeInboundRequest inbound, PurchaseReceiptItem item, PurchaseOrderItem poItem) {
        if (inbound == null || item == null) {
            return;
        }

        Integer resolvedThickness = null;
        Integer resolvedWidth = null;
        Integer resolvedLength = null;

        if (poItem != null) {
            Integer t = toPositiveInt(poItem.getThickness());
            Integer w = toPositiveInt(poItem.getWidth());
            Integer l = toPositiveInt(poItem.getLength());
            resolvedThickness = t;
            resolvedWidth = w;
            resolvedLength = l;
        }

        // 采购单规格优先：filmSpecRaw -> rawSpec -> 收货行 specification。
        // 同时归一为单一规格文本，避免出现“两个规格拼接在一条入库申请”导致误读。
        String spec = normalizeSingleSpecText(poItem == null ? null : poItem.getFilmSpecRaw());
        if (!StringUtils.hasText(spec)) {
            spec = normalizeSingleSpecText(poItem == null ? null : poItem.getRawSpec());
        }
        if (!StringUtils.hasText(spec)) {
            spec = normalizeSingleSpecText(item.getSpecification());
        }

        if (!StringUtils.hasText(spec)) {
            return;
        }

        // 采购来料规格以采购单据原文为准，禁止按系统固定模板重写单位。
        // 例如："6mm*77mm*1080m" 必须原样展示，不应被改写为 "6μm*77mm*1080m"。
        inbound.setSpecDesc(spec);

        List<Integer> dims = parseSpecificationNumbers(spec);
        if (dims.size() >= 3) {
            // 规格文本可完整解析时，强制覆盖厚宽长，避免与spec_desc不一致
            resolvedThickness = dims.get(0);
            resolvedWidth = dims.get(1);
            resolvedLength = dims.get(2);
        } else if (dims.size() == 2) {
            // 两段规格兜底：支持“宽(mm)*长(m)”和“厚(μm)*宽(mm)”
            String lower = spec.toLowerCase();
            boolean hasUm = lower.contains("μm") || lower.contains("um");
            boolean hasMm = lower.contains("mm");
            boolean hasMeter = lower.matches(".*\\d+(?:\\.\\d+)?\\s*m(?!m).*");

            if (hasMm && hasMeter) {
                resolvedWidth = dims.get(0);
                resolvedLength = dims.get(1);
            } else if (hasUm && hasMm) {
                resolvedThickness = dims.get(0);
                resolvedWidth = dims.get(1);
            }
        }

        if (resolvedThickness != null && resolvedThickness > 0) {
            inbound.setThickness(resolvedThickness);
        }
        if (resolvedWidth != null && resolvedWidth > 0) {
            inbound.setWidth(resolvedWidth);
        }
        if (resolvedLength != null && resolvedLength > 0) {
            inbound.setLength(resolvedLength);
        }
    }

    private PurchaseOrderItem findBestPurchaseOrderItem(PurchaseReceiptItem item) {
        if (item == null || !StringUtils.hasText(item.getMaterialCode())) {
            return null;
        }
        try {
            String poNo = item.getPurchaseOrderNo();
            IPage<PurchaseOrderItem> page = purchaseOrderItemMapper.selectItems(new Page<>(1, 10), poNo, item.getMaterialCode());
            List<PurchaseOrderItem> records = page == null ? null : page.getRecords();
            if (records == null || records.isEmpty()) {
                return null;
            }
            return pickBestPurchaseOrderItem(records, item);
        } catch (Exception ignored) {
            return null;
        }
    }

    private PurchaseOrderItem pickBestPurchaseOrderItem(List<PurchaseOrderItem> records, PurchaseReceiptItem item) {
        if (records == null || records.isEmpty()) {
            return null;
        }

        String receiptSpec = normalizeSingleSpecText(item == null ? null : item.getSpecification());
        String receiptSpecKey = canonicalSpec(receiptSpec);
        List<Integer> receiptDims = parseSpecificationNumbers(receiptSpec);

        PurchaseOrderItem best = records.get(0);
        int bestScore = Integer.MIN_VALUE;

        for (PurchaseOrderItem rec : records) {
            int score = 0;

            String poSpec = normalizeSingleSpecText(rec == null ? null : rec.getFilmSpecRaw());
            if (!StringUtils.hasText(poSpec)) {
                poSpec = normalizeSingleSpecText(rec == null ? null : rec.getRawSpec());
            }
            String poSpecKey = canonicalSpec(poSpec);

            if (StringUtils.hasText(receiptSpecKey) && StringUtils.hasText(poSpecKey)) {
                if (receiptSpecKey.equals(poSpecKey)) {
                    score += 100;
                } else if (receiptSpecKey.contains(poSpecKey) || poSpecKey.contains(receiptSpecKey)) {
                    score += 60;
                }
            }

            Integer t = toPositiveInt(rec == null ? null : rec.getThickness());
            Integer w = toPositiveInt(rec == null ? null : rec.getWidth());
            Integer l = toPositiveInt(rec == null ? null : rec.getLength());

            if (t != null && w != null && l != null) {
                score += 10;
                if (receiptDims.size() >= 3
                        && t.equals(receiptDims.get(0))
                        && w.equals(receiptDims.get(1))
                        && l.equals(receiptDims.get(2))) {
                    score += 80;
                }
            }

            if (StringUtils.hasText(poSpecKey)) {
                score += 5;
            }

            if (score > bestScore) {
                bestScore = score;
                best = rec;
            }
        }

        return best;
    }

    private String canonicalSpec(String spec) {
        if (!StringUtils.hasText(spec)) {
            return "";
        }
        return spec.replace('\u00A0', ' ')
                .replace('\u3000', ' ')
                .replaceAll("\\s+", "")
                .toUpperCase();
    }

    private String normalizeSpecText(String spec) {
        if (!StringUtils.hasText(spec)) {
            return "";
        }
        String trimmed = spec.trim();
        if ("-".equals(trimmed) || "--".equals(trimmed) || "—".equals(trimmed) || "/".equals(trimmed)) {
            return "";
        }
        return trimmed;
    }

    private Integer toPositiveInt(BigDecimal val) {
        if (val == null || val.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        try {
            int parsed = val.setScale(0, BigDecimal.ROUND_HALF_UP).intValue();
            return parsed > 0 ? parsed : null;
        } catch (Exception ignored) {
            return null;
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

    private Integer resolveInboundRolls(PurchaseReceiptItem item, PurchaseOrderItem poItem) {
        if (item == null) {
            return 0;
        }

        // 仅当原始单位就是“卷”时，才直接采用收货数量。
        String[] unitCandidates = new String[] {
                item.getStockUomCode(),
                item.getUnit(),
                item.getPurchaseUomCode(),
                item.getPriceUomCode()
        };
        boolean countUnit = false;
        for (String unit : unitCandidates) {
            if (isCountUnit(unit)) {
                countUnit = true;
                break;
            }
        }

        if (countUnit) {
            if (item.getReceivedQty() != null && item.getReceivedQty() > 0) {
                return item.getReceivedQty();
            }
            if (item.getExpectedQty() != null && item.getExpectedQty() > 0) {
                return item.getExpectedQty();
            }
            if (item.getPurchaseQty() != null && item.getPurchaseQty().compareTo(BigDecimal.ZERO) > 0) {
                return item.getPurchaseQty().setScale(0, BigDecimal.ROUND_HALF_UP).intValue();
            }
        }

        // 非卷单位（kg/㎡等）严禁直接当卷数；优先采用采购明细 rolls。
        if (poItem != null && poItem.getRolls() != null && poItem.getRolls() > 0) {
            return poItem.getRolls();
        }

        // 规格包含多个卷段（逗号/分号/换行）时，按段数估算卷数。
        int specRollCount = countSpecSegments(item.getSpecification());
        if (specRollCount <= 0) {
            specRollCount = countSpecSegments(poItem == null ? null : poItem.getFilmSpecRaw());
        }
        if (specRollCount <= 0) {
            specRollCount = countSpecSegments(poItem == null ? null : poItem.getRawSpec());
        }
        if (specRollCount > 0) {
            return specRollCount;
        }

        // 无法可靠推断时返回1卷，避免出现“12156卷”这类错误兜底。
        return 1;
    }

    private boolean isCountUnit(String raw) {
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        String normalized = normalizeQtyUnit(raw);
        return "ROLL".equals(normalized) || "PCS".equals(normalized);
    }

    private String normalizeSingleSpecText(String spec) {
        String normalized = normalizeSpecText(spec);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        String[] parts = normalized.split("[，,;；\\n\\r]+");
        for (String part : parts) {
            String p = normalizeSpecText(part);
            if (StringUtils.hasText(p)) {
                return p;
            }
        }
        return normalized;
    }

    private int countSpecSegments(String spec) {
        String normalized = normalizeSpecText(spec);
        if (!StringUtils.hasText(normalized)) {
            return 0;
        }
        String[] parts = normalized.split("[，,;；\\n\\r]+");
        int count = 0;
        for (String part : parts) {
            String p = normalizeSpecText(part);
            if (StringUtils.hasText(p)) {
                count++;
            }
        }
        return count;
    }

    private String buildInboundSourceToken(Long receiptId, Long itemId) {
        return INBOUND_SOURCE_TAG + "|receiptId=" + receiptId + "|itemId=" + itemId;
    }

    private String buildInboundRemark(PurchaseReceipt receipt, PurchaseReceiptItem item, PurchaseOrderItem poItem) {
        String token = buildInboundSourceToken(receipt.getId(), item.getId());
        String qtyUnit = resolveInboundQtyUnit(item, poItem);
        String inboundCategory = resolveInboundCategory(item, poItem);
        return token
                + "|receiptNo=" + defaultString(receipt.getReceiptNo(), "-")
                + "|purchaseOrderNo=" + defaultString(receipt.getPurchaseOrderNo(), "-")
                + "|supplier=" + defaultString(receipt.getSupplier(), "-")
                + "|qtyUnit=" + defaultString(qtyUnit, "数量")
                + "|inboundCategory=" + defaultString(inboundCategory, "GENERAL");
    }

    private String resolveInboundCategory(PurchaseReceiptItem item, PurchaseOrderItem poItem) {
        String code = defaultString(item == null ? null : item.getMaterialCode()).toUpperCase();
        String name = defaultString(item == null ? null : item.getMaterialName());
        String spec = defaultString(item == null ? null : item.getSpecification());

        // 统一口径：PEG/PE管类按包材仓入库，避免误入化工仓
        if (isPegTubeMaterial(item, poItem)) {
            return "PACKAGING";
        }

        if (isPcsMaterial(item, poItem)) {
            return "PACKAGING";
        }

        try {
            TapeRawMaterial rawMaterial = tapeFormulaMapper.selectRawMaterialByCode(code);
            if (rawMaterial != null) {
                String category = normalizeLower(rawMaterial.getMaterialCategory());
                String categoryRaw = normalizeLower(rawMaterial.getMaterialCategoryRaw());
                String type = normalizeLower(rawMaterial.getMaterialType());
                String unit = normalizeLower(rawMaterial.getUnit());

                if (containsAny(category, "film", "薄膜", "原膜")
                        || containsAny(categoryRaw, "film", "薄膜", "原膜")) {
                    return "FILM";
                }
                if (containsAny(category, "foam", "泡棉")
                        || containsAny(categoryRaw, "foam", "泡棉")
                        || containsAny(type, "foam", "泡棉")) {
                    return "FOAM";
                }
                if (containsAny(category, "chemical", "化工")
                        || containsAny(categoryRaw, "chemical", "化工")
                        || containsAny(type, "solvent", "additive", "resin", "curing", "胶", "胶水", "溶剂", "助剂", "树脂", "固化")
                        || containsAny(unit, "kg", "公斤", "千克", "桶", "包", "drum", "barrel")) {
                    return "CHEMICAL";
                }
            }
        } catch (Exception ignored) {
        }

        String merged = (code + " " + name + " " + spec).toLowerCase();
        if (containsAny(merged, "泡棉", "foam") || code.startsWith("PM")) {
            return "FOAM";
        }
        if (containsAny(merged, "release", "离型", "release film", "release paper")) {
            return "RELEASE_FILM_PAPER";
        }
        if (containsAny(merged, "薄膜", "原膜", "film", "pet", "bopp", "opp", "cpp", "ops", "tpu") || code.startsWith("M")) {
            return "FILM";
        }
        if (containsAny(merged, "化工", "胶", "胶水", "树脂", "溶剂", "助剂", "固化", "resin", "solvent", "additive", "curing")
                || code.startsWith("LMR") || code.startsWith("HHFT") || code.startsWith("RH")) {
            return "CHEMICAL";
        }

        String[] units = new String[] {
                item == null ? null : item.getUnit(),
                item == null ? null : item.getPurchaseUomCode(),
                item == null ? null : item.getStockUomCode(),
                item == null ? null : item.getPriceUomCode(),
                poItem == null ? null : poItem.getPurchaseUomCode(),
                poItem == null ? null : poItem.getStockUomCode(),
                poItem == null ? null : poItem.getPriceUomCode()
        };
        for (String u : units) {
            String normalized = normalizeQtyUnit(u);
            if ("KG".equals(normalized)) {
                return "CHEMICAL";
            }
            if (containsAny(normalized == null ? "" : normalized.toLowerCase(), "drum", "barrel", "bucket", "桶", "包")) {
                return "CHEMICAL";
            }
        }

        return "GENERAL";
    }

    private String resolveInboundQtyUnit(PurchaseReceiptItem item, PurchaseOrderItem poItem) {
        // 业务显式规则：管/纸管/纸箱一律按件数单位。
        if (isPcsMaterial(item, poItem)) {
            return "个";
        }

        String inboundCategory = resolveInboundCategory(item, poItem);
        boolean chemicalCategory = "CHEMICAL".equalsIgnoreCase(inboundCategory);

        // 化工类优先沿用采购单位（桶/包/kg），禁止被 rolls 信号误判为卷。
        if (chemicalCategory) {
            String[] chemicalCandidates = new String[] {
                    item == null ? null : item.getUnit(),
                    item == null ? null : item.getPurchaseUomCode(),
                    item == null ? null : item.getStockUomCode(),
                    item == null ? null : item.getPriceUomCode(),
                    poItem == null ? null : poItem.getPurchaseUomCode(),
                    poItem == null ? null : poItem.getStockUomCode(),
                    poItem == null ? null : poItem.getPriceUomCode()
            };
            for (String c : chemicalCandidates) {
                String unit = normalizeInboundDisplayUnit(c);
                if (!StringUtils.hasText(unit)) {
                    continue;
                }
                if ("桶".equals(unit) || "包".equals(unit) || "kg".equalsIgnoreCase(unit)) {
                    return unit;
                }
            }
            if (poItem != null && poItem.getRolls() != null && poItem.getRolls() > 0) {
                return "桶";
            }
        }

        String[] rollSignals = new String[] {
                item == null ? null : item.getUnit(),
                item == null ? null : item.getPurchaseUomCode(),
                item == null ? null : item.getStockUomCode(),
                item == null ? null : item.getPriceUomCode(),
                poItem == null ? null : poItem.getPurchaseUomCode(),
                poItem == null ? null : poItem.getStockUomCode(),
                poItem == null ? null : poItem.getPriceUomCode()
        };

        for (String signal : rollSignals) {
            String normalized = normalizeQtyUnit(signal);
            if ("ROLL".equals(normalized)) {
                return "卷";
            }
        }

        if (!chemicalCategory && poItem != null && poItem.getRolls() != null && poItem.getRolls() > 0) {
            return "卷";
        }

        String materialSpec = item == null ? null : item.getSpecification();
        // 仅在“多段规格”（逗号/分号/换行）时才按卷段判定 ROLL，避免单一规格误判。
        if (!chemicalCategory && countSpecSegments(materialSpec) > 1) {
            return "卷";
        }

        String[] candidates = new String[] {
                item == null ? null : item.getPurchaseUomCode(),
                item == null ? null : item.getUnit(),
                item == null ? null : item.getStockUomCode(),
                item == null ? null : item.getPriceUomCode(),
                poItem == null ? null : poItem.getPurchaseUomCode(),
                poItem == null ? null : poItem.getStockUomCode(),
                poItem == null ? null : poItem.getPriceUomCode()
        };

        for (String c : candidates) {
            String unit = normalizeInboundDisplayUnit(c);
            if (!StringUtils.hasText(unit)) {
                continue;
            }
            if ("个".equals(unit) || "支".equals(unit) || "箱".equals(unit)) {
                return unit;
            }
        }

        throw new RuntimeException("无法判定采购收货入库数量单位，materialCode="
                + defaultString(item == null ? null : item.getMaterialCode(), "-")
                + ", receiptItemId=" + (item == null || item.getId() == null ? "-" : item.getId().toString()));
    }

    private String normalizeInboundDisplayUnit(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String t = raw.trim();
        String upper = t.toUpperCase();
        if ("ROLL".equals(upper) || "RL".equals(upper) || "卷".equals(t)) {
            return "卷";
        }
        if ("KG".equals(upper) || "KGS".equals(upper) || "公斤".equals(t) || "千克".equals(t)) {
            return "kg";
        }
        if ("DRUM".equals(upper) || "BARREL".equals(upper) || "BUCKET".equals(upper)
                || "桶".equals(t) || "TONG".equals(upper)) {
            return "桶";
        }
        if ("BAG".equals(upper) || "SACK".equals(upper) || "包".equals(t)) {
            return "包";
        }
        if ("PCS".equals(upper) || "PC".equals(upper) || "EA".equals(upper) || "个".equals(t)) {
            return "个";
        }
        if ("TUBE".equals(upper) || t.contains("纸管") || "管".equals(t) || t.endsWith("管") || "支".equals(t)) {
            return "支";
        }
        if ("BOX".equals(upper) || "CTN".equals(upper) || "箱".equals(t) || t.contains("纸箱")) {
            return "箱";
        }
        if ("M2".equals(upper) || "M²".equals(upper) || "㎡".equals(t) || "平方米".equals(t)) {
            return "㎡";
        }
        return t;
    }

    private String normalizeQtyUnit(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String t = raw.trim();
        String upper = t.toUpperCase();
        // 计数类统一为 PCS，避免“个/箱/桶/支”等口径不一致
        if ("EA".equals(upper) || "PCS".equals(upper) || "PC".equals(upper)
                || "个".equals(t) || "支".equals(t)
                || "BOX".equals(upper) || "CTN".equals(upper) || "箱".equals(t) || t.contains("纸箱")
                || "TUBE".equals(upper) || t.contains("纸管") || "管".equals(t) || t.endsWith("管")
                || "DRUM".equals(upper) || "BARREL".equals(upper) || "桶".equals(t)) {
            return "PCS";
        }
        if ("ROLL".equals(upper) || "RL".equals(upper) || "卷".equals(t)) {
            return "ROLL";
        }
        if ("KG".equals(upper) || "KGS".equals(upper) || "公斤".equals(t) || "千克".equals(t)) {
            return "KG";
        }
        if ("M2".equals(upper) || "M²".equals(upper) || "㎡".equals(t) || "平方米".equals(t)) {
            return "M2";
        }
        return upper;
    }

    private boolean isPcsMaterial(PurchaseReceiptItem item, PurchaseOrderItem poItem) {
        String[] texts = new String[] {
                item == null ? null : item.getMaterialCode(),
                item == null ? null : item.getMaterialName(),
                item == null ? null : item.getSpecification(),
                poItem == null ? null : poItem.getMaterialCode(),
                poItem == null ? null : poItem.getMaterialName(),
                poItem == null ? null : poItem.getRawSpec(),
                poItem == null ? null : poItem.getFilmSpecRaw()
        };
        for (String text : texts) {
            if (!StringUtils.hasText(text)) {
                continue;
            }
            String normalized = text.trim().toUpperCase();
            if (normalized.contains("PEG") || normalized.contains("PE管")
                    || normalized.contains("纸箱") || normalized.contains("纸管") || normalized.contains("PP管")
                    || normalized.contains("PVC管") || normalized.contains("管材") || normalized.contains("TUBE")) {
                return true;
            }
        }
        return false;
    }

    private boolean isPegTubeMaterial(PurchaseReceiptItem item, PurchaseOrderItem poItem) {
        String[] texts = new String[] {
                item == null ? null : item.getMaterialCode(),
                item == null ? null : item.getMaterialName(),
                item == null ? null : item.getSpecification(),
                poItem == null ? null : poItem.getMaterialCode(),
                poItem == null ? null : poItem.getMaterialName(),
                poItem == null ? null : poItem.getRawSpec(),
                poItem == null ? null : poItem.getFilmSpecRaw()
        };
        for (String text : texts) {
            if (!StringUtils.hasText(text)) {
                continue;
            }
            String normalized = text.trim().toUpperCase();
            if (normalized.startsWith("PEG") || normalized.contains("PEG") || normalized.contains("PE管")) {
                return true;
            }
        }
        return false;
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

    private String resolveInboundApplicantAccount(PurchaseReceipt receipt) {
        if (receipt == null) {
            return getCurrentUsername();
        }
        if (StringUtils.hasText(receipt.getCreatedBy())) {
            return receipt.getCreatedBy().trim();
        }
        if (StringUtils.hasText(receipt.getUpdatedBy())) {
            return receipt.getUpdatedBy().trim();
        }
        if (StringUtils.hasText(receipt.getPurchaseOrderNo())) {
            try {
                PurchaseOrder order = purchaseOrderMapper.selectByOrderNo(receipt.getPurchaseOrderNo());
                if (order != null) {
                    if (StringUtils.hasText(order.getCreatedBy())) {
                        return order.getCreatedBy().trim();
                    }
                    if (StringUtils.hasText(order.getUpdatedBy())) {
                        return order.getUpdatedBy().trim();
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return getCurrentUsername();
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !StringUtils.hasText(authentication.getName())
                || "anonymousUser".equalsIgnoreCase(authentication.getName())) {
            return "system";
        }
        return authentication.getName();
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
        boolean plannedOnly = "planned".equalsIgnoreCase(newStatus);
        String expectedDateText = receipt.getExpectedDate() == null ? "" : receipt.getExpectedDate().toString();

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
            String title = plannedOnly ? "预计到货提醒" : "收货提醒";
            String content = plannedOnly
                ? supplierDisplayName + "有预计到货信息" + (StringUtils.hasText(expectedDateText) ? "（预计日期：" + expectedDateText + "）" : "") + "，请关注。"
                : supplierDisplayName + "的到货信息已更新，请查收。";
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

                String title = plannedOnly ? "预计到货提醒" : "收货提醒";
                String content = plannedOnly
                    ? supplierDisplayName + "的" + materialName + "预计到货" + qtyText
                    + (StringUtils.hasText(expectedDateText) ? "（预计日期：" + expectedDateText + "）" : "")
                    + "，请提前安排入库。"
                    : supplierDisplayName + "的" + materialName + "货到了" + qtyText + "，请查收。";

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
            return "-";
        }
        String value = supplierCodeOrName.trim();

        // 优先按主数据反查标准供应商代码，避免把供应商名称当作代码返回。
        LambdaQueryWrapper<PurchaseSupplier> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PurchaseSupplier::getIsDeleted, 0)
                .and(w -> w.eq(PurchaseSupplier::getSupplierCode, value)
                        .or().eq(PurchaseSupplier::getSupplierName, value)
                        .or().eq(PurchaseSupplier::getShortName, value))
                .last("LIMIT 1");
        PurchaseSupplier supplier = purchaseSupplierMapper.selectOne(wrapper);
        if (supplier != null && StringUtils.hasText(supplier.getSupplierCode())) {
            return supplier.getSupplierCode().trim();
        }

        String inner = extractInnerAlias(value);
        if (StringUtils.hasText(inner)) {
            return inner;
        }
        return "-";
    }

    private String resolveSupplierDisplayName(String supplierCodeOrName) {
        if (!StringUtils.hasText(supplierCodeOrName)) {
            return "供应商";
        }
        String value = supplierCodeOrName.trim();
        LambdaQueryWrapper<PurchaseSupplier> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PurchaseSupplier::getIsDeleted, 0)
                .and(w -> w.eq(PurchaseSupplier::getSupplierCode, value)
                        .or().eq(PurchaseSupplier::getSupplierName, value)
                        .or().eq(PurchaseSupplier::getShortName, value))
                .last("LIMIT 1");
        PurchaseSupplier supplier = purchaseSupplierMapper.selectOne(wrapper);
        if (supplier == null) {
            return value;
        }
        if (StringUtils.hasText(supplier.getShortName())) {
            return supplier.getShortName().trim();
        }
        if (StringUtils.hasText(supplier.getSupplierName())) {
            return supplier.getSupplierName().trim();
        }
        if (StringUtils.hasText(supplier.getSupplierCode())) {
            return supplier.getSupplierCode().trim();
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
        return "planned".equalsIgnoreCase(status)
                || "receiving".equalsIgnoreCase(status)
                || "received".equalsIgnoreCase(status)
                || "partial".equalsIgnoreCase(status);
    }
}
