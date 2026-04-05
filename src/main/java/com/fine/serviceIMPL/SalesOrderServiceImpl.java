package com.fine.serviceIMPL;

import java.math.BigDecimal;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.SalesOrderItemMapper;
import com.fine.Dao.SalesOrderSyncStateMapper;
import com.fine.Dao.production.SalesOrderMapper;
import com.fine.Dao.schedule.ManualScheduleMapper;
import com.fine.Dao.stock.TapeStockMapper;
import com.fine.Dao.rd.TapeSpecMapper;
import com.fine.Dao.DeliveryNoticeItemMapper;
import com.fine.Utils.ResponseResult;
import com.fine.modle.LoginUser;
import com.fine.modle.SalesOrder;
import com.fine.modle.SalesOrderItem;
import com.fine.modle.SalesOrderSyncState;
import com.fine.modle.stock.TapeStock;
import com.fine.Dao.CustomerMapper;
import com.fine.modle.Customer;
import com.fine.modle.rd.TapeSpec;
import com.fine.service.SalesOrderService;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.http.HttpServletResponse;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.IPage;

@Service
public class SalesOrderServiceImpl extends ServiceImpl<SalesOrderMapper, SalesOrder> implements SalesOrderService {

    private static final Set<String> LEGACY_ORDER_STATUSES = new HashSet<>(Arrays.asList(
        "pending", "processing", "completed", "cancelled", "canceled", "closed"
    ));
    private static final Set<String> LIFECYCLE_V2_STATUSES = new HashSet<>(Arrays.asList(
        "CREATED", "SCHEDULED", "IN_PRODUCTION", "PRODUCED", "SHIPPED_PARTIAL", "SHIPPED_FULL",
        "PAYMENT_PARTIAL", "PAID", "CLOSED", "CANCELLED"
    ));

    private static final Logger log = LoggerFactory.getLogger(SalesOrderServiceImpl.class);

    private final DataFormatter dataFormatter = new DataFormatter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private SalesOrderMapper salesOrderMapper;
    
    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;

    @Autowired
    private SalesOrderSyncStateMapper salesOrderSyncStateMapper;

    @Autowired
    private TapeSpecMapper tapeSpecMapper;

    @Autowired
    private DeliveryNoticeItemMapper deliveryNoticeItemMapper;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private ManualScheduleMapper manualScheduleMapper;

    @Autowired
    private TapeStockMapper tapeStockMapper;

    @Override
    public ResponseResult<?> getAllOrders(Integer pageNum, Integer pageSize, String orderNo, String customer, String completionStatus,
                                          Boolean showCompleted, String startDate, String endDate, String sortProp, String sortOrder) {
        try {
            LoginUser loginUser = getLoginUser();
            Long salesUserId = null;
            Long documentationPersonUserId = null;
            if (loginUser != null && !hasRole(loginUser, "admin")) {
                Long uid = getCurrentUserId(loginUser);
                if (uid == null) {
                    Page<SalesOrder> emptyPage = new Page<>(pageNum != null ? pageNum : 1, pageSize != null ? pageSize : 10);
                    return new ResponseResult<>(200, "success", emptyPage);
                }
                // 非管理员：仅能查看自己销售或自己跟单的订单
                salesUserId = uid;
                documentationPersonUserId = uid;
            }
            // 使用 MyBatis-Plus 分页
            Page<SalesOrder> page = new Page<>(pageNum != null ? pageNum : 1, pageSize != null ? pageSize : 10);

            String safeSortProp = normalizeSortProp(sortProp);
            String safeSortOrder = "ascending".equalsIgnoreCase(sortOrder) ? "ascending" : "descending";
            
            // 使用自定义SQL查询，支持客户名称搜索
            IPage<SalesOrder> pageResult = salesOrderMapper.selectOrdersWithCustomerSearch(
                page, 
                orderNo, 
                customer,  // 现在支持客户代码、客户名称、简称的模糊搜索
                completionStatus,
                showCompleted,
                startDate, 
                endDate,
                salesUserId,
                documentationPersonUserId,
                safeSortProp,
                safeSortOrder
            );

            if (pageResult.getRecords() != null) {
                for (SalesOrder record : pageResult.getRecords()) {
                    enrichOrderCustomerFields(record);
                    Integer shippedRolls = record.getShippedRolls();
                    Integer remainingRolls = record.getRemainingRolls();
                    if (shippedRolls == null || remainingRolls == null) {
                        Map<String, Object> rollProgress = salesOrderItemMapper.selectOrderRollProgress(record.getId());
                        if (rollProgress != null) {
                            record.setShippedRolls(getIntFromObject(rollProgress.get("completed_rolls")));
                            record.setRemainingRolls(getIntFromObject(rollProgress.get("remaining_rolls")));
                        } else {
                            record.setShippedRolls(0);
                            record.setRemainingRolls(0);
                        }
                    }
                }
            }
            
            log.debug("查询订单完成, customerKeyword={}, resultCount={}", customer, pageResult.getRecords().size());
            
            return new ResponseResult<>(200, "success", pageResult);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "Failed to get orders: " + e.getMessage());
        }
    }

    private String normalizeSortProp(String sortProp) {
        if (sortProp == null) {
            return null;
        }
        String key = sortProp.trim();
        if ("customerDisplay".equals(key) || "orderNo".equals(key) || "totalAmount".equals(key)
                || "totalArea".equals(key) || "orderDate".equals(key) || "deliveryDate".equals(key)
                || "completionStatus".equals(key)) {
            return key;
        }
        return null;
    }

    private void enrichOrderCustomerFields(SalesOrder order) {
        if (order == null) {
            return;
        }

        Customer customerInfo = resolveCustomer(order);
        if (customerInfo != null) {
            applyCustomerInfo(order, customerInfo);
            if (order.getSalesUserId() == null) {
                order.setSalesUserId(customerInfo.getSalesUserId());
            }
            if (order.getDocumentationPersonUserId() == null) {
                order.setDocumentationPersonUserId(customerInfo.getDocumentationPersonUserId());
            }
            return;
        }

        String rawCustomer = firstNonBlank(
                order.getCustomerCode(),
                order.getCustomer(),
                order.getCustomerName(),
                order.getShortName(),
                order.getCustomerDisplay()
        );
        if (rawCustomer == null) {
            return;
        }

        if (trimToNull(order.getCustomerCode()) == null) {
            order.setCustomerCode(rawCustomer);
        }
        if (trimToNull(order.getCustomerName()) == null) {
            order.setCustomerName(rawCustomer);
        }
        if (trimToNull(order.getCustomerDisplay()) == null) {
            order.setCustomerDisplay(rawCustomer);
        }
    }

    private void normalizeIncomingOrderCustomer(SalesOrder order) {
        if (order == null) {
            return;
        }

        Customer customerInfo = resolveCustomer(order);
        if (customerInfo != null) {
            order.setCustomer(customerInfo.getCustomerCode());
            applyCustomerInfo(order, customerInfo);
            if (order.getSalesUserId() == null) {
                order.setSalesUserId(customerInfo.getSalesUserId());
            }
            if (order.getDocumentationPersonUserId() == null) {
                order.setDocumentationPersonUserId(customerInfo.getDocumentationPersonUserId());
            }
            return;
        }

        String rawCustomer = firstNonBlank(
                order.getCustomerCode(),
                order.getCustomer(),
                order.getCustomerName(),
                order.getShortName(),
                order.getCustomerDisplay()
        );
        if (rawCustomer != null) {
            String normalizedCustomer = firstNonBlank(normalizeCustomerToken(rawCustomer), rawCustomer);
            order.setCustomer(normalizedCustomer);
            if (trimToNull(order.getCustomerCode()) == null) {
                order.setCustomerCode(normalizedCustomer);
            }
        }
    }

    private Customer resolveCustomer(SalesOrder order) {
        if (order == null) {
            return null;
        }

        List<String> candidates = Arrays.asList(
                order.getCustomerCode(),
                order.getCustomer(),
                order.getCustomerName(),
                order.getShortName(),
                order.getCustomerDisplay()
        );
        for (String candidate : candidates) {
            Customer customer = resolveCustomer(candidate);
            if (customer != null) {
                return customer;
            }
        }
        return null;
    }

    private Customer resolveCustomer(String customerValue) {
        String normalized = trimToNull(customerValue);
        if (normalized == null) {
            return null;
        }

        Customer customer = customerMapper.selectByCustomerCode(normalized);
        if (customer != null) {
            return customer;
        }

        customer = customerMapper.selectByShortNameOrCustomerName(normalized, normalized);
        if (customer != null) {
            return customer;
        }

        String cleaned = normalizeCustomerToken(normalized);
        if (cleaned != null && !cleaned.equals(normalized)) {
            customer = customerMapper.selectByCustomerCode(cleaned);
            if (customer != null) {
                return customer;
            }
            customer = customerMapper.selectByShortNameOrCustomerName(cleaned, cleaned);
            if (customer != null) {
                return customer;
            }
        }

        return null;
    }

    private void applyCustomerInfo(SalesOrder order, Customer customerInfo) {
        if (order == null || customerInfo == null) {
            return;
        }

        order.setCustomerId(customerInfo.getId());
        order.setCustomerCode(trimToNull(customerInfo.getCustomerCode()));
        order.setCustomerName(firstNonBlank(customerInfo.getCustomerName(), customerInfo.getCustomerCode()));
        order.setShortName(trimToNull(customerInfo.getShortName()));
        order.setCustomerDisplay(firstNonBlank(customerInfo.getShortName(), customerInfo.getCustomerName(), customerInfo.getCustomerCode()));

        if (trimToNull(order.getCustomer()) == null) {
            order.setCustomer(customerInfo.getCustomerCode());
        }
    }

    private String resolveCustomerCode(String customerValue) {
        Customer customer = resolveCustomer(customerValue);
        if (customer != null && trimToNull(customer.getCustomerCode()) != null) {
            return customer.getCustomerCode().trim();
        }
        String raw = trimToNull(customerValue);
        return firstNonBlank(normalizeCustomerToken(raw), raw);
    }

    /**
     * 客户代码清洗：移除空白与特殊符号，仅保留中英文、数字、下划线、短横线。
     * 例如：RP01✭ -> RP01
     */
    private String normalizeCustomerToken(String customerValue) {
        String normalized = trimToNull(customerValue);
        if (normalized == null) {
            return null;
        }
        String cleaned = normalized.replaceAll("[^0-9A-Za-z\\u4e00-\\u9fa5_-]", "").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String normalizeMaterialCodeToken(String materialCode) {
        String normalized = trimToNull(materialCode);
        if (normalized == null) {
            return null;
        }
        String cleaned = normalized
                .replace("\u00A0", "")
                .replace("\u3000", "")
                .replaceAll("\\s+", "");
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value
                .replace('\u00A0', ' ')
                .replace('\u3000', ' ')
                .trim();
        return normalized.isEmpty() ? null : normalized;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> createOrder(SalesOrder salesOrder) {
        try {
            normalizeIncomingOrderCustomer(salesOrder);

            // 获取当前登录用户
            String username = getCurrentUsername();
            
            // 生成订单号
            if (salesOrder.getOrderNo() == null || salesOrder.getOrderNo().isEmpty()) {
                salesOrder.setOrderNo(buildOrderNo(salesOrder.getCustomer(), salesOrder.getOrderDate()));
            }


            // 若未传销售/跟单，按客户回填
            if ((salesOrder.getSalesUserId() == null || salesOrder.getDocumentationPersonUserId() == null)
                    && salesOrder.getCustomer() != null && !salesOrder.getCustomer().isEmpty()) {
                Customer customer = customerMapper.selectByCustomerCode(salesOrder.getCustomer());
                if (customer != null) {
                    if (salesOrder.getSalesUserId() == null) {
                        salesOrder.setSalesUserId(customer.getSalesUserId());
                    }
                    if (salesOrder.getDocumentationPersonUserId() == null) {
                        salesOrder.setDocumentationPersonUserId(customer.getDocumentationPersonUserId());
                    }
                }
            }
            
            // 新进订单状态统一按明细进度推导（取消/关闭除外）
            normalizeOrderStatusByItems(salesOrder);
            if (salesOrder.getStatus() == null || salesOrder.getStatus().trim().isEmpty()) {
                salesOrder.setStatus("CREATED");
            }
            
            // 设置创建信息
            salesOrder.setCreatedBy(username);
            salesOrder.setUpdatedBy(username);
            salesOrder.setCreatedAt(new Date());
            salesOrder.setUpdatedAt(new Date());
            salesOrder.setIsDeleted(0);
            
            // 计算总金额和总面积
            calculateOrderTotals(salesOrder);
            
            // 预先填充规格信息，确保保存时包含颜色代码
            enrichItemsWithSpecInfo(salesOrder.getItems());

            // 保存订单主表
            int result = salesOrderMapper.insert(salesOrder);
            
                log.info("开始保存订单明细, orderId={}, itemCount={}", salesOrder.getId(),
                    salesOrder.getItems() != null ? salesOrder.getItems().size() : 0);
            
            if (result > 0 && salesOrder.getItems() != null && !salesOrder.getItems().isEmpty()) {
                // 保存订单明细
                int itemIndex = 0;
                for (SalesOrderItem item : salesOrder.getItems()) {
                    itemIndex++;
                    item.setOrderId(salesOrder.getId());
                    item.setCreatedBy(username);
                    item.setUpdatedBy(username);
                    item.setCreatedAt(new Date());
                    item.setUpdatedAt(new Date());
                    item.setIsDeleted(0);
                    
                    // 计算平方米数和金额
                    calculateItemAmounts(item);
                        prepareItemForPersistence(item);
                    
                    salesOrderItemMapper.insert(item);
                    log.debug("保存明细 index={}, materialCode={}, materialName={}", itemIndex, item.getMaterialCode(), item.getMaterialName());
                }
            }

                    populateItemDisplayFields(salesOrder.getItems());
            
            log.info("订单创建成功, orderNo={}, customer={}, totalAmount={}", salesOrder.getOrderNo(),
                    salesOrder.getCustomer(), salesOrder.getTotalAmount());
            
            Map<String, Object> data = new HashMap<>();
            data.put("data", salesOrder);
            
            return new ResponseResult<>(200, "创建订单成功", data);
        } catch (Exception e) {
            log.error("创建订单失败", e);
            return new ResponseResult<>(500, "创建订单失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> updateOrder(SalesOrder salesOrder) {
        try {
            LoginUser loginUser = getLoginUser();
            normalizeIncomingOrderCustomer(salesOrder);
            // 获取当前登录用户
            String username = getCurrentUsername();

            if (salesOrder == null) {
                return new ResponseResult<>(400, "请求参数不能为空");
            }
            if (trimToNull(salesOrder.getOrderNo()) == null) {
                return new ResponseResult<>(400, "订单编号不能为空");
            }
            salesOrder.setOrderNo(trimToNull(salesOrder.getOrderNo()));
            
            log.info("开始更新订单, orderNo={}, itemCount={}", salesOrder.getOrderNo(),
                    salesOrder.getItems() != null ? salesOrder.getItems().size() : 0);
            if (salesOrder.getItems() != null) {
                for (int i = 0; i < salesOrder.getItems().size(); i++) {
                    SalesOrderItem item = salesOrder.getItems().get(i);
                    log.debug("更新明细预览 index={}, id={}, materialCode={}", i, item.getId(), item.getMaterialCode());
                }
            }
            
            // 查询原订单（优先按ID，兼容编辑时修改订单号）
            SalesOrder existingOrder = null;
            if (salesOrder.getId() != null) {
                LambdaQueryWrapper<SalesOrder> idWrapper = new LambdaQueryWrapper<>();
                idWrapper.eq(SalesOrder::getId, salesOrder.getId())
                        .eq(SalesOrder::getIsDeleted, 0);
                existingOrder = salesOrderMapper.selectOne(idWrapper);
            }
            if (existingOrder == null) {
                LambdaQueryWrapper<SalesOrder> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(SalesOrder::getOrderNo, salesOrder.getOrderNo())
                        .eq(SalesOrder::getIsDeleted, 0);
                existingOrder = salesOrderMapper.selectOne(queryWrapper);
            }
            
            if (existingOrder == null) {
                return new ResponseResult<>(404, "订单不存在");
            }
            if (!canAccessOrder(loginUser, existingOrder)) {
                return new ResponseResult<>(403, "无权限操作该订单");
            }

            String incomingStatus = trimToNull(salesOrder.getStatus());
            String existingStatus = trimToNull(existingOrder.getStatus());
            if (incomingStatus != null && !incomingStatus.equalsIgnoreCase(existingStatus == null ? "" : existingStatus)) {
                String normalizedIncoming = incomingStatus.toLowerCase(Locale.ROOT);
                boolean isCancelAction = "cancelled".equals(normalizedIncoming) || "canceled".equals(normalizedIncoming);
                if (!isCancelAction) {
                    return new ResponseResult<>(400, "订单状态不允许手工修改，仅支持取消订单并填写取消原因");
                }
                String cancelReason = trimToNull(salesOrder.getCancelReason());
                if (cancelReason == null) {
                    return new ResponseResult<>(400, "取消订单必须填写取消原因");
                }
                salesOrder.setRemark(appendCancelReason(existingOrder.getRemark(), cancelReason, username));
            }

            // 订单号唯一性校验（允许本订单保持原编号）
            LambdaQueryWrapper<SalesOrder> duplicatedOrderNoWrapper = new LambdaQueryWrapper<>();
            duplicatedOrderNoWrapper.eq(SalesOrder::getOrderNo, salesOrder.getOrderNo())
                    .eq(SalesOrder::getIsDeleted, 0)
                    .ne(SalesOrder::getId, existingOrder.getId());
            SalesOrder duplicatedOrder = salesOrderMapper.selectOne(duplicatedOrderNoWrapper);
            if (duplicatedOrder != null) {
                return new ResponseResult<>(409, "订单编号已存在，请更换后再保存");
            }
            
            // 更新订单信息，保留原有的创建信息
            salesOrder.setId(existingOrder.getId());
            salesOrder.setCreatedBy(existingOrder.getCreatedBy());
            salesOrder.setCreatedAt(existingOrder.getCreatedAt());
            salesOrder.setUpdatedBy(username);
            salesOrder.setUpdatedAt(new Date());
            salesOrder.setIsDeleted(0); // 确保不会被误删

            
            // 计算总金额和总面积
            calculateOrderTotals(salesOrder);

            // 预先填充规格信息
            enrichItemsWithSpecInfo(salesOrder.getItems());

            // 状态统一按明细进度推导（取消/关闭除外）
            normalizeOrderStatusByItems(salesOrder);
            
            // 更新订单主表
            salesOrderMapper.updateById(salesOrder);
            
            // 获取原有明细列表（只查未删除的）
            LambdaQueryWrapper<SalesOrderItem> itemWrapper = new LambdaQueryWrapper<>();
            itemWrapper.eq(SalesOrderItem::getOrderId, existingOrder.getId())
                      .eq(SalesOrderItem::getIsDeleted, 0);
            List<SalesOrderItem> oldItems = salesOrderItemMapper.selectList(itemWrapper);
            
            log.debug("数据库中旧明细数量={}", oldItems.size());
            for (SalesOrderItem oldItem : oldItems) {
                log.debug("旧明细 id={}, materialCode={}", oldItem.getId(), oldItem.getMaterialCode());
            }
            
            // 收集前端传来的明细ID
            Set<Long> newItemIds = new HashSet<>();
            if (salesOrder.getItems() != null) {
                for (SalesOrderItem item : salesOrder.getItems()) {
                    if (item.getId() != null) {
                        newItemIds.add(item.getId());
                    }
                }
            }
            
            log.debug("前端传来的明细ID集合={}", newItemIds);

            List<Long> removedItemIds = new ArrayList<>();
            
            // 逻辑删除前端没有传来的旧明细（说明被删除了）
            for (SalesOrderItem oldItem : oldItems) {
                if (!newItemIds.contains(oldItem.getId())) {
                    removedItemIds.add(oldItem.getId());
                    log.debug("删除旧明细 id={}", oldItem.getId());
                    oldItem.setIsDeleted(1);
                    salesOrderItemMapper.updateById(oldItem);
                }
            }

            // 若订单状态改为取消/关闭，联动取消全部明细排程；否则仅取消被删除明细
            String status = salesOrder.getStatus() == null ? "" : salesOrder.getStatus().toLowerCase();
            if ("cancelled".equals(status) || "canceled".equals(status) || "closed".equals(status)) {
                List<Long> allItemIds = new ArrayList<>();
                for (SalesOrderItem oldItem : oldItems) {
                    allItemIds.add(oldItem.getId());
                }
                cancelManualSchedulesForOrderDetails(allItemIds, username, "订单取消联动撤销排程");
            } else if (!removedItemIds.isEmpty()) {
                cancelManualSchedulesForOrderDetails(removedItemIds, username, "订单明细删除联动撤销排程");
            }
            
            // 处理明细：有ID就更新，无ID就插入
            if (salesOrder.getItems() != null && !salesOrder.getItems().isEmpty()) {
                for (SalesOrderItem item : salesOrder.getItems()) {
                    item.setOrderId(salesOrder.getId());
                    item.setUpdatedBy(username);
                    item.setUpdatedAt(new Date());
                    item.setIsDeleted(0);
                    
                    // 计算平方米数和金额
                    calculateItemAmounts(item);
                    prepareItemForPersistence(item);
                    
                    if (item.getId() != null && item.getId() > 0) {
                        // 更新现有明细
                        log.debug("更新明细 id={}, materialCode={}", item.getId(), item.getMaterialCode());
                        salesOrderItemMapper.updateById(item);
                    } else {
                        // 新增明细
                        log.debug("新增明细 materialCode={}", item.getMaterialCode());
                        item.setCreatedBy(username);
                        item.setCreatedAt(new Date());
                        salesOrderItemMapper.insert(item);
                    }
                }
            }

            // 以数据库当前有效明细为准再归一一次订单状态，避免删除明细后状态滞后
            refreshOrderStatusFromDb(salesOrder.getId(), username);

            populateItemDisplayFields(salesOrder.getItems());
            
            log.info("订单更新成功, orderNo={}", salesOrder.getOrderNo());
            
            Map<String, Object> data = new HashMap<>();
            data.put("data", salesOrder);
            
            return new ResponseResult<>(200, "更新订单成功", data);
        } catch (Exception e) {
            log.error("更新订单失败", e);
            return new ResponseResult<>(500, "更新订单失败: " + e.getMessage());
        }
    }

    private void enrichItemsWithSpecInfo(List<SalesOrderItem> items) {
        if (items == null || items.isEmpty()) return;
        
        for (SalesOrderItem item : items) {
            if (item.getMaterialCode() != null) {
                // 尝试从规格表中获取颜色代码
                TapeSpec spec = tapeSpecMapper.selectByMaterialCode(item.getMaterialCode());
                
                if (spec != null) {
                    if (item.getMaterialName() == null || item.getMaterialName().trim().isEmpty()) item.setMaterialName(spec.getProductName());
                    item.setColorCode(spec.getColorCode());
                    // 如果明细中没有规格信息，使用规格表中的默认值
                    if (item.getThickness() == null) item.setThickness(spec.getTotalThickness());
                }
            }
        }
    }
    
    private void calculateOrderTotals(SalesOrder salesOrder) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalArea = BigDecimal.ZERO;
        
        if (salesOrder.getItems() != null) {
            for (SalesOrderItem item : salesOrder.getItems()) {
                calculateItemAmounts(item);
                if (item.getAmount() != null) {
                    totalAmount = totalAmount.add(item.getAmount());
                }
                if (item.getSqm() != null) {
                    totalArea = totalArea.add(item.getSqm());
                }
            }
        }
        
        salesOrder.setTotalAmount(totalAmount);
        salesOrder.setTotalArea(totalArea);
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return ((LoginUser) authentication.getPrincipal()).getUsername();
        }
        return "system";
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

    private boolean canAccessOrder(LoginUser loginUser, SalesOrder order) {
        if (order == null) return true;
        if (loginUser == null) return false;
        if (hasRole(loginUser, "admin")) return true;
        if (hasRole(loginUser, "production") || hasRole(loginUser, "packaging") || hasRole(loginUser, "packing")) {
            return true;
        }
        Long uid = getCurrentUserId(loginUser);
        if (uid == null) return false;

        // 优先按订单归属人判断
        if (uid.equals(order.getSalesUserId()) || uid.equals(order.getDocumentationPersonUserId())) {
            return true;
        }

        // 回退按客户归属判断（兼容历史订单未回填 sales/documentation_person 的场景）
        Customer customerInfo = resolveCustomer(order);
        if (customerInfo != null) {
            if (uid.equals(customerInfo.getSalesUserId()) || uid.equals(customerInfo.getDocumentationPersonUserId())) {
                return true;
            }
        }

        // 再回退到“客户编码/名称白名单”匹配，避免脏数据导致误拒绝
        List<String> allowedCodes = customerMapper.selectCustomerCodesByOwner(uid);
        List<String> allowedNames = customerMapper.selectCustomerNamesByOwner(uid);
        Set<String> allowed = new HashSet<>();
        if (allowedCodes != null) {
            for (String code : allowedCodes) {
                String normalized = trimToNull(code);
                if (normalized != null) allowed.add(normalized);
            }
        }
        if (allowedNames != null) {
            for (String name : allowedNames) {
                String normalized = trimToNull(name);
                if (normalized != null) allowed.add(normalized);
            }
        }
        if (allowed.isEmpty()) {
            return false;
        }

        String[] candidates = new String[] {
                trimToNull(order.getCustomerCode()),
                trimToNull(order.getCustomer()),
                trimToNull(order.getCustomerName()),
                trimToNull(order.getShortName()),
                trimToNull(order.getCustomerDisplay())
        };
        for (String candidate : candidates) {
            if (candidate != null && allowed.contains(candidate)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public ResponseResult<?> generateOrderNo(String customerCode, java.time.LocalDate orderDate) {
        LocalDate date = orderDate != null ? orderDate : LocalDate.now(ZoneId.of("Asia/Shanghai"));
        String orderNo = buildOrderNo(resolveCustomerCode(customerCode), date);
        Map<String, Object> data = new HashMap<>();
        data.put("orderNo", orderNo);
        return new ResponseResult<>(200, "success", data);
    }

    @Override
    public ResponseResult<?> getCustomerMaterialHistorySpecs(String customerCode, String materialCode) {
        try {
            String normalizedCustomerCode = resolveCustomerCode(customerCode);
            String normalizedMaterialCode = normalizeMaterialCodeToken(materialCode);
            if (normalizedCustomerCode == null || normalizedMaterialCode == null) {
                return new ResponseResult<>(200, "success", java.util.Collections.emptyList());
            }

            List<Map<String, Object>> specs = salesOrderItemMapper.selectCustomerMaterialHistorySpecs(
                    normalizedCustomerCode, normalizedMaterialCode);
            if (specs == null || specs.isEmpty()) {
                specs = salesOrderItemMapper.selectCustomerMaterialHistorySpecsFromQuotationBaseline(
                        normalizedCustomerCode, normalizedMaterialCode);
            }
            if (specs == null) {
                specs = java.util.Collections.emptyList();
            }
            return new ResponseResult<>(200, "success", specs);
        } catch (Exception e) {
            log.error("查询历史下单规格失败, customerCode={}, materialCode={}", customerCode, materialCode, e);
            return new ResponseResult<>(500, "查询历史下单规格失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseResult<?> getCustomerOrderRemarkHistory(String customerCode, Integer limit) {
        try {
            String normalizedCustomerCode = resolveCustomerCode(customerCode);
            if (normalizedCustomerCode == null) {
                return new ResponseResult<>(200, "success", Collections.emptyList());
            }

            LoginUser loginUser = getLoginUser();
            if (!hasRole(loginUser, "admin")) {
                Long uid = getCurrentUserId(loginUser);
                if (uid == null) {
                    return new ResponseResult<>(403, "无权限访问该客户数据");
                }
                List<String> allowedCodes = customerMapper.selectCustomerCodesByOwner(uid);
                Set<String> allowed = new HashSet<>();
                if (allowedCodes != null) {
                    for (String code : allowedCodes) {
                        String normalized = trimToNull(code);
                        if (normalized != null) {
                            allowed.add(normalized);
                        }
                    }
                }
                if (!allowed.contains(normalizedCustomerCode)) {
                    return new ResponseResult<>(403, "无权限访问该客户数据");
                }
            }

            int safeLimit = (limit == null || limit <= 0) ? 20 : Math.min(limit, 100);
            List<Map<String, Object>> rows = salesOrderMapper.selectCustomerOrderRemarkHistory(normalizedCustomerCode, safeLimit);
            if (rows == null) {
                rows = Collections.emptyList();
            }
            return new ResponseResult<>(200, "success", rows);
        } catch (Exception e) {
            log.error("查询客户历史订单备注失败, customerCode={}", customerCode, e);
            return new ResponseResult<>(500, "查询客户历史订单备注失败: " + e.getMessage());
        }
    }

    private String buildOrderNo(String customerCode, LocalDate orderDate) {
        LocalDate date = orderDate != null ? orderDate : LocalDate.now(ZoneId.of("Asia/Shanghai"));
        String dateStr = date.format(DateTimeFormatter.ofPattern("yyMMdd"));

        Customer customer = null;
        String normalizedCustomerCode = resolveCustomerCode(customerCode);
        if (normalizedCustomerCode != null && !normalizedCustomerCode.isEmpty()) {
            customer = customerMapper.selectByCustomerCode(normalizedCustomerCode);
        }
        String prefix = (customer != null && customer.getOrderNoPrefix() != null && !customer.getOrderNoPrefix().isEmpty())
                ? customer.getOrderNoPrefix()
                : "DH";
        String rawSuffix = (customer != null && customer.getOrderNoSuffix() != null) ? customer.getOrderNoSuffix().trim() : "";
        String normalizedSuffix = rawSuffix.startsWith("-") ? rawSuffix.substring(1) : rawSuffix;

        // 规则：前缀 + 短日期(yyMMdd) + '-' + 后缀 + 序号(01起)
        // 自增分组：同日期 + 同后缀（忽略前缀）
        String delimiter = normalizedSuffix.isEmpty() ? "" : "-";
        String base = prefix + dateStr + delimiter + normalizedSuffix;

        String lastNo = normalizedSuffix.isEmpty()
                ? salesOrderMapper.selectLastOrderNoByBase(base)
                : salesOrderMapper.selectLastOrderNoByDateAndSuffix(dateStr, normalizedSuffix);
        int nextSeq = 1;
        if (lastNo != null) {
            String tail = null;
            if (normalizedSuffix.isEmpty() && lastNo.startsWith(base)) {
                tail = lastNo.substring(base.length());
            } else if (!normalizedSuffix.isEmpty()) {
                String marker = dateStr + "-" + normalizedSuffix;
                Matcher matcher = Pattern.compile(".*" + Pattern.quote(marker) + "(\\d+)$").matcher(lastNo);
                if (matcher.matches()) {
                    tail = matcher.group(1);
                }
            }
            if (tail != null && tail.matches("\\d+")) {
                nextSeq = Integer.parseInt(tail) + 1;
            }
        }
        String seq = String.format("%02d", nextSeq);
        return base + seq;
    }

    

    private void calculateItemAmounts(SalesOrderItem item) {
        if (item == null) {
            return;
        }
        item.setUnit(normalizePricingUnit(item.getUnit()));
        item.setWidth(normalizeWidthOneDecimal(item.getWidth()));
        if (item.getWidth() != null && item.getLength() != null && item.getRolls() != null) {
            // length已经是米，width是毫米，需要转换为米
            BigDecimal widthM = item.getWidth().divide(new BigDecimal(1000), 6, BigDecimal.ROUND_HALF_UP);
            BigDecimal lengthM = item.getLength(); // 已经是米，不需要转换
            
            BigDecimal area = widthM.multiply(lengthM).multiply(new BigDecimal(item.getRolls()));
            item.setSqm(area.setScale(2, BigDecimal.ROUND_HALF_UP));

            if (item.getUnitPrice() != null) {
                BigDecimal chargeQty;
                String unit = item.getUnit();
                if ("卷".equals(unit)) {
                    chargeQty = new BigDecimal(item.getRolls());
                } else if ("m".equals(unit)) {
                    chargeQty = lengthM.multiply(new BigDecimal(item.getRolls()));
                } else {
                    chargeQty = area;
                }
                item.setAmount(chargeQty.multiply(item.getUnitPrice()).setScale(2, BigDecimal.ROUND_HALF_UP));
            }
        }
    }

    private String normalizePricingUnit(String unit) {
        if (unit == null) {
            return "㎡";
        }
        String normalized = unit.trim();
        if (normalized.isEmpty()) {
            return "㎡";
        }
        if ("米".equalsIgnoreCase(normalized) || "m".equalsIgnoreCase(normalized)) {
            return "m";
        }
        if ("平方米".equalsIgnoreCase(normalized)
                || "m²".equalsIgnoreCase(normalized)
                || "m2".equalsIgnoreCase(normalized)
                || "㎡".equals(normalized)) {
            return "㎡";
        }
        if ("卷".equals(normalized)) {
            return "卷";
        }
        return "㎡";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> deleteOrder(String orderNo) {
        try {
            LoginUser loginUser = getLoginUser();
            // 查询订单
            LambdaQueryWrapper<SalesOrder> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(SalesOrder::getOrderNo, orderNo);
            SalesOrder order = salesOrderMapper.selectOne(queryWrapper);
            
            if (order == null) {
                return new ResponseResult<>(404, "订单不存在或已被删除: " + orderNo);
            }
            if (!canAccessOrder(loginUser, order)) {
                return new ResponseResult<>(403, "无权限操作该订单");
            }

            // 1. 逻辑删除关联明细
            // MyBatis-Plus 逻辑删除: Mapper.delete() 会自动转换为 UPDATE is_deleted=1 ...
            LambdaQueryWrapper<SalesOrderItem> itemWrapper = new LambdaQueryWrapper<>();
            itemWrapper.eq(SalesOrderItem::getOrderId, order.getId());
            List<SalesOrderItem> oldItems = salesOrderItemMapper.selectList(itemWrapper);
            List<Long> orderDetailIds = new ArrayList<>();
            for (SalesOrderItem item : oldItems) {
                orderDetailIds.add(item.getId());
            }
            cancelManualSchedulesForOrderDetails(orderDetailIds, getCurrentUsername(), "订单删除联动撤销排程");
            salesOrderItemMapper.delete(itemWrapper);
            
            // 2. 逻辑删除主订单
            salesOrderMapper.deleteById(order.getId());
            
            log.info("删除订单成功, orderNo={}", orderNo);
            
            return new ResponseResult<>(200, "删除成功");
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "删除失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> cancelOrder(String orderNo, String cancelReason) {
        try {
            String normalizedOrderNo = trimToNull(orderNo);
            String normalizedReason = trimToNull(cancelReason);
            if (normalizedOrderNo == null) {
                return new ResponseResult<>(400, "订单编号不能为空");
            }
            if (normalizedReason == null) {
                return new ResponseResult<>(400, "取消原因不能为空");
            }

            LoginUser loginUser = getLoginUser();
            LambdaQueryWrapper<SalesOrder> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(SalesOrder::getOrderNo, normalizedOrderNo)
                    .eq(SalesOrder::getIsDeleted, 0);
            SalesOrder order = salesOrderMapper.selectOne(queryWrapper);

            if (order == null) {
                return new ResponseResult<>(404, "订单不存在或已被删除: " + normalizedOrderNo);
            }
            if (!canAccessOrder(loginUser, order)) {
                return new ResponseResult<>(403, "无权限操作该订单");
            }

            String oldStatus = trimToNull(order.getStatus());
            String oldStatusLower = oldStatus == null ? "" : oldStatus.toLowerCase(Locale.ROOT);
            if ("cancelled".equals(oldStatusLower) || "canceled".equals(oldStatusLower)) {
                return new ResponseResult<>(400, "订单已取消，无需重复操作");
            }
            if ("closed".equals(oldStatusLower) || "closed".equals(oldStatus)) {
                return new ResponseResult<>(400, "订单已关闭，不能取消");
            }

            LambdaQueryWrapper<SalesOrderItem> itemWrapper = new LambdaQueryWrapper<>();
            itemWrapper.eq(SalesOrderItem::getOrderId, order.getId())
                    .eq(SalesOrderItem::getIsDeleted, 0);
            List<SalesOrderItem> oldItems = salesOrderItemMapper.selectList(itemWrapper);
            List<Long> orderDetailIds = new ArrayList<>();
            for (SalesOrderItem item : oldItems) {
                if (item != null && item.getId() != null) {
                    orderDetailIds.add(item.getId());
                }
            }
            cancelManualSchedulesForOrderDetails(orderDetailIds, getCurrentUsername(), "订单取消联动撤销排程");

            String operator = getCurrentUsername();
            SalesOrder patch = new SalesOrder();
            patch.setId(order.getId());
            patch.setStatus(resolveCancelledStatusByOrder(order));
            patch.setRemark(appendCancelReason(order.getRemark(), normalizedReason, operator));
            patch.setUpdatedBy(operator);
            patch.setUpdatedAt(new Date());
            salesOrderMapper.updateById(patch);

            return new ResponseResult<>(200, "取消订单成功");
        } catch (Exception e) {
            log.error("取消订单失败, orderNo={}", orderNo, e);
            return new ResponseResult<>(500, "取消订单失败: " + e.getMessage());
        }
    }

    @Override
    public ResponseResult<?> getOrderByOrderNo(String orderNo) {
        LambdaQueryWrapper<SalesOrder> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SalesOrder::getOrderNo, orderNo)
                .eq(SalesOrder::getIsDeleted, 0);
        SalesOrder order = salesOrderMapper.selectOne(queryWrapper);

        if (order == null) {
            return new ResponseResult<>(404, "订单不存在或已被删除");
        }

        if (!canAccessOrder(getLoginUser(), order)) {
            return new ResponseResult<>(403, "无权限访问该订单");
        }
        
        if (order != null) {
            enrichOrderCustomerFields(order);
            // 获取订单详情时，加载明细数据
            LambdaQueryWrapper<SalesOrderItem> itemWrapper = new LambdaQueryWrapper<>();
            itemWrapper.eq(SalesOrderItem::getOrderId, order.getId())
                      .eq(SalesOrderItem::getIsDeleted, 0);
            List<SalesOrderItem> items = salesOrderItemMapper.selectList(itemWrapper);
            
            // 填充颜色代码等信息
            enrichItemsWithSpecInfo(items);
            populateItemDisplayFields(items);
            
            // 填充已发货数量
            for (SalesOrderItem item : items) {
                Integer shipped = deliveryNoticeItemMapper.getShippedQuantityByOrderItemId(item.getId());
                item.setShippedRolls(shipped == null ? 0 : Math.max(shipped, 0));
            }

            order.setItems(items);
        }
        
        return new ResponseResult<>(200, "success", order);
    }

    @Override
    public ResponseResult<?> searchOrders(String keyword, String status, String customer) {
        try {
            LambdaQueryWrapper<SalesOrder> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(SalesOrder::getIsDeleted, 0);

            String statusFilter = status == null ? "" : status.trim();
            String statusFilterLower = statusFilter.toLowerCase(Locale.ROOT);
            boolean modeAll = "all".equals(statusFilterLower);
            boolean modeCompleted = "completed".equals(statusFilterLower)
                || "complete".equals(statusFilterLower)
                || "done".equals(statusFilterLower)
                || "finished".equals(statusFilterLower);
            boolean modePending = statusFilter.isEmpty()
                || "pending".equals(statusFilterLower)
                || "unshipped".equals(statusFilterLower);

            LoginUser loginUser = getLoginUser();
            if (loginUser != null && !hasRole(loginUser, "admin") && !hasRole(loginUser, "warehouse")) {
                Long uid = getCurrentUserId(loginUser);
                if (uid == null) {
                    return new ResponseResult<>(200, "success", Collections.emptyList());
                }
                List<String> allowedNames = customerMapper.selectCustomerNamesByOwner(uid);
                List<String> allowedCodes = customerMapper.selectCustomerCodesByOwner(uid);
                List<String> allowed = new ArrayList<>();
                if (allowedNames != null) allowed.addAll(allowedNames);
                if (allowedCodes != null) allowed.addAll(allowedCodes);
                if (allowed.isEmpty()) {
                    return new ResponseResult<>(200, "success", Collections.emptyList());
                }
                queryWrapper.in(SalesOrder::getCustomer, allowed);
            }
            
            // 根据关键词搜索订单号或客户名
            if (keyword != null && !keyword.isEmpty()) {
                queryWrapper.and(wrapper -> 
                    wrapper.like(SalesOrder::getOrderNo, keyword)
                          .or()
                          .like(SalesOrder::getCustomer, keyword)
                );
            }

            if (customer != null && !customer.trim().isEmpty()) {
                String customerKeyword = customer.trim();
                LambdaQueryWrapper<Customer> customerWrapper = new LambdaQueryWrapper<>();
                customerWrapper.eq(Customer::getIsDeleted, 0)
                        .and(w -> w.like(Customer::getCustomerName, customerKeyword)
                                .or()
                                .like(Customer::getCustomerCode, customerKeyword)
                                .or()
                                .like(Customer::getShortName, customerKeyword));
                List<Customer> matchedCustomers = customerMapper.selectList(customerWrapper);

                Set<String> customerAliases = new HashSet<>();
                if (matchedCustomers != null) {
                    for (Customer c : matchedCustomers) {
                        if (c == null) continue;
                        if (c.getCustomerName() != null && !c.getCustomerName().trim().isEmpty()) {
                            customerAliases.add(c.getCustomerName().trim());
                        }
                        if (c.getCustomerCode() != null && !c.getCustomerCode().trim().isEmpty()) {
                            customerAliases.add(c.getCustomerCode().trim());
                        }
                        if (c.getShortName() != null && !c.getShortName().trim().isEmpty()) {
                            customerAliases.add(c.getShortName().trim());
                        }
                    }
                }

                if (customerAliases.isEmpty()) {
                    queryWrapper.like(SalesOrder::getCustomer, customerKeyword);
                } else {
                    queryWrapper.and(w -> w.like(SalesOrder::getCustomer, customerKeyword)
                            .or()
                            .in(SalesOrder::getCustomer, customerAliases));
                }
            }
            
            // 非搜索模式参数时，仍支持按订单状态字段精确筛选
            if (!statusFilter.isEmpty() && !modeAll && !modeCompleted && !modePending) {
                queryWrapper.eq(SalesOrder::getStatus, statusFilter);
            }
            
            queryWrapper.orderByDesc(SalesOrder::getCreatedAt);
            if (!modeCompleted) {
                queryWrapper.last("LIMIT 20"); // 默认场景限制返回20条，completed模式不限制
            }
            
            List<SalesOrder> orders = salesOrderMapper.selectList(queryWrapper);

            // 按模式返回订单：
            // 1) pending/unshipped（默认）：仅未发完
            // 2) completed：仅已发完
            // 3) all：全部
            List<SalesOrder> filtered = new ArrayList<>();
            for (SalesOrder order : orders) {
                if (order == null || order.getId() == null) continue;
                enrichOrderCustomerFields(order);
                List<SalesOrderItem> items = salesOrderItemMapper.selectList(
                    new LambdaQueryWrapper<SalesOrderItem>()
                        .eq(SalesOrderItem::getOrderId, order.getId())
                        .eq(SalesOrderItem::getIsDeleted, 0)
                );
                int total = 0;
                int shipped = 0;
                if (items != null) {
                    for (SalesOrderItem item : items) {
                        total += item.getRolls() != null ? item.getRolls() : 0;
                        Integer shippedQty = deliveryNoticeItemMapper.getShippedQuantityByOrderItemId(item.getId());
                        shipped += shippedQty != null ? Math.max(shippedQty, 0) : 0;
                    }
                }
                int remaining = Math.max(0, total - shipped);
                order.setTotalRolls(total);
                order.setShippedRolls(shipped);
                order.setRemainingRolls(remaining);
                if (modeAll || (!modeCompleted && remaining > 0) || (modeCompleted && remaining <= 0)) {
                    filtered.add(order);
                }
            }
            
            return new ResponseResult<>(200, "success", filtered);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseResult<>(500, "搜索订单失败: " + e.getMessage());
        }
    }

    @Override
    public void exportOrders(HttpServletResponse response) {
        try {
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("销售订单导出");

            String[] headers = {
                    "订单号", "客户编码", "客户订单号", "下单日期", "交货日期", "送货地址", "状态", "订单备注",
                    "料号", "品名", "颜色代码", "厚度(μm)", "宽度(mm)", "长度(m)", "卷数", "单价", "金额", "明细备注", "涂布日期"
            };

            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5000);
            }

            List<SalesOrder> orders = salesOrderMapper.selectList(
                    new LambdaQueryWrapper<SalesOrder>()
                            .eq(SalesOrder::getIsDeleted, 0)
                            .orderByDesc(SalesOrder::getCreatedAt)
            );

            int rowIndex = 1;
            for (SalesOrder order : orders) {
                List<SalesOrderItem> items = salesOrderItemMapper.selectList(
                        new LambdaQueryWrapper<SalesOrderItem>()
                                .eq(SalesOrderItem::getOrderId, order.getId())
                                .eq(SalesOrderItem::getIsDeleted, 0)
                );
                populateItemDisplayFields(items);

                if (items == null || items.isEmpty()) {
                    Row row = sheet.createRow(rowIndex++);
                    fillOrderColumns(row, order);
                    continue;
                }

                for (SalesOrderItem item : items) {
                    Row row = sheet.createRow(rowIndex++);
                    fillOrderColumns(row, order);
                    row.createCell(8).setCellValue(safeString(item.getMaterialCode()));
                    row.createCell(9).setCellValue(safeString(resolveMaterialName(item)));
                    row.createCell(10).setCellValue(safeString(item.getColorCode()));
                    setDecimalCell(row, 11, item.getThickness());
                    setDecimalCell(row, 12, item.getWidth());
                    setDecimalCell(row, 13, item.getLength());
                    setIntCell(row, 14, item.getRolls());
                    setDecimalCell(row, 15, item.getUnitPrice());
                    setDecimalCell(row, 16, item.getAmount());
                    row.createCell(17).setCellValue(safeString(item.getRemark()));
                    row.createCell(18).setCellValue(formatDate(item.getCoatingDate()));
                }
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment;filename=" +
                    URLEncoder.encode("销售订单数据.xlsx", "UTF-8"));

            OutputStream out = response.getOutputStream();
            workbook.write(out);
            out.flush();
            out.close();
            workbook.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> importOrders(MultipartFile file, String username) {
        return importOrdersInternal(file, username, false, false);
    }

    private ResponseResult<?> importOrdersInternal(MultipartFile file, String username, boolean mergeExistingOrders, boolean disableDuplicateValidation) {
        List<String> errors = new ArrayList<>();
        Set<String> missingOrderNos = new LinkedHashSet<>();
        int successOrders = 0;
        int successItems = 0;
        int duplicateOrderSkipCount = 0;
        int duplicateItemSkipCount = 0;
        int mergedOrders = 0;
        int conflictOrderSkipCount = 0;
        List<Map<String, Object>> failedDetails = new ArrayList<>();

        try {
            Workbook workbook = WorkbookFactory.create(file.getInputStream());

            // 以订单号分组；若订单号为空，则该行按独立订单处理
            Map<String, SalesOrder> orderMap = new LinkedHashMap<>();

            for (int sheetIdx = 0; sheetIdx < workbook.getNumberOfSheets(); sheetIdx++) {
                Sheet sheet = workbook.getSheetAt(sheetIdx);
                if (sheet == null || sheet.getLastRowNum() <= 0) {
                    continue;
                }
                Map<String, Integer> headerIndexMap = buildHeaderIndexMap(sheet.getRow(0));

                for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                    Row row = sheet.getRow(i);
                    if (row == null || isRowEmpty(row)) {
                        continue;
                    }

                    try {
                        String orderNo = getCellString(getCellByHeaderOrIndex(row, headerIndexMap, 0, "订单号", "订单编号", "销售订单号", "orderNo"));
                        String customer = getCellString(getCellByHeaderOrIndex(row, headerIndexMap, 1, "客户编码", "客户代码", "客户", "customer"));
                        String customerOrderNo = getCellString(getCellByHeaderOrIndex(row, headerIndexMap, 2, "客户订单号", "customerOrderNo"));
                        LocalDate orderDate = getCellLocalDate(getCellByHeaderOrIndex(row, headerIndexMap, 3, "下单日期", "订单日期", "orderDate"));
                        LocalDate deliveryDate = getCellLocalDate(getCellByHeaderOrIndex(row, headerIndexMap, 4, "交货日期", "交期", "deliveryDate"));
                        String deliveryAddress = getCellString(getCellByHeaderOrIndex(row, headerIndexMap, 5, "送货地址", "deliveryAddress"));
                        String status = getCellString(getCellByHeaderOrIndex(row, headerIndexMap, 6, "状态", "status"));
                        String orderRemark = getCellString(getCellByHeaderOrIndex(row, headerIndexMap, 7, "订单备注", "备注", "orderRemark"));

                        String materialCode = getCellString(getCellByHeaderOrIndex(row, headerIndexMap, 8, "料号", "产品编码", "产品代码", "materialCode"));
                        String materialName = getCellString(getCellByHeaderOrIndex(row, headerIndexMap, 9, "品名", "产品名称", "materialName"));
                        String colorCode = getCellString(getCellByHeaderOrIndex(row, headerIndexMap, 10, "颜色代码", "colorCode"));
                        BigDecimal thickness = getCellDecimal(getCellByHeaderOrIndex(row, headerIndexMap, 11, "厚度", "厚度/μ", "厚度(μm)", "thickness"));
                        BigDecimal width = getCellDecimal(getCellByHeaderOrIndex(row, headerIndexMap, 12, "宽度", "宽度/mm", "width"));
                        BigDecimal length = getCellDecimal(getCellByHeaderOrIndex(row, headerIndexMap, 13, "长度", "长度/m", "length"));
                            Integer rolls = getCellInteger(getCellByHeaderOrIndex(row, headerIndexMap, 14,
                                "卷数", "生产数量", "生产数量(卷)", "生产数量（卷）", "生产数量卷", "数量", "rolls"));
                        BigDecimal unitPrice = getCellDecimal(getCellByHeaderOrIndex(row, headerIndexMap, 15, "单价", "unitPrice"));
                            BigDecimal excelSqm = getCellDecimal(getCellByHeaderOrIndex(row, headerIndexMap, 16,
                                "面积", "总平方数", "总平方", "平方米", "千平方米", "平方数", "sqm"));
                        BigDecimal excelAmount = getCellDecimal(getCellByHeaderOrIndex(row, headerIndexMap, 17, "金额", "amount"));
                            String itemRemark = getCellString(getCellByHeaderOrIndex(row, headerIndexMap, 18, "明细备注", "备注", "itemRemark"));
                            Date coatingDate = getCellDate(getCellByHeaderOrIndex(row, headerIndexMap, 19, "涂布日期", "coatingDate"));
                            Integer completedRolls = getCellInteger(getCellByHeaderOrIndex(row, headerIndexMap, 20,
                                "完成卷数", "已完成卷数", "完成数量", "completedRolls"));
                            String completionStatus = getCellString(getCellByHeaderOrIndex(row, headerIndexMap, 21,
                                "完成状态", "completionStatus", "完工状态"));

                    // 厚度统一按 μm 存储，不做单位换算

                    if (customer == null || customer.isEmpty()) {
                        throw new IllegalArgumentException("客户编码不能为空");
                    }
                    if (orderDate == null) {
                        throw new IllegalArgumentException("下单日期不能为空，格式示例：2026-02-28");
                    }
                    if (materialCode == null || materialCode.isEmpty()) {
                        throw new IllegalArgumentException("料号不能为空");
                    }
                    if (rolls == null || rolls <= 0) {
                        throw new IllegalArgumentException("卷数必须大于0");
                    }

                    TapeSpec spec = tapeSpecMapper.selectByMaterialCode(materialCode);
                    if ((materialName == null || materialName.isEmpty()) && spec != null) {
                        materialName = spec.getProductName();
                    }
                    if ((colorCode == null || colorCode.isEmpty()) && spec != null) {
                        colorCode = spec.getColorCode();
                    }
                    if (materialName == null || materialName.isEmpty()) {
                        materialName = materialCode;
                    }

                        String key = (orderNo != null && !orderNo.trim().isEmpty()) ? orderNo.trim() : "__AUTO_ROW_" + sheetIdx + "_" + i;
                        SalesOrder order = orderMap.get(key);
                        if (order == null) {
                            order = new SalesOrder();
                            order.setOrderNo(orderNo);
                            order.setCustomer(customer);
                            normalizeIncomingOrderCustomer(order);
                            order.setCustomerOrderNo(customerOrderNo);
                            order.setOrderDate(orderDate);
                            order.setDeliveryDate(deliveryDate);
                            order.setDeliveryAddress(deliveryAddress);
                            order.setStatus(trimToNull(status));
                            order.setRemark(orderRemark);
                            order.setCreatedBy(username);
                            order.setUpdatedBy(username);
                            order.setCreatedAt(new Date());
                            order.setUpdatedAt(new Date());
                            order.setIsDeleted(0);
                            Customer customerInfo = resolveCustomer(order);
                            if (customerInfo != null) {
                                order.setSalesUserId(customerInfo.getSalesUserId());
                                order.setDocumentationPersonUserId(customerInfo.getDocumentationPersonUserId());
                            }
                            order.setItems(new ArrayList<>());
                            orderMap.put(key, order);
                        }

                        SalesOrderItem item = new SalesOrderItem();
                        item.setMaterialCode(materialCode);
                        item.setMaterialName(materialName);
                        item.setColorCode(colorCode);
                        item.setThickness(thickness);
                        item.setWidth(width != null ? width : BigDecimal.ZERO);
                        item.setLength(length != null ? length : BigDecimal.ZERO);
                        item.setRolls(rolls);
                        item.setUnitPrice(unitPrice != null ? unitPrice : BigDecimal.ZERO);
                        item.setRemark(itemRemark);
                        item.setCoatingDate(coatingDate);
                        item.setCreatedBy(username);
                        item.setUpdatedBy(username);
                        item.setCreatedAt(new Date());
                        item.setUpdatedAt(new Date());
                        item.setIsDeleted(0);
                        item.setScheduledQty(0);
                        item.setScheduledArea(BigDecimal.ZERO);
                        item.setProducedArea(BigDecimal.ZERO);
                        item.setDeliveredArea(BigDecimal.ZERO);

                        calculateItemAmounts(item);

                        if (excelSqm != null && excelSqm.compareTo(BigDecimal.ZERO) > 0) {
                            item.setSqm(excelSqm.setScale(2, BigDecimal.ROUND_HALF_UP));
                        }
                        if (excelAmount != null && excelAmount.compareTo(BigDecimal.ZERO) >= 0) {
                            item.setAmount(excelAmount.setScale(2, BigDecimal.ROUND_HALF_UP));
                        }
                        if (item.getSqm() == null) item.setSqm(BigDecimal.ZERO);
                        if (item.getAmount() == null) item.setAmount(BigDecimal.ZERO);

                        // 历史完成信息：归一完成卷数，并同步写入卷数口径与面积口径
                        int normalizedCompletedRolls = normalizeCompletedRolls(completedRolls, completionStatus, rolls);
                        int normalizedRemainingRolls = Math.max((rolls == null ? 0 : rolls) - normalizedCompletedRolls, 0);
                        item.setDeliveredQty(normalizedCompletedRolls);
                        item.setRemainingQty(normalizedRemainingRolls);
                        if (normalizedRemainingRolls <= 0) {
                            item.setProductionStatus("completed");
                        } else if (normalizedCompletedRolls <= 0) {
                            item.setProductionStatus("not_started");
                        } else {
                            item.setProductionStatus("partial");
                        }
                        if (normalizedCompletedRolls > 0) {
                            item.setDeliveredArea(calcAreaByRolls(item, normalizedCompletedRolls));
                        }

                        order.getItems().add(item);

                    } catch (Exception e) {
                        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                        errors.add("Sheet" + (sheetIdx + 1) + " 第" + (i + 1) + "行：" + msg);
                        String failedOrderNo = getCellString(getCellByHeaderOrIndex(row, headerIndexMap, 0, "订单号", "订单编号", "销售订单号", "orderNo"));
                        if (failedOrderNo != null && !failedOrderNo.trim().isEmpty()) {
                            missingOrderNos.add(failedOrderNo.trim());
                        }
                    }
                }
            }

            for (SalesOrder order : orderMap.values()) {
                try {
                    if (order.getItems() == null || order.getItems().isEmpty()) {
                        continue;
                    }

                    if (order.getOrderNo() == null || order.getOrderNo().trim().isEmpty()) {
                        order.setOrderNo(buildOrderNo(order.getCustomer(), order.getOrderDate()));
                    }

                    // 依据明细完成卷数自动归一订单状态
                    normalizeOrderStatusByItems(order);

                    SalesOrder existing = salesOrderMapper.selectByOrderNo(order.getOrderNo());
                    if (existing != null) {
                        if (disableDuplicateValidation) {
                            order.setOrderNo(buildOrderNo(order.getCustomer(), order.getOrderDate()));
                            existing = salesOrderMapper.selectByOrderNo(order.getOrderNo());
                        }

                        if (!mergeExistingOrders) {
                            if (existing != null) {
                                duplicateOrderSkipCount++;
                                errors.add("订单号已存在，跳过：" + order.getOrderNo());
                                if (order.getOrderNo() != null) {
                                    missingOrderNos.add(order.getOrderNo());
                                }
                                for (SalesOrderItem item : order.getItems()) {
                                    addFailedDetail(failedDetails, order, item, "订单号已存在，普通导入不允许覆盖");
                                }
                                continue;
                            }
                        }

                        if (!isSameOrderHeader(existing, order)) {
                            conflictOrderSkipCount++;
                            errors.add("订单头不一致，跳过增量：" + order.getOrderNo() + "（客户或下单日期不一致）");
                            if (order.getOrderNo() != null) {
                                missingOrderNos.add(order.getOrderNo());
                            }
                            for (SalesOrderItem item : order.getItems()) {
                                addFailedDetail(failedDetails, order, item, "订单号存在但客户代码/下单日期不一致");
                            }
                            continue;
                        }

                        List<SalesOrderItem> existingItems = salesOrderItemMapper.selectList(
                                new LambdaQueryWrapper<SalesOrderItem>()
                                        .eq(SalesOrderItem::getOrderId, existing.getId())
                                        .eq(SalesOrderItem::getIsDeleted, 0)
                        );

                        int insertedForExisting = 0;
                        for (SalesOrderItem item : order.getItems()) {
                            if (isDuplicateCompositeItem(existingItems, item)) {
                                duplicateItemSkipCount++;
                                if (order.getOrderNo() != null) {
                                    missingOrderNos.add(order.getOrderNo());
                                }
                                addFailedDetail(failedDetails, order, item, "明细重复（订单号+客户代码+下单日期+料号+厚度+长度+宽度）");
                                continue;
                            }
                            item.setOrderId(existing.getId());
                            prepareItemForPersistence(item);
                            salesOrderItemMapper.insert(item);
                            existingItems.add(item);
                            successItems++;
                            insertedForExisting++;
                        }

                        if (insertedForExisting > 0) {
                            mergedOrders++;
                            refreshOrderTotalsFromItems(existing, existingItems, username);
                        }
                        continue;
                    }

                    calculateOrderTotals(order);
                    salesOrderMapper.insert(order);
                    successOrders++;

                    for (SalesOrderItem item : order.getItems()) {
                        item.setOrderId(order.getId());
                        prepareItemForPersistence(item);
                        salesOrderItemMapper.insert(item);
                        successItems++;
                    }
                } catch (Exception e) {
                    String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    errors.add("订单导入失败（订单号=" + safeString(order.getOrderNo()) + "）：" + msg);
                    if (order.getOrderNo() != null && !order.getOrderNo().trim().isEmpty()) {
                        missingOrderNos.add(order.getOrderNo().trim());
                    }
                }
            }

            workbook.close();
        } catch (Exception e) {
            return new ResponseResult<>(500, "导入失败：" + e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("successOrders", successOrders);
        result.put("successItems", successItems);
        result.put("mergedOrders", mergedOrders);
        result.put("duplicateOrderSkipCount", duplicateOrderSkipCount);
        result.put("duplicateItemSkipCount", duplicateItemSkipCount);
        result.put("conflictOrderSkipCount", conflictOrderSkipCount);
        result.put("failCount", errors.size());
        result.put("errors", errors);
        result.put("missingOrderNos", new ArrayList<>(missingOrderNos));
        result.put("failedDetails", failedDetails);

        boolean hasImported = successOrders > 0 || successItems > 0 || mergedOrders > 0;
        boolean hasOnlySkips = !hasImported && (duplicateOrderSkipCount > 0 || duplicateItemSkipCount > 0 || conflictOrderSkipCount > 0);

        if (!mergeExistingOrders && successOrders == 0) {
            return new ResponseResult<>(50000, "导入失败：没有成功导入任何订单", result);
        }
        if (mergeExistingOrders && !hasImported && !hasOnlySkips && !errors.isEmpty()) {
            return new ResponseResult<>(50000, "增量同步失败：没有成功导入任何数据", result);
        }

        String message = mergeExistingOrders
                ? "增量同步完成：新增订单" + successOrders + "条，新增明细" + successItems + "条，合并订单" + mergedOrders + "条"
                : "导入完成：成功订单" + successOrders + "条，明细" + successItems + "条";
        return new ResponseResult<>(200, message, result);
    }

    @Override
    public ResponseResult<?> getHistoryInitStatus() {
        try {
            ensureSalesOrderSyncStateTable();
            SalesOrderSyncState state = salesOrderSyncStateMapper.selectSingleton();
            if (state == null) {
                state = new SalesOrderSyncState();
                state.setId(1L);
                state.setInitialized(0);
                state.setTotalOrders(0);
                state.setTotalItems(0);
            }
            return new ResponseResult<>(200, "success", state);
        } catch (Exception e) {
            log.error("查询销售订单历史初始化状态失败", e);
            return new ResponseResult<>(500, "查询历史初始化状态失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> importHistoryInit(MultipartFile file, String operator) {
        try {
            ensureSalesOrderSyncStateTable();
            SalesOrderSyncState state = salesOrderSyncStateMapper.selectSingleton();
            if (state != null && Integer.valueOf(1).equals(state.getInitialized())) {
                return new ResponseResult<>(400, "历史初始化已完成，不能重复初始化，请使用增量同步");
            }

            ResponseResult<?> importResult = importOrdersInternal(file, operator, false, true);
            if (!isSuccessCode(importResult.getCode())) {
                return importResult;
            }

            Map<String, Object> dataMap = toDataMap(importResult.getData());
            int successOrders = getIntFromMap(dataMap, "successOrders");
            int successItems = getIntFromMap(dataMap, "successItems");

            SalesOrderSyncState newState = state != null ? state : new SalesOrderSyncState();
            Date now = new Date();
            newState.setId(1L);
            newState.setInitialized(1);
            newState.setInitializedAt(now);
            newState.setInitializedBy(operator);
            newState.setLastSyncAt(now);
            newState.setLastSyncBy(operator);
            newState.setLastImportFile(file != null ? file.getOriginalFilename() : null);
            newState.setTotalOrders(successOrders);
            newState.setTotalItems(successItems);
            newState.setRemark("历史初始化完成");
            if (newState.getCreatedAt() == null) {
                newState.setCreatedAt(now);
            }
            newState.setUpdatedAt(now);
            salesOrderSyncStateMapper.upsert(newState);

            dataMap.put("historyInitialized", true);
            dataMap.put("syncState", newState);
            return new ResponseResult<>(importResult.getCode(), importResult.getMsg(), dataMap);
        } catch (Exception e) {
            log.error("历史初始化导入失败", e);
            return new ResponseResult<>(500, "历史初始化导入失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> syncIncrementalOrders(MultipartFile file, String operator) {
        try {
            ensureSalesOrderSyncStateTable();
            SalesOrderSyncState state = salesOrderSyncStateMapper.selectSingleton();
            if (state == null || !Integer.valueOf(1).equals(state.getInitialized())) {
                return new ResponseResult<>(400, "请先完成历史初始化，再执行增量同步");
            }

            ResponseResult<?> importResult = importOrdersInternal(file, operator, true, false);
            if (!isSuccessCode(importResult.getCode())) {
                return importResult;
            }

            Map<String, Object> dataMap = toDataMap(importResult.getData());
            int successOrders = getIntFromMap(dataMap, "successOrders");
            int successItems = getIntFromMap(dataMap, "successItems");
            int mergedOrders = getIntFromMap(dataMap, "mergedOrders");

            Date now = new Date();
            state.setLastSyncAt(now);
            state.setLastSyncBy(operator);
            state.setLastImportFile(file != null ? file.getOriginalFilename() : null);
            state.setTotalOrders(safeInt(state.getTotalOrders()) + successOrders);
            state.setTotalItems(safeInt(state.getTotalItems()) + successItems);
            state.setRemark("增量同步完成");
            if (state.getCreatedAt() == null) {
                state.setCreatedAt(now);
            }
            state.setUpdatedAt(now);
            salesOrderSyncStateMapper.upsert(state);

            dataMap.put("incrementalSynced", true);
            dataMap.put("noNewOrders", successOrders == 0 && successItems == 0 && mergedOrders == 0);
            dataMap.put("syncState", state);
            String message = (successOrders == 0 && successItems == 0 && mergedOrders == 0)
                    ? "增量同步完成：无新增数据（重复或冲突已跳过）"
                    : importResult.getMsg();
            return new ResponseResult<>(200, message, dataMap);
        } catch (Exception e) {
            log.error("增量同步导入失败", e);
            return new ResponseResult<>(500, "增量同步导入失败: " + e.getMessage());
        }
    }

    private boolean isSameOrderHeader(SalesOrder existing, SalesOrder incoming) {
        if (existing == null || incoming == null) {
            return false;
        }
        String existingCustomer = resolveCustomerCode(existing.getCustomer());
        String incomingCustomer = resolveCustomerCode(incoming.getCustomer());
        if (!Objects.equals(existingCustomer, incomingCustomer)) {
            return false;
        }
        return Objects.equals(existing.getOrderDate(), incoming.getOrderDate());
    }

    private boolean isDuplicateCompositeItem(List<SalesOrderItem> existingItems, SalesOrderItem incoming) {
        if (incoming == null || existingItems == null || existingItems.isEmpty()) {
            return false;
        }
        String materialCode = normalizeText(incoming.getMaterialCode());
        BigDecimal thickness = normalizeDecimal(incoming.getThickness());
        BigDecimal length = normalizeDecimal(incoming.getLength());
        BigDecimal width = normalizeDecimal(incoming.getWidth());

        for (SalesOrderItem item : existingItems) {
            if (item == null) {
                continue;
            }
            if (Objects.equals(normalizeText(item.getMaterialCode()), materialCode)
                    && isDecimalEqual(item.getThickness(), thickness)
                    && isDecimalEqual(item.getLength(), length)
                    && isDecimalEqual(item.getWidth(), width)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDecimalEqual(BigDecimal left, BigDecimal right) {
        BigDecimal l = normalizeDecimal(left);
        BigDecimal r = normalizeDecimal(right);
        if (l == null && r == null) {
            return true;
        }
        if (l == null || r == null) {
            return false;
        }
        return l.compareTo(r) == 0;
    }

    private BigDecimal normalizeDecimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }

    private BigDecimal normalizeWidthOneDecimal(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(1, BigDecimal.ROUND_HALF_UP);
    }

    private String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void refreshOrderTotalsFromItems(SalesOrder order, List<SalesOrderItem> items, String username) {
        if (order == null) {
            return;
        }
        BigDecimal totalArea = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;
        if (items != null) {
            for (SalesOrderItem item : items) {
                if (item == null) {
                    continue;
                }
                BigDecimal sqm = item.getSqm() == null ? BigDecimal.ZERO : item.getSqm();
                BigDecimal amount = item.getAmount() == null ? BigDecimal.ZERO : item.getAmount();
                totalArea = totalArea.add(sqm);
                totalAmount = totalAmount.add(amount);
            }
        }
        order.setTotalArea(totalArea.setScale(2, BigDecimal.ROUND_HALF_UP));
        order.setTotalAmount(totalAmount.setScale(2, BigDecimal.ROUND_HALF_UP));
        order.setUpdatedBy(username);
        order.setUpdatedAt(new Date());
        salesOrderMapper.updateById(order);
    }

    private void addFailedDetail(List<Map<String, Object>> failedDetails,
                                 SalesOrder order,
                                 SalesOrderItem item,
                                 String reason) {
        if (failedDetails == null) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("orderNo", order == null ? null : order.getOrderNo());
        row.put("customer", order == null ? null : order.getCustomer());
        row.put("orderDate", order == null ? null : order.getOrderDate());
        row.put("materialCode", item == null ? null : item.getMaterialCode());
        row.put("thickness", item == null ? null : item.getThickness());
        row.put("length", item == null ? null : item.getLength());
        row.put("width", item == null ? null : item.getWidth());
        row.put("reason", reason);
        failedDetails.add(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> resetHistoryInitialization(String operator) {
        try {
            ensureSalesOrderSyncStateTable();

            int deletedItems = salesOrderItemMapper.deleteAllPhysical();
            int deletedOrders = salesOrderMapper.deleteAllPhysical();
            salesOrderItemMapper.resetAutoIncrement();
            salesOrderMapper.resetAutoIncrement();
            salesOrderSyncStateMapper.deleteSingleton();

            Map<String, Object> result = new HashMap<>();
            result.put("deletedOrders", deletedOrders);
            result.put("deletedItems", deletedItems);
            result.put("historyInitialized", false);
            result.put("operator", operator);

            return new ResponseResult<>(200, "销售订单历史初始化数据已清空，初始化状态已重置", result);
        } catch (Exception e) {
            log.error("重置销售订单历史初始化失败", e);
            return new ResponseResult<>(500, "重置销售订单历史初始化失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResponseResult<?> rebuildHistoryInitializationState(String operator) {
        try {
            ensureSalesOrderSyncStateTable();

            int totalOrders = Math.toIntExact(salesOrderMapper.selectCount(
                    new LambdaQueryWrapper<SalesOrder>().eq(SalesOrder::getIsDeleted, 0)
            ));
            int totalItems = Math.toIntExact(salesOrderItemMapper.selectCount(
                    new LambdaQueryWrapper<SalesOrderItem>().eq(SalesOrderItem::getIsDeleted, 0)
            ));

            SalesOrderSyncState state = salesOrderSyncStateMapper.selectSingleton();
            Date now = new Date();
            if (state == null) {
                state = new SalesOrderSyncState();
                state.setId(1L);
                state.setCreatedAt(now);
            }

            state.setInitialized(1);
            if (state.getInitializedAt() == null) {
                state.setInitializedAt(now);
            }
            state.setInitializedBy(operator);
            state.setLastSyncAt(now);
            state.setLastSyncBy(operator);
            if (state.getLastImportFile() == null || state.getLastImportFile().trim().isEmpty()) {
                state.setLastImportFile("REBUILT_FROM_EXISTING_DATA");
            }
            state.setTotalOrders(totalOrders);
            state.setTotalItems(totalItems);
            state.setRemark("基于现有订单数据重建历史初始化状态");
            state.setUpdatedAt(now);
            salesOrderSyncStateMapper.upsert(state);

            Map<String, Object> result = new HashMap<>();
            result.put("historyInitialized", true);
            result.put("totalOrders", totalOrders);
            result.put("totalItems", totalItems);
            result.put("syncState", state);
            return new ResponseResult<>(200, "已基于现有订单数据重建历史初始化状态", result);
        } catch (Exception e) {
            log.error("基于现有订单数据重建历史初始化状态失败", e);
            return new ResponseResult<>(500, "重建历史初始化状态失败: " + e.getMessage());
        }
    }

    private void ensureSalesOrderSyncStateTable() {
        try {
            salesOrderSyncStateMapper.ensureTableExists();
        } catch (Exception e) {
            log.error("创建销售订单同步状态表失败", e);
            throw new RuntimeException("初始化销售订单同步状态表失败: " + e.getMessage(), e);
        }
    }

    private void prepareItemForPersistence(SalesOrderItem item) {
        if (item == null) {
            return;
        }
        item.setMaterialCode(normalizeMaterialCodeToken(item.getMaterialCode()));
        item.setUnit(normalizePricingUnit(item.getUnit()));
        normalizeItemCompletionFields(item);
        String materialName = resolveMaterialName(item);
        if (trimToNull(materialName) == null) {
            materialName = trimToNull(item.getMaterialCode());
        }
        item.setMaterialName(materialName);
    }

    private void normalizeItemCompletionFields(SalesOrderItem item) {
        int rolls = item.getRolls() == null ? 0 : Math.max(item.getRolls(), 0);

        int completed = item.getDeliveredQty() == null ? 0 : Math.max(item.getDeliveredQty(), 0);
        if (completed > rolls) {
            completed = rolls;
        }

        int remaining;
        if (item.getRemainingQty() != null) {
            remaining = Math.max(item.getRemainingQty(), 0);
            int maxRemaining = Math.max(rolls - completed, 0);
            if (remaining > maxRemaining) {
                remaining = maxRemaining;
            }
        } else {
            remaining = Math.max(rolls - completed, 0);
        }

        item.setDeliveredQty(completed);
        item.setRemainingQty(remaining);

        String status = trimToNull(item.getProductionStatus());
        if (status == null) {
            if (remaining <= 0) {
                status = "completed";
            } else if (completed <= 0) {
                status = "not_started";
            } else {
                status = "partial";
            }
        }
        item.setProductionStatus(status);
    }

    private void populateItemDisplayFields(List<SalesOrderItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (SalesOrderItem item : items) {
            item.setMaterialName(resolveMaterialName(item));
        }
    }

    private String resolveMaterialName(SalesOrderItem item) {
        if (item == null) {
            return null;
        }
        String materialName = trimToNull(item.getMaterialName());
        if (materialName != null) {
            return materialName;
        }
        String materialCode = trimToNull(item.getMaterialCode());
        if (materialCode == null) {
            return null;
        }
        TapeSpec spec = tapeSpecMapper.selectByMaterialCode(materialCode);
        if (spec != null && trimToNull(spec.getProductName()) != null) {
            return spec.getProductName().trim();
        }
        return materialCode;
    }

    @Override
    public void downloadTemplate(HttpServletResponse response) {
        try {
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("销售订单导入模板");

            String[] headers = {
                    "订单号(可空)", "客户编码*", "客户订单号", "下单日期*", "交货日期", "送货地址", "状态", "订单备注",
                    "料号*", "品名", "颜色代码", "厚度(μm)", "宽度(mm)", "长度(m)", "卷数*", "单价", "金额(可空)", "明细备注", "涂布日期", "完成卷数", "完成状态"
            };

            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5200);
            }

            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue("DH260228-01");
            sample.createCell(1).setCellValue("KH001");
            sample.createCell(2).setCellValue("PO-20260228-01");
            sample.createCell(3).setCellValue("2026-02-28");
            sample.createCell(4).setCellValue("2026-03-05");
            sample.createCell(5).setCellValue("深圳市南山区XX路");
            sample.createCell(6).setCellValue("pending");
            sample.createCell(7).setCellValue("首单");
            sample.createCell(8).setCellValue("1012-R015-5055-B08-1300");
            sample.createCell(9).setCellValue("110μm动力电池保护膜");
            sample.createCell(10).setCellValue("B08");
            sample.createCell(11).setCellValue(110);
            sample.createCell(12).setCellValue(1300);
            sample.createCell(13).setCellValue(200);
            sample.createCell(14).setCellValue(10);
            sample.createCell(15).setCellValue(18.5);
            sample.createCell(17).setCellValue("常规订单");
            sample.createCell(18).setCellValue("2026-03-01");
            sample.createCell(19).setCellValue(6);
            sample.createCell(20).setCellValue("未完成");

            Row noteRow = sheet.createRow(3);
            noteRow.createCell(0).setCellValue("说明：带*为必填；订单号为空时系统自动生成；品名可留空，仅用于展示，系统按料号回填且不作为持久化字段；金额可留空系统自动计算；完成卷数/完成状态可用于历史初始化");

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment;filename=" +
                    URLEncoder.encode("销售订单导入模板.xlsx", "UTF-8"));

            OutputStream out = response.getOutputStream();
            workbook.write(out);
            out.flush();
            out.close();
            workbook.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void fillOrderColumns(Row row, SalesOrder order) {
        row.createCell(0).setCellValue(safeString(order.getOrderNo()));
        row.createCell(1).setCellValue(safeString(order.getCustomer()));
        row.createCell(2).setCellValue(safeString(order.getCustomerOrderNo()));
        row.createCell(3).setCellValue(order.getOrderDate() != null ? order.getOrderDate().toString() : "");
        row.createCell(4).setCellValue(order.getDeliveryDate() != null ? order.getDeliveryDate().toString() : "");
        row.createCell(5).setCellValue(safeString(order.getDeliveryAddress()));
        row.createCell(6).setCellValue(safeString(order.getStatus()));
        row.createCell(7).setCellValue(safeString(order.getRemark()));
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private void setDecimalCell(Row row, int index, BigDecimal value) {
        Cell cell = row.createCell(index);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        } else {
            cell.setCellValue("");
        }
    }

    private void setIntCell(Row row, int index, Integer value) {
        Cell cell = row.createCell(index);
        if (value != null) {
            cell.setCellValue(value);
        } else {
            cell.setCellValue("");
        }
    }

    private String formatDate(Date date) {
        if (date == null) {
            return "";
        }
        return new SimpleDateFormat("yyyy-MM-dd").format(date);
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int i = 0; i <= 21; i++) {
            String value = getCellString(row.getCell(i));
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String getCellString(Cell cell) {
        if (cell == null) return null;
        String value = dataFormatter.formatCellValue(cell);
        if (value == null) return null;
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    private BigDecimal getCellDecimal(Cell cell) {
        String value = getCellString(cell);
        if (value == null) return null;
        String normalized = value.replace("，", "")
                .replace(",", "")
                .replace("mm", "")
                .replace("m", "")
                .replace("μm", "")
                .replace("µm", "")
                .trim();
        if (normalized.isEmpty()) return null;
        return new BigDecimal(normalized);
    }

    private Integer getCellInteger(Cell cell) {
        BigDecimal decimal = getCellDecimal(cell);
        return decimal == null ? null : decimal.intValue();
    }

    private void cancelManualSchedulesForOrderDetails(List<Long> orderDetailIds, String operator, String reason) {
        if (orderDetailIds == null || orderDetailIds.isEmpty()) {
            return;
        }
        for (Long orderDetailId : orderDetailIds) {
            if (orderDetailId == null) continue;

            // 已开工排程禁止直接取消，必须走终止/减量流程
            int startedCount = manualScheduleMapper.countStartedSchedulesByOrderDetailId(orderDetailId);
            if (startedCount > 0) {
                throw new RuntimeException("订单明细ID=" + orderDetailId + " 已开工，不能直接取消，请先执行终止/减量流程");
            }

            List<Map<String, Object>> schedules = manualScheduleMapper.selectCancelableSchedulesByOrderDetailId(orderDetailId);
            if (schedules == null || schedules.isEmpty()) {
                continue;
            }

            BigDecimal rollbackQty = BigDecimal.ZERO;
            for (Map<String, Object> schedule : schedules) {
                Object q = schedule.get("schedule_qty");
                if (q instanceof Number) {
                    rollbackQty = rollbackQty.add(BigDecimal.valueOf(((Number) q).doubleValue()));
                }

                // 释放库存锁定（仅有锁定分配的排程）
                Object alloc = schedule.get("stock_allocations");
                if (alloc != null) {
                    releaseStockAllocations(alloc.toString());
                }
            }

            if (rollbackQty.compareTo(BigDecimal.ZERO) > 0) {
                manualScheduleMapper.rollbackScheduledQtyByDetailId(orderDetailId, rollbackQty);
            }

            String finalReason = (reason == null || reason.isEmpty()) ? "排程取消" : reason;
            if (operator != null && !operator.isEmpty()) {
                finalReason = finalReason + "（操作人:" + operator + "）";
            }
            manualScheduleMapper.cancelSchedulesByOrderDetailId(orderDetailId, finalReason);
        }
    }

    private void releaseStockAllocations(String stockAllocationsJson) {
        if (stockAllocationsJson == null || stockAllocationsJson.trim().isEmpty()) {
            return;
        }
        try {
            List<Map<String, Object>> list = objectMapper.readValue(stockAllocationsJson, List.class);
            for (Map<String, Object> item : list) {
                if (item == null) continue;
                Object stockIdObj = item.get("stockId");
                Object areaObj = item.get("area");
                if (stockIdObj == null || areaObj == null) continue;

                Long stockId;
                if (stockIdObj instanceof Number) {
                    stockId = ((Number) stockIdObj).longValue();
                } else {
                    stockId = Long.parseLong(String.valueOf(stockIdObj));
                }

                BigDecimal area;
                if (areaObj instanceof Number) {
                    area = BigDecimal.valueOf(((Number) areaObj).doubleValue());
                } else {
                    area = new BigDecimal(String.valueOf(areaObj));
                }
                if (area.compareTo(BigDecimal.ZERO) <= 0) continue;

                // 乐观锁重试释放
                for (int i = 0; i < 3; i++) {
                    TapeStock current = tapeStockMapper.selectById(stockId);
                    if (current == null) {
                        break;
                    }
                    Integer version = current.getVersion() == null ? 0 : current.getVersion();
                    int ok = tapeStockMapper.releaseLock(stockId, area, version);
                    if (ok > 0) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // 释放失败不中断主流程，避免影响订单取消/删除
            log.error("释放库存锁定失败", e);
        }
    }

    private LocalDate getCellLocalDate(Cell cell) {
        Date date = getCellDate(cell);
        if (date != null) {
            return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        String value = getCellString(cell);
        if (value == null) return null;
        value = value.replace("日", "").trim();
        List<String> patterns = Arrays.asList("yyyy-MM-dd", "yyyy/MM/dd", "yyyyMMdd", "M/d");
        for (String pattern : patterns) {
            try {
                if ("M/d".equals(pattern)) {
                    String[] parts = value.split("/");
                    if (parts.length == 2) {
                        int month = Integer.parseInt(parts[0].trim());
                        int day = Integer.parseInt(parts[1].trim());
                        return LocalDate.of(LocalDate.now(ZoneId.of("Asia/Shanghai")).getYear(), month, day);
                    }
                    continue;
                }
                return LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Date getCellDate(Cell cell) {
        if (cell == null) return null;
        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getDateCellValue();
            }
        } catch (Exception ignored) {
        }
        String value = getCellString(cell);
        if (value == null) return null;
        value = value.replace("日", "").trim();
        List<String> patterns = Arrays.asList("yyyy-MM-dd", "yyyy/MM/dd", "yyyyMMdd", "M/d");
        for (String pattern : patterns) {
            try {
                if ("M/d".equals(pattern)) {
                    String full = LocalDate.now(ZoneId.of("Asia/Shanghai")).getYear() + "/" + value;
                    return new SimpleDateFormat("yyyy/M/d").parse(full);
                }
                return new SimpleDateFormat(pattern).parse(value);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Map<String, Integer> buildHeaderIndexMap(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        if (headerRow == null) {
            return map;
        }
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            String header = getCellString(headerRow.getCell(i));
            if (header == null) continue;
            map.put(normalizeHeader(header), i);
        }
        return map;
    }

    private String normalizeHeader(String header) {
        return header.replace("*", "")
                .replace("（", "(")
                .replace("）", ")")
                .replace("/", "")
                .replace(" ", "")
                .trim()
                .toLowerCase();
    }

    private Cell getCellByHeaderOrIndex(Row row, Map<String, Integer> headerIndexMap, int fallbackIndex, String... candidates) {
        if (headerIndexMap != null && !headerIndexMap.isEmpty() && candidates != null) {
            for (String c : candidates) {
                Integer idx = headerIndexMap.get(normalizeHeader(c));
                if (idx != null) {
                    return row.getCell(idx);
                }
                Integer fuzzyIdx = findHeaderIndexByContains(headerIndexMap, c);
                if (fuzzyIdx != null) {
                    return row.getCell(fuzzyIdx);
                }
            }
        }
        return row.getCell(fallbackIndex);
    }

    private Integer findHeaderIndexByContains(Map<String, Integer> headerIndexMap, String candidate) {
        if (candidate == null || headerIndexMap == null || headerIndexMap.isEmpty()) {
            return null;
        }
        String normalized = normalizeHeader(candidate);
        for (Map.Entry<String, Integer> entry : headerIndexMap.entrySet()) {
            String key = entry.getKey();
            if (key.contains(normalized) || normalized.contains(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean isSuccessCode(Integer code) {
        return Integer.valueOf(200).equals(code) || Integer.valueOf(20000).equals(code);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toDataMap(Object data) {
        if (data instanceof Map) {
            return (Map<String, Object>) data;
        }
        return new HashMap<>();
    }

    private int getIntFromMap(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return 0;
        }
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private int safeInt(Integer val) {
        return val == null ? 0 : val;
    }

    private Integer getIntFromObject(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return 0;
        }
    }

    private int normalizeCompletedRolls(Integer completedRolls, String completionStatus, Integer totalRolls) {
        int total = totalRolls == null || totalRolls < 0 ? 0 : totalRolls;
        int completed = completedRolls == null ? -1 : Math.max(0, completedRolls);

        String status = completionStatus == null ? "" : completionStatus.trim().toLowerCase();
        boolean uncompletedByStatus = containsAny(status,
                "未完成", "unfinished", "pending", "processing", "not_started", "partial");
        boolean completedByStatus = !uncompletedByStatus && containsAny(status,
                "已完成", "完成", "completed", "done", "finish");

        // 未填写完成卷数时，按状态推断；已填写则优先信任数字
        if (completed < 0) {
            if (completedByStatus) {
                completed = total;
            } else {
                completed = 0;
            }
        }

        // 当状态明确“未完成”但卷数填满时，兜底修正为仍有欠卷
        if (uncompletedByStatus && completed >= total && total > 0) {
            completed = Math.max(0, total - 1);
        }

        if (total > 0 && completed > total) {
            completed = total;
        }
        if (completed < 0) {
            completed = 0;
        }
        return completed;
    }

    private boolean containsAny(String source, String... tokens) {
        if (source == null || source.isEmpty() || tokens == null || tokens.length == 0) {
            return false;
        }
        for (String token : tokens) {
            if (token != null && !token.isEmpty() && source.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal calcAreaByRolls(SalesOrderItem item, int completedRolls) {
        if (item == null || completedRolls <= 0 || item.getRolls() == null || item.getRolls() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal sqm = item.getSqm() == null ? BigDecimal.ZERO : item.getSqm();
        if (sqm.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal perRoll = sqm.divide(new BigDecimal(item.getRolls()), 6, BigDecimal.ROUND_HALF_UP);
        return perRoll.multiply(new BigDecimal(completedRolls)).setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    private int estimateCompletedRolls(SalesOrderItem item) {
        if (item == null || item.getRolls() == null || item.getRolls() <= 0 || item.getSqm() == null
                || item.getSqm().compareTo(BigDecimal.ZERO) <= 0 || item.getDeliveredArea() == null
                || item.getDeliveredArea().compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }

        BigDecimal perRoll = item.getSqm().divide(new BigDecimal(item.getRolls()), 6, BigDecimal.ROUND_HALF_UP);
        if (perRoll.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        int completed = item.getDeliveredArea().divide(perRoll, 0, BigDecimal.ROUND_HALF_UP).intValue();
        return Math.min(Math.max(completed, 0), item.getRolls());
    }

    private void normalizeOrderStatusByItems(SalesOrder order) {
        if (order == null) {
            return;
        }

        String status = trimToNull(order.getStatus());
        if (isTerminalOrderStatus(status) || isPaymentLockedStatus(status)) {
            return;
        }

        if (shouldUseLifecycleV2(order)) {
            normalizeOrderStatusByItemsV2(order);
            return;
        }

        normalizeOrderStatusByItemsLegacy(order);
    }

    private void normalizeOrderStatusByItemsLegacy(SalesOrder order) {
        if (order == null) {
            return;
        }

        if (order.getItems() == null || order.getItems().isEmpty()) {
            if (order.getStatus() == null || order.getStatus().trim().isEmpty()) {
                order.setStatus("pending");
            }
            return;
        }

        boolean hasEffectiveItem = false;
        boolean anyUnfinished = false;
        boolean anyCompleted = false;

        for (SalesOrderItem item : order.getItems()) {
            if (item == null) {
                continue;
            }

            normalizeItemCompletionFields(item);

            int rolls = item.getRolls() == null ? 0 : Math.max(item.getRolls(), 0);
            int completedRolls = item.getDeliveredQty() == null ? 0 : Math.max(item.getDeliveredQty(), 0);
            int remainingRolls = item.getRemainingQty() == null ? Math.max(rolls - completedRolls, 0) : Math.max(item.getRemainingQty(), 0);

            if (rolls <= 0 && completedRolls <= 0 && remainingRolls <= 0) {
                continue;
            }

            hasEffectiveItem = true;

            if (completedRolls > 0) {
                anyCompleted = true;
            }
            if (remainingRolls > 0) {
                anyUnfinished = true;
            }
        }

        if (!hasEffectiveItem) {
            if (order.getStatus() == null || order.getStatus().trim().isEmpty()) {
                order.setStatus("pending");
            }
            return;
        }

        if (!anyUnfinished) {
            order.setStatus("completed");
        } else if (anyCompleted) {
            order.setStatus("processing");
        } else {
            order.setStatus("pending");
        }
    }

    private void normalizeOrderStatusByItemsV2(SalesOrder order) {
        if (order == null) {
            return;
        }

        if (order.getItems() == null || order.getItems().isEmpty()) {
            if (trimToNull(order.getStatus()) == null) {
                order.setStatus("CREATED");
            }
            return;
        }

        int totalRolls = 0;
        int producedRolls = 0;
        int scheduledRolls = 0;
        int shippedRolls = 0;

        for (SalesOrderItem item : order.getItems()) {
            if (item == null) {
                continue;
            }

            normalizeItemCompletionFields(item);

            int rolls = item.getRolls() == null ? 0 : Math.max(item.getRolls(), 0);
            int completedRolls = item.getDeliveredQty() == null ? 0 : Math.max(item.getDeliveredQty(), 0);
            int oneScheduled = item.getScheduledQty() == null ? 0 : Math.max(item.getScheduledQty(), 0);

            totalRolls += rolls;
            producedRolls += Math.min(completedRolls, rolls);
            scheduledRolls += Math.min(oneScheduled, rolls);

            if (item.getId() != null) {
                Integer oneShipped = deliveryNoticeItemMapper.getShippedQuantityByOrderItemId(item.getId());
                shippedRolls += oneShipped == null ? 0 : Math.max(oneShipped, 0);
            }
        }

        if (totalRolls <= 0) {
            order.setStatus("CREATED");
            return;
        }

        int normalizedShipped = Math.min(shippedRolls, totalRolls);
        int normalizedProduced = Math.min(producedRolls, totalRolls);
        int normalizedScheduled = Math.min(scheduledRolls, totalRolls);

        if (normalizedShipped >= totalRolls) {
            order.setStatus("SHIPPED_FULL");
        } else if (normalizedShipped > 0) {
            order.setStatus("SHIPPED_PARTIAL");
        } else if (normalizedProduced >= totalRolls) {
            order.setStatus("PRODUCED");
        } else if (normalizedProduced > 0) {
            order.setStatus("IN_PRODUCTION");
        } else if (normalizedScheduled > 0) {
            order.setStatus("SCHEDULED");
        } else {
            order.setStatus("CREATED");
        }
    }

    private boolean shouldUseLifecycleV2(SalesOrder order) {
        if (order == null) {
            return false;
        }
        String status = trimToNull(order.getStatus());
        if (isLifecycleV2Status(status)) {
            return true;
        }
        if (status == null && order.getId() == null) {
            return true;
        }
        if (status == null) {
            return false;
        }
        return !LEGACY_ORDER_STATUSES.contains(status.toLowerCase(Locale.ROOT));
    }

    private boolean isLifecycleV2Status(String status) {
        String normalized = trimToNull(status);
        return normalized != null && LIFECYCLE_V2_STATUSES.contains(normalized.toUpperCase(Locale.ROOT));
    }

    private boolean isTerminalOrderStatus(String status) {
        String normalized = trimToNull(status);
        if (normalized == null) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return "cancelled".equals(lower)
                || "canceled".equals(lower)
                || "closed".equals(lower)
                || "cancelled".equals(normalized)
                || "closed".equals(normalized)
                || "CANCELLED".equalsIgnoreCase(normalized)
                || "CLOSED".equalsIgnoreCase(normalized);
    }

    private boolean isPaymentLockedStatus(String status) {
        String normalized = trimToNull(status);
        if (normalized == null) {
            return false;
        }
        return "PAYMENT_PARTIAL".equalsIgnoreCase(normalized)
                || "PAID".equalsIgnoreCase(normalized);
    }

    private String resolveCancelledStatusByOrder(SalesOrder order) {
        if (order == null) {
            return "cancelled";
        }
        return shouldUseLifecycleV2(order) ? "CANCELLED" : "cancelled";
    }

    private String appendCancelReason(String originRemark, String cancelReason, String operator) {
        String reason = trimToNull(cancelReason);
        if (reason == null) {
            return originRemark;
        }
        StringBuilder sb = new StringBuilder();
        String base = trimToNull(originRemark);
        if (base != null) {
            sb.append(base);
            if (!base.endsWith("\n")) {
                sb.append("\n");
            }
        }
        sb.append("[取消原因]")
                .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        String op = trimToNull(operator);
        if (op != null) {
            sb.append("[").append(op).append("]");
        }
        sb.append(reason);
        return sb.toString();
    }

    private void refreshOrderStatusFromDb(Long orderId, String username) {
        if (orderId == null) {
            return;
        }

        SalesOrder currentOrder = salesOrderMapper.selectById(orderId);
        if (currentOrder == null || Integer.valueOf(1).equals(currentOrder.getIsDeleted())) {
            return;
        }

        LambdaQueryWrapper<SalesOrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(SalesOrderItem::getOrderId, orderId)
                .eq(SalesOrderItem::getIsDeleted, 0);
        List<SalesOrderItem> activeItems = salesOrderItemMapper.selectList(itemWrapper);

        String before = currentOrder.getStatus() == null ? "" : currentOrder.getStatus().trim();
        currentOrder.setItems(activeItems);
        normalizeOrderStatusByItems(currentOrder);
        String after = currentOrder.getStatus() == null ? "" : currentOrder.getStatus().trim();

        if (!before.equalsIgnoreCase(after)) {
            SalesOrder patch = new SalesOrder();
            patch.setId(orderId);
            patch.setStatus(after);
            patch.setUpdatedBy(username);
            patch.setUpdatedAt(new Date());
            salesOrderMapper.updateById(patch);
        }
    }

}
