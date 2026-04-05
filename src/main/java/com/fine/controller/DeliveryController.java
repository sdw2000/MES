package com.fine.controller;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.modle.DeliveryNotice;
import com.fine.service.DeliveryNoticeService;
import com.fine.Dao.CustomerMapper;
import com.fine.Dao.DeliveryNoticeItemMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.Customer;
import com.fine.modle.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Date;
import com.fine.modle.DeliveryNoticeItem;

@RestController
@RequestMapping("/delivery")
@PreAuthorize("hasAnyAuthority('admin', 'sales', 'finance')")
public class DeliveryController {
    
    @Autowired
    private DeliveryNoticeService deliveryNoticeService;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private DeliveryNoticeItemMapper deliveryNoticeItemMapper;
    
    /**
     * 分页查询发货通知
     */
    @GetMapping("/list")
    public ResponseResult list(
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

        // 默认按创建时间倒序
        if (sortKey.isEmpty()) {
            queryWrapper.orderByDesc("created_at");
            return ResponseResult.success(deliveryNoticeService.page(page, queryWrapper));
        }

        // 全表排序：先按筛选取全量，再排序后分页
        List<DeliveryNotice> all = deliveryNoticeService.list(queryWrapper);
        if (all == null) {
            all = new ArrayList<>();
        }

        // 批量查询明细，避免N+1
        List<Long> noticeIds = new ArrayList<>();
        for (DeliveryNotice notice : all) {
            if (notice != null && notice.getId() != null) {
                noticeIds.add(notice.getId());
            }
        }

        Map<Long, List<DeliveryNoticeItem>> itemMap = new HashMap<>();
        if (!noticeIds.isEmpty()) {
            List<DeliveryNoticeItem> allItems = deliveryNoticeItemMapper.selectList(
                    new QueryWrapper<DeliveryNoticeItem>().in("notice_id", noticeIds)
            );
            for (DeliveryNoticeItem item : allItems) {
                if (item == null || item.getNoticeId() == null) {
                    continue;
                }
                itemMap.computeIfAbsent(item.getNoticeId(), k -> new ArrayList<>()).add(item);
            }
        }
        for (DeliveryNotice notice : all) {
            if (notice != null && notice.getId() != null) {
                notice.setItems(itemMap.getOrDefault(notice.getId(), Collections.emptyList()));
            }
        }

        Comparator<DeliveryNotice> comparator = buildComparator(sortKey, sortDir);
        all.sort(comparator);

        int safePageNum = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : pageSize;
        long total = all.size();
        int fromIndex = Math.max(0, (safePageNum - 1) * safePageSize);
        int toIndex = Math.min(all.size(), fromIndex + safePageSize);
        List<DeliveryNotice> records = fromIndex >= toIndex ? new ArrayList<>() : all.subList(fromIndex, toIndex);

        page.setTotal(total);
        page.setCurrent(safePageNum);
        page.setSize(safePageSize);
        page.setRecords(records);

        return ResponseResult.success(page);
    }

    private Comparator<DeliveryNotice> buildComparator(String sortProp, String sortOrder) {
        boolean asc = "ascending".equalsIgnoreCase(sortOrder);
        Comparator<DeliveryNotice> comparator = (a, b) -> {
            Comparable av = sortValue(a, sortProp);
            Comparable bv = sortValue(b, sortProp);
            if (av == bv) return 0;
            if (av == null) return -1;
            if (bv == null) return 1;
            return av.compareTo(bv);
        };
        return asc ? comparator : comparator.reversed();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Comparable sortValue(DeliveryNotice notice, String sortProp) {
        if (notice == null) return "";
        String key = sortProp == null ? "" : sortProp.trim();
        if ("customer".equals(key)) {
            return safeText(notice.getCustomer());
        }
        if ("noticeNo".equals(key)) {
            return safeText(notice.getNoticeNo());
        }
        if ("orderNo".equals(key)) {
            return safeText(notice.getOrderNo());
        }
        if ("deliveryDate".equals(key)) {
            return notice.getDeliveryDate() == null ? 0L : notice.getDeliveryDate().toEpochDay();
        }
        if ("status".equals(key)) {
            return safeText(notice.getStatus());
        }
        if ("totalQty".equals(key)) {
            int sum = 0;
            List<DeliveryNoticeItem> items = notice.getItems();
            if (items != null) {
                for (DeliveryNoticeItem item : items) {
                    if (item != null && item.getQuantity() != null) {
                        sum += item.getQuantity();
                    }
                }
            }
            return sum;
        }
        if ("specText".equals(key)) {
            List<DeliveryNoticeItem> items = notice.getItems();
            if (items != null && !items.isEmpty()) {
                DeliveryNoticeItem first = items.get(0);
                if (first != null && first.getSpec() != null) {
                    return safeText(first.getSpec());
                }
            }
            return "";
        }
        return notice.getCreatedAt() == null ? 0L : notice.getCreatedAt().getTime();
    }

    private String safeText(String value) {
        return value == null ? "" : value.toUpperCase();
    }
    
    /**
     * 创建发货通知
     */
    @PostMapping("/create")
    public ResponseResult create(@RequestBody DeliveryNotice deliveryNotice) {
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
    public ResponseResult getDetail(@PathVariable Long id) {
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
            return ResponseResult.error(500, msg);
        } catch (Exception e) {
            return ResponseResult.error(500, "物流查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 确认发货 - 更新状态为已发货
     */
    @PostMapping("/confirm/{id}")
    public ResponseResult confirmShip(@PathVariable Long id) {
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
            
            // 更新状态为已发货
            notice.setStatus("已发货");
            notice.setUpdatedBy(getCurrentUsername(loginUser));
            notice.setUpdatedAt(new Date());
            boolean updated = deliveryNoticeService.updateById(notice);
            
            if (updated) {
                return ResponseResult.success("确认发货成功");
            } else {
                return ResponseResult.error(500, "确认发货失败");
            }
        } catch (Exception e) {
            return ResponseResult.error(500, "确认发货失败: " + e.getMessage());
        }
    }

    /**
     * 确认收货 - 更新状态为已收货
     */
    @PostMapping("/receive/{id}")
    public ResponseResult confirmReceive(@PathVariable Long id) {
        try {
            LoginUser loginUser = getLoginUser();
            DeliveryNotice notice = deliveryNoticeService.getById(id);
            if (notice == null) {
                return ResponseResult.error(404, "未找到该发货单");
            }
            if (!canAccessNotice(loginUser, notice)) {
                return ResponseResult.error(403, "无权限操作该发货单");
            }

            String status = notice.getStatus();
            boolean shipped = "已发货".equals(status) || "shipped".equalsIgnoreCase(status);
            if (!shipped) {
                return ResponseResult.error(400, "请先确认发货，再确认收货");
            }
            if ("已收货".equals(status) || "received".equalsIgnoreCase(status)) {
                return ResponseResult.error(400, "该发货单已确认收货");
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
     * 更新发货通知（包含明细）
     */
    @PostMapping("/update")
    public ResponseResult update(@RequestBody DeliveryNotice deliveryNotice) {
        try {
            if (deliveryNotice.getId() == null) {
                return ResponseResult.error(400, "缺少发货单ID");
            }

            DeliveryNotice existing = deliveryNoticeService.getById(deliveryNotice.getId());
            if (existing != null && !canAccessNotice(getLoginUser(), existing)) {
                return ResponseResult.error(403, "无权限操作该发货单");
            }

            deliveryNoticeService.updateDeliveryNotice(deliveryNotice);

            return ResponseResult.success("更新成功");
        } catch (Exception e) {
            return ResponseResult.error(500, "更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除发货通知（仅待发货状态可删除）
     */
    @DeleteMapping("/{id}")
    public ResponseResult delete(@PathVariable Long id) {
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
        List<String> allowedNames = customerMapper.selectCustomerNamesByOwner(uid);
        List<String> allowedCodes = customerMapper.selectCustomerCodesByOwner(uid);
        List<String> allowed = new java.util.ArrayList<>();
        if (allowedNames != null) allowed.addAll(allowedNames);
        if (allowedCodes != null) allowed.addAll(allowedCodes);
        return !allowed.isEmpty() && allowed.contains(notice.getCustomer());
    }
}
