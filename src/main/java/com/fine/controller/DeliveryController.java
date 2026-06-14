package com.fine.controller;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.modle.DeliveryNotice;
import com.fine.service.DeliveryNoticeService;
import com.fine.Dao.CustomerMapper;
import com.fine.Dao.DeliveryNoticeItemMapper;
import com.fine.Dao.stock.TapeOutboundRequestMapper;
import com.fine.Dao.production.SalesOrderMapper;
import com.fine.Dao.SalesOrderItemMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.Customer;
import com.fine.modle.LoginUser;
import com.fine.modle.SalesOrder;
import com.fine.modle.SalesOrderItem;
import com.fine.modle.stock.TapeOutboundRequest;
import com.fine.modle.stock.TapeStock;
import com.fine.service.stock.TapeStockService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;
import com.fine.modle.DeliveryNoticeItem;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/delivery")
@PreAuthorize("hasAnyAuthority('admin','sales','finance','production','packaging','packing','plan','warehouse','quality','rd')")
public class DeliveryController {

    private static final Set<String> RP_CUSTOMER_CODES = new LinkedHashSet<>(Arrays.asList(
            "RP01", "GDRP01", "JSRP01", "JXRP001", "LZRP01", "SHRP001"
    ));
    
    @Autowired
    private DeliveryNoticeService deliveryNoticeService;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private DeliveryNoticeItemMapper deliveryNoticeItemMapper;

    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;

    @Autowired
    private SalesOrderMapper salesOrderMapper;

    @Autowired
    private TapeStockService tapeStockService;

    @Autowired
    private TapeOutboundRequestMapper tapeOutboundRequestMapper;

    @Autowired
    private WeComController weComController;

    /**
     * 手动触发一次企业微信推送测试
     */
    @GetMapping("/notices/test-wecom-push/{id}")
    public ResponseResult<?> testWeComPush(@PathVariable Long id) {
        return deliveryNoticeService.testWeComPush(id);
    }

    private static final String SLITTING_PENDING_OUTBOUND_LOCATION = "成品待出库区";
    
    /**
     * 分页查询发货通知
     */
    @GetMapping("/list")
    public ResponseResult<?> list(
        @RequestParam(defaultValue = "1") Integer pageNum,
        @RequestParam(defaultValue = "10") Integer pageSize,
        @RequestParam(required = false) String noticeNo,
        @RequestParam(required = false) String orderNo,
        @RequestParam(required = false) String customer,
        @RequestParam(required = false) String sortProp,
        @RequestParam(required = false) String sortOrder
    ) {
        Page<DeliveryNotice> page = new Page<>(pageNum, pageSize);
        QueryWrapper<DeliveryNotice> queryWrapper = new QueryWrapper<>();

        LoginUser loginUser = getLoginUser();
        if (loginUser != null && !hasRole(loginUser, "admin")) {
            Long uid = getCurrentUserId(loginUser);
            List<String> allowedNames = customerMapper.selectCustomerNamesByOwner(uid);
            List<String> allowedCodes = customerMapper.selectCustomerCodesByOwner(uid);
            List<String> allowed = new java.util.ArrayList<>();
            if (allowedNames != null) allowed.addAll(allowedNames);
            if (allowedCodes != null) allowed.addAll(allowedCodes);
            if (allowed.isEmpty()) {
                return ResponseResult.success(page);
            }
            queryWrapper.in("customer", allowed);
        }
        
        if (noticeNo != null && !noticeNo.isEmpty()) {
            queryWrapper.like("notice_no", noticeNo);
        }
        if (orderNo != null && !orderNo.isEmpty()) {
            queryWrapper.like("order_no", orderNo);
        }
        if (customer != null && !customer.isEmpty()) {
            String kw = customer.trim();
            QueryWrapper<Customer> customerQuery = new QueryWrapper<>();
            customerQuery.eq("is_deleted", 0)
                    .and(w -> w.like("customer_name", kw)
                            .or()
                            .like("customer_code", kw)
                            .or()
                            .like("short_name", kw));
            List<Customer> matchedCustomers = customerMapper.selectList(customerQuery);

            java.util.Set<String> aliases = new java.util.HashSet<>();
            if (matchedCustomers != null) {
                for (Customer c : matchedCustomers) {
                    if (c == null) continue;
                    if (c.getCustomerName() != null && !c.getCustomerName().trim().isEmpty()) {
                        aliases.add(c.getCustomerName().trim());
                    }
                    if (c.getCustomerCode() != null && !c.getCustomerCode().trim().isEmpty()) {
                        aliases.add(c.getCustomerCode().trim());
                    }
                    if (c.getShortName() != null && !c.getShortName().trim().isEmpty()) {
                        aliases.add(c.getShortName().trim());
                    }
                }
            }

            if (aliases.isEmpty()) {
                queryWrapper.like("customer", kw);
            } else {
                queryWrapper.and(w -> w.like("customer", kw).or().in("customer", aliases));
            }
        }
        String sortKey = sortProp == null ? "" : sortProp.trim();
        String sortDir = sortOrder == null ? "" : sortOrder.trim();

        // 映射排序字段
        String dbSortField = mapToSortField(sortKey);
        if (dbSortField != null) {
            if ("ascending".equalsIgnoreCase(sortDir)) {
                queryWrapper.orderByAsc(dbSortField);
            } else {
                queryWrapper.orderByDesc(dbSortField);
            }
        } else {
            queryWrapper.orderByDesc("created_at");
        }

        // 1. 执行数据库分页查询 (不带 items)
        IPage<DeliveryNotice> resultPage = deliveryNoticeService.page(page, queryWrapper);
        List<DeliveryNotice> records = resultPage.getRecords();

        if (records == null || records.isEmpty()) {
            return ResponseResult.success(resultPage);
        }

        // 2. 批量查询客户信息，填充简称和代码，避免前端加载全量客户表
        java.util.Set<String> customerKeys = new java.util.HashSet<>();
        for (DeliveryNotice notice : records) {
            if (StringUtils.hasText(notice.getCustomer())) {
                customerKeys.add(notice.getCustomer().trim());
            }
        }
        if (!customerKeys.isEmpty()) {
            QueryWrapper<Customer> cqw = new QueryWrapper<>();
            cqw.eq("is_deleted", 0)
               .and(w -> w.in("customer_name", customerKeys)
                          .or().in("customer_code", customerKeys)
                          .or().in("short_name", customerKeys));
            List<Customer> customerList = customerMapper.selectList(cqw);
            Map<String, Customer> cMap = new HashMap<>();
            for (Customer c : customerList) {
                if (StringUtils.hasText(c.getCustomerName())) cMap.put(c.getCustomerName().trim(), c);
                if (StringUtils.hasText(c.getCustomerCode())) cMap.put(c.getCustomerCode().trim(), c);
                if (StringUtils.hasText(c.getShortName())) cMap.put(c.getShortName().trim(), c);
            }
            for (DeliveryNotice notice : records) {
                Customer c = cMap.get(notice.getCustomer() != null ? notice.getCustomer().trim() : "");
                if (c != null) {
                    notice.setCustomerCode(c.getCustomerCode());
                    notice.setCustomerShortName(StringUtils.hasText(c.getShortName()) ? c.getShortName() : c.getCustomerName());
                } else {
                    notice.setCustomerShortName(notice.getCustomer());
                }
            }
        }

        // 3. 仅为当前页的 records 批量查询明细 (Items)，避免 N+1
        List<Long> noticeIds = new ArrayList<>();
        for (DeliveryNotice notice : records) {
            if (notice != null && notice.getId() != null) {
                noticeIds.add(notice.getId());
            }
        }

        if (!noticeIds.isEmpty()) {
            List<DeliveryNoticeItem> allItems = deliveryNoticeItemMapper.selectByNoticeIds(noticeIds);
            Map<Long, List<DeliveryNoticeItem>> itemMap = new HashMap<>();
            for (DeliveryNoticeItem item : allItems) {
                if (item != null && item.getNoticeId() != null) {
                    itemMap.computeIfAbsent(item.getNoticeId(), k -> new ArrayList<>()).add(item);
                }
            }
            for (DeliveryNotice notice : records) {
                notice.setItems(itemMap.getOrDefault(notice.getId(), Collections.emptyList()));
            }
        }

        return ResponseResult.success(resultPage);
    }

    private String mapToSortField(String sortProp) {
        if (sortProp == null) return null;
        switch (sortProp) {
            case "customer": return "customer";
            case "noticeNo": return "notice_no";
            case "orderNo": return "order_no";
            case "deliveryDate": return "delivery_date";
            case "status": return "status";
            case "createdAt": return "created_at";
            default: return null;
        }
    }

    
    /**
     * 创建发货通知
     */
    @PostMapping("/create")
    public ResponseResult<?> create(@RequestBody DeliveryNotice deliveryNotice) {
        try {
            DeliveryNotice created = deliveryNoticeService.createDeliveryNotice(deliveryNotice);
            return ResponseResult.success(created);
        } catch (Exception e) {
            return ResponseResult.error(500, "创建失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取发货通知单详情
     */
    @GetMapping("/{id}")
    public ResponseResult<?> getDetail(@PathVariable Long id) {
        DeliveryNotice notice = deliveryNoticeService.getDeliveryNoticeDetail(id);
        if (notice != null) {
            if (!canAccessNotice(getLoginUser(), notice)) {
                return ResponseResult.error(403, "无权限访问该发货单");
            }
            return ResponseResult.success(notice);
        } else {
            return ResponseResult.error(404, "未找到该发货单");
        }
    }

    /**
     * RP共享池预览（按订单明细）：返回池可发、池已报工、池已发与池内明细数。
     */
    @GetMapping("/rp-pool-preview")
    public ResponseResult<?> getRpPoolPreview(
            @RequestParam String orderItemIds,
            @RequestParam(required = false) Long currentNoticeId
    ) {
        if (!StringUtils.hasText(orderItemIds)) {
            return ResponseResult.success(new LinkedHashMap<>());
        }
        List<Long> ids = new ArrayList<>();
        String[] parts = orderItemIds.split(",");
        for (String part : parts) {
            if (!StringUtils.hasText(part)) {
                continue;
            }
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (Exception ignore) {
                // 忽略非法ID，继续解析其他ID
            }
        }
        if (ids.isEmpty()) {
            return ResponseResult.success(new LinkedHashMap<>());
        }
        return ResponseResult.success(deliveryNoticeService.getRpPoolPreview(ids, currentNoticeId));
    }

    /**
     * 查询物流轨迹
     */
    @GetMapping("/{id}/logistics")
    public ResponseResult<Map<String, Object>> queryLogistics(@PathVariable Long id) {
        try {
            DeliveryNotice notice = deliveryNoticeService.getById(id);
            if (notice == null) {
                return ResponseResult.error(404, "未找到该发货单");
            }
            if (!canAccessNotice(getLoginUser(), notice)) {
                return ResponseResult.error(403, "无权限访问该发货单");
            }
            Map<String, Object> result = deliveryNoticeService.queryLogistics(id);
            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseResult.success(result);
            }
            String msg = String.valueOf(result.getOrDefault("message", "物流查询失败"));
            if (msg.contains("查询无结果")) {
                result.put("success", false);
                result.put("status", result.getOrDefault("status", "暂无轨迹"));
                result.put("lastUpdate", result.getOrDefault("lastUpdate", "-"));
                result.put("traces", result.getOrDefault("traces", java.util.Collections.emptyList()));
                return ResponseResult.success(result);
            }
            if (msg.contains("线下查询") || msg.contains("线下承运")) {
                result.put("success", false);
                result.put("status", result.getOrDefault("status", "未识别承运公司"));
                result.put("lastUpdate", result.getOrDefault("lastUpdate", "-"));
                result.put("traces", result.getOrDefault("traces", java.util.Collections.emptyList()));
                return ResponseResult.success(result);
            }
            if (msg.contains("不支持此快递公司") || msg.contains("未识别快递公司")) {
                result.put("success", false);
                result.put("status", result.getOrDefault("status", "未识别承运公司"));
                result.put("lastUpdate", result.getOrDefault("lastUpdate", "-"));
                result.put("traces", result.getOrDefault("traces", java.util.Collections.emptyList()));
                return ResponseResult.success(result);
            }
            if (msg.contains("单号长度不符合") || msg.contains("单号格式不正确")) {
                result.put("success", false);
                result.put("status", result.getOrDefault("status", "暂无轨迹"));
                result.put("lastUpdate", result.getOrDefault("lastUpdate", "-"));
                result.put("traces", result.getOrDefault("traces", java.util.Collections.emptyList()));
                return ResponseResult.success(result);
            }
            return ResponseResult.error(500, msg);
        } catch (Exception e) {
            return ResponseResult.error(500, "物流查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 确认发货 - 更新状态为已发货
     */
    @PostMapping("/confirm/{id}")
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> confirmShip(@PathVariable Long id) {
        try {
            LoginUser loginUser = getLoginUser();
            DeliveryNotice notice = deliveryNoticeService.getById(id);
            if (notice == null) {
                return ResponseResult.error(404, "未找到该发货单");
            }
            if (!canAccessNotice(loginUser, notice)) {
                return ResponseResult.error(403, "无权限操作该发货单");
            }
            
            if ("已收货".equals(notice.getStatus()) || "received".equalsIgnoreCase(notice.getStatus())) {
                return ResponseResult.error(400, "该发货单已确认收货，不能重复确认发货");
            }

            if ("已发货".equals(notice.getStatus())) {
                return ResponseResult.error(400, "该发货单已确认发货");
            }

            // 分切成品（成品待出库区）自动出库，免人工审核；其他来源库存不受影响
            autoOutboundSlittingStocksIfNeeded(notice, getCurrentUsername(loginUser));
            
            // 更新状态为已发货
            notice.setStatus("已发货");
            notice.setUpdatedBy(getCurrentUsername(loginUser));
            notice.setUpdatedAt(new Date());
            boolean updated = deliveryNoticeService.updateById(notice);
            
            if (updated) {
                // 新增：自动推送企微通知 (方案二)
                try {
                    Customer customer = customerMapper.selectOne(new QueryWrapper<Customer>().eq("customer_code", notice.getCustomer()).last("LIMIT 1"));
                    if (customer != null && customer.getWecomChatId() != null && !customer.getWecomChatId().isEmpty()) {
                        weComController.sendAutoShipmentNotification(customer.getWecomChatId(), notice);
                    }
                } catch (Exception weEx) {
                    System.err.println("自动推带企微消息失败: " + weEx.getMessage());
                }

                if (isRpCustomerCode(notice.getCustomer())) {
                    deliveryNoticeService.rebalanceRpProducedCreditsByNotice(notice.getId(), getCurrentUsername(loginUser));
                } else {
                    syncSalesOrderItemsDeliveryProgress(notice.getOrderId());
                }
                return ResponseResult.success("确认发货成功");
            } else {
                return ResponseResult.error(500, "确认发货失败");
            }
        } catch (Exception e) {
            return ResponseResult.error(500, "确认发货失败: " + e.getMessage());
        }
    }

    private void autoOutboundSlittingStocksIfNeeded(DeliveryNotice notice, String operator) {
        if (notice == null || notice.getId() == null) {
            return;
        }
        List<DeliveryNoticeItem> items = deliveryNoticeItemMapper.selectByNoticeId(notice.getId());
        if (items == null || items.isEmpty()) {
            return;
        }
        for (DeliveryNoticeItem item : items) {
            if (item == null) {
                continue;
            }
            int needRolls = item.getQuantity() == null ? 0 : Math.max(item.getQuantity().intValue(), 0);
            if (needRolls <= 0) {
                continue;
            }
            String materialCode = item.getMaterialCode() == null ? "" : item.getMaterialCode().trim();
            Map<Long, Integer> stockAllocations = new LinkedHashMap<>();
            int allocatedRolls = 0;

            // 优先按发货明细批次号定位
            for (String oneBatch : splitBatchNos(item.getBatchNo())) {
                TapeStock stock = tapeStockService.getStockByBatchNo(oneBatch);
                if (!isSlittingPendingOutboundStock(stock, materialCode)) {
                    continue;
                }
                int available = stock.getTotalRolls() == null ? 0 : Math.max(stock.getTotalRolls(), 0);
                int canAllocate = Math.min(needRolls - allocatedRolls, available);
                if (canAllocate <= 0) {
                    continue;
                }
                stockAllocations.put(stock.getId(), canAllocate);
                allocatedRolls += canAllocate;
                if (allocatedRolls >= needRolls) {
                    break;
                }
            }

            // 批次不足时按料号FIFO补足
            if (allocatedRolls < needRolls && StringUtils.hasText(materialCode)) {
                List<TapeStock> fifoStocks = tapeStockService.getStockByMaterialFIFO(materialCode);
                for (TapeStock stock : fifoStocks) {
                    if (!isSlittingPendingOutboundStock(stock, materialCode)) {
                        continue;
                    }
                    if (stockAllocations.containsKey(stock.getId())) {
                        continue;
                    }
                    int available = stock.getTotalRolls() == null ? 0 : Math.max(stock.getTotalRolls(), 0);
                    int canAllocate = Math.min(needRolls - allocatedRolls, available);
                    if (canAllocate <= 0) {
                        continue;
                    }
                    stockAllocations.put(stock.getId(), canAllocate);
                    allocatedRolls += canAllocate;
                    if (allocatedRolls >= needRolls) {
                        break;
                    }
                }
            }

            // 仅对“成品待出库区”的分切库存做自动出库，不应阻塞其他来源库存的正常发货。
            // 因此这里采用“尽力处理”策略：
            // - 匹配到0卷：直接跳过该明细
            // - 匹配不足：按实际匹配到的卷数执行自动出库
            if (stockAllocations.isEmpty()) {
                continue;
            }

            for (Map.Entry<Long, Integer> allocation : stockAllocations.entrySet()) {
                Long stockId = allocation.getKey();
                Integer allocateRolls = allocation.getValue();
                if (stockId == null || allocateRolls == null || allocateRolls <= 0) {
                    continue;
                }
                TapeOutboundRequest outbound = new TapeOutboundRequest();
                outbound.setStockId(stockId);
                outbound.setRolls(allocateRolls);
                outbound.setApplicant(operator);
                outbound.setApplyDept("销售发货");
                outbound.setRemark("发货单" + notice.getNoticeNo() + "自动出库");
                outbound.setOrderNo(notice.getOrderNo());
                outbound.setOrderItemId(item.getOrderItemId());
                outbound.setDeliveryNoticeId(notice.getId());
                outbound.setDeliveryNoticeNo(notice.getNoticeNo());
                outbound.setBizType(TapeOutboundRequest.BIZ_TYPE_SALES_AUTO);
                TapeOutboundRequest created = tapeStockService.createOutboundRequest(outbound);

                TapeStock stock = tapeStockService.getStockById(stockId);
                String scanCode = stock == null ? "" : (StringUtils.hasText(stock.getBatchNo()) ? stock.getBatchNo() : stock.getQrCode());
                tapeStockService.approveOutbound(created.getId(), true, operator, "销售发货自动出库（分切成品）", scanCode);
            }
        }
    }

    private boolean isSlittingPendingOutboundStock(TapeStock stock, String expectedMaterialCode) {
        if (stock == null || stock.getId() == null) {
            return false;
        }
        if (stock.getStatus() == null || stock.getStatus() != 1) {
            return false;
        }
        if (stock.getTotalRolls() == null || stock.getTotalRolls() <= 0) {
            return false;
        }
        if (StringUtils.hasText(expectedMaterialCode)) {
            String materialCode = stock.getMaterialCode() == null ? "" : stock.getMaterialCode().trim();
            if (!expectedMaterialCode.equalsIgnoreCase(materialCode)) {
                return false;
            }
        }
        String location = stock.getLocation() == null ? "" : stock.getLocation().trim();
        return SLITTING_PENDING_OUTBOUND_LOCATION.equals(location);
    }

    private List<String> splitBatchNos(String batchNoText) {
        List<String> result = new ArrayList<>();
        if (!StringUtils.hasText(batchNoText)) {
            return result;
        }
        String[] parts = batchNoText.split("[,，]");
        for (String part : parts) {
            String one = part == null ? "" : part.trim();
            if (StringUtils.hasText(one)) {
                result.add(one);
            }
        }
        return result;
    }

    private void syncSalesOrderItemsDeliveryProgress(Long orderId) {
        if (orderId == null) {
            return;
        }

        List<SalesOrderItem> orderItems = salesOrderItemMapper.selectList(
                new QueryWrapper<SalesOrderItem>()
                        .eq("order_id", orderId)
                        .eq("is_deleted", 0)
        );
        if (orderItems == null || orderItems.isEmpty()) {
            return;
        }

        for (SalesOrderItem item : orderItems) {
            if (item == null || item.getId() == null) {
                continue;
            }

            int totalRolls = item.getRolls() == null ? 0 : item.getRolls().intValue();
            Double confirmedShipped = deliveryNoticeItemMapper.getConfirmedShippedQuantityByOrderItemId(item.getId());
            int shippedRolls = confirmedShipped == null ? 0 : Math.max(confirmedShipped.intValue(), 0);
            int deliveredRolls = Math.min(totalRolls, shippedRolls);
            int remainingRolls = Math.max(totalRolls - deliveredRolls, 0);

            item.setDeliveredQty((double) deliveredRolls);
            item.setRemainingQty((double) remainingRolls);
            if (deliveredRolls <= 0) {
                item.setProductionStatus("not_started");
            } else if (remainingRolls <= 0) {
                item.setProductionStatus("completed");
            } else {
                item.setProductionStatus("partial");
            }
            salesOrderItemMapper.updateById(item);
        }
    }

    /**
     * 历史补扣：按“发货单+料号”对账已确认发货/收货单，补齐遗漏的分切成品自动出库。
     * - dryRun=true: 仅计算差额，不执行扣减
     * - dryRun=false: 执行补扣（仅扣“成品待出库区”库存）
     */
    @PostMapping("/repair/auto-outbound-slitting")
    @PreAuthorize("hasAuthority('admin')")
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<Map<String, Object>> repairAutoOutboundSlitting(
            @RequestParam(defaultValue = "true") boolean dryRun,
            @RequestParam(required = false) Integer limit) {
        try {
            LoginUser loginUser = getLoginUser();
            String operator = getCurrentUsername(loginUser);

            QueryWrapper<DeliveryNotice> wrapper = new QueryWrapper<>();
            wrapper.eq("is_deleted", 0)
                    .and(w -> w.eq("status", "已发货")
                            .or().eq("status", "shipped")
                            .or().eq("status", "已收货")
                            .or().eq("status", "received"))
                    .orderByDesc("id");
            List<DeliveryNotice> notices = deliveryNoticeService.list(wrapper);

            if (limit != null && limit > 0 && notices.size() > limit) {
                notices = notices.subList(0, limit);
            }

            int scannedNotices = 0;
            int affectedNotices = 0;
            int repairedRolls = 0;
            int shortageRolls = 0;
            List<Map<String, Object>> details = new ArrayList<>();

            for (DeliveryNotice notice : notices) {
                if (notice == null || notice.getId() == null || !StringUtils.hasText(notice.getNoticeNo())) {
                    continue;
                }
                scannedNotices++;

                List<DeliveryNoticeItem> items = deliveryNoticeItemMapper.selectByNoticeId(notice.getId());
                if (items == null || items.isEmpty()) {
                    continue;
                }

                Map<String, Integer> requiredByMaterial = new LinkedHashMap<>();
                for (DeliveryNoticeItem item : items) {
                    if (item == null) {
                        continue;
                    }
                    String material = normalizeMaterialCode(item.getMaterialCode());
                    int qty = item.getQuantity() == null ? 0 : Math.max(item.getQuantity().intValue(), 0);
                    if (!StringUtils.hasText(material) || qty <= 0) {
                        continue;
                    }
                    Integer oldRequired = requiredByMaterial.get(material);
                    requiredByMaterial.put(material, (oldRequired == null ? 0 : oldRequired) + qty);
                }
                if (requiredByMaterial.isEmpty()) {
                    continue;
                }

                Map<String, Double> approvedByMaterial = loadApprovedAutoOutboundByMaterial(notice.getNoticeNo());

                boolean noticeAffected = false;
                for (Map.Entry<String, Integer> entry : requiredByMaterial.entrySet()) {
                    String materialCode = entry.getKey();
                    int required = entry.getValue() == null ? 0 : Math.max(entry.getValue(), 0);
                    int approved = approvedByMaterial.getOrDefault(materialCode, 0.0).intValue();
                    int deficit = required - approved;
                    if (deficit <= 0) {
                        continue;
                    }

                    noticeAffected = true;
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("noticeNo", notice.getNoticeNo());
                    d.put("materialCode", materialCode);
                    d.put("requiredRolls", required);
                    d.put("approvedRolls", approved);
                    d.put("deficitRolls", deficit);

                    if (dryRun) {
                        d.put("dryRun", true);
                        details.add(d);
                        repairedRolls += deficit;
                        continue;
                    }

                    int remaining = deficit;
                    int fixed = 0;
                    List<TapeStock> fifoStocks = tapeStockService.getStockByMaterialFIFO(materialCode);
                    for (TapeStock stock : fifoStocks) {
                        if (remaining <= 0) {
                            break;
                        }
                        if (!isSlittingPendingOutboundStock(stock, materialCode)) {
                            continue;
                        }
                        int available = stock.getTotalRolls() == null ? 0 : Math.max(stock.getTotalRolls(), 0);
                        if (available <= 0) {
                            continue;
                        }

                        int allocate = Math.min(remaining, available);
                        TapeOutboundRequest outbound = new TapeOutboundRequest();
                        outbound.setStockId(stock.getId());
                        outbound.setRolls(allocate);
                        outbound.setApplicant(operator);
                        outbound.setApplyDept("销售发货");
                        outbound.setRemark("发货单" + notice.getNoticeNo() + "自动补扣出库");
                        outbound.setOrderNo(notice.getOrderNo());
                        outbound.setDeliveryNoticeId(notice.getId());
                        outbound.setDeliveryNoticeNo(notice.getNoticeNo());
                        outbound.setBizType(TapeOutboundRequest.BIZ_TYPE_SALES_REPAIR);
                        TapeOutboundRequest created = tapeStockService.createOutboundRequest(outbound);

                        String scanCode = StringUtils.hasText(stock.getBatchNo()) ? stock.getBatchNo() : stock.getQrCode();
                        tapeStockService.approveOutbound(created.getId(), true, operator, "历史发货自动补扣（分切成品）", scanCode);

                        fixed += allocate;
                        remaining -= allocate;
                    }

                    d.put("dryRun", false);
                    d.put("fixedRolls", fixed);
                    d.put("shortageRolls", Math.max(remaining, 0));
                    details.add(d);

                    repairedRolls += fixed;
                    shortageRolls += Math.max(remaining, 0);
                }

                if (noticeAffected) {
                    affectedNotices++;
                }
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("dryRun", dryRun);
            data.put("scannedNotices", scannedNotices);
            data.put("affectedNotices", affectedNotices);
            data.put("repairedRolls", repairedRolls);
            data.put("shortageRolls", shortageRolls);
            data.put("details", details);
            return ResponseResult.success(dryRun ? "历史补扣预览完成" : "历史补扣执行完成", data);
        } catch (Exception e) {
            return ResponseResult.error(500, "历史补扣失败: " + e.getMessage());
        }
    }

    private Map<String, Double> loadApprovedAutoOutboundByMaterial(String noticeNo) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (!StringUtils.hasText(noticeNo)) {
            return result;
        }
        QueryWrapper<TapeOutboundRequest> wrapper = new QueryWrapper<>();
        wrapper.eq("status", TapeOutboundRequest.STATUS_APPROVED)
                .eq("apply_dept", "销售发货")
                .likeRight("remark", "发货单" + noticeNo + "自动")
                .orderByAsc("id");
        List<TapeOutboundRequest> list = tapeOutboundRequestMapper.selectList(wrapper);
        if (list == null || list.isEmpty()) {
            return result;
        }
        for (TapeOutboundRequest req : list) {
            if (req == null) {
                continue;
            }
            String material = normalizeMaterialCode(req.getMaterialCode());
            Double rolls = req.getRolls() == null ? 0.0 : Math.max(req.getRolls(), 0.0);
            if (!StringUtils.hasText(material) || rolls <= 0) {
                continue;
            }
            Double oldApproved = result.get(material);
            result.put(material, (oldApproved == null ? 0.0 : oldApproved) + rolls);
        }
        return result;
    }

    private String normalizeMaterialCode(String materialCode) {
        return materialCode == null ? "" : materialCode.trim();
    }

    private boolean isRpCustomerCode(String customerCode) {
        if (!StringUtils.hasText(customerCode)) {
            return false;
        }
        return RP_CUSTOMER_CODES.contains(customerCode.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * 确认收货 - 更新状态为已收货
     */
    @PostMapping("/receive/{id}")
    public ResponseResult<?> confirmReceive(@PathVariable Long id,
                                            @RequestBody(required = false) Map<String, Object> body) {
        try {
            LoginUser loginUser = getLoginUser();
            DeliveryNotice notice = deliveryNoticeService.getById(id);
            if (notice == null) {
                return ResponseResult.error(404, "未找到该发货单");
            }
            if (!canAccessNotice(loginUser, notice)) {
                return ResponseResult.error(403, "无权限操作该发货单");
            }

            // 兼容“确认收货弹窗”一并提交物流信息，避免先调 /delivery/update 触发已扣减库存禁止编辑
            if (body != null) {
                Object carrierNameObj = body.get("carrierName");
                Object carrierNoObj = body.get("carrierNo");
                Object carrierPhoneObj = body.get("carrierPhone");
                String reqCarrierName = carrierNameObj == null ? "" : String.valueOf(carrierNameObj).trim();
                String reqCarrierNo = carrierNoObj == null ? "" : String.valueOf(carrierNoObj).trim();
                String reqCarrierPhone = carrierPhoneObj == null ? "" : String.valueOf(carrierPhoneObj).trim();
                if (StringUtils.hasText(reqCarrierName)) {
                    notice.setCarrierName(reqCarrierName);
                }
                if (StringUtils.hasText(reqCarrierNo)) {
                    notice.setCarrierNo(reqCarrierNo);
                }
                if (StringUtils.hasText(reqCarrierPhone)) {
                    notice.setCarrierPhone(reqCarrierPhone);
                }
            }

            String status = notice.getStatus();
            if ("已收货".equals(status) || "received".equalsIgnoreCase(status)) {
                return ResponseResult.success("该发货单已是已收货状态");
            }

            boolean shipped = "已发货".equals(status) || "shipped".equalsIgnoreCase(status);
            if (!shipped) {
                // 兼容需求：物流显示已送达/已签收时，可直接转已收货
                Map<String, Object> logistics = deliveryNoticeService.queryLogistics(id);
                DeliveryNotice latest = deliveryNoticeService.getById(id);
                String latestStatus = latest == null ? "" : latest.getStatus();
                if ("已收货".equals(latestStatus) || "received".equalsIgnoreCase(latestStatus)) {
                    return ResponseResult.success("物流已送达，系统已自动确认收货");
                }

                String logisticsStatus = logistics == null ? "" : String.valueOf(logistics.getOrDefault("status", ""));
                boolean delivered = logisticsStatus.contains("已送达") || logisticsStatus.contains("已签收");
                if (delivered && latest != null) {
                    latest.setStatus("已收货");
                    latest.setUpdatedBy(getCurrentUsername(loginUser));
                    latest.setUpdatedAt(new Date());
                    if (deliveryNoticeService.updateById(latest)) {
                        return ResponseResult.success("物流已送达，系统已自动确认收货");
                    }
                }
                return ResponseResult.error(400, "请先确认发货，再确认收货");
            }

            if (notice.getCarrierName() == null || notice.getCarrierName().trim().isEmpty()) {
                return ResponseResult.error(400, "请先填写物流公司，再确认收货");
            }
            if (notice.getCarrierNo() == null || notice.getCarrierNo().trim().isEmpty()) {
                return ResponseResult.error(400, "请先填写快递单号，再确认收货");
            }

            notice.setStatus("已收货");
            notice.setUpdatedBy(getCurrentUsername(loginUser));
            notice.setUpdatedAt(new Date());
            boolean updated = deliveryNoticeService.updateById(notice);
            if (updated) {
                return ResponseResult.success("确认收货成功（确认人：" + getCurrentUsername(loginUser) + "）");
            } else {
                return ResponseResult.error(500, "确认收货失败");
            }
        } catch (Exception e) {
            return ResponseResult.error(500, "确认收货失败: " + e.getMessage());
        }
    }

    /**
     * 批量检查“已发货”发货单：若物流显示已送达/已签收，自动改为已收货
     */
    @PostMapping("/receive/auto-sync-delivered")
    public ResponseResult<Map<String, Object>> autoSyncDeliveredReceipts() {
        try {
            LoginUser loginUser = getLoginUser();
            QueryWrapper<DeliveryNotice> wrapper = new QueryWrapper<>();
            wrapper.eq("is_deleted", 0)
                    .and(w -> w.eq("status", "已发货").or().eq("status", "shipped"))
                    .isNotNull("carrier_no");
            List<DeliveryNotice> notices = deliveryNoticeService.list(wrapper);

            int scanned = 0;
            int changed = 0;
            int skipped = 0;

            for (DeliveryNotice notice : notices) {
                if (notice == null || notice.getId() == null) {
                    skipped++;
                    continue;
                }
                if (!canAccessNotice(loginUser, notice)) {
                    skipped++;
                    continue;
                }
                String carrierNo = notice.getCarrierNo();
                if (carrierNo == null || carrierNo.trim().isEmpty()) {
                    skipped++;
                    continue;
                }
                scanned++;

                try {
                    Map<String, Object> logistics = deliveryNoticeService.queryLogistics(notice.getId());
                    String logisticsStatus = logistics == null ? "" : String.valueOf(logistics.getOrDefault("status", ""));
                    boolean delivered = logisticsStatus.contains("已送达") || logisticsStatus.contains("已签收");
                    DeliveryNotice latest = deliveryNoticeService.getById(notice.getId());
                    String latestStatus = latest == null ? "" : latest.getStatus();

                    if ((delivered || "已收货".equals(latestStatus) || "received".equalsIgnoreCase(latestStatus)) && latest != null) {
                        if (!("已收货".equals(latestStatus) || "received".equalsIgnoreCase(latestStatus))) {
                            latest.setStatus("已收货");
                            latest.setUpdatedBy(getCurrentUsername(loginUser));
                            latest.setUpdatedAt(new Date());
                            if (deliveryNoticeService.updateById(latest)) {
                                changed++;
                            }
                        } else {
                            changed++;
                        }
                    }
                } catch (Exception ignore) {
                    // 单条异常不中断整体同步
                }
            }

            Map<String, Object> data = new HashMap<>();
            data.put("scanned", scanned);
            data.put("changed", changed);
            data.put("skipped", skipped);
            return ResponseResult.success(data);
        } catch (Exception e) {
            return ResponseResult.error(500, "批量同步已收货状态失败: " + e.getMessage());
        }
    }

    /**
     * 更新发货通知（包含明细）
     */
    @PostMapping("/update")
    public ResponseResult<?> update(@RequestBody DeliveryNotice deliveryNotice) {
        try {
            if (deliveryNotice.getId() == null) {
                return ResponseResult.error(400, "缺少发货单ID");
            }

            DeliveryNotice existing = deliveryNoticeService.getById(deliveryNotice.getId());
            if (existing != null && !canAccessNotice(getLoginUser(), existing)) {
                return ResponseResult.error(403, "无权限操作该发货单");
            }

            // 已完成库存扣减的发货单，不再允许整单编辑；
            // 但确认收货场景仍需补录物流信息，允许仅更新承运信息字段。
            if (existing != null) {
                String remark = existing.getRemark() == null ? "" : existing.getRemark();
                if (remark.contains("[STOCK_OUT_SYNCED]")) {
                    if (deliveryNotice.getCarrierName() != null) {
                        existing.setCarrierName(deliveryNotice.getCarrierName());
                    }
                    if (deliveryNotice.getCarrierNo() != null) {
                        existing.setCarrierNo(deliveryNotice.getCarrierNo());
                    }
                    if (deliveryNotice.getCarrierPhone() != null) {
                        existing.setCarrierPhone(deliveryNotice.getCarrierPhone());
                    }
                    if (deliveryNotice.getDeliveryDate() != null) {
                        existing.setDeliveryDate(deliveryNotice.getDeliveryDate());
                    }
                    existing.setUpdatedBy(getCurrentUsername(getLoginUser()));
                    existing.setUpdatedAt(new Date());
                    boolean updated = deliveryNoticeService.updateById(existing);
                    if (updated) {
                        return ResponseResult.success("更新成功");
                    }
                    return ResponseResult.error(500, "更新失败");
                }
            }

            deliveryNoticeService.updateDeliveryNotice(deliveryNotice);

            return ResponseResult.success("更新成功");
        } catch (Exception e) {
            return ResponseResult.error(500, "更新失败: " + e.getMessage());
        }
    }

    /**
     * 标签打印时：按送货单号追加批次号（逗号分隔、唯一值）
     */
    @PreAuthorize("hasAnyAuthority('admin','sales','finance','production','packaging','packing','plan','warehouse','quality','rd')")
    @PostMapping("/append-batch-no")
    public ResponseResult<?> appendBatchNo(@RequestBody Map<String, Object> body) {
        String noticeNo = body == null ? "" : String.valueOf(body.getOrDefault("noticeNo", "")).trim();
        String batchNo = body == null ? "" : String.valueOf(body.getOrDefault("batchNo", "")).trim();
        String materialCode = body == null ? "" : String.valueOf(body.getOrDefault("materialCode", "")).trim();
        return doAppendBatchNo(noticeNo, batchNo, materialCode);
    }

    /**
     * 兼容入口：支持 query 参数调用，避免客户端方法不一致导致 405。
     */
    @PreAuthorize("hasAnyAuthority('admin','sales','finance','production','packaging','packing','plan','warehouse','quality','rd')")
    @GetMapping("/append-batch-no")
    public ResponseResult<?> appendBatchNoGet(@RequestParam String noticeNo,
                                              @RequestParam String batchNo,
                                              @RequestParam(required = false) String materialCode) {
        return doAppendBatchNo(
                noticeNo == null ? "" : noticeNo.trim(),
                batchNo == null ? "" : batchNo.trim(),
                materialCode == null ? "" : materialCode.trim()
        );
    }

    private ResponseResult<?> doAppendBatchNo(String noticeNo, String batchNo, String materialCode) {
        try {
            if (noticeNo.isEmpty()) {
                return ResponseResult.error(400, "送货单号不能为空");
            }
            if (batchNo.isEmpty()) {
                return ResponseResult.error(400, "批次号不能为空");
            }

            DeliveryNotice notice = deliveryNoticeService.getOne(
                    new QueryWrapper<DeliveryNotice>().eq("notice_no", noticeNo).eq("is_deleted", 0).last("LIMIT 1")
            );
            if (notice == null) {
                return ResponseResult.error(404, "未找到送货单：" + noticeNo);
            }
            if (!canAccessNotice(getLoginUser(), notice)) {
                return ResponseResult.error(403, "无权限操作该发货单");
            }

            String merged = deliveryNoticeService.appendBatchNoByNoticeNo(noticeNo, batchNo);
            int itemUpdated = deliveryNoticeService.syncItemBatchNoByNoticeNo(noticeNo, materialCode, batchNo);
            Map<String, Object> data = new HashMap<>();
            data.put("noticeNo", noticeNo);
            data.put("batchNos", merged);
            data.put("itemUpdated", itemUpdated);
            return ResponseResult.success(data);
        } catch (Exception e) {
            return ResponseResult.error(500, "保存批次号失败: " + e.getMessage());
        }
    }

    /**
     * 删除发货通知（仅待发货状态可删除）
     */
    @DeleteMapping("/{id}")
    public ResponseResult<?> delete(@PathVariable Long id) {
        try {
            DeliveryNotice existing = deliveryNoticeService.getById(id);
            if (existing == null) {
                return ResponseResult.error(404, "发货单不存在");
            }
            if (!canAccessNotice(getLoginUser(), existing)) {
                return ResponseResult.error(403, "无权限操作该发货单");
            }

            boolean deleted = deliveryNoticeService.deleteDeliveryNotice(id);
            return deleted ? ResponseResult.success("删除成功") : ResponseResult.error(500, "删除失败");
        } catch (Exception e) {
            return ResponseResult.error(500, "删除失败: " + e.getMessage());
        }
    }

    private LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return (LoginUser) authentication.getPrincipal();
        }
        return null;
    }

    private boolean hasRole(LoginUser loginUser, String role) {
        return loginUser != null && loginUser.getPermissions() != null && loginUser.getPermissions().contains(role);
    }

    private Long getCurrentUserId(LoginUser loginUser) {
        return loginUser != null && loginUser.getUser() != null ? loginUser.getUser().getId() : null;
    }

    private String getCurrentUsername(LoginUser loginUser) {
        return loginUser != null ? loginUser.getUsername() : "system";
    }

    private boolean canAccessNotice(LoginUser loginUser, DeliveryNotice notice) {
        if (notice == null) return true;
        if (loginUser == null) return false;
        if (hasRole(loginUser, "admin")) return true;
        Long uid = getCurrentUserId(loginUser);
        if (uid == null) return false;

        if (notice.getOrderId() != null) {
            SalesOrder order = salesOrderMapper.selectById(notice.getOrderId());
            if (order != null) {
                if (uid.equals(order.getSalesUserId()) || uid.equals(order.getDocumentationPersonUserId())) {
                    return true;
                }
                List<String> orderAllowedNames = customerMapper.selectCustomerNamesByOwner(uid);
                List<String> orderAllowedCodes = customerMapper.selectCustomerCodesByOwner(uid);
                java.util.Set<String> orderAllowed = new java.util.HashSet<>();
                if (orderAllowedNames != null) orderAllowed.addAll(orderAllowedNames);
                if (orderAllowedCodes != null) orderAllowed.addAll(orderAllowedCodes);
                if (orderAllowed.contains(order.getCustomer())) {
                    return true;
                }
            }
        }

        List<String> allowedNames = customerMapper.selectCustomerNamesByOwner(uid);
        List<String> allowedCodes = customerMapper.selectCustomerCodesByOwner(uid);
        List<String> allowed = new java.util.ArrayList<>();
        if (allowedNames != null) allowed.addAll(allowedNames);
        if (allowedCodes != null) allowed.addAll(allowedCodes);
        return !allowed.isEmpty() && allowed.contains(notice.getCustomer());
    }
}
