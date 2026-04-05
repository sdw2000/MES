package com.fine.serviceIMPL.stock;

import com.fine.Dao.production.SalesOrderMapper;
import com.fine.Dao.stock.MaterialIssueOrderItemMapper;
import com.fine.Dao.stock.MaterialIssueOrderMapper;
import com.fine.Dao.stock.ScheduleMaterialLockMapper;
import com.fine.Dao.stock.TapeStockMapper;
import com.fine.modle.SalesOrder;
import com.fine.modle.stock.MaterialIssueOrder;
import com.fine.modle.stock.MaterialIssueOrderItem;
import com.fine.modle.stock.ScheduleMaterialLock;
import com.fine.modle.stock.TapeStock;
import com.fine.service.stock.MaterialIssueOrderService;
import com.fine.service.stock.ScheduleMaterialLockingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class MaterialIssueOrderServiceImpl implements MaterialIssueOrderService {

    @Autowired
    private MaterialIssueOrderMapper materialIssueOrderMapper;

    @Autowired
    private MaterialIssueOrderItemMapper materialIssueOrderItemMapper;

    @Autowired
    private ScheduleMaterialLockMapper scheduleMaterialLockMapper;

    @Autowired
    private TapeStockMapper tapeStockMapper;

    @Autowired
    private SalesOrderMapper salesOrderMapper;

    @Autowired
    private ScheduleMaterialLockingService scheduleMaterialLockingService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialIssueOrder createIssueOrder(List<Long> lockIds,
                                               LocalDate planDate,
                                               String materialCode,
                                               String orderNo,
                                               String remark) throws Exception {
        if (lockIds == null || lockIds.isEmpty()) {
            throw new IllegalArgumentException("锁定记录不能为空");
        }

        List<Long> uniqIds = new ArrayList<>(new LinkedHashSet<>(lockIds));
        List<ScheduleMaterialLock> locks = scheduleMaterialLockMapper.selectBatchIds(uniqIds);
        if (locks == null || locks.isEmpty()) {
            throw new IllegalArgumentException("未找到锁定记录");
        }
        if (locks.size() != uniqIds.size()) {
            throw new IllegalArgumentException("部分锁定记录不存在，请刷新后重试");
        }

        for (ScheduleMaterialLock lock : locks) {
            String status = String.valueOf(lock.getLockStatus());
            if (!("锁定中".equals(status) || "LOCKED".equalsIgnoreCase(status))) {
                throw new IllegalArgumentException("仅支持锁定中记录领料，锁定ID=" + lock.getId() + " 当前状态=" + status);
            }
        }

        scheduleMaterialLockingService.allocateLocks(uniqIds);

        BigDecimal totalArea = BigDecimal.ZERO;
        Set<String> materialCodes = new LinkedHashSet<>();
        Set<String> orderNos = new LinkedHashSet<>();
        Date now = new Date();
        LocalDate finalPlanDate = planDate == null ? LocalDate.now() : planDate;

        for (ScheduleMaterialLock lock : locks) {
            totalArea = totalArea.add(lock.getLockedArea() == null ? BigDecimal.ZERO : lock.getLockedArea());

            TapeStock tapeStock = tapeStockMapper.selectById(lock.getFilmStockId());
            if (tapeStock != null && tapeStock.getMaterialCode() != null) {
                materialCodes.add(tapeStock.getMaterialCode());
            }

            SalesOrder so = lock.getOrderId() == null ? null : salesOrderMapper.selectById(lock.getOrderId());
            if (so != null && so.getOrderNo() != null) {
                orderNos.add(so.getOrderNo());
            }
        }

        MaterialIssueOrder order = new MaterialIssueOrder();
        order.setIssueNo(generateIssueNo(finalPlanDate));
        order.setPlanDate(finalPlanDate);
        order.setMaterialCode(notBlank(materialCode) ? materialCode : joinOrMulti(materialCodes));
        order.setOrderNo(notBlank(orderNo) ? orderNo : joinOrMulti(orderNos));
        order.setTotalArea(totalArea);
        order.setItemCount(locks.size());
        order.setStatus("CREATED");
        order.setRemark(remark);
        order.setCreatedBy("system");
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        order.setIsDeleted(0);
        materialIssueOrderMapper.insert(order);

        List<MaterialIssueOrderItem> items = new ArrayList<>();
        for (ScheduleMaterialLock lock : locks) {
            TapeStock tapeStock = tapeStockMapper.selectById(lock.getFilmStockId());
            SalesOrder so = lock.getOrderId() == null ? null : salesOrderMapper.selectById(lock.getOrderId());

            MaterialIssueOrderItem item = new MaterialIssueOrderItem();
            item.setIssueOrderId(order.getId());
            item.setLockId(lock.getId());
            item.setScheduleId(lock.getScheduleId());
            item.setOrderId(lock.getOrderId());
            item.setOrderNo(so != null ? so.getOrderNo() : null);
            item.setMaterialCode(tapeStock != null ? tapeStock.getMaterialCode() : null);
            item.setFilmStockId(lock.getFilmStockId());
            item.setIssuedArea(lock.getLockedArea());
            item.setLockStatusBefore(lock.getLockStatus());
            item.setCreatedAt(now);
            item.setIsDeleted(0);
            materialIssueOrderItemMapper.insert(item);
            items.add(item);
        }

        order.setItems(items);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialIssueOrder createIssueOrderBySchedule(Long scheduleId,
                                                         String processType,
                                                         LocalDate planDate,
                                                         String materialCode,
                                                         String orderNo,
                                                         String remark) throws Exception {
        if (scheduleId == null) {
            throw new IllegalArgumentException("排程ID不能为空");
        }

        List<ScheduleMaterialLock> locks = scheduleMaterialLockMapper.selectByScheduleId(scheduleId);
        if (locks == null || locks.isEmpty()) {
            throw new IllegalArgumentException("该排程暂无可领用的锁定物料");
        }

        String normalizedProcessType = trimToNull(processType);
        String normalizedMaterialCode = trimToNull(materialCode);
        String normalizedOrderNo = trimToNull(orderNo);

        List<Long> matchedLockIds = locks.stream()
                .filter(Objects::nonNull)
                .filter(lock -> isLocked(lock.getLockStatus()))
                .filter(lock -> normalizedMaterialCode == null || normalizedMaterialCode.equalsIgnoreCase(trimToNull(lock.getMaterialCode())))
                .filter(lock -> normalizedOrderNo == null || normalizedOrderNo.equalsIgnoreCase(trimToNull(lock.getOrderNo())))
                .filter(lock -> matchProcessType(lock, normalizedProcessType))
                .map(ScheduleMaterialLock::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());

        if (matchedLockIds.isEmpty()) {
            throw new IllegalArgumentException("该排程在指定工序下没有可领用的锁定记录");
        }

        return createIssueOrder(matchedLockIds, planDate, materialCode, orderNo, remark);
    }

    @Override
    public MaterialIssueOrder getIssueOrderDetail(String issueNo) {
        if (!notBlank(issueNo)) {
            return null;
        }
        MaterialIssueOrder order = materialIssueOrderMapper.selectByIssueNo(issueNo.trim());
        if (order == null) {
            return null;
        }
        List<MaterialIssueOrderItem> items = materialIssueOrderItemMapper.selectByIssueOrderId(order.getId());
        order.setItems(items);
        return order;
    }

    @Override
    public Map<String, Object> getIssueOrderPage(int current, int size, LocalDate planDate, String orderNo, String materialCode) {
        int safeCurrent = Math.max(current, 1);
        int safeSize = Math.max(size, 1);
        int offset = (safeCurrent - 1) * safeSize;

        List<MaterialIssueOrder> records = materialIssueOrderMapper.selectPage(planDate,
                trimToNull(orderNo), trimToNull(materialCode), offset, safeSize);
        int total = materialIssueOrderMapper.selectPageCount(planDate,
                trimToNull(orderNo), trimToNull(materialCode));

        Map<String, Object> data = new HashMap<>();
        data.put("records", records);
        data.put("total", total);
        data.put("current", safeCurrent);
        data.put("size", safeSize);
        return data;
    }

    private String generateIssueNo(LocalDate date) {
        LocalDate d = date == null ? LocalDate.now() : date;
        String day = d.format(DateTimeFormatter.ofPattern("yyMMdd"));
        String prefix = "LL" + day;
        String lastNo = materialIssueOrderMapper.selectLastIssueNoByPrefix(prefix);
        int seq = 1;
        if (notBlank(lastNo) && lastNo.length() > prefix.length()) {
            try {
                seq = Integer.parseInt(lastNo.substring(prefix.length())) + 1;
            } catch (Exception e) {
                log.warn("解析领料单流水失败: {}", lastNo);
            }
        }
        return prefix + String.format("%04d", seq);
    }

    private String joinOrMulti(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        if (values.size() == 1) {
            return values.iterator().next();
        }
        return "多项";
    }

    private boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private boolean isLocked(Object status) {
        if (status == null) {
            return false;
        }
        String value = String.valueOf(status).trim();
        return "锁定中".equals(value) || "LOCKED".equalsIgnoreCase(value);
    }

    private boolean matchProcessType(ScheduleMaterialLock lock, String processType) {
        if (lock == null) {
            return false;
        }
        if (!notBlank(processType)) {
            return true;
        }
        String remark = lock.getRemark() == null ? "" : lock.getRemark();
        if ("REWINDING".equalsIgnoreCase(processType)) {
            return remark.contains("source=coating-report-auto-lock")
                    || remark.contains("source=urgent-preempt-lock")
                    || remark.contains("source=urgent-order-direct-lock");
        }
        if ("SLITTING".equalsIgnoreCase(processType)) {
            return remark.contains("source=slitting-schedule-lock");
        }
        if ("COATING".equalsIgnoreCase(processType)) {
            return remark.contains("source=coating-");
        }
        return true;
    }

    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
