package com.fine.serviceIMPL.production;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.SalesOrderItemMapper;
import com.fine.Dao.production.EquipmentMapper;
import com.fine.Dao.production.ScheduleBatchMapper;
import com.fine.Dao.production.ScheduleBatchOrderMapper;
import com.fine.Dao.production.ScheduleTaskMapper;
import com.fine.Dao.stock.TapeStockMapper;
import com.fine.model.production.Equipment;
import com.fine.model.production.ScheduleBatch;
import com.fine.model.production.ScheduleBatchOrder;
import com.fine.model.production.ScheduleTask;
import com.fine.model.production.ScheduleTaskPlanRequest;
import com.fine.modle.SalesOrderItem;
import com.fine.service.production.ScheduleTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class ScheduleTaskServiceImpl extends ServiceImpl<ScheduleTaskMapper, ScheduleTask> implements ScheduleTaskService {

    private static final int DEFAULT_GAP_MIN = 10;
    private static final int COATING_SPEED_MPM = 40;
    private static final int REWIND_SPEED_MPM = 50;
    private static final int SLIT_SPEED_MPM = 80;

    private static class BatchGroup {
        String processType;
        Date planDate;
        String materialCode;
        String materialName;
        String colorCode;
        BigDecimal thickness;
        BigDecimal widthMm;
        BigDecimal length;
        BigDecimal totalArea = BigDecimal.ZERO;
        int totalQty = 0;
        BigDecimal maxPriority = BigDecimal.ZERO;
        Date earliestDeliveryDate;
        List<BatchOrderItem> items = new ArrayList<>();
    }

    private static class BatchOrderItem {
        Long orderId;
        Long orderItemId;
        String orderNo;
        Integer quantity;
        BigDecimal area;
    }

    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;

    @Autowired
    private TapeStockMapper tapeStockMapper;

    @Autowired
    private EquipmentMapper equipmentMapper;

    @Autowired
    private ScheduleTaskMapper scheduleTaskMapper;

    @Autowired
    private ScheduleBatchMapper scheduleBatchMapper;

    @Autowired
    private ScheduleBatchOrderMapper scheduleBatchOrderMapper;

    @Override
    @Transactional
    public Map<String, Object> planTasks(ScheduleTaskPlanRequest request) {
        Map<String, Object> result = new HashMap<>();
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            result.put("total", 0);
            result.put("scheduled", 0);
            result.put("unscheduled", 0);
            result.put("totalArea", BigDecimal.ZERO);
            return result;
        }

        int gapMin = request.getGapMinutes() != null ? Math.max(request.getGapMinutes(), 0) : DEFAULT_GAP_MIN;
        Date now = new Date();
        Date deadline = new Date(now.getTime() + 48L * 60L * 60L * 1000L);
        Date planDate = truncateDate(now);

        List<ScheduleTask> tasks = new ArrayList<>();
        BigDecimal totalArea = BigDecimal.ZERO;
        Map<String, BatchGroup> batchGroups = new LinkedHashMap<>();

        for (ScheduleTaskPlanRequest.PlanItem item : request.getItems()) {
            if (item == null || item.getOrderItemId() == null) {
                continue;
            }
            SalesOrderItem orderItem = salesOrderItemMapper.selectById(item.getOrderItemId());
            if (orderItem == null) {
                continue;
            }
            Map<String, Object> full = salesOrderItemMapper.selectFullItemById(item.getOrderItemId());

            BigDecimal widthMm = orderItem.getWidth() != null ? orderItem.getWidth() : BigDecimal.ZERO;
            BigDecimal lengthM = orderItem.getLength() != null ? orderItem.getLength() : BigDecimal.ZERO;
            BigDecimal perRollArea = computePerRollArea(widthMm, lengthM);

            int rolls = orderItem.getRolls() != null ? orderItem.getRolls() : 0;
            int scheduledQty = orderItem.getScheduledQty() != null ? orderItem.getScheduledQty() : 0;
            int pendingRolls = Math.max(rolls - scheduledQty, 0);

            int selectedRolls = item.getQuantity() != null ? Math.min(item.getQuantity(), pendingRolls) : pendingRolls;
            BigDecimal selectedArea = item.getArea() != null ? item.getArea() : perRollArea.multiply(new BigDecimal(selectedRolls));
            if (selectedArea.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            totalArea = totalArea.add(selectedArea);

            // 持久化更新订单明细的已排程数量/面积与待排面积
            if (selectedRolls > 0) {
                salesOrderItemMapper.updateScheduledQty(orderItem.getId(), selectedRolls);
            }
            if (selectedArea.compareTo(BigDecimal.ZERO) > 0) {
                salesOrderItemMapper.updateScheduledArea(orderItem.getId(), selectedArea);
            }

            String materialCode = orderItem.getMaterialCode();
            BigDecimal availableRewound = tapeStockMapper.selectAvailableRewoundArea24h(materialCode);
            if (availableRewound == null) {
                availableRewound = BigDecimal.ZERO;
            }
            BigDecimal availableJumbo = tapeStockMapper.selectAvailableAreaByMaterialAndType(
                    materialCode, "母卷", "jumbo");
            if (availableJumbo == null) {
                availableJumbo = BigDecimal.ZERO;
            }

            BigDecimal slittingArea = availableRewound.min(selectedArea);
            BigDecimal remaining = selectedArea.subtract(slittingArea);
            BigDecimal rewindingArea = BigDecimal.ZERO;
            BigDecimal coatingArea = BigDecimal.ZERO;

            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                if (availableJumbo != null && availableJumbo.compareTo(BigDecimal.ZERO) > 0) {
                    rewindingArea = remaining.min(availableJumbo);
                }
                coatingArea = remaining.subtract(rewindingArea);
            }

            if (slittingArea.compareTo(BigDecimal.ZERO) > 0) {
                tasks.add(buildTask(orderItem, full, widthMm, lengthM, perRollArea, slittingArea,
                        "SLITTING", item.getPriorityScore(), request.getLockStock()));
            }
            if (rewindingArea.compareTo(BigDecimal.ZERO) > 0) {
                addToBatchGroup(batchGroups, orderItem, full, perRollArea, rewindingArea,
                        "REWINDING", item.getPriorityScore(), planDate);
            }
            if (coatingArea.compareTo(BigDecimal.ZERO) > 0) {
                addToBatchGroup(batchGroups, orderItem, full, perRollArea, coatingArea,
                        "COATING", item.getPriorityScore(), planDate);
            }
        }

        for (BatchGroup group : batchGroups.values()) {
            ScheduleBatch batch = new ScheduleBatch();
            batch.setProcessType(group.processType);
            batch.setPlanDate(group.planDate);
            batch.setMaterialCode(group.materialCode);
            batch.setMaterialName(group.materialName);
            batch.setColorCode(group.colorCode);
            batch.setThickness(group.thickness);
            batch.setWidthMm(group.widthMm);
            batch.setLength(group.length);
            batch.setTotalQty(group.totalQty);
            batch.setTotalArea(group.totalArea);
            batch.setStatus("UNSCHEDULED");
            scheduleBatchMapper.insert(batch);

            String batchNo = buildBatchNo(batch.getId(), group.planDate, group.processType);
            batch.setBatchNo(batchNo);
            scheduleBatchMapper.updateById(batch);

            for (BatchOrderItem item : group.items) {
                ScheduleBatchOrder order = new ScheduleBatchOrder();
                order.setBatchId(batch.getId());
                order.setOrderId(item.orderId);
                order.setOrderItemId(item.orderItemId);
                order.setOrderNo(item.orderNo);
                order.setQuantity(item.quantity);
                order.setArea(item.area);
                scheduleBatchOrderMapper.insert(order);
            }

            ScheduleTask task = buildBatchTask(batch, group, group.maxPriority, group.earliestDeliveryDate);
            tasks.add(task);
        }

        // 排程：按优先级+交期排序
        tasks.sort((a, b) -> {
            BigDecimal pa = a.getPriorityScore() != null ? a.getPriorityScore() : BigDecimal.ZERO;
            BigDecimal pb = b.getPriorityScore() != null ? b.getPriorityScore() : BigDecimal.ZERO;
            int cmp = pb.compareTo(pa);
            if (cmp != 0) return cmp;
            Date da = a.getDeliveryDate();
            Date db = b.getDeliveryDate();
            if (da == null && db == null) return 0;
            if (da == null) return 1;
            if (db == null) return -1;
            return da.compareTo(db);
        });

        int scheduled = 0;
        int unscheduled = 0;

        Map<String, List<Equipment>> equipmentByType = new HashMap<>();
        equipmentByType.put("COATING", equipmentMapper.selectAvailableByType("COATING"));
        equipmentByType.put("REWINDING", equipmentMapper.selectAvailableByType("REWINDING"));
        equipmentByType.put("SLITTING", equipmentMapper.selectAvailableByType("SLITTING"));

        Map<String, Map<Long, Date>> nextAvailableByType = new HashMap<>();

        for (ScheduleTask task : tasks) {
            String type = task.getProcessType();
            List<Equipment> equipments = equipmentByType.get(type);
            if (equipments == null || equipments.isEmpty()) {
                task.setStatus("SCHEDULED");
                task.setCanShipBy48h(0);
                scheduleTaskMapper.insert(task);
                updateBatchStatusIfNeeded(task, task.getStatus());
                unscheduled++;
                continue;
            }

            Map<Long, Date> nextAvailable = nextAvailableByType.computeIfAbsent(type, k -> new HashMap<>());
            Equipment selected = null;
            Date bestTime = null;

            for (Equipment eq : equipments) {
                Date next = nextAvailable.get(eq.getId());
                if (next == null) {
                    Date maxEnd = scheduleTaskMapper.selectMaxEndTime(eq.getId(), type);
                    next = maxEnd != null ? maxEnd : todayAtEight();
                    nextAvailable.put(eq.getId(), next);
                }
                if (bestTime == null || next.before(bestTime)) {
                    bestTime = next;
                    selected = eq;
                }
            }

            int durationMin = estimateDurationMinutes(task, perRollArea(task));
            Date start = bestTime != null ? bestTime : todayAtEight();
            Date end = new Date(start.getTime() + durationMin * 60L * 1000L);

            if (end.after(deadline)) {
                task.setStatus("SCHEDULED");
                task.setCanShipBy48h(0);
                task.setPlanStartTime(null);
                task.setPlanEndTime(null);
                task.setPlanDurationMin(durationMin);
                scheduleTaskMapper.insert(task);
                updateBatchStatusIfNeeded(task, task.getStatus());
                unscheduled++;
                continue;
            }

            task.setEquipmentId(selected.getId());
            task.setPlanStartTime(start);
            task.setPlanEndTime(end);
            task.setPlanDurationMin(durationMin);
            task.setStatus("SCHEDULED");
            task.setCanShipBy48h(1);
            scheduleTaskMapper.insert(task);
            updateBatchStatusIfNeeded(task, task.getStatus());
            scheduled++;

            Date next = new Date(end.getTime() + gapMin * 60L * 1000L);
            nextAvailable.put(selected.getId(), next);
        }

        result.put("total", tasks.size());
        result.put("scheduled", scheduled);
        result.put("unscheduled", unscheduled);
        result.put("totalArea", totalArea);
        return result;
    }

    @Override
    public ScheduleTask assignPlan(Long taskId, Long equipmentId, Date startTime) {
        ScheduleTask task = this.getById(taskId);
        if (task == null) {
            return null;
        }
        Equipment equipment = equipmentMapper.selectById(equipmentId);
        if (equipment == null) {
            return null;
        }
        if (task.getProcessType() != null && equipment.getEquipmentType() != null
                && !task.getProcessType().equalsIgnoreCase(equipment.getEquipmentType())) {
            return null;
        }

        Date start = startTime != null ? startTime : new Date();
        Date lastEnd = scheduleTaskMapper.selectMaxEndTime(equipmentId, task.getProcessType());
        if (lastEnd != null && start.before(lastEnd)) {
            start = lastEnd;
        }

        int durationMin = estimateDurationMinutes(task, perRollArea(task));
        Date end = new Date(start.getTime() + durationMin * 60L * 1000L);

        task.setEquipmentId(equipmentId);
        task.setPlanStartTime(start);
        task.setPlanEndTime(end);
        task.setPlanDurationMin(durationMin);
        if (task.getStatus() == null || "UNSCHEDULED".equalsIgnoreCase(task.getStatus())) {
            task.setStatus("SCHEDULED");
        }

        Date deadline = new Date(new Date().getTime() + 48L * 60L * 60L * 1000L);
        task.setCanShipBy48h(end.after(deadline) ? 0 : 1);

        this.updateById(task);
        return task;
    }

    @Override
    @Transactional
    public void createRewindingFromCoatingBatch(Long coatingBatchId) {
        if (coatingBatchId == null) {
            return;
        }
        ScheduleBatch coatingBatch = scheduleBatchMapper.selectById(coatingBatchId);
        if (coatingBatch == null || coatingBatch.getProcessType() == null
                || !"COATING".equalsIgnoreCase(coatingBatch.getProcessType())) {
            return;
        }
        ScheduleBatch existing = scheduleBatchMapper.selectBySourceBatchId(coatingBatchId, "REWINDING");
        if (existing != null) {
            return;
        }
        List<ScheduleBatchOrder> sourceOrders = scheduleBatchOrderMapper.selectByBatchId(coatingBatchId);
        if (sourceOrders == null || sourceOrders.isEmpty()) {
            return;
        }

        BigDecimal totalArea = BigDecimal.ZERO;
        int totalQty = 0;
        for (ScheduleBatchOrder o : sourceOrders) {
            if (o.getArea() != null) {
                totalArea = totalArea.add(o.getArea());
            }
            if (o.getQuantity() != null) {
                totalQty += o.getQuantity();
            }
        }
        if (totalArea.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        ScheduleBatch rewindingBatch = new ScheduleBatch();
        rewindingBatch.setProcessType("REWINDING");
        rewindingBatch.setPlanDate(truncateDate(new Date()));
        rewindingBatch.setMaterialCode(coatingBatch.getMaterialCode());
        rewindingBatch.setMaterialName(coatingBatch.getMaterialName());
        rewindingBatch.setColorCode(coatingBatch.getColorCode());
        rewindingBatch.setThickness(coatingBatch.getThickness());
        rewindingBatch.setWidthMm(coatingBatch.getWidthMm());
        rewindingBatch.setLength(coatingBatch.getLength());
        rewindingBatch.setTotalQty(totalQty);
        rewindingBatch.setTotalArea(totalArea);
        rewindingBatch.setStatus("UNSCHEDULED");
        rewindingBatch.setSourceBatchId(coatingBatchId);
        scheduleBatchMapper.insert(rewindingBatch);

        String batchNo = buildBatchNo(rewindingBatch.getId(), rewindingBatch.getPlanDate(), "REWINDING");
        rewindingBatch.setBatchNo(batchNo);
        scheduleBatchMapper.updateById(rewindingBatch);

        for (ScheduleBatchOrder src : sourceOrders) {
            ScheduleBatchOrder dest = new ScheduleBatchOrder();
            dest.setBatchId(rewindingBatch.getId());
            dest.setOrderId(src.getOrderId());
            dest.setOrderItemId(src.getOrderItemId());
            dest.setOrderNo(src.getOrderNo());
            dest.setQuantity(src.getQuantity());
            dest.setArea(src.getArea());
            scheduleBatchOrderMapper.insert(dest);
        }

        ScheduleTask task = new ScheduleTask();
        task.setBatchId(rewindingBatch.getId());
        task.setBatchNo(rewindingBatch.getBatchNo());
        task.setMaterialCode(rewindingBatch.getMaterialCode());
        task.setMaterialName(rewindingBatch.getMaterialName());
        task.setWidthMm(rewindingBatch.getWidthMm());
        task.setLength(rewindingBatch.getLength());
        task.setArea(totalArea);
        task.setQuantity(totalQty);
        task.setProcessType("REWINDING");
        task.setPriorityScore(BigDecimal.ZERO);
        task.setStatus("UNSCHEDULED");

        Date deadline = new Date(new Date().getTime() + 48L * 60L * 60L * 1000L);
        scheduleSingleTask(task, DEFAULT_GAP_MIN, deadline);
    }

    private void addToBatchGroup(Map<String, BatchGroup> groups,
                                 SalesOrderItem orderItem,
                                 Map<String, Object> full,
                                 BigDecimal perRollArea,
                                 BigDecimal area,
                                 String processType,
                                 BigDecimal priority,
                                 Date planDate) {
        if (area == null || area.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        String key = buildGroupKey(processType, planDate, orderItem.getMaterialCode());

        BatchGroup group = groups.computeIfAbsent(key, k -> {
            BatchGroup g = new BatchGroup();
            g.processType = processType;
            g.planDate = planDate;
            g.materialCode = orderItem.getMaterialCode();
            g.materialName = orderItem.getMaterialName();
            g.colorCode = orderItem.getColorCode();
            g.thickness = orderItem.getThickness();
            g.widthMm = orderItem.getWidth();
            g.length = orderItem.getLength();
            return g;
        });

        int qty = perRollArea.compareTo(BigDecimal.ZERO) > 0
                ? area.divide(perRollArea, 0, BigDecimal.ROUND_UP).intValue()
                : 0;

        group.totalArea = group.totalArea.add(area);
        group.totalQty += qty;
        if (priority != null && priority.compareTo(group.maxPriority) > 0) {
            group.maxPriority = priority;
        }
        Date delivery = full != null ? (Date) full.get("delivery_date") : null;
        if (delivery != null) {
            if (group.earliestDeliveryDate == null || delivery.before(group.earliestDeliveryDate)) {
                group.earliestDeliveryDate = delivery;
            }
        }

        BatchOrderItem item = new BatchOrderItem();
        item.orderId = orderItem.getOrderId();
        item.orderItemId = orderItem.getId();
        item.orderNo = full != null && full.get("order_no") != null ? full.get("order_no").toString() : null;
        item.quantity = qty;
        item.area = area;
        group.items.add(item);
    }

    private ScheduleTask buildBatchTask(ScheduleBatch batch, BatchGroup group, BigDecimal priority, Date deliveryDate) {
        ScheduleTask task = new ScheduleTask();
        task.setBatchId(batch.getId());
        task.setBatchNo(batch.getBatchNo());
        task.setMaterialCode(batch.getMaterialCode());
        task.setMaterialName(batch.getMaterialName());
        task.setWidthMm(batch.getWidthMm());
        task.setLength(batch.getLength());
        task.setArea(batch.getTotalArea());
        task.setQuantity(batch.getTotalQty());
        task.setProcessType(batch.getProcessType());
        task.setPriorityScore(priority != null ? priority : BigDecimal.ZERO);
        task.setDeliveryDate(deliveryDate);
        task.setStatus("UNSCHEDULED");
        return task;
    }

    private void scheduleSingleTask(ScheduleTask task, int gapMin, Date deadline) {
        List<Equipment> equipments = equipmentMapper.selectAvailableByType(task.getProcessType());
        if (equipments == null || equipments.isEmpty()) {
            task.setStatus("SCHEDULED");
            task.setCanShipBy48h(0);
            scheduleTaskMapper.insert(task);
            updateBatchStatusIfNeeded(task, task.getStatus());
            return;
        }

        Equipment selected = null;
        Date bestTime = null;
        for (Equipment eq : equipments) {
            Date next = scheduleTaskMapper.selectMaxEndTime(eq.getId(), task.getProcessType());
            if (next == null) {
                next = todayAtEight();
            }
            if (bestTime == null || next.before(bestTime)) {
                bestTime = next;
                selected = eq;
            }
        }

        int durationMin = estimateDurationMinutes(task, perRollArea(task));
        Date start = bestTime != null ? bestTime : todayAtEight();
        Date end = new Date(start.getTime() + durationMin * 60L * 1000L);

        if (end.after(deadline)) {
            task.setStatus("SCHEDULED");
            task.setCanShipBy48h(0);
            task.setPlanStartTime(null);
            task.setPlanEndTime(null);
            task.setPlanDurationMin(durationMin);
            scheduleTaskMapper.insert(task);
            updateBatchStatusIfNeeded(task, task.getStatus());
            return;
        }

        task.setEquipmentId(selected.getId());
        task.setPlanStartTime(start);
        task.setPlanEndTime(end);
        task.setPlanDurationMin(durationMin);
        task.setStatus("SCHEDULED");
        task.setCanShipBy48h(1);
        scheduleTaskMapper.insert(task);
        updateBatchStatusIfNeeded(task, task.getStatus());

        Date next = new Date(end.getTime() + gapMin * 60L * 1000L);
        if (selected != null) {
            // No persistent cache for single task; next available time is determined by DB on next call.
        }
    }

    private void updateBatchStatusIfNeeded(ScheduleTask task, String status) {
        if (task.getBatchId() == null) {
            return;
        }
        ScheduleBatch batch = new ScheduleBatch();
        batch.setId(task.getBatchId());
        batch.setStatus(status);
        if (task.getBatchNo() != null) {
            batch.setBatchNo(task.getBatchNo());
        }
        scheduleBatchMapper.updateById(batch);
    }

    private String buildGroupKey(String processType, Date planDate, String materialCode) {
        String dateStr = planDate != null ? new java.text.SimpleDateFormat("yyyyMMdd").format(planDate) : "";
        return (processType != null ? processType : "") + "|" + dateStr + "|" +
                (materialCode != null ? materialCode : "");
    }

    private String buildBatchNo(Long id, Date planDate, String processType) {
        String dateStr = planDate != null ? new java.text.SimpleDateFormat("yyyyMMdd").format(planDate) : "";
        String type = processType != null ? processType.toUpperCase() : "";
        String shortType = type.length() >= 2 ? type.substring(0, 2) : type;
        return "B" + dateStr + shortType + String.format("%06d", id != null ? id : 0);
    }

    private Date truncateDate(Date date) {
        Calendar c = Calendar.getInstance();
        c.setTime(date != null ? date : new Date());
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    private ScheduleTask buildTask(SalesOrderItem orderItem,
                                   Map<String, Object> full,
                                   BigDecimal widthMm,
                                   BigDecimal lengthM,
                                   BigDecimal perRollArea,
                                   BigDecimal area,
                                   String processType,
                                   BigDecimal priority,
                                   Integer lockStock) {
        ScheduleTask task = new ScheduleTask();
        task.setOrderId(orderItem.getOrderId());
        task.setOrderItemId(orderItem.getId());
        task.setOrderNo(full != null && full.get("order_no") != null ? full.get("order_no").toString() : null);
        task.setMaterialCode(orderItem.getMaterialCode());
        task.setMaterialName(orderItem.getMaterialName());
        task.setWidthMm(widthMm);
        task.setLength(lengthM);
        task.setArea(area);
        task.setProcessType(processType);
        task.setPriorityScore(priority != null ? priority : BigDecimal.ZERO);
        task.setDeliveryDate(full != null ? (Date) full.get("delivery_date") : null);
        task.setLockStock(lockStock != null ? lockStock : 0);

        int qty = perRollArea.compareTo(BigDecimal.ZERO) > 0
                ? area.divide(perRollArea, 0, BigDecimal.ROUND_UP).intValue()
                : 0;
        task.setQuantity(qty);
        return task;
    }

    private BigDecimal computePerRollArea(BigDecimal widthMm, BigDecimal lengthM) {
        if (widthMm == null || lengthM == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal widthM = widthMm.divide(new BigDecimal(1000), 6, BigDecimal.ROUND_HALF_UP);
        return widthM.multiply(lengthM);
    }

    private BigDecimal perRollArea(ScheduleTask task) {
        BigDecimal widthMm = task.getWidthMm() != null ? task.getWidthMm() : BigDecimal.ZERO;
        BigDecimal lengthM = task.getLength() != null ? task.getLength() : BigDecimal.ZERO;
        return computePerRollArea(widthMm, lengthM);
    }

    private int estimateDurationMinutes(ScheduleTask task, BigDecimal perRollArea) {
        String type = task.getProcessType();
        if ("COATING".equalsIgnoreCase(type)) {
            BigDecimal widthM = (task.getWidthMm() != null ? task.getWidthMm() : BigDecimal.ZERO)
                    .divide(new BigDecimal(1000), 6, BigDecimal.ROUND_HALF_UP);
            double rate = Math.max(COATING_SPEED_MPM * (widthM.doubleValue() > 0 ? widthM.doubleValue() : 1.0), 1.0);
            double area = task.getArea() != null ? task.getArea().doubleValue() : 0.0;
            return Math.max((int)Math.ceil(area / rate), 1);
        }
        int speed = "SLITTING".equalsIgnoreCase(type) ? SLIT_SPEED_MPM : REWIND_SPEED_MPM;
        int qty = task.getQuantity() != null ? task.getQuantity() : 0;
        BigDecimal lengthM = task.getLength() != null ? task.getLength() : BigDecimal.ZERO;
        double minutes = qty * lengthM.doubleValue() / Math.max(speed, 1);
        return Math.max((int)Math.ceil(minutes), 1);
    }

    private Date todayAtEight() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 8);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }
}
