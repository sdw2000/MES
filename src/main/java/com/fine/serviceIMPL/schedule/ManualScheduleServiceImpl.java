package com.fine.serviceIMPL.schedule;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.schedule.ManualScheduleCoatingAllocationMapper;
import com.fine.Dao.schedule.ManualScheduleMapper;
import com.fine.Dao.production.EquipmentMapper;
import com.fine.Dao.production.EquipmentOccupationMapper;
import com.fine.modle.schedule.ManualScheduleCoatingAllocation;
import com.fine.modle.schedule.ManualSchedule;
import com.fine.modle.stock.TapeInboundRequest;
import com.fine.modle.stock.TapeStock;
import com.fine.model.production.Equipment;
import com.fine.model.production.EquipmentScheduleConfig;
import com.fine.model.production.EquipmentOccupation;
import com.fine.model.production.ProductionStaff;
import com.fine.model.production.ProcessParams;
import com.fine.model.production.RewindingProcessParams;
import com.fine.model.production.SlittingProcessParams;
import com.fine.model.production.EquipmentDailyStatus;
import com.fine.service.production.EquipmentScheduleConfigService;
import com.fine.service.production.EquipmentDailyPlanningService;
import com.fine.service.production.ProductionStaffService;
import com.fine.service.schedule.ManualScheduleService;
import com.fine.service.production.ProcessParamsService;
import com.fine.service.production.RewindingProcessParamsService;
import com.fine.service.production.SlittingProcessParamsService;
import com.fine.service.stock.TapeStockService;
import com.fine.Dao.stock.TapeStockMapper;
import com.fine.Dao.stock.ScheduleMaterialLockMapper;
import com.fine.Dao.rd.TapeFormulaMapper;
import com.fine.Dao.production.SalesOrderMapper;
import com.fine.Dao.production.EquipmentDailyStatusMapper;
import com.fine.Dao.SalesOrderItemMapper;
import com.fine.modle.SalesOrderItem;
import com.fine.modle.SalesOrder;
import com.fine.modle.rd.TapeFormula;
import com.fine.modle.rd.TapeFormulaItem;
import com.fine.modle.stock.ScheduleMaterialLock;
import com.fine.modle.schedule.SchedulePlan;
import com.fine.Utils.RedisCache;
import com.fine.service.schedule.SchedulePlanService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 手动排程服务实现
 */
@Service
public class ManualScheduleServiceImpl extends ServiceImpl<ManualScheduleMapper, ManualSchedule> implements ManualScheduleService {

    private static final String URGENT_PREEMPT_CONFIG_CACHE_KEY = "mes:schedule:urgent-preempt:config";
    private static final long DEFAULT_PREEMPT_START_PROTECT_WINDOW_MINUTES = 240L;
    private static final BigDecimal DEFAULT_PREEMPT_MIN_PROTECT_AREA = new BigDecimal("300");
    private static final BigDecimal DEFAULT_PREEMPT_MIN_PROTECT_RATIO = new BigDecimal("0.20");

    /** 抢占保护：临近开工窗口（分钟） */
    @Value("${mes.schedule.urgent-preempt.start-protect-window-minutes:240}")
    private long preemptStartProtectWindowMinutes;
    /** 抢占保护：每个被抢占订单+料号最小保底面积（㎡） */
    @Value("${mes.schedule.urgent-preempt.min-protect-area:300}")
    private BigDecimal preemptMinProtectArea;
    /** 抢占保护：每个被抢占订单+料号保底比例 */
    @Value("${mes.schedule.urgent-preempt.min-protect-ratio:0.20}")
    private BigDecimal preemptMinProtectRatio;
    
    @Autowired
    private ManualScheduleMapper scheduleMapper;
    
    @Autowired
    private TapeStockService tapeStockService;

    @Autowired
    private TapeStockMapper tapeStockMapper;

    @Autowired
    private ScheduleMaterialLockMapper scheduleMaterialLockMapper;

    @Autowired
    private SalesOrderMapper salesOrderMapper;

    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;

    @Autowired
    private SchedulePlanService schedulePlanService;

    @Autowired
    private ManualScheduleCoatingAllocationMapper manualScheduleCoatingAllocationMapper;

    @Autowired
    private EquipmentMapper equipmentMapper;

    @Autowired
    private ProcessParamsService processParamsService;

    @Autowired
    private RewindingProcessParamsService rewindingProcessParamsService;

    @Autowired
    private SlittingProcessParamsService slittingProcessParamsService;

    @Autowired
    private EquipmentOccupationMapper equipmentOccupationMapper;

    @Autowired
    private EquipmentScheduleConfigService equipmentScheduleConfigService;

    @Autowired
    private TapeFormulaMapper tapeFormulaMapper;

    @Autowired
    private ProductionStaffService productionStaffService;

    @Autowired
    private EquipmentDailyPlanningService equipmentDailyPlanningService;

    @Autowired
    private EquipmentDailyStatusMapper equipmentDailyStatusMapper;

    @Autowired
    private RedisCache redisCache;

    private void ensureOrderDetailSchedulable(Long orderDetailId) {
        if (orderDetailId == null) {
            throw new RuntimeException("orderDetailId 不能为空");
        }
        int cnt = scheduleMapper.countSchedulableOrderDetail(orderDetailId);
        if (cnt <= 0) {
            throw new RuntimeException("订单已取消/关闭/完成，不能继续排程");
        }
    }
    
    @Override
    public List<Map<String, Object>> getPendingOrders() {
        return getPendingOrders(false);
    }

    @Override
    public List<Map<String, Object>> getPendingOrders(boolean includeCompleted) {
        List<Map<String, Object>> records = includeCompleted
                ? scheduleMapper.selectPendingOrdersIncludeCompleted()
                : scheduleMapper.selectPendingOrders();
        enrichRouteFields(records);
        return records;
    }

    @Override
    public IPage<Map<String, Object>> getPendingOrdersPage(long current, long size) {
        return getPendingOrdersPage(current, size, false);
    }

    @Override
    public IPage<Map<String, Object>> getPendingOrdersPage(long current, long size, boolean includeCompleted) {
        return getPendingOrdersPage(current, size, includeCompleted, null);
        }

        @Override
        public IPage<Map<String, Object>> getPendingOrdersPage(long current, long size, boolean includeCompleted, String orderNo) {
        Page<Map<String, Object>> page = new Page<>(current, size);
        String keyword = orderNo == null ? null : orderNo.trim();
        List<Map<String, Object>> records = includeCompleted
            ? scheduleMapper.selectPendingOrdersPageIncludeCompleted(page, keyword)
            : scheduleMapper.selectPendingOrdersPage(page);
        enrichRouteFields(records);
        Long total = includeCompleted
            ? scheduleMapper.selectPendingOrdersCountIncludeCompleted(keyword)
                : scheduleMapper.selectPendingOrdersCount();
        page.setRecords(records);
        page.setTotal(total == null ? 0 : total);
        return page;
    }
    
    @Override
    public List<Map<String, Object>> getCoatingCompletedOrders() {
        return scheduleMapper.selectCoatingCompletedOrders();
    }

    @Override
    public IPage<Map<String, Object>> getCoatingCompletedOrdersPage(long current, long size) {
        Page<Map<String, Object>> page = new Page<>(current, size);
        List<Map<String, Object>> records = scheduleMapper.selectCoatingCompletedOrdersPage(page);
        Long total = scheduleMapper.selectCoatingCompletedOrdersCount();
        page.setRecords(records);
        page.setTotal(total == null ? 0 : total);
        return page;
    }
    
    @Override
    public Map<String, Object> matchStock(String materialCode, Integer width, Integer thickness, Integer requiredQty) {
        // 查询可用库存（先进先出排序）
        List<Map<String, Object>> stockList = scheduleMapper.selectAvailableStock(materialCode, width, thickness);
        
        Map<String, Object> result = new HashMap<>();
        result.put("stockList", stockList);
        
        // 计算总可用卷数与面积
        int totalAvailableRolls = stockList.stream()
            .mapToInt(s -> ((Number) s.get("available_rolls")).intValue())
            .sum();
        double totalAvailableArea = stockList.stream()
            .mapToDouble(s -> ((Number) s.get("available_area")).doubleValue())
            .sum();
        
        result.put("totalAvailable", totalAvailableRolls);
        result.put("totalAvailableRolls", totalAvailableRolls);
        result.put("totalAvailableArea", totalAvailableArea);
        result.put("requiredQty", requiredQty);
        result.put("isSufficient", totalAvailableRolls >= requiredQty);
        result.put("shortage", Math.max(0, requiredQty - totalAvailableRolls));
        return result;
    }

    @Override
    public Map<String, Object> calculateCoatingRequirement(String orderNo, String materialCode, BigDecimal plannedArea) {
        if (materialCode == null || materialCode.trim().isEmpty()) {
            throw new RuntimeException("materialCode 不能为空");
        }

        Integer thickness = null;
        if (orderNo != null && !orderNo.trim().isEmpty()) {
            List<Map<String, Object>> orders = scheduleMapper.selectPendingOrders();
            for (Map<String, Object> order : orders) {
                Object orderNoObj = order.get("order_no");
                Object materialCodeObj = order.get("material_code");
                if (orderNoObj != null && materialCodeObj != null
                        && orderNo.equals(orderNoObj.toString())
                        && materialCode.equals(materialCodeObj.toString())) {
                    Object thicknessObj = order.get("thickness");
                    if (thicknessObj instanceof Number) {
                        thickness = ((Number) thicknessObj).intValue();
                    } else if (thicknessObj instanceof String) {
                        thickness = Integer.parseInt((String) thicknessObj);
                    }
                    break;
                }
            }
        }

        if (thickness == null) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("material_code", materialCode);
            empty.put("thickness", null);
            empty.put("total_required_qty", 0);
            empty.put("total_required_area", 0);
            empty.put("details", Collections.emptyList());
            empty.put("remarkText", "");
            return empty;
        }

        List<Map<String, Object>> details = scheduleMapper.selectCoatingRequirementDetails(orderNo, materialCode, thickness);
        confirmCoatingAggregation(orderNo, materialCode, thickness, details);

        applyPlannedAreaAllocation(details, plannedArea);

        BigDecimal totalRequiredQty = BigDecimal.ZERO;
        BigDecimal totalRequiredArea = BigDecimal.ZERO;
        if (details != null) {
            for (Map<String, Object> d : details) {
                BigDecimal qty = toBigDecimal(d.get("remaining_qty"));
                BigDecimal area = toBigDecimal(d.get("remaining_area"));
                if (qty != null && qty.compareTo(BigDecimal.ZERO) > 0) {
                    totalRequiredQty = totalRequiredQty.add(qty);
                }
                if (area != null && area.compareTo(BigDecimal.ZERO) > 0) {
                    totalRequiredArea = totalRequiredArea.add(area);
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("material_code", materialCode);
        result.put("thickness", thickness);
        result.put("total_required_qty", totalRequiredQty.setScale(2, RoundingMode.HALF_UP));
        result.put("total_required_area", totalRequiredArea.setScale(2, RoundingMode.HALF_UP));
        result.put("confirmed", true);

        result.put("details", details);
        result.put("remarkText", buildCoatingRemark(details));
        result.put("plannedArea", plannedArea == null ? null : plannedArea);
        return result;
    }

    private void confirmCoatingAggregation(String orderNo,
                                           String materialCode,
                                           Integer thickness,
                                           List<Map<String, Object>> details) {
        if (orderNo == null || orderNo.trim().isEmpty()) {
            return;
        }
        if (materialCode == null || materialCode.trim().isEmpty()) {
            return;
        }
        if (thickness == null) {
            return;
        }
        if (details == null || details.isEmpty()) {
            throw new RuntimeException("涂布聚合校验失败：未查询到需求明细，请刷新后重试");
        }

        String targetOrderNo = orderNo.trim();
        String targetMaterial = materialCode.trim();

        for (Map<String, Object> d : details) {
            if (d == null) {
                continue;
            }
            String dOrderNo = str(d.get("order_no"));
            String dMaterial = str(d.get("material_code"));
            Integer dThickness = toInteger(d.get("thickness"));
            if (targetOrderNo.equalsIgnoreCase(dOrderNo)
                    && targetMaterial.equalsIgnoreCase(dMaterial)
                    && thickness.equals(dThickness)) {
                return;
            }
        }

        throw new RuntimeException("涂布聚合校验失败：当前订单未纳入统一口径，请刷新后重试");
    }

    private String buildCoatingRemark(List<Map<String, Object>> details) {
        if (details == null || details.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Map<String, Object> d : details) {
            double includedArea = 0;
            Object includedAreaObj = d.get("included_area");
            if (includedAreaObj instanceof Number) {
                includedArea = ((Number) includedAreaObj).doubleValue();
            } else if (includedAreaObj != null) {
                try {
                    includedArea = Double.parseDouble(String.valueOf(includedAreaObj));
                } catch (Exception ignored) {
                }
            }
            if (includedArea <= 0) {
                continue;
            }
            String orderNo = d.get("order_no") == null ? "-" : String.valueOf(d.get("order_no"));
            double area = 0;
            Object areaObj = d.get("remaining_area");
            if (areaObj instanceof Number) {
                area = ((Number) areaObj).doubleValue();
            } else if (areaObj != null) {
                try {
                    area = Double.parseDouble(String.valueOf(areaObj));
                } catch (Exception ignored) {
                }
            }
            parts.add(orderNo + "(" + String.format("%.2f", area) + "㎡)");
        }
        return String.join("，", parts);
    }

    private void applyPlannedAreaAllocation(List<Map<String, Object>> details, BigDecimal plannedArea) {
        if (details == null) {
            return;
        }
        double remain = plannedArea == null ? Double.MAX_VALUE : Math.max(0, plannedArea.doubleValue());
        int sortNo = 1;
        for (Map<String, Object> d : details) {
            double area = 0;
            Object areaObj = d.get("remaining_area");
            if (areaObj instanceof Number) {
                area = ((Number) areaObj).doubleValue();
            } else if (areaObj != null) {
                try {
                    area = Double.parseDouble(String.valueOf(areaObj));
                } catch (Exception ignored) {
                }
            }
            double includedArea = Math.min(area, remain);
            int included = includedArea > 0 ? 1 : 0;
            d.put("included_area", BigDecimal.valueOf(includedArea).setScale(2, BigDecimal.ROUND_HALF_UP));
            d.put("included_flag", included);
            d.put("sort_no", sortNo++);
            if (plannedArea != null) {
                remain = Math.max(0, remain - includedArea);
            }
        }
    }

    @Override
    @Transactional
    public boolean saveCoatingAllocationDetails(Long scheduleId, List<Map<String, Object>> details) {
        if (details != null && !details.isEmpty()) {
            for (Map<String, Object> d : details) {
                Integer includedFlag = toInteger(d.get("included_flag"));
                BigDecimal includedArea = toBigDecimal(d.get("included_area"));
                if (includedFlag == null || includedFlag != 1 || includedArea == null || includedArea.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                String orderNo = d.get("order_no") == null ? null : String.valueOf(d.get("order_no"));
                String materialCode = d.get("material_code") == null ? null : String.valueOf(d.get("material_code"));
                BigDecimal thickness = toBigDecimal(d.get("thickness"));
                if (orderNo == null || materialCode == null || thickness == null) {
                    continue;
                }
                int overlap = scheduleMapper.countActiveCoatingAllocationOverlap(orderNo, materialCode, thickness, scheduleId);
                if (overlap > 0) {
                    throw new RuntimeException("检测到重复涂布覆盖：订单" + orderNo + " 已在其他有效涂布计划中，请刷新后重试");
                }
            }
        }

        manualScheduleCoatingAllocationMapper.deleteByScheduleId(scheduleId);
        if (details == null || details.isEmpty()) {
            return true;
        }
        for (Map<String, Object> d : details) {
            ManualScheduleCoatingAllocation rec = new ManualScheduleCoatingAllocation();
            rec.setScheduleId(scheduleId);
            rec.setOrderNo(d.get("order_no") == null ? null : String.valueOf(d.get("order_no")));
            rec.setMaterialCode(d.get("material_code") == null ? null : String.valueOf(d.get("material_code")));
            rec.setThickness(toBigDecimal(d.get("thickness")));
            rec.setRemainingQty(toInteger(d.get("remaining_qty")));
            rec.setRemainingArea(toBigDecimal(d.get("remaining_area")));
            rec.setIncludedArea(toBigDecimal(d.get("included_area")));
            rec.setIncludedFlag(toInteger(d.get("included_flag")));
            rec.setPriorityScore(toBigDecimal(d.get("priority_score")));
            rec.setSortNo(toInteger(d.get("sort_no")));
            rec.setCreatedAt(LocalDateTime.now());
            manualScheduleCoatingAllocationMapper.insert(rec);
        }
        return true;
    }

    private BigDecimal toBigDecimal(Object obj) {
        if (obj == null) return null;
        if (obj instanceof BigDecimal) return (BigDecimal) obj;
        if (obj instanceof Number) return BigDecimal.valueOf(((Number) obj).doubleValue());
        try {
            return new BigDecimal(String.valueOf(obj));
        } catch (Exception e) {
            return null;
        }
    }

    private Integer toInteger(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            String val = String.valueOf(obj).trim();
            if (val.isEmpty()) {
                return null;
            }
            return new BigDecimal(val).setScale(0, java.math.RoundingMode.HALF_UP).intValue();
        } catch (Exception e) {
            return null;
        }
    }

    private void enrichRouteFields(List<Map<String, Object>> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        Set<Long> orderDetailIds = new HashSet<>();
        for (Map<String, Object> row : records) {
            Long orderDetailId = toLong(row == null ? null : (row.containsKey("order_detail_id") ? row.get("order_detail_id") : row.get("orderDetailId")));
            if (orderDetailId != null) {
                orderDetailIds.add(orderDetailId);
            }
        }
        Map<Long, Double> lockedAreaByDetail = loadLockedAreaByOrderDetail(orderDetailIds);

        for (Map<String, Object> row : records) {
            if (row == null) {
                continue;
            }
            Integer width = toInteger(row.get("width"));
            Integer length = toInteger(row.get("length"));
            String routeType = resolveRouteType(width, length);
            row.put("route_type", routeType);
            row.put("route_name", routeName(routeType));

            Long orderDetailId = toLong(row.containsKey("order_detail_id") ? row.get("order_detail_id") : row.get("orderDetailId"));
            double lockedArea = orderDetailId == null ? 0D : lockedAreaByDetail.getOrDefault(orderDetailId, 0D);

            double singleArea = 0D;
            if (width != null && width > 0 && length != null && length > 0) {
                singleArea = (width / 1000.0) * length;
            }
            double coatingReportedQty = toDouble(row.get("coating_report_qty"));
            double producedArea = singleArea > 0 ? (coatingReportedQty * singleArea) : 0D;
            double unlockedArea = Math.max(0D, producedArea - lockedArea);

            row.put("locked_area_total", round2(lockedArea));
            row.put("produced_area", round2(producedArea));
            row.put("unlocked_area", round2(unlockedArea));
            row.put("lock_status", resolveLockStatus(producedArea, lockedArea));
        }
    }

    private Map<Long, Double> loadLockedAreaByOrderDetail(Set<Long> orderDetailIds) {
        Map<Long, Double> result = new HashMap<>();
        if (orderDetailIds == null || orderDetailIds.isEmpty()) {
            return result;
        }

        List<ManualSchedule> schedules = this.list(new LambdaQueryWrapper<ManualSchedule>()
                .in(ManualSchedule::getOrderDetailId, orderDetailIds)
                .in(ManualSchedule::getStatus, Arrays.asList("PENDING", "COATING_SCHEDULED", "REWINDING_SCHEDULED", "CONFIRMED")));

        for (ManualSchedule schedule : schedules) {
            if (schedule == null || schedule.getOrderDetailId() == null) {
                continue;
            }
            double stockLockedArea = sumStockAllocationArea(schedule.getStockAllocations());
            double rewindingLockedArea = schedule.getRewindingScheduledArea() == null ? 0D : schedule.getRewindingScheduledArea().doubleValue();
            double lockedForSchedule = Math.max(stockLockedArea, rewindingLockedArea);
            if (lockedForSchedule <= 0) {
                continue;
            }
            result.put(schedule.getOrderDetailId(), result.getOrDefault(schedule.getOrderDetailId(), 0D) + lockedForSchedule);
        }
        return result;
    }

    private double sumStockAllocationArea(String stockAllocationsJson) {
        if (stockAllocationsJson == null || stockAllocationsJson.trim().isEmpty()) {
            return 0D;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> list = mapper.readValue(stockAllocationsJson, List.class);
            if (list == null || list.isEmpty()) {
                return 0D;
            }
            double total = 0D;
            for (Map<String, Object> item : list) {
                if (item == null) {
                    continue;
                }
                total += toDouble(item.get("area"));
            }
            return total;
        } catch (Exception e) {
            return 0D;
        }
    }

    private double toDouble(Object obj) {
        if (obj == null) {
            return 0D;
        }
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(obj));
        } catch (Exception e) {
            return 0D;
        }
    }

    private Long toLong(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(obj));
        } catch (Exception e) {
            return null;
        }
    }

    private double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    private String resolveLockStatus(double producedArea, double lockedArea) {
        if (producedArea <= 0) {
            return "NOT_STARTED";
        }
        if (lockedArea <= 0) {
            return "UNLOCKED";
        }
        if (lockedArea + 0.0001 >= producedArea) {
            return "LOCKED";
        }
        return "PARTIAL";
    }

    private String resolveRouteType(Integer width, Integer length) {
        int w = width == null ? 0 : width;
        int l = length == null ? 0 : length;
        if (w > 450 && l < 1500) {
            return "REWINDING_SHIP";
        }
        if (w > 450 && l > 1500) {
            return "COATING_SHIP";
        }
        return "SLITTING_SHIP";
    }

    private String routeName(String routeType) {
        if ("REWINDING_SHIP".equalsIgnoreCase(routeType)) {
            return "复卷出货";
        }
        if ("COATING_SHIP".equalsIgnoreCase(routeType)) {
            return "母卷出货";
        }
        return "分切出货";
    }

    @Override
    public List<Map<String, Object>> getCoatingSchedules() {
        return scheduleMapper.selectCoatingSchedules();
    }

    @Override
    public IPage<Map<String, Object>> getCoatingSchedulesPage(long current, long size) {
        Page<Map<String, Object>> page = new Page<>(current, size);
        List<Map<String, Object>> records = scheduleMapper.selectCoatingSchedulesPage(page);
        Long total = scheduleMapper.selectCoatingSchedulesCount();
        page.setRecords(records);
        page.setTotal(total == null ? 0 : total);
        return page;
    }

    @Override
    public List<Map<String, Object>> getSlittingSchedules() {
        return scheduleMapper.selectSlittingSchedules();
    }

    @Override
    public IPage<Map<String, Object>> getSlittingSchedulesPage(long current, long size) {
        return getSlittingSchedulesPage(current, size, null);
    }

    @Override
    public IPage<Map<String, Object>> getSlittingSchedulesPage(long current, long size, String orderNo) {
        Page<Map<String, Object>> page = new Page<>(current, size);
        List<Map<String, Object>> records = scheduleMapper.selectSlittingSchedulesPage(page, orderNo);
        Long total = scheduleMapper.selectSlittingSchedulesCount(orderNo);
        page.setRecords(records);
        page.setTotal(total == null ? 0 : total);
        return page;
    }

    @Override
    public List<Map<String, Object>> getRewindingSchedules() {
        return scheduleMapper.selectRewindingSchedules();
    }

    @Override
    public IPage<Map<String, Object>> getRewindingSchedulesPage(long current, long size) {
        Page<Map<String, Object>> page = new Page<>(current, size);
        List<Map<String, Object>> records = scheduleMapper.selectRewindingSchedulesPage(page);
        Long total = scheduleMapper.selectRewindingSchedulesCount();
        page.setRecords(records);
        page.setTotal(total == null ? 0 : total);
        return page;
    }

    @Override
    @Transactional
    public boolean reportProcessWork(Long scheduleId,
                                     Long orderDetailId,
                                     String processType,
                                     String startTime,
                                     String endTime,
                                     BigDecimal producedQty,
                                     Boolean proceedNextProcess,
                                     List<Map<String, Object>> producedRolls,
                                     List<Map<String, Object>> materialIssues,
                                     String operator,
                                     String remark) {
        String normalizedProcessType = normalizeProcessType(processType);
        ManualSchedule schedule = resolveScheduleForReport(scheduleId, orderDetailId, normalizedProcessType);
        if (schedule == null || schedule.getId() == null) {
            throw new RuntimeException("报工缺少有效排程");
        }
        Long actualScheduleId = schedule.getId();

        LocalDateTime start = parseDateTime(startTime);
        LocalDateTime end = parseDateTime(endTime);
        if (start == null || end == null) {
            throw new RuntimeException("开始时间和结束时间不能为空");
        }
        if (end.isBefore(start)) {
            throw new RuntimeException("结束时间不能早于开始时间");
        }
        if (producedQty == null || producedQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("生产数量必须大于0");
        }

        String operatorName = (operator == null || operator.trim().isEmpty()) ? "unknown" : operator.trim();
        int proceedNext = (proceedNextProcess == null || proceedNextProcess) ? 1 : 0;
        int affected = scheduleMapper.insertProcessReport(actualScheduleId, normalizedProcessType, start, end, producedQty, operatorName, proceedNext, remark);
        if (affected <= 0) {
            return false;
        }

        Long reportId = scheduleMapper.selectLastInsertId();
        if (reportId == null || reportId <= 0) {
            throw new RuntimeException("报工记录ID获取失败");
        }

        if ("COATING".equals(normalizedProcessType) && producedRolls != null && !producedRolls.isEmpty()) {
            persistCoatingRolls(actualScheduleId, reportId, producedRolls);
            autoCreateCoatingOrderLocks(actualScheduleId, reportId, producedRolls);
        }

        if (materialIssues != null && !materialIssues.isEmpty()) {
            persistMaterialIssues(actualScheduleId, reportId, normalizedProcessType, operatorName, materialIssues);
        }

        autoCreateInboundRequestFromReport(schedule, normalizedProcessType, reportId, producedQty, producedRolls, operatorName);
        refreshOrderCompletionAfterReport(schedule.getOrderDetailId());
        return true;
    }

    @Override
    @Transactional
    public boolean issueProcessMaterial(Long scheduleId,
                                        Long orderDetailId,
                                        String processType,
                                        List<Map<String, Object>> materialIssues,
                                        String operator,
                                        String remark) {
        if (materialIssues == null || materialIssues.isEmpty()) {
            throw new RuntimeException("materialIssues 不能为空");
        }
        String normalizedProcessType = normalizeProcessType(processType);
        ManualSchedule schedule = resolveScheduleForReport(scheduleId, orderDetailId, normalizedProcessType);
        if (schedule == null || schedule.getId() == null) {
            throw new RuntimeException("领料登记缺少有效排程");
        }

        String operatorName = (operator == null || operator.trim().isEmpty()) ? "unknown" : operator.trim();
        LocalDateTime now = LocalDateTime.now();
        String finalRemark = (remark == null || remark.trim().isEmpty()) ? "仅领料登记" : "仅领料登记; " + remark.trim();
        int affected = scheduleMapper.insertProcessReport(
                schedule.getId(),
                normalizedProcessType,
                now,
                now,
                BigDecimal.ZERO,
                operatorName,
                1,
                finalRemark
        );
        if (affected <= 0) {
            return false;
        }
        Long reportId = scheduleMapper.selectLastInsertId();
        if (reportId == null || reportId <= 0) {
            throw new RuntimeException("领料登记ID获取失败");
        }

        persistMaterialIssues(schedule.getId(), reportId, normalizedProcessType, operatorName, materialIssues);
        return true;
    }

    private void autoCreateInboundRequestFromReport(ManualSchedule schedule,
                                                    String processType,
                                                    Long reportId,
                                                    BigDecimal producedQty,
                                                    List<Map<String, Object>> producedRolls,
                                                    String operatorName) {
        if (schedule == null || reportId == null) {
            return;
        }

        SalesOrderItem item = null;
        if (schedule.getOrderDetailId() != null) {
            item = salesOrderItemMapper.selectById(schedule.getOrderDetailId());
        }

        String materialCode = item != null ? item.getMaterialCode() : null;
        String materialName = item != null ? item.getMaterialName() : null;
        Integer thickness = item != null && item.getThickness() != null ? item.getThickness().intValue() : null;

        Integer width = item != null && item.getWidth() != null ? item.getWidth().intValue() : null;
        Integer length = item != null && item.getLength() != null ? item.getLength().intValue() : null;

        int rolls = 0;
        if ("COATING".equals(processType)) {
            List<Map<String, Object>> validRolls = producedRolls == null
                    ? Collections.emptyList()
                    : producedRolls.stream()
                        .filter(Objects::nonNull)
                        .filter(r -> {
                            BigDecimal area = toBigDecimal(r.get("area"));
                            return area != null && area.compareTo(BigDecimal.ZERO) > 0;
                        })
                        .collect(java.util.stream.Collectors.toList());

            if (!validRolls.isEmpty()) {
                int idx = 1;
                for (Map<String, Object> roll : validRolls) {
                    String rollCode = stringVal(roll.get("rollCode"));
                    if (rollCode == null || rollCode.trim().isEmpty()) {
                        rollCode = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"))
                                + "-COATING-"
                                + schedule.getId()
                                + "-"
                                + reportId
                                + "-"
                                + idx;
                    }

                    Integer rollWidth = toInteger(roll.get("widthMm"));
                    Integer rollLength = toInteger(roll.get("lengthM"));
                    Integer finalWidth = (rollWidth != null && rollWidth > 0) ? rollWidth : width;
                    Integer finalLength = (rollLength != null && rollLength > 0) ? rollLength : length;

                    String finalMaterialCode = (materialCode == null || materialCode.trim().isEmpty()) ? "AUTO-" + processType : materialCode;
                    String finalMaterialName = (materialName == null || materialName.trim().isEmpty()) ? processType + "自动入库" : materialName;

                    TapeInboundRequest inboundRequest = new TapeInboundRequest();
                    inboundRequest.setMaterialCode(finalMaterialCode);
                    inboundRequest.setProductName(finalMaterialName);
                    // 生产批次号改为母卷号
                    inboundRequest.setBatchNo(rollCode);
                    inboundRequest.setThickness(thickness);
                    inboundRequest.setWidth(finalWidth);
                    inboundRequest.setLength(finalLength);
                    inboundRequest.setRolls(1);
                    inboundRequest.setLocation("待上架");
                    inboundRequest.setApplicant((operatorName == null || operatorName.trim().isEmpty()) ? "system" : operatorName);
                    inboundRequest.setApplyDept("生产部");
                    inboundRequest.setProdDate(LocalDate.now());
                    inboundRequest.setProdYear(LocalDate.now().getYear() % 100);
                    inboundRequest.setProdMonth(LocalDate.now().getMonthValue());
                    inboundRequest.setProdDay(LocalDate.now().getDayOfMonth());
                    inboundRequest.setRemark("自动生成-工序报工入库申请; process=" + processType
                            + ", scheduleId=" + schedule.getId()
                            + ", reportId=" + reportId
                            + ", orderNo=" + (schedule.getOrderNo() == null ? "-" : schedule.getOrderNo())
                            + ", motherRollNo=" + rollCode);

                    tapeStockService.createInboundRequest(inboundRequest);
                    idx++;
                }
                return;
            }
        } else {
            int qty = producedQty == null ? 0 : producedQty.setScale(0, java.math.RoundingMode.HALF_UP).intValue();
            rolls = Math.max(qty, 0);
        }

        if (rolls <= 0) {
            return;
        }

        String batchNo = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"))
            + "-"
            + processType
            + "-"
            + schedule.getId()
            + "-"
            + reportId;

        if (materialCode == null || materialCode.trim().isEmpty()) {
            materialCode = "AUTO-" + processType;
        }
        if (materialName == null || materialName.trim().isEmpty()) {
            materialName = processType + "自动入库";
        }

        TapeInboundRequest inboundRequest = new TapeInboundRequest();
        inboundRequest.setMaterialCode(materialCode);
        inboundRequest.setProductName(materialName);
        inboundRequest.setBatchNo(batchNo);
        inboundRequest.setThickness(thickness);
        inboundRequest.setWidth(width);
        inboundRequest.setLength(length);
        inboundRequest.setRolls(rolls);
        inboundRequest.setLocation("待上架");
        inboundRequest.setApplicant((operatorName == null || operatorName.trim().isEmpty()) ? "system" : operatorName);
        inboundRequest.setApplyDept("生产部");
        inboundRequest.setProdDate(LocalDate.now());
        inboundRequest.setProdYear(LocalDate.now().getYear() % 100);
        inboundRequest.setProdMonth(LocalDate.now().getMonthValue());
        inboundRequest.setProdDay(LocalDate.now().getDayOfMonth());
        inboundRequest.setRemark("自动生成-工序报工入库申请; process=" + processType
                + ", scheduleId=" + schedule.getId()
                + ", reportId=" + reportId
                + ", orderNo=" + (schedule.getOrderNo() == null ? "-" : schedule.getOrderNo()));

        tapeStockService.createInboundRequest(inboundRequest);
    }

    @Override
    public List<Map<String, Object>> getProcessWorkReports(Long scheduleId, String processType) {
        if (scheduleId == null) {
            throw new RuntimeException("scheduleId 不能为空");
        }
        String normalizedProcessType = normalizeProcessType(processType);
        return scheduleMapper.selectProcessReports(scheduleId, normalizedProcessType);
    }

    @Override
    @Transactional
    public boolean updateProcessWorkReport(Long reportId,
                                           String startTime,
                                           String endTime,
                                           BigDecimal producedQty,
                                           Boolean proceedNextProcess,
                                           String operator,
                                           String remark) {
        if (reportId == null) {
            throw new RuntimeException("reportId 不能为空");
        }
        Map<String, Object> report = scheduleMapper.selectProcessReportById(reportId);
        if (report == null || report.isEmpty()) {
            throw new RuntimeException("报工记录不存在");
        }

        LocalDateTime start = parseDateTime(startTime);
        LocalDateTime end = parseDateTime(endTime);
        if (start == null || end == null) {
            throw new RuntimeException("开始时间和结束时间不能为空");
        }
        if (end.isBefore(start)) {
            throw new RuntimeException("结束时间不能早于开始时间");
        }
        if (producedQty == null || producedQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("生产数量必须大于0");
        }

        String operatorName = (operator == null || operator.trim().isEmpty()) ? "unknown" : operator.trim();
        int proceedNext = (proceedNextProcess == null || proceedNextProcess) ? 1 : 0;
        int affected = scheduleMapper.updateProcessReport(reportId, start, end, producedQty, operatorName, proceedNext, remark);
        if (affected <= 0) {
            return false;
        }

        Long scheduleId = toLong(report.get("schedule_id"));
        if (scheduleId != null) {
            ManualSchedule schedule = this.getById(scheduleId);
            if (schedule != null && schedule.getOrderDetailId() != null) {
                refreshOrderCompletionAfterReport(schedule.getOrderDetailId());
            }
        }
        return true;
    }

    @Override
    @Transactional
    public boolean deleteProcessWorkReport(Long reportId) {
        if (reportId == null) {
            throw new RuntimeException("reportId 不能为空");
        }
        Map<String, Object> report = scheduleMapper.selectProcessReportById(reportId);
        if (report == null || report.isEmpty()) {
            throw new RuntimeException("报工记录不存在");
        }

        int affected = scheduleMapper.deleteProcessReport(reportId);
        if (affected <= 0) {
            return false;
        }
        scheduleMapper.deleteCoatingRollsByReportId(reportId);
        scheduleMapper.deleteMaterialIssuesByReportId(reportId);
        scheduleMapper.deleteCoatingOrderLocksByReportId(reportId);

        Long scheduleId = toLong(report.get("schedule_id"));
        if (scheduleId != null) {
            ManualSchedule schedule = this.getById(scheduleId);
            if (schedule != null && schedule.getOrderDetailId() != null) {
                refreshOrderCompletionAfterReport(schedule.getOrderDetailId());
            }
        }
        return true;
    }

    @Override
    public List<Map<String, Object>> getCoatingRollLocks(String orderNo) {
        if (orderNo == null || orderNo.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return scheduleMapper.selectCoatingRollLocksByOrderNo(orderNo.trim());
    }

    @Override
    public List<Map<String, Object>> getProcessMaterialIssues(Long scheduleId, String processType) {
        if (scheduleId == null) {
            throw new RuntimeException("scheduleId 不能为空");
        }
        String normalizedProcessType = normalizeProcessType(processType);
        return scheduleMapper.selectProcessMaterialIssues(scheduleId, normalizedProcessType);
    }

    @Override
    public String generateNextCoatingRollCode(Long scheduleId, String workGroup, String productionDateTime) {
        if (scheduleId == null) {
            throw new RuntimeException("scheduleId 不能为空");
        }
        ManualSchedule schedule = this.getById(scheduleId);
        if (schedule == null) {
            throw new RuntimeException("排程记录不存在");
        }

        String lineNo = "0";
        Equipment equipment = resolveEquipmentByIdOrCode(schedule.getCoatingEquipment());
        String equipmentCode = equipment == null ? schedule.getCoatingEquipment() : equipment.getEquipmentCode();
        if (equipmentCode != null && !equipmentCode.trim().isEmpty()) {
            lineNo = extractLineNo(equipmentCode);
        }

        String groupCode = normalizeGroupCode(workGroup);
        if (productionDateTime == null || productionDateTime.trim().isEmpty()) {
            throw new RuntimeException("productionDateTime 不能为空");
        }
        LocalDate date = parseDateTime(productionDateTime).toLocalDate();
        String datePart = date.format(DateTimeFormatter.ofPattern("yyMMdd"));
        String prefix = datePart + lineNo + groupCode;
        Integer maxSeq = scheduleMapper.selectMaxCoatingRollSeqByPrefix(prefix);
        int nextSeq = (maxSeq == null ? 0 : maxSeq) + 1;
        return prefix + String.format("%02d", nextSeq);
    }

    @Override
    public Long getLatestScheduleIdByOrderDetailId(Long orderDetailId) {
        if (orderDetailId == null) {
            throw new RuntimeException("orderDetailId 不能为空");
        }
        return scheduleMapper.selectLatestScheduleId(orderDetailId);
    }

    private ManualSchedule resolveScheduleForReport(Long scheduleId, Long orderDetailId, String processType) {
        if (scheduleId != null) {
            ManualSchedule schedule = this.getById(scheduleId);
            if (schedule == null) {
                throw new RuntimeException("排程记录不存在");
            }
            return schedule;
        }
        if (orderDetailId == null) {
            throw new RuntimeException("scheduleId 和 orderDetailId 不能同时为空");
        }

        Long latestScheduleId = scheduleMapper.selectLatestScheduleId(orderDetailId);
        if (latestScheduleId != null && latestScheduleId > 0) {
            ManualSchedule latest = this.getById(latestScheduleId);
            if (latest != null) {
                return latest;
            }
        }

        SalesOrderItem item = salesOrderItemMapper.selectById(orderDetailId);
        if (item == null) {
            throw new RuntimeException("订单明细不存在");
        }
        ensureOrderDetailSchedulable(orderDetailId);

        SalesOrder order = item.getOrderId() == null ? null : salesOrderMapper.selectById(item.getOrderId());
        ManualSchedule adhoc = new ManualSchedule();
        adhoc.setOrderDetailId(orderDetailId);
        adhoc.setOrderNo(order == null ? null : order.getOrderNo());
        adhoc.setScheduleQty(item.getRolls() == null ? 0 : item.getRolls());
        adhoc.setShortageQty(0);
        adhoc.setScheduleType("COATING".equals(processType) ? "COATING" : "STOCK");
        adhoc.setStatus("COATING".equals(processType) ? "COATING_SCHEDULED" : "REWINDING_SCHEDULED");
        Long newId = createSchedule(adhoc);
        ManualSchedule created = this.getById(newId);
        if (created == null) {
            throw new RuntimeException("创建直报排程失败");
        }
        return created;
    }

    private void refreshOrderCompletionAfterReport(Long orderDetailId) {
        if (orderDetailId == null) {
            return;
        }

        SalesOrderItem item = salesOrderItemMapper.selectById(orderDetailId);
        if (item == null) {
            return;
        }

        BigDecimal orderQty = item.getRolls() == null
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(item.getRolls());
        BigDecimal completedQty = calculateCompletedQtyByOrderDetail(orderDetailId);
        boolean completed = orderQty.compareTo(BigDecimal.ZERO) > 0 && completedQty.compareTo(orderQty) >= 0;

        int rolls = item.getRolls() == null ? 0 : Math.max(item.getRolls(), 0);
        int deliveredRolls = completedQty.setScale(0, RoundingMode.HALF_UP).intValue();
        if (deliveredRolls < 0) {
            deliveredRolls = 0;
        }
        if (deliveredRolls > rolls) {
            deliveredRolls = rolls;
        }
        int remainingRolls = Math.max(rolls - deliveredRolls, 0);

        BigDecimal singleArea = BigDecimal.ZERO;
        if (item.getWidth() != null && item.getLength() != null) {
            singleArea = item.getWidth()
                    .divide(new BigDecimal("1000"), 8, RoundingMode.HALF_UP)
                    .multiply(item.getLength());
        }
        BigDecimal producedArea = singleArea.multiply(completedQty).setScale(2, RoundingMode.HALF_UP);
        if (item.getSqm() != null && item.getSqm().compareTo(BigDecimal.ZERO) > 0 && producedArea.compareTo(item.getSqm()) > 0) {
            producedArea = item.getSqm().setScale(2, RoundingMode.HALF_UP);
        }

        item.setProducedArea(producedArea);
        item.setDeliveredQty(deliveredRolls);
        item.setRemainingQty(remainingRolls);
        if (remainingRolls <= 0) {
            item.setProductionStatus("completed");
        } else if (deliveredRolls <= 0) {
            item.setProductionStatus("not_started");
        } else {
            item.setProductionStatus("partial");
        }
        if (completed) {
            item.setScheduledQty(item.getRolls());
        }
        salesOrderItemMapper.updateById(item);

        if (completed) {
            List<ManualSchedule> schedules = this.list(new LambdaQueryWrapper<ManualSchedule>()
                    .eq(ManualSchedule::getOrderDetailId, orderDetailId));
            for (ManualSchedule schedule : schedules) {
                if (schedule == null || schedule.getId() == null) {
                    continue;
                }
                String st = schedule.getStatus() == null ? "" : schedule.getStatus().trim().toUpperCase();
                if ("TERMINATED".equals(st) || "CANCELLED".equals(st) || "COMPLETED".equals(st)) {
                    continue;
                }
                schedule.setStatus("COMPLETED");
                this.updateById(schedule);
            }

            // 同步排程计划状态：订单明细已完工后，相关工序计划一并置为 COMPLETED
            schedulePlanService.update(
                    new LambdaUpdateWrapper<SchedulePlan>()
                            .eq(SchedulePlan::getOrderDetailId, orderDetailId)
                            .notIn(SchedulePlan::getStatus, Arrays.asList("CANCELLED", "COMPLETED"))
                            .set(SchedulePlan::getStatus, "COMPLETED")
            );
        }

        if (item.getOrderId() == null) {
            return;
        }
        List<Map<String, Object>> completionStats = scheduleMapper.selectOrderDetailCompletionStatsByOrderId(item.getOrderId());
        boolean allCompleted = completionStats != null && !completionStats.isEmpty();
        if (completionStats != null) {
            for (Map<String, Object> stats : completionStats) {
                if (stats == null) {
                    continue;
                }
                BigDecimal qty = nvl(toBigDecimal(stats.get("order_qty")));
                BigDecimal done = calculateCompletedQtyFromStats(stats);
                if (!(qty.compareTo(BigDecimal.ZERO) > 0 && done.compareTo(qty) >= 0)) {
                    allCompleted = false;
                    break;
                }
            }
        }

        SalesOrder order = salesOrderMapper.selectById(item.getOrderId());
        if (order != null) {
            String status = order.getStatus() == null ? "" : order.getStatus().trim().toLowerCase();
            boolean lifecycleV2 = isLifecycleV2OrderStatus(order.getStatus());
            boolean changed = false;
            if (allCompleted) {
                String targetStatus = lifecycleV2 ? "PRODUCED" : "completed";
                if (!targetStatus.equalsIgnoreCase(order.getStatus())) {
                    order.setStatus(targetStatus);
                    changed = true;
                }
            } else if (!"cancelled".equals(status) && !"canceled".equals(status) && !"closed".equals(status)) {
                if (lifecycleV2) {
                    String targetStatus = "IN_PRODUCTION";
                    if (!targetStatus.equalsIgnoreCase(order.getStatus())) {
                        order.setStatus(targetStatus);
                        changed = true;
                    }
                } else {
                    // 避免已人工置为 completed 的订单被报工链路降级回 processing
                    if (!"completed".equals(status) && !"processing".equals(status)) {
                        order.setStatus("processing");
                        changed = true;
                    }
                }
            }
            if (changed) {
                salesOrderMapper.updateById(order);
            }
        }
    }

    private boolean isLifecycleV2OrderStatus(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return "CREATED".equals(normalized)
                || "SCHEDULED".equals(normalized)
                || "IN_PRODUCTION".equals(normalized)
                || "PRODUCED".equals(normalized)
                || "SHIPPED_PARTIAL".equals(normalized)
                || "SHIPPED_FULL".equals(normalized)
                || "PAYMENT_PARTIAL".equals(normalized)
                || "PAID".equals(normalized)
                || "CLOSED".equals(normalized)
                || "CANCELLED".equals(normalized);
    }


    private BigDecimal calculateCompletedQtyByOrderDetail(Long orderDetailId) {
        if (orderDetailId == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal coating = nvl(scheduleMapper.sumProcessReportedQtyByOrderDetail(orderDetailId, "COATING"));
        BigDecimal rewinding = nvl(scheduleMapper.sumProcessReportedQtyByOrderDetail(orderDetailId, "REWINDING"));
        BigDecimal slitting = nvl(scheduleMapper.sumProcessReportedQtyByOrderDetail(orderDetailId, "SLITTING"));

        int coatingStop = nvlInt(scheduleMapper.maxStopNextByOrderDetail(orderDetailId, "COATING"));
        int rewindingStop = nvlInt(scheduleMapper.maxStopNextByOrderDetail(orderDetailId, "REWINDING"));
        int slittingStop = nvlInt(scheduleMapper.maxStopNextByOrderDetail(orderDetailId, "SLITTING"));

        if (slittingStop == 1) {
            return slitting;
        }
        if (rewindingStop == 1) {
            return rewinding;
        }
        if (coatingStop == 1) {
            return coating;
        }
        if (slitting.compareTo(BigDecimal.ZERO) > 0) {
            return slitting;
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal calculateCompletedQtyFromStats(Map<String, Object> stats) {
        if (stats == null || stats.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal coating = nvl(toBigDecimal(stats.get("coating_qty")));
        BigDecimal rewinding = nvl(toBigDecimal(stats.get("rewinding_qty")));
        BigDecimal slitting = nvl(toBigDecimal(stats.get("slitting_qty")));

        int coatingStop = nvlInt(toInteger(stats.get("coating_stop")));
        int rewindingStop = nvlInt(toInteger(stats.get("rewinding_stop")));
        int slittingStop = nvlInt(toInteger(stats.get("slitting_stop")));

        if (slittingStop == 1) {
            return slitting;
        }
        if (rewindingStop == 1) {
            return rewinding;
        }
        if (coatingStop == 1) {
            return coating;
        }
        if (slitting.compareTo(BigDecimal.ZERO) > 0) {
            return slitting;
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal nvl(BigDecimal val) {
        return val == null ? BigDecimal.ZERO : val;
    }

    private int nvlInt(Integer val) {
        return val == null ? 0 : val;
    }

    private String normalizeProcessType(String processType) {
        String p = processType == null ? "" : processType.trim().toUpperCase();
        if (!"COATING".equals(p) && !"REWINDING".equals(p) && !"SLITTING".equals(p)) {
            throw new RuntimeException("processType 仅支持 COATING/REWINDING/SLITTING");
        }
        return p;
    }

    private String extractLineNo(String equipmentCode) {
        String code = equipmentCode == null ? "" : equipmentCode.trim();
        if (code.isEmpty()) {
            return "0";
        }
        String digits = code.replaceAll("\\D+", "");
        if (digits.isEmpty()) {
            return "0";
        }
        String normalized = digits.replaceFirst("^0+(?!$)", "");
        return normalized.isEmpty() ? "0" : normalized;
    }

    private String normalizeGroupCode(String workGroup) {
        String raw = workGroup == null ? "" : workGroup.trim().toUpperCase();
        if (raw.endsWith("班")) {
            raw = raw.substring(0, raw.length() - 1);
        }
        raw = raw.replaceAll("[^A-Z0-9]", "");
        if (raw.isEmpty()) {
            return "A";
        }
        return String.valueOf(raw.charAt(0));
    }

    private void persistCoatingRolls(Long scheduleId, Long reportId, List<Map<String, Object>> producedRolls) {
        int idx = 1;
        for (Map<String, Object> roll : producedRolls) {
            if (roll == null) {
                continue;
            }
            BigDecimal area = toBigDecimal(roll.get("area"));
            if (area == null || area.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String rollCode = stringVal(roll.get("rollCode"));
            if (rollCode == null || rollCode.trim().isEmpty()) {
                rollCode = "COAT-" + scheduleId + "-" + reportId + "-" + idx;
            }

            scheduleMapper.insertCoatingRoll(
                    scheduleId,
                    reportId,
                    rollCode,
                    stringVal(roll.get("batchNo")),
                    toBigDecimal(roll.get("widthMm")),
                    toBigDecimal(roll.get("lengthM")),
                    area,
                    toBigDecimal(roll.get("weightKg")),
                    stringVal(roll.get("remark"))
            );
            idx++;
        }
    }

    private void autoCreateCoatingOrderLocks(Long scheduleId, Long reportId, List<Map<String, Object>> producedRolls) {
        List<Map<String, Object>> allocations = scheduleMapper.selectIncludedCoatingAllocations(scheduleId);
        if (allocations == null || allocations.isEmpty()) {
            return;
        }

        Map<String, BigDecimal> orderRemaining = new LinkedHashMap<>();
        Map<String, String> orderMaterialMap = new HashMap<>();
        for (Map<String, Object> a : allocations) {
            String orderNo = stringVal(a.get("order_no"));
            if (orderNo == null || orderNo.trim().isEmpty()) {
                continue;
            }
            BigDecimal includedArea = toBigDecimal(a.get("included_area"));
            if (includedArea == null || includedArea.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal cur = orderRemaining.getOrDefault(orderNo, BigDecimal.ZERO);
            orderRemaining.put(orderNo, cur.add(includedArea));
            String materialCode = stringVal(a.get("material_code"));
            if (materialCode != null && !materialCode.trim().isEmpty()) {
                orderMaterialMap.putIfAbsent(orderNo, materialCode);
            }
        }
        if (orderRemaining.isEmpty()) {
            return;
        }

        List<String> orderNos = new ArrayList<>(orderRemaining.keySet());
        int orderIdx = 0;

        for (Map<String, Object> roll : producedRolls) {
            if (roll == null) {
                continue;
            }
            BigDecimal remainingRollArea = toBigDecimal(roll.get("area"));
            if (remainingRollArea == null || remainingRollArea.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String rollCode = stringVal(roll.get("rollCode"));
            if (rollCode == null || rollCode.trim().isEmpty()) {
                continue;
            }

            while (remainingRollArea.compareTo(BigDecimal.ZERO) > 0 && orderIdx < orderNos.size()) {
                String orderNo = orderNos.get(orderIdx);
                BigDecimal orderNeed = orderRemaining.getOrDefault(orderNo, BigDecimal.ZERO);
                if (orderNeed.compareTo(BigDecimal.ZERO) <= 0) {
                    orderIdx++;
                    continue;
                }

                BigDecimal lockArea = remainingRollArea.min(orderNeed).setScale(2, BigDecimal.ROUND_HALF_UP);
                if (lockArea.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }

                scheduleMapper.insertCoatingOrderLock(
                        scheduleId,
                        reportId,
                        orderNo,
                        orderMaterialMap.get(orderNo),
                        rollCode,
                        lockArea,
                        "LOCKED"
                );

                    // 同步写入标准锁定表（用于领/退料链路，不再依赖历史锁定表）
                    createStandardMaterialLock(scheduleId, orderNo, orderMaterialMap.get(orderNo), rollCode, lockArea, "coating-report-auto-lock", "rewinding-shortage-pending");

                remainingRollArea = remainingRollArea.subtract(lockArea);
                orderRemaining.put(orderNo, orderNeed.subtract(lockArea));
                if (orderRemaining.get(orderNo).compareTo(BigDecimal.ZERO) <= 0) {
                    orderIdx++;
                }
            }
        }
    }

    private void createStandardMaterialLock(Long scheduleId,
                                            String orderNo,
                                            String materialCode,
                                            String rollCode,
                                            BigDecimal lockArea,
                                            String source,
                                            String pendingSourceToConsume) {
        if (lockArea == null || lockArea.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        Long orderId = null;
        if (orderNo != null && !orderNo.trim().isEmpty()) {
            SalesOrder order = salesOrderMapper.selectByOrderNo(orderNo.trim());
            if (order != null) {
                orderId = order.getId();
            }
        }

        TapeStock stock = null;
        if (rollCode != null && !rollCode.trim().isEmpty()) {
            stock = tapeStockMapper.selectByQrCode(rollCode.trim());
            if (stock == null) {
                stock = tapeStockMapper.selectByBatchNo(rollCode.trim());
            }
        }
        if (stock == null && materialCode != null && !materialCode.trim().isEmpty()) {
            QueryWrapper<TapeStock> w = new QueryWrapper<>();
            w.eq("material_code", materialCode.trim())
             .eq("status", 1)
             .orderByDesc("id")
             .last("LIMIT 1");
            stock = tapeStockMapper.selectOne(w);
        }

        ScheduleMaterialLock lock = new ScheduleMaterialLock();
        lock.setScheduleId(scheduleId);
        lock.setOrderId(orderId);
        lock.setOrderNo(orderNo);
        lock.setMaterialCode(materialCode);
        lock.setFilmStockId(stock == null ? null : stock.getId());
        lock.setFilmStockDetailId(stock == null ? null : stock.getId());
        lock.setRollCode(rollCode);
        lock.setLockedArea(lockArea.setScale(2, BigDecimal.ROUND_HALF_UP));
        lock.setRequiredArea(lockArea.setScale(2, BigDecimal.ROUND_HALF_UP));
        lock.setLockStatus(ScheduleMaterialLock.LockStatus.LOCKED);
        lock.setLockedTime(LocalDateTime.now());
        lock.setLockedByUserId(1L);
        lock.setVersion(1);
        lock.setRemark("source=" + (source == null ? "standard-lock" : source)
            + ";materialCode=" + (materialCode == null ? "" : materialCode)
            + ";orderNo=" + (orderNo == null ? "" : orderNo)
            + ";op=system"
            + ";ts=" + LocalDateTime.now());
        scheduleMaterialLockMapper.insert(lock);

        // 新增锁定后，按订单优先冲销待补锁
        consumePendingLocks(orderNo, materialCode, lockArea, pendingSourceToConsume);
    }

    private void consumePendingLocks(String orderNo, String materialCode, BigDecimal allocatedArea, String pendingSourceToConsume) {
        if (allocatedArea == null || allocatedArea.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (orderNo == null || orderNo.trim().isEmpty()) {
            return;
        }

        QueryWrapper<ScheduleMaterialLock> qw = new QueryWrapper<>();
        qw.eq("lock_status", ScheduleMaterialLock.LockStatus.PENDING_SUPPLY)
                    .eq("order_no", orderNo.trim());
                if (materialCode != null && !materialCode.trim().isEmpty()) {
                        qw.eq("material_code", materialCode.trim());
                }
                if (pendingSourceToConsume != null && !pendingSourceToConsume.trim().isEmpty()) {
                        qw.like("remark", "source=" + pendingSourceToConsume.trim());
                }
                qw
          .orderByAsc("id");
        List<ScheduleMaterialLock> pendingList = scheduleMaterialLockMapper.selectList(qw);
        if (pendingList == null || pendingList.isEmpty()) {
            return;
        }

        BigDecimal remain = allocatedArea;
        for (ScheduleMaterialLock pending : pendingList) {
            if (remain.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal required = pending.getRequiredArea() == null ? BigDecimal.ZERO : pending.getRequiredArea();
            BigDecimal alreadyLocked = pending.getLockedArea() == null ? BigDecimal.ZERO : pending.getLockedArea();
            BigDecimal need = required.subtract(alreadyLocked);
            if (need.compareTo(BigDecimal.ZERO) <= 0) {
                pending.setLockStatus(ScheduleMaterialLock.LockStatus.FULFILLED);
                pending.setReleasedTime(LocalDateTime.now());
                scheduleMaterialLockMapper.updateById(pending);
                continue;
            }
            BigDecimal consume = remain.min(need).setScale(2, BigDecimal.ROUND_HALF_UP);
            pending.setLockedArea(alreadyLocked.add(consume).setScale(2, BigDecimal.ROUND_HALF_UP));
            if (pending.getLockedArea().compareTo(required) >= 0) {
                pending.setLockStatus(ScheduleMaterialLock.LockStatus.FULFILLED);
                pending.setReleasedTime(LocalDateTime.now());
            }
            scheduleMaterialLockMapper.updateById(pending);
            remain = remain.subtract(consume);
        }
    }

    private void createPendingMaterialLock(Long scheduleId, String orderNo, String materialCode, BigDecimal requiredArea, String source) {
        if (requiredArea == null || requiredArea.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        SalesOrder order = null;
        if (orderNo != null && !orderNo.trim().isEmpty()) {
            order = salesOrderMapper.selectByOrderNo(orderNo.trim());
        }
        ScheduleMaterialLock pending = new ScheduleMaterialLock();
        pending.setScheduleId(scheduleId);
        pending.setOrderId(order == null ? null : order.getId());
        pending.setOrderNo(orderNo);
        pending.setMaterialCode(materialCode);
        pending.setFilmStockId(null);
        pending.setFilmStockDetailId(null);
        pending.setRollCode(null);
        pending.setLockedArea(BigDecimal.ZERO.setScale(2, BigDecimal.ROUND_HALF_UP));
        pending.setRequiredArea(requiredArea.setScale(2, BigDecimal.ROUND_HALF_UP));
        pending.setLockStatus(ScheduleMaterialLock.LockStatus.PENDING_SUPPLY);
        pending.setLockedTime(LocalDateTime.now());
        pending.setLockedByUserId(1L);
        pending.setVersion(1);
        pending.setRemark("source=" + (source == null ? "pending-lock" : source)
                + ";orderNo=" + (orderNo == null ? "" : orderNo)
                + ";materialCode=" + (materialCode == null ? "" : materialCode)
                + ";op=system"
                + ";ts=" + LocalDateTime.now());
        scheduleMaterialLockMapper.insert(pending);
    }

    private void createCoatingBomPendingLocksIfNeeded(Long scheduleId,
                                                      String orderNo,
                                                      String finishedMaterialCode,
                                                      BigDecimal coatingArea) {
        if (scheduleId == null || finishedMaterialCode == null || finishedMaterialCode.trim().isEmpty()) {
            return;
        }
        if (coatingArea == null || coatingArea.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        TapeFormula formula = tapeFormulaMapper.selectByMaterialCode(finishedMaterialCode.trim());
        if (formula == null || formula.getId() == null) {
            return;
        }
        List<TapeFormulaItem> items = tapeFormulaMapper.selectItemsByFormulaId(formula.getId());
        if (items == null || items.isEmpty()) {
            return;
        }
        BigDecimal formulaArea = formula.getCoatingArea();
        if (formulaArea == null || formulaArea.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal factor = coatingArea.divide(formulaArea, 8, RoundingMode.HALF_UP);
        BigDecimal totalWeight = formula.getTotalWeight() == null ? BigDecimal.ZERO : formula.getTotalWeight();

        for (TapeFormulaItem item : items) {
            if (item == null || item.getMaterialCode() == null || item.getMaterialCode().trim().isEmpty()) {
                continue;
            }
            BigDecimal need = BigDecimal.ZERO;
            if (item.getWeight() != null && item.getWeight().compareTo(BigDecimal.ZERO) > 0) {
                need = item.getWeight().multiply(factor);
            } else if (item.getRatio() != null
                    && item.getRatio().compareTo(BigDecimal.ZERO) > 0
                    && totalWeight.compareTo(BigDecimal.ZERO) > 0) {
                need = totalWeight
                        .multiply(item.getRatio())
                        .divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP)
                        .multiply(factor);
            }
            need = need.setScale(2, RoundingMode.HALF_UP);
            if (need.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            String rawCode = item.getMaterialCode().trim();
            BigDecimal existing = sumOpenCoatingBomPendingRequired(scheduleId, orderNo, rawCode);
            BigDecimal delta = need.subtract(existing).setScale(2, RoundingMode.HALF_UP);
            if (delta.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            createPendingMaterialLock(
                    scheduleId,
                    orderNo,
                    rawCode,
                    delta,
                    "coating-bom-pending;finishedMaterial=" + finishedMaterialCode.trim() + ";formulaId=" + formula.getId()
            );
        }
    }

    private BigDecimal sumOpenCoatingBomPendingRequired(Long scheduleId, String orderNo, String materialCode) {
        QueryWrapper<ScheduleMaterialLock> qw = new QueryWrapper<>();
        qw.eq("schedule_id", scheduleId)
                .eq("material_code", materialCode)
                .in("lock_status", Arrays.asList(ScheduleMaterialLock.LockStatus.PENDING_SUPPLY, ScheduleMaterialLock.LockStatus.FULFILLED))
                .like("remark", "source=coating-bom-pending");
        if (orderNo != null && !orderNo.trim().isEmpty()) {
            qw.eq("order_no", orderNo.trim());
        }
        List<ScheduleMaterialLock> rows = scheduleMaterialLockMapper.selectList(qw);
        if (rows == null || rows.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (ScheduleMaterialLock row : rows) {
            if (row != null && row.getRequiredArea() != null) {
                sum = sum.add(row.getRequiredArea());
            }
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    private void persistMaterialIssues(Long scheduleId,
                                       Long reportId,
                                       String processType,
                                       String operatorName,
                                       List<Map<String, Object>> materialIssues) {
        for (Map<String, Object> issue : materialIssues) {
            if (issue == null) {
                continue;
            }
            BigDecimal actualArea = toBigDecimal(issue.get("actualArea"));
            BigDecimal planArea = toBigDecimal(issue.get("planArea"));
            BigDecimal lossArea = toBigDecimal(issue.get("lossArea"));
            if ((actualArea == null || actualArea.compareTo(BigDecimal.ZERO) <= 0)
                    && (planArea == null || planArea.compareTo(BigDecimal.ZERO) <= 0)
                    && (lossArea == null || lossArea.compareTo(BigDecimal.ZERO) <= 0)) {
                continue;
            }
            scheduleMapper.insertProcessMaterialIssue(
                    scheduleId,
                    reportId,
                    processType,
                    stringVal(issue.get("materialType")),
                    stringVal(issue.get("materialCode")),
                    toLong(issue.get("stockId")),
                    stringVal(issue.get("rollCode")),
                    planArea,
                    actualArea,
                    lossArea,
                    operatorName,
                    LocalDateTime.now(),
                    stringVal(issue.get("remark"))
            );
        }
    }

    private String stringVal(Object obj) {
        if (obj == null) {
            return null;
        }
        String s = String.valueOf(obj);
        return s.trim().isEmpty() ? null : s.trim();
    }

    @Override
    @Transactional
    public boolean updateSlittingInfo(Long scheduleId, String packagingDate, String slittingEquipment) {
        ManualSchedule schedule = this.getById(scheduleId);
        if (schedule == null) {
            throw new RuntimeException("排程记录不存在");
        }

        String equipmentCode = (slittingEquipment != null && !slittingEquipment.trim().isEmpty())
            ? slittingEquipment.trim()
            : null;
        if (equipmentCode == null || equipmentCode.trim().isEmpty()) {
            throw new RuntimeException("请先选择分切机台");
        }
        if (packagingDate == null || packagingDate.trim().isEmpty()) {
            throw new RuntimeException("请先选择分切日期时间");
        }

        Equipment eq = resolveEquipmentByIdOrCode(equipmentCode);
        if (eq != null && eq.getEquipmentCode() != null && !eq.getEquipmentCode().trim().isEmpty()) {
            equipmentCode = eq.getEquipmentCode().trim();
        }
        LocalDate slittingPlanDate = parseDateTime(packagingDate).toLocalDate();
        validateEquipmentAvailabilityForManualSchedule(eq, equipmentCode, slittingPlanDate);

        SalesOrderItem item = salesOrderItemMapper.selectById(schedule.getOrderDetailId());
        if (item == null) {
            throw new RuntimeException("订单明细不存在");
        }

        SlittingProcessParams slittingParams = slittingProcessParamsService.getByDimensions(
                item.getThickness(),
                item.getLength(),
                item.getWidth(),
                equipmentCode
        );
        if (slittingParams == null || slittingParams.getProductionSpeed() == null || slittingParams.getProductionSpeed().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("未维护该规格在当前机台的分切速度，请先在分切参数中维护");
        }

        Integer durationMinutes = calcDurationMinutesByRolls(schedule.getScheduleQty(), slittingParams.getProductionSpeed());
        if (durationMinutes <= 0) {
            throw new RuntimeException("分切卷数或分切速度无效，无法计算机台占用时长");
        }
        LocalDateTime rewindingEnd = resolveRewindingEndTime(schedule);
        LocalDateTime selectedStart = parseDateTime(packagingDate);
        if (rewindingEnd != null && selectedStart.isBefore(rewindingEnd)) {
            selectedStart = rewindingEnd;
        }
        selectedStart = alignStartAvoidSlittingOverlap(equipmentCode, scheduleId, selectedStart, durationMinutes);
        LocalDateTime selectedEnd = selectedStart.plusMinutes(durationMinutes);

        shiftConflictingSlittingOccupations(equipmentCode, scheduleId, selectedStart, selectedEnd);

        schedule.setSlittingScheduleDate(selectedStart.toLocalDate());
        schedule.setPackagingDate(selectedStart.toLocalDate());
        schedule.setStatus("CONFIRMED");

        // 分切锁定链：优先锁定仓库现有复卷，不足创建待补锁（等待复卷入库自动补锁）
        lockMaterialsForSlittingIfNeeded(schedule, item);

        boolean updated = this.updateById(schedule);

        EquipmentOccupation oc = new EquipmentOccupation();
        oc.setScheduleId(scheduleId);
        oc.setProcessType("SLITTING");
        oc.setEquipmentId(eq == null ? null : eq.getId());
        oc.setEquipmentCode(equipmentCode);
        oc.setStartTime(selectedStart);
        oc.setEndTime(selectedEnd);
        oc.setDurationMinutes(durationMinutes);
        oc.setStatus("PLANNED");
        oc.setRemark("分切排程自动回推机台占用");
        equipmentOccupationMapper.deleteByScheduleAndProcess(scheduleId, "SLITTING");
        equipmentOccupationMapper.insert(oc);

        // 写入统一排程计划表（分切）
        try {
            SchedulePlan plan = new SchedulePlan();
            plan.setOrderDetailId(schedule.getOrderDetailId());
            plan.setOrderNo(schedule.getOrderNo());
            if (item != null) {
                plan.setMaterialCode(item.getMaterialCode());
                plan.setMaterialName(item.getMaterialName());
                plan.setThickness(item.getThickness() != null ? item.getThickness().intValue() : null);
                plan.setWidth(item.getWidth() != null ? item.getWidth().intValue() : null);
                plan.setLength(item.getLength() != null ? item.getLength().intValue() : null);
            }
            plan.setStage("SLITTING");
            plan.setPlanDate(selectedStart);
            plan.setEquipment(equipmentCode);
            plan.setPlanArea(schedule.getRewindingScheduledArea());
            plan.setStatus("CONFIRMED");
            schedulePlanService.upsertPlan(plan);
        } catch (Exception e) {
            System.err.println("写入分切计划失败: " + e.getMessage());
        }

        try {
            if (schedule.getOrderDetailId() != null) {
                scheduleMapper.updateSalesOrderPackagingDateByDetailId(
                        schedule.getOrderDetailId(),
                        selectedStart.toLocalDate().toString()
                );
            }
        } catch (Exception e) {
            System.err.println("回写分切日期失败: " + e.getMessage());
        }

        return updated;
    }

    private void lockMaterialsForSlittingIfNeeded(ManualSchedule schedule, SalesOrderItem item) {
        if (schedule == null || item == null || item.getMaterialCode() == null || item.getMaterialCode().trim().isEmpty()) {
            return;
        }

        // 幂等：同一排程若已写过分切锁定链记录，则不重复写入
        QueryWrapper<ScheduleMaterialLock> existsQ = new QueryWrapper<>();
        existsQ.eq("schedule_id", schedule.getId())
               .like("remark", "source=slitting-")
               .last("LIMIT 1");
        ScheduleMaterialLock exists = scheduleMaterialLockMapper.selectOne(existsQ);
        if (exists != null) {
            return;
        }

        BigDecimal targetArea = schedule.getRewindingScheduledArea();
        if (targetArea == null || targetArea.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        // 先复用历史锁定（同订单+同料号+同长度），避免重复锁定
        BigDecimal reusedArea = sumReusableLockedAreaForSlitting(schedule.getOrderNo(), item.getMaterialCode(), item);
        BigDecimal remain = targetArea.subtract(reusedArea).setScale(2, BigDecimal.ROUND_HALF_UP);
        if (remain.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        List<TapeStock> stocks = tapeStockMapper.selectByMaterialCode(item.getMaterialCode());
        if (stocks != null) {
            for (TapeStock stock : stocks) {
                if (remain.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
                if (stock == null || !isRewoundStock(stock)) {
                    continue;
                }
                if (!isLengthMatchedForOrder(stock, item)) {
                    continue;
                }
                BigDecimal available = stock.getAvailableArea() == null ? BigDecimal.ZERO : stock.getAvailableArea();
                if (available.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                BigDecimal lockArea = remain.min(available).setScale(2, BigDecimal.ROUND_HALF_UP);
                if (lockArea.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                boolean locked = false;
                for (int i = 0; i < 3; i++) {
                    TapeStock current = tapeStockMapper.selectById(stock.getId());
                    if (current == null) {
                        break;
                    }
                    if (!isLengthMatchedForOrder(current, item)) {
                        break;
                    }
                    BigDecimal curAvailable = current.getAvailableArea() == null ? BigDecimal.ZERO : current.getAvailableArea();
                    BigDecimal tryArea = lockArea.min(curAvailable).setScale(2, BigDecimal.ROUND_HALF_UP);
                    if (tryArea.compareTo(BigDecimal.ZERO) <= 0) {
                        break;
                    }
                    Integer version = current.getVersion() == null ? 0 : current.getVersion();
                    int ok = tapeStockMapper.updateReservedAreaWithVersion(current.getId(), tryArea, version);
                    if (ok > 0) {
                        String rollCode = current.getQrCode() != null && !current.getQrCode().trim().isEmpty()
                                ? current.getQrCode() : current.getBatchNo();
                        createStandardMaterialLock(
                                schedule.getId(),
                                schedule.getOrderNo(),
                                item.getMaterialCode(),
                                rollCode,
                                tryArea,
                                "slitting-schedule-lock",
                                "slitting-shortage-pending"
                        );
                        remain = remain.subtract(tryArea);
                        locked = true;
                        break;
                    }
                }
                if (!locked) {
                    // 单卷锁定失败时继续尝试下一卷，避免整单中断
                    continue;
                }
            }
        }

        if (remain.compareTo(BigDecimal.ZERO) > 0) {
            createPendingMaterialLock(schedule.getId(), schedule.getOrderNo(), item.getMaterialCode(), remain, "slitting-shortage-pending");
        }
    }

    private boolean isRewoundStock(TapeStock stock) {
        String rollType = stock.getRollType() == null ? "" : stock.getRollType().trim();
        String reelType = stock.getReelType() == null ? "" : stock.getReelType().trim();
        return "复卷".equals(rollType) || "复卷".equals(reelType) || "rewound".equalsIgnoreCase(rollType) || "rewound".equalsIgnoreCase(reelType);
    }

    private boolean isLengthMatchedForOrder(TapeStock stock, SalesOrderItem item) {
        if (stock == null || item == null || item.getLength() == null) {
            return true;
        }
        int orderLen = item.getLength().setScale(0, RoundingMode.HALF_UP).intValue();
        Integer stockLen = stock.getCurrentLength() != null ? stock.getCurrentLength() : stock.getLength();
        if (stockLen == null) {
            return false;
        }
        return stockLen == orderLen;
    }

    private BigDecimal sumReusableLockedAreaForSlitting(String orderNo, String materialCode, SalesOrderItem item) {
        if (orderNo == null || orderNo.trim().isEmpty() || materialCode == null || materialCode.trim().isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        QueryWrapper<ScheduleMaterialLock> q = new QueryWrapper<>();
        q.eq("order_no", orderNo.trim())
                .eq("material_code", materialCode.trim())
                .eq("lock_status", ScheduleMaterialLock.LockStatus.LOCKED)
                .isNotNull("film_stock_id")
                .orderByAsc("id");
        List<ScheduleMaterialLock> rows = scheduleMaterialLockMapper.selectList(q);
        if (rows == null || rows.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (ScheduleMaterialLock row : rows) {
            if (row == null || row.getFilmStockId() == null) {
                continue;
            }
            TapeStock stock = tapeStockMapper.selectById(row.getFilmStockId());
            if (!isLengthMatchedForOrder(stock, item)) {
                continue;
            }
            BigDecimal area = row.getLockedArea() == null ? BigDecimal.ZERO : row.getLockedArea();
            if (area.compareTo(BigDecimal.ZERO) > 0) {
                sum = sum.add(area);
            }
        }
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public List<Map<String, Object>> getLockedStocks() {
        return scheduleMapper.selectLockedStocks();
    }
    
    @Override
    @Transactional
    public Long createSchedule(ManualSchedule schedule) {
        ensureOrderDetailSchedulable(schedule.getOrderDetailId());

        // 涂布排程做幂等：同一订单明细仅保留一条可编辑记录，避免重复插入
        if ("COATING".equalsIgnoreCase(schedule.getScheduleType()) && schedule.getOrderDetailId() != null) {
            QueryWrapper<ManualSchedule> qw = new QueryWrapper<>();
            qw.eq("order_detail_id", schedule.getOrderDetailId())
              .eq("schedule_type", "COATING")
              .orderByDesc("id")
              .last("LIMIT 1");
            ManualSchedule existing = this.getOne(qw);
                if (existing != null && ("PENDING".equalsIgnoreCase(existing.getStatus())
                    || "COATING_SCHEDULED".equalsIgnoreCase(existing.getStatus())
                    || "MATERIAL_UNSATISFIED".equalsIgnoreCase(existing.getStatus()))) {
                existing.setOrderNo(schedule.getOrderNo());
                existing.setScheduleQty(schedule.getScheduleQty());
                existing.setShortageQty(schedule.getShortageQty());
                existing.setCoatingArea(schedule.getCoatingArea());
                existing.setCoatingScheduleDate(schedule.getCoatingScheduleDate());
                existing.setCoatingDate(schedule.getCoatingDate());
                existing.setCoatingEquipment(schedule.getCoatingEquipment());
                existing.setCoatingWidth(schedule.getCoatingWidth());
                existing.setCoatingLength(schedule.getCoatingLength());
                existing.setRewindingDate(schedule.getRewindingDate());
                existing.setPackagingDate(schedule.getPackagingDate());
                existing.setRemark(schedule.getRemark());
                existing.setStatus(schedule.getStatus() == null ? "PENDING" : schedule.getStatus());
                boolean updated = this.updateById(existing);
                if (!updated) {
                    throw new RuntimeException("更新排程失败");
                }

                SalesOrderItem item = existing.getOrderDetailId() == null ? null : salesOrderItemMapper.selectById(existing.getOrderDetailId());
                if (item != null) {
                    createCoatingBomPendingLocksIfNeeded(existing.getId(), existing.getOrderNo(), item.getMaterialCode(), existing.getCoatingArea());
                }
                return existing.getId();
            }
        }

        if ("COATING".equalsIgnoreCase(schedule.getScheduleType())) {
            schedule.setStatus(schedule.getStatus() == null ? "PENDING" : schedule.getStatus());
        } else {
            schedule.setStatus(schedule.getStatus() == null ? "PENDING" : schedule.getStatus());
        }
        schedule.setCreatedAt(java.time.LocalDateTime.now());
        boolean saved = this.save(schedule);
        if (!saved) {
            throw new RuntimeException("创建排程失败");
        }

        if ("COATING".equalsIgnoreCase(schedule.getScheduleType())) {
            SalesOrderItem item = schedule.getOrderDetailId() == null ? null : salesOrderItemMapper.selectById(schedule.getOrderDetailId());
            if (item != null) {
                createCoatingBomPendingLocksIfNeeded(schedule.getId(), schedule.getOrderNo(), item.getMaterialCode(), schedule.getCoatingArea());
            }
        }
        return schedule.getId();
    }
    
    @Override
    @Transactional
    public Long createRewindingSchedule(Long scheduleId, List<Map<String, Object>> stockAllocations) {
        ManualSchedule schedule = this.getById(scheduleId);
        if (schedule == null) {
            throw new RuntimeException("排程记录不存在");
        }
        ensureOrderDetailSchedulable(schedule.getOrderDetailId());

        // 归一化库存面积字段
        try {
            tapeStockMapper.normalizeAreaFields();
        } catch (Exception ignored) {
        }
        
        // 验证库存充足性
        for (Map<String, Object> allocation : stockAllocations) {
            Object stockIdObj = allocation.get("stockId");
            Long stockId = null;
            if (stockIdObj instanceof Number) {
                stockId = ((Number) stockIdObj).longValue();
            } else if (stockIdObj instanceof String) {
                stockId = Long.parseLong((String) stockIdObj);
            }
            if (stockId == null) {
                throw new RuntimeException("stockId 不能为空");
            }

            Object areaObj = allocation.containsKey("area") ? allocation.get("area") : allocation.get("qty");
            double area = 0;
            if (areaObj instanceof Number) {
                area = ((Number) areaObj).doubleValue();
            } else if (areaObj instanceof String) {
                area = Double.parseDouble((String) areaObj);
            }
            
            TapeStock stock = tapeStockService.getStockById(stockId);
            if (stock == null) {
                throw new RuntimeException("库存不存在");
            }
            double availableArea = stock.getAvailableArea() != null ? stock.getAvailableArea().doubleValue() : 0;
            if (area <= 0 || availableArea < area) {
                throw new RuntimeException("库存不足或已被占用");
            }

            // 乐观锁写回库存预留面积
            boolean locked = false;
            for (int i = 0; i < 3; i++) {
                TapeStock current = tapeStockService.getStockById(stockId);
                if (current == null) {
                    throw new RuntimeException("库存不存在");
                }
                double currentAvailable = current.getAvailableArea() != null ? current.getAvailableArea().doubleValue() : 0;
                if (currentAvailable < area) {
                    throw new RuntimeException("库存不足或已被占用");
                }
                Integer version = current.getVersion() != null ? current.getVersion() : 0;
                int updated = tapeStockMapper.updateReservedAreaWithVersion(
                        stockId,
                        BigDecimal.valueOf(area),
                        version
                );
                if (updated > 0) {
                    String rollCode = current.getQrCode() != null && !current.getQrCode().trim().isEmpty()
                        ? current.getQrCode() : current.getBatchNo();
                    createStandardMaterialLock(
                        schedule.getId(),
                        schedule.getOrderNo(),
                        current.getMaterialCode(),
                        rollCode,
                        BigDecimal.valueOf(area),
                        "rewinding-schedule-lock",
                        null
                    );
                    locked = true;
                    break;
                }
            }
            if (!locked) {
                throw new RuntimeException("库存锁定失败，请重试");
            }
        }
        
        // 更新手动排程状态
        schedule.setStatus("REWINDING_SCHEDULED");
        schedule.setScheduleType("STOCK");
        double totalLockedArea = stockAllocations.stream()
                .mapToDouble(a -> {
                    Object areaObj = a.containsKey("area") ? a.get("area") : a.get("qty");
                    if (areaObj instanceof Number) {
                        return ((Number) areaObj).doubleValue();
                    } else if (areaObj instanceof String) {
                        return Double.parseDouble((String) areaObj);
                    }
                    return 0;
                })
                .sum();
        if (totalLockedArea > 0) {
            schedule.setRewindingScheduledArea(BigDecimal.valueOf(totalLockedArea).setScale(2, BigDecimal.ROUND_HALF_UP));
        }
        SalesOrderItem orderItemForPending = schedule.getOrderDetailId() == null ? null : salesOrderItemMapper.selectById(schedule.getOrderDetailId());
        String pendingMaterialCode = orderItemForPending == null ? null : orderItemForPending.getMaterialCode();
        BigDecimal targetArea = schedule.getCoatingArea() != null ? schedule.getCoatingArea() : BigDecimal.ZERO;
        BigDecimal lockedAreaBd = BigDecimal.valueOf(totalLockedArea).setScale(2, BigDecimal.ROUND_HALF_UP);
        BigDecimal shortage = targetArea.subtract(lockedAreaBd);
        if (shortage.compareTo(BigDecimal.ZERO) > 0) {
            createPendingMaterialLock(schedule.getId(), schedule.getOrderNo(), pendingMaterialCode, shortage, "rewinding-shortage-pending");
        }
        // 保存锁定的库存号与分配面积
        try {
            List<Map<String, Object>> allocationInfos = new ArrayList<>();
            for (Map<String, Object> allocation : stockAllocations) {
                Map<String, Object> info = new HashMap<>();
                Object stockIdObj = allocation.get("stockId");
                Long stockId = null;
                if (stockIdObj instanceof Number) {
                    stockId = ((Number) stockIdObj).longValue();
                } else if (stockIdObj instanceof String) {
                    stockId = Long.parseLong((String) stockIdObj);
                }
                if (stockId != null) {
                    TapeStock stock = tapeStockService.getStockById(stockId);
                    info.put("stockId", stockId);
                    if (stock != null) {
                        String qrCode = stock.getQrCode();
                        if (qrCode == null || qrCode.trim().isEmpty()) {
                            qrCode = stock.getBatchNo();
                        }
                        info.put("qrCode", qrCode);
                        info.put("batchNo", stock.getBatchNo());
                    }
                }
                Object areaObj = allocation.containsKey("area") ? allocation.get("area") : allocation.get("qty");
                double area = 0;
                if (areaObj instanceof Number) {
                    area = ((Number) areaObj).doubleValue();
                } else if (areaObj instanceof String) {
                    area = Double.parseDouble((String) areaObj);
                }
                info.put("area", area);
                allocationInfos.add(info);
            }
            ObjectMapper mapper = new ObjectMapper();
            schedule.setStockAllocations(mapper.writeValueAsString(allocationInfos));
        } catch (Exception e) {
            // 忽略序列化错误，不影响排程主流程
        }
        this.updateById(schedule);

        // 写入统一排程计划表（复卷）
        try {
            SchedulePlan plan = new SchedulePlan();
            plan.setOrderDetailId(schedule.getOrderDetailId());
            plan.setOrderNo(schedule.getOrderNo());
            SalesOrderItem item = salesOrderItemMapper.selectById(schedule.getOrderDetailId());
            if (item != null) {
                plan.setMaterialCode(item.getMaterialCode());
                plan.setMaterialName(item.getMaterialName());
                plan.setThickness(item.getThickness() != null ? item.getThickness().intValue() : null);
                plan.setWidth(item.getWidth() != null ? item.getWidth().intValue() : null);
                plan.setLength(item.getLength() != null ? item.getLength().intValue() : null);
            }
            plan.setStage("REWINDING");
            if (schedule.getRewindingDate() != null) {
                plan.setPlanDate(schedule.getRewindingDate().atStartOfDay());
            }
            plan.setEquipment(schedule.getRewindingEquipment());
            plan.setPlanArea(schedule.getRewindingScheduledArea());
            plan.setStatus("CONFIRMED");
            schedulePlanService.upsertPlan(plan);
        } catch (Exception e) {
            System.err.println("写入复卷计划失败: " + e.getMessage());
        }
        
        // TODO: 创建复卷排程任务（根据实际业务逻辑）
        // RewindingScheduleService.createTask(schedule, stockAllocations)
        
        // 返回排程ID（待实际创建复卷记录后返回真实ID）
        return scheduleId;
    }

    private String toBaseMaterialCode(String materialCode) {
        if (materialCode == null) {
            return null;
        }
        return materialCode.trim().toUpperCase().replaceAll("-\\d+-\\d+$", "");
    }

    /**
     * 兼容旧料号格式：如 B0-1 -> B01
     */
    private String toRdStyleMaterialCode(String materialCode) {
        if (materialCode == null) {
            return null;
        }
        String normalized = materialCode.trim().toUpperCase();
        return normalized.replaceAll("-([A-Z]\\d)-(\\d)-", "-$1$2-");
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new RuntimeException("日期时间不能为空");
        }
        String v = value.trim();
        List<DateTimeFormatter> formatters = Arrays.asList(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(v, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        try {
            return LocalDate.parse(v, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atTime(8, 0);
        } catch (DateTimeParseException ignored) {
        }
        throw new RuntimeException("日期时间格式错误，支持 yyyy-MM-dd 或 yyyy-MM-dd HH:mm[:ss]");
    }

    private LocalDateTime normalizeEquipmentScheduleStart(String equipmentCode,
                                                          LocalDateTime candidate,
                                                          Integer durationMinutes) {
        if (candidate == null) {
            return null;
        }
        try {
            return equipmentScheduleConfigService.normalizeScheduleStart(equipmentCode, candidate, durationMinutes);
        } catch (Exception ignored) {
            return candidate;
        }
    }

    private EquipmentOccupation findOverlappingOccupation(String equipmentCode,
                                                          String processType,
                                                          Long currentScheduleId,
                                                          LocalDateTime requestedStart) {
        if (equipmentCode == null || requestedStart == null) {
            return null;
        }
        LambdaQueryWrapper<EquipmentOccupation> qw = new LambdaQueryWrapper<>();
        qw.eq(EquipmentOccupation::getEquipmentCode, equipmentCode)
                .eq(EquipmentOccupation::getProcessType, processType)
                .eq(EquipmentOccupation::getStatus, "PLANNED")
                .inSql(EquipmentOccupation::getScheduleId, "SELECT id FROM manual_schedule")
                .ne(currentScheduleId != null, EquipmentOccupation::getScheduleId, currentScheduleId)
                .lt(EquipmentOccupation::getStartTime, requestedStart)
                .gt(EquipmentOccupation::getEndTime, requestedStart)
                .orderByDesc(EquipmentOccupation::getEndTime)
                .last("LIMIT 1");
        return equipmentOccupationMapper.selectOne(qw);
    }

    private LocalDateTime resolveAlignedStartByProcess(String equipmentCode,
                                                       String processType,
                                                       Long currentScheduleId,
                                                       LocalDateTime requestedStart,
                                                       Integer durationMinutes) {
        if (requestedStart == null) {
            return null;
        }
        LocalDateTime candidate = normalizeEquipmentScheduleStart(equipmentCode, requestedStart, durationMinutes);
        for (int i = 0; i < 5; i++) {
            LocalDateTime next = candidate;
            EquipmentOccupation overlap = findOverlappingOccupation(equipmentCode, processType, currentScheduleId, candidate);
            if (overlap != null && overlap.getEndTime() != null && overlap.getEndTime().isAfter(next)) {
                next = overlap.getEndTime();
            }
            LocalDateTime latestEnd = equipmentOccupationMapper.selectLatestEndTime(equipmentCode, processType, currentScheduleId);
            if (latestEnd != null && latestEnd.isAfter(next)) {
                next = latestEnd;
            }
            next = normalizeEquipmentScheduleStart(equipmentCode, next, durationMinutes);
            if (next == null || next.equals(candidate)) {
                return next == null ? candidate : next;
            }
            candidate = next;
        }
        return candidate;
    }

    private int calcDurationMinutes(Double coatingLength, BigDecimal speed) {
        if (coatingLength == null || coatingLength <= 0 || speed == null || speed.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        double raw = coatingLength / speed.doubleValue();
        return (int) (Math.ceil(raw / 10.0) * 10);
    }

    private int calcDurationMinutesByRolls(Integer rolls, BigDecimal speed) {
        if (rolls == null || rolls <= 0 || speed == null || speed.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        double raw = rolls / speed.doubleValue();
        return (int) Math.ceil(raw);
    }

    private int calcRewindingDurationMinutesByArea(Double areaSquareMeter, Double rewindingWidthMm, BigDecimal speedMeterPerMinute) {
        if (areaSquareMeter == null || areaSquareMeter <= 0
                || rewindingWidthMm == null || rewindingWidthMm <= 0
                || speedMeterPerMinute == null || speedMeterPerMinute.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        double widthMeter = rewindingWidthMm / 1000.0;
        if (widthMeter <= 0) {
            return 0;
        }
        double rewindingLengthMeter = areaSquareMeter / widthMeter;
        if (rewindingLengthMeter <= 0) {
            return 0;
        }
        double rawMinutes = rewindingLengthMeter / speedMeterPerMinute.doubleValue();
        return (int) (Math.ceil(rawMinutes / 10.0) * 10);
    }

    private LocalDateTime resolveCoatingStartTime(ManualSchedule schedule) {
        if (schedule == null || schedule.getId() == null) {
            return null;
        }
        LocalDateTime coatingStart = equipmentOccupationMapper.selectStartTimeByScheduleAndProcess(schedule.getId(), "COATING");
        if (coatingStart != null) {
            return coatingStart;
        }
        if (schedule.getCoatingScheduleDate() != null) {
            return schedule.getCoatingScheduleDate().atTime(8, 0);
        }
        if (schedule.getCoatingDate() != null) {
            return schedule.getCoatingDate().atTime(8, 0);
        }
        return null;
    }

    private LocalDateTime resolveRewindingEndTime(ManualSchedule schedule) {
        if (schedule == null || schedule.getId() == null) {
            return null;
        }
        LocalDateTime rewindingEnd = equipmentOccupationMapper.selectEndTimeByScheduleAndProcess(schedule.getId(), "REWINDING");
        if (rewindingEnd != null) {
            return rewindingEnd;
        }
        if (schedule.getRewindingDate() != null) {
            return schedule.getRewindingDate().atTime(8, 0);
        }
        return null;
    }

    private void validateEquipmentAvailabilityForManualSchedule(Equipment equipment, String equipmentCode, LocalDate planDate) {
        if (equipmentCode == null || equipmentCode.trim().isEmpty()) {
            throw new RuntimeException("机台编码为空，无法排程");
        }

        Equipment eq = equipment;
        if (eq == null) {
            eq = resolveEquipmentByIdOrCode(equipmentCode.trim());
        }
        if (eq == null) {
            throw new RuntimeException("机台不存在，无法排程");
        }

        String status = eq.getStatus() == null ? "" : eq.getStatus().trim().toLowerCase();
        if (!"normal".equals(status)) {
            throw new RuntimeException("机台当前状态不可排程: " + (eq.getStatus() == null ? "未知" : eq.getStatus()));
        }

        EquipmentScheduleConfig config = equipmentScheduleConfigService.getEffectiveConfig(equipmentCode.trim());
        if (config != null && config.getEnabled() != null && config.getEnabled() == 0) {
            throw new RuntimeException("机台今日未安排生产（排程配置已停用）");
        }

        LocalDate targetDate = planDate == null ? LocalDate.now() : planDate;
        LocalDateTime targetDateTime = targetDate.atTime(8, 0, 0);
        EquipmentDailyStatus dailyStatus = equipmentDailyStatusMapper.selectByDateAndEquipmentCode(targetDateTime, equipmentCode.trim());
        if (dailyStatus == null) {
            throw new RuntimeException("机台当日状态未维护，请先在设备日历维护");
        }
        String ds = dailyStatus.getDailyStatus() == null ? "OPEN" : dailyStatus.getDailyStatus().trim().toUpperCase();
        if (!"OPEN".equals(ds)) {
            throw new RuntimeException("机台当日状态不可排程: " + ds);
        }

        String equipmentType = eq.getEquipmentType();
        if (equipmentType != null && !equipmentType.trim().isEmpty()) {
            List<ProductionStaff> staffList = productionStaffService.getStaffByEquipmentType(equipmentType.trim());
            if (staffList == null || staffList.isEmpty()) {
                throw new RuntimeException("人员不足：当前无可操作该机型的在岗人员");
            }
        }

        int required = dailyStatus.getMinStaffRequired() == null || dailyStatus.getMinStaffRequired() <= 0
                ? 1
                : dailyStatus.getMinStaffRequired();
        int available = equipmentDailyPlanningService.countOnDutyQualifiedStaff(
                equipmentCode.trim(),
                targetDate,
                dailyStatus.getRequiredSkillLevel());
        if (available < required) {
            throw new RuntimeException("人员不足：当日在岗可操作人数(" + available + ")小于最低需求(" + required + ")");
        }
    }

    private void shiftConflictingCoatingOccupations(String equipmentCode,
                                                    Long currentScheduleId,
                                                    LocalDateTime currentStart,
                                                    LocalDateTime currentEnd) {
        LambdaQueryWrapper<EquipmentOccupation> qw = new LambdaQueryWrapper<>();
        qw.eq(EquipmentOccupation::getEquipmentCode, equipmentCode)
                .eq(EquipmentOccupation::getProcessType, "COATING")
                .eq(EquipmentOccupation::getStatus, "PLANNED")
            .inSql(EquipmentOccupation::getScheduleId, "SELECT id FROM manual_schedule")
                .ne(EquipmentOccupation::getScheduleId, currentScheduleId)
                .gt(EquipmentOccupation::getEndTime, currentStart)
                .orderByAsc(EquipmentOccupation::getStartTime)
                .orderByAsc(EquipmentOccupation::getId);

        List<EquipmentOccupation> occupations = equipmentOccupationMapper.selectList(qw);
        LocalDateTime cursor = currentEnd;

        for (EquipmentOccupation occ : occupations) {
            LocalDateTime start = occ.getStartTime();
            LocalDateTime end = occ.getEndTime();
            if (start == null || end == null) {
                continue;
            }
            int duration = occ.getDurationMinutes() != null && occ.getDurationMinutes() > 0
                    ? occ.getDurationMinutes()
                    : (int) java.time.Duration.between(start, end).toMinutes();
            if (duration <= 0) {
                duration = 10;
            }

            if (start.isBefore(cursor)) {
                LocalDateTime newStart = normalizeEquipmentScheduleStart(equipmentCode, cursor, duration);
                LocalDateTime newEnd = newStart.plusMinutes(duration);
                occ.setStartTime(newStart);
                occ.setEndTime(newEnd);
                occ.setDurationMinutes(duration);
                equipmentOccupationMapper.updateById(occ);

                ManualSchedule shifted = this.getById(occ.getScheduleId());
                if (shifted != null) {
                    shifted.setCoatingScheduleDate(newStart.toLocalDate());
                    shifted.setCoatingDate(newStart.toLocalDate());
                    this.updateById(shifted);

                    try {
                        SchedulePlan shiftedPlan = new SchedulePlan();
                        shiftedPlan.setOrderDetailId(shifted.getOrderDetailId());
                        shiftedPlan.setOrderNo(shifted.getOrderNo());
                        shiftedPlan.setStage("COATING");
                        shiftedPlan.setPlanDate(newStart);
                        shiftedPlan.setEquipment(shifted.getCoatingEquipment());
                        shiftedPlan.setPlanArea(shifted.getCoatingArea());
                        shiftedPlan.setStatus("CONFIRMED");
                        schedulePlanService.upsertPlan(shiftedPlan);
                    } catch (Exception ignored) {
                    }
                }
                cursor = newEnd;
            } else {
                cursor = end;
            }
        }
    }

    private Equipment resolveEquipmentByIdOrCode(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String v = value.trim();
        try {
            Long id = Long.parseLong(v);
            Equipment byId = equipmentMapper.selectById(id);
            if (byId != null) {
                return byId;
            }
        } catch (NumberFormatException ignored) {
        }
        QueryWrapper<Equipment> qw = new QueryWrapper<>();
        qw.eq("equipment_code", v).eq("is_deleted", 0).last("LIMIT 1");
        return equipmentMapper.selectOne(qw);
    }

    private void shiftConflictingRewindingOccupations(String equipmentCode,
                                                      Long currentScheduleId,
                                                      LocalDateTime currentStart,
                                                      LocalDateTime currentEnd) {
        LambdaQueryWrapper<EquipmentOccupation> qw = new LambdaQueryWrapper<>();
        qw.eq(EquipmentOccupation::getEquipmentCode, equipmentCode)
                .eq(EquipmentOccupation::getProcessType, "REWINDING")
                .eq(EquipmentOccupation::getStatus, "PLANNED")
                .inSql(EquipmentOccupation::getScheduleId, "SELECT id FROM manual_schedule")
                .ne(EquipmentOccupation::getScheduleId, currentScheduleId)
            .ge(EquipmentOccupation::getStartTime, currentStart)
                .orderByAsc(EquipmentOccupation::getStartTime)
                .orderByAsc(EquipmentOccupation::getId);

        List<EquipmentOccupation> occupations = equipmentOccupationMapper.selectList(qw);
        LocalDateTime cursor = currentEnd;

        for (EquipmentOccupation occ : occupations) {
            LocalDateTime start = occ.getStartTime();
            LocalDateTime end = occ.getEndTime();
            if (start == null || end == null) {
                continue;
            }
            int duration = occ.getDurationMinutes() != null && occ.getDurationMinutes() > 0
                    ? occ.getDurationMinutes()
                    : (int) java.time.Duration.between(start, end).toMinutes();
            if (duration <= 0) {
                duration = 10;
            }

            if (start.isBefore(cursor)) {
                LocalDateTime newStart = normalizeEquipmentScheduleStart(equipmentCode, cursor, duration);
                LocalDateTime newEnd = newStart.plusMinutes(duration);
                occ.setStartTime(newStart);
                occ.setEndTime(newEnd);
                occ.setDurationMinutes(duration);
                equipmentOccupationMapper.updateById(occ);

                ManualSchedule shifted = this.getById(occ.getScheduleId());
                if (shifted != null) {
                    shifted.setRewindingDate(newStart.toLocalDate());
                    shifted.setRewindingScheduleDate(newStart.toLocalDate());
                    this.updateById(shifted);

                    try {
                        SchedulePlan shiftedPlan = new SchedulePlan();
                        shiftedPlan.setOrderDetailId(shifted.getOrderDetailId());
                        shiftedPlan.setOrderNo(shifted.getOrderNo());
                        shiftedPlan.setStage("REWINDING");
                        shiftedPlan.setPlanDate(newStart);
                        shiftedPlan.setEquipment(shifted.getRewindingEquipment());
                        shiftedPlan.setPlanArea(shifted.getRewindingScheduledArea());
                        shiftedPlan.setStatus("CONFIRMED");
                        schedulePlanService.upsertPlan(shiftedPlan);
                    } catch (Exception ignored) {
                    }
                }
                cursor = newEnd;
            } else {
                cursor = end;
            }
        }
    }

    private LocalDateTime alignStartAvoidRewindingOverlap(String equipmentCode,
                                                          Long currentScheduleId,
                                                          LocalDateTime requestedStart,
                                                          Integer durationMinutes) {
        return resolveAlignedStartByProcess(equipmentCode, "REWINDING", currentScheduleId, requestedStart, durationMinutes);
    }

    private void shiftConflictingSlittingOccupations(String equipmentCode,
                                                     Long currentScheduleId,
                                                     LocalDateTime currentStart,
                                                     LocalDateTime currentEnd) {
        LambdaQueryWrapper<EquipmentOccupation> qw = new LambdaQueryWrapper<>();
        qw.eq(EquipmentOccupation::getEquipmentCode, equipmentCode)
                .eq(EquipmentOccupation::getProcessType, "SLITTING")
                .eq(EquipmentOccupation::getStatus, "PLANNED")
                .inSql(EquipmentOccupation::getScheduleId, "SELECT id FROM manual_schedule")
                .ne(EquipmentOccupation::getScheduleId, currentScheduleId)
            .ge(EquipmentOccupation::getStartTime, currentStart)
                .orderByAsc(EquipmentOccupation::getStartTime)
                .orderByAsc(EquipmentOccupation::getId);

        List<EquipmentOccupation> occupations = equipmentOccupationMapper.selectList(qw);
        LocalDateTime cursor = currentEnd;

        for (EquipmentOccupation occ : occupations) {
            LocalDateTime start = occ.getStartTime();
            LocalDateTime end = occ.getEndTime();
            if (start == null || end == null) {
                continue;
            }
            int duration = occ.getDurationMinutes() != null && occ.getDurationMinutes() > 0
                    ? occ.getDurationMinutes()
                    : (int) java.time.Duration.between(start, end).toMinutes();
            if (duration <= 0) {
                duration = 10;
            }

            if (start.isBefore(cursor)) {
                LocalDateTime newStart = normalizeEquipmentScheduleStart(equipmentCode, cursor, duration);
                LocalDateTime newEnd = newStart.plusMinutes(duration);
                occ.setStartTime(newStart);
                occ.setEndTime(newEnd);
                occ.setDurationMinutes(duration);
                equipmentOccupationMapper.updateById(occ);

                ManualSchedule shifted = this.getById(occ.getScheduleId());
                if (shifted != null) {
                    shifted.setSlittingScheduleDate(newStart.toLocalDate());
                    shifted.setPackagingDate(newStart.toLocalDate());
                    this.updateById(shifted);

                    try {
                        SchedulePlan shiftedPlan = new SchedulePlan();
                        shiftedPlan.setOrderDetailId(shifted.getOrderDetailId());
                        shiftedPlan.setOrderNo(shifted.getOrderNo());
                        shiftedPlan.setStage("SLITTING");
                        shiftedPlan.setPlanDate(newStart);
                        shiftedPlan.setEquipment(equipmentCode);
                        shiftedPlan.setPlanArea(shifted.getCoatingArea());
                        shiftedPlan.setStatus("CONFIRMED");
                        schedulePlanService.upsertPlan(shiftedPlan);
                    } catch (Exception ignored) {
                    }
                }
                cursor = newEnd;
            } else {
                cursor = end;
            }
        }
    }

    private LocalDateTime alignStartAvoidSlittingOverlap(String equipmentCode,
                                                         Long currentScheduleId,
                                                         LocalDateTime requestedStart,
                                                         Integer durationMinutes) {
        return resolveAlignedStartByProcess(equipmentCode, "SLITTING", currentScheduleId, requestedStart, durationMinutes);
    }

    @Override
    public Map<String, Object> previewRewindingOccupation(Long scheduleId, String rewindingEquipment, String rewindingDate) {
        if (scheduleId == null) {
            throw new RuntimeException("scheduleId 不能为空");
        }
        ManualSchedule schedule = this.getById(scheduleId);
        if (schedule == null) {
            throw new RuntimeException("排程记录不存在");
        }
        if (rewindingEquipment == null || rewindingEquipment.trim().isEmpty()) {
            throw new RuntimeException("请先选择复卷机台");
        }
        if (rewindingDate == null || rewindingDate.trim().isEmpty()) {
            throw new RuntimeException("请先选择复卷日期时间");
        }

        Equipment equipment = resolveEquipmentByIdOrCode(rewindingEquipment);
        if (equipment == null) {
            throw new RuntimeException("复卷机台不存在");
        }
        String equipmentCode = equipment.getEquipmentCode();
        if (equipmentCode == null || equipmentCode.trim().isEmpty()) {
            throw new RuntimeException("复卷机台编码为空，无法排程");
        }
        LocalDateTime requestedStart = parseDateTime(rewindingDate);
        validateEquipmentAvailabilityForManualSchedule(equipment, equipmentCode, requestedStart.toLocalDate());

        SalesOrderItem item = salesOrderItemMapper.selectById(schedule.getOrderDetailId());
        if (item == null) {
            throw new RuntimeException("订单明细不存在");
        }
        String materialCode = item.getMaterialCode();
        String baseCode = toBaseMaterialCode(materialCode);

        RewindingProcessParams params = rewindingProcessParamsService.getByMaterialAndEquipment(materialCode, equipmentCode);
        if ((params == null || params.getRewindingSpeed() == null || params.getRewindingSpeed().compareTo(BigDecimal.ZERO) <= 0)
                && baseCode != null && !baseCode.equalsIgnoreCase(materialCode)) {
            params = rewindingProcessParamsService.getByMaterialAndEquipment(baseCode, equipmentCode);
        }

        if (params == null || params.getRewindingSpeed() == null || params.getRewindingSpeed().compareTo(BigDecimal.ZERO) <= 0) {
            String rdStyleCode = toRdStyleMaterialCode(materialCode);
            if (rdStyleCode != null && !rdStyleCode.equalsIgnoreCase(materialCode)) {
                params = rewindingProcessParamsService.getByMaterialAndEquipment(rdStyleCode, equipmentCode);
                if ((params == null || params.getRewindingSpeed() == null || params.getRewindingSpeed().compareTo(BigDecimal.ZERO) <= 0)) {
                    String rdBaseCode = toBaseMaterialCode(rdStyleCode);
                    if (rdBaseCode != null && !rdBaseCode.equalsIgnoreCase(rdStyleCode)) {
                        params = rewindingProcessParamsService.getByMaterialAndEquipment(rdBaseCode, equipmentCode);
                    }
                }
            }
        }

        if (params == null || params.getRewindingSpeed() == null || params.getRewindingSpeed().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("未维护该料号的复卷速度，请先在工艺参数中维护");
        }

        Double rewindingArea = schedule.getRewindingScheduledArea() != null
                ? schedule.getRewindingScheduledArea().doubleValue()
                : null;
        if (rewindingArea == null || rewindingArea <= 0) {
            rewindingArea = schedule.getCoatingArea() != null ? schedule.getCoatingArea().doubleValue() : null;
        }
        Double rewindingWidthMm = schedule.getRewindingWidth() != null
                ? schedule.getRewindingWidth().doubleValue()
                : null;
        if (rewindingWidthMm == null || rewindingWidthMm <= 0) {
            rewindingWidthMm = item.getWidth() != null ? item.getWidth().doubleValue() : 500D;
        }

        int durationMinutes = calcRewindingDurationMinutesByArea(rewindingArea, rewindingWidthMm, params.getRewindingSpeed());
        if (durationMinutes <= 0) {
            throw new RuntimeException("复卷面积/宽度/速度无效，无法计算机台占用时长");
        }

        LocalDateTime suggestedStart = alignStartAvoidRewindingOverlap(equipmentCode, scheduleId, requestedStart, durationMinutes);
        LocalDateTime coatingStart = resolveCoatingStartTime(schedule);
        boolean earlierThanCoating = coatingStart != null && suggestedStart.isBefore(coatingStart);
        if (earlierThanCoating) {
            suggestedStart = alignStartAvoidRewindingOverlap(equipmentCode, scheduleId, coatingStart, durationMinutes);
        }
        LocalDateTime latestEnd = equipmentOccupationMapper.selectLatestEndTime(equipmentCode, "REWINDING", scheduleId);
        LocalDateTime suggestedEnd = suggestedStart.plusMinutes(durationMinutes);

        Map<String, Object> result = new HashMap<>();
        result.put("equipmentId", equipment.getId());
        result.put("equipmentCode", equipmentCode);
        result.put("equipmentName", equipment.getEquipmentName());
        result.put("materialCode", materialCode);
        result.put("rewindingSpeed", params.getRewindingSpeed());
        result.put("rewindingArea", rewindingArea);
        result.put("rewindingWidth", rewindingWidthMm);
        result.put("durationMinutes", durationMinutes);
        result.put("suggestedStart", suggestedStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        result.put("suggestedEnd", suggestedEnd.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        result.put("latestEnd", latestEnd == null ? null : latestEnd.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        if (coatingStart != null) {
            result.put("coatingStart", coatingStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        result.put("earlierThanCoating", earlierThanCoating);
        if (earlierThanCoating) {
            result.put("warning", "复卷开始时间不能早于涂布开始时间，已自动调整");
        }
        return result;
    }

    @Override
    public Map<String, Object> previewSlittingOccupation(Long scheduleId, String slittingEquipment, String packagingDate) {
        if (scheduleId == null) {
            throw new RuntimeException("scheduleId 不能为空");
        }
        ManualSchedule schedule = this.getById(scheduleId);
        if (schedule == null) {
            throw new RuntimeException("排程记录不存在");
        }
        if (slittingEquipment == null || slittingEquipment.trim().isEmpty()) {
            throw new RuntimeException("请先选择分切机台");
        }
        if (packagingDate == null || packagingDate.trim().isEmpty()) {
            throw new RuntimeException("请先选择分切日期时间");
        }

        String equipmentCode = slittingEquipment.trim();
        Equipment eq = resolveEquipmentByIdOrCode(equipmentCode);
        if (eq != null && eq.getEquipmentCode() != null && !eq.getEquipmentCode().trim().isEmpty()) {
            equipmentCode = eq.getEquipmentCode().trim();
        }
        LocalDateTime requestedStart = parseDateTime(packagingDate);
        validateEquipmentAvailabilityForManualSchedule(eq, equipmentCode, requestedStart.toLocalDate());

        SalesOrderItem item = salesOrderItemMapper.selectById(schedule.getOrderDetailId());
        if (item == null) {
            throw new RuntimeException("订单明细不存在");
        }

        SlittingProcessParams slittingParams = slittingProcessParamsService.getByDimensions(
                item.getThickness(),
                item.getLength(),
                item.getWidth(),
                equipmentCode
        );
        if (slittingParams == null || slittingParams.getProductionSpeed() == null || slittingParams.getProductionSpeed().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("未维护该规格在当前机台的分切速度，请先在分切参数中维护");
        }

        Integer durationMinutes = calcDurationMinutesByRolls(schedule.getScheduleQty(), slittingParams.getProductionSpeed());
        if (durationMinutes == null || durationMinutes <= 0) {
            throw new RuntimeException("分切卷数或分切速度无效，无法计算机台占用时长");
        }

        LocalDateTime rewindingEnd = resolveRewindingEndTime(schedule);
        boolean earlierThanRewinding = rewindingEnd != null && requestedStart.isBefore(rewindingEnd);
        if (earlierThanRewinding) {
            requestedStart = rewindingEnd;
        }

        LocalDateTime latestEnd = equipmentOccupationMapper.selectLatestEndTime(equipmentCode, "SLITTING", scheduleId);
        LocalDateTime suggestedStart = alignStartAvoidSlittingOverlap(equipmentCode, scheduleId, requestedStart, durationMinutes);
        if (rewindingEnd != null && suggestedStart.isBefore(rewindingEnd)) {
            suggestedStart = alignStartAvoidSlittingOverlap(equipmentCode, scheduleId, rewindingEnd, durationMinutes);
        }
        LocalDateTime suggestedEnd = suggestedStart.plusMinutes(durationMinutes);

        Map<String, Object> result = new HashMap<>();
        result.put("equipmentCode", equipmentCode);
        result.put("equipmentName", eq == null ? null : eq.getEquipmentName());
        result.put("productionSpeed", slittingParams.getProductionSpeed());
        result.put("durationMinutes", durationMinutes);
        result.put("suggestedStart", suggestedStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        result.put("suggestedEnd", suggestedEnd.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        result.put("latestEnd", latestEnd == null ? null : latestEnd.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        result.put("rewindingEnd", rewindingEnd == null ? null : rewindingEnd.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        result.put("earlierThanRewinding", earlierThanRewinding);
        if (earlierThanRewinding) {
            result.put("warning", "分切开始时间不能早于复卷结束时间，已自动调整");
        }
        return result;
    }

    @Override
    public Map<String, Object> previewCoatingOccupation(Long scheduleId, String equipmentId, String coatingDate, Double coatingLength) {
        if (scheduleId == null) {
            throw new RuntimeException("scheduleId 不能为空");
        }
        ManualSchedule schedule = this.getById(scheduleId);
        if (schedule == null) {
            throw new RuntimeException("排程记录不存在");
        }
        if (equipmentId == null || equipmentId.trim().isEmpty()) {
            throw new RuntimeException("请先选择涂布机台");
        }
        if (coatingDate == null || coatingDate.trim().isEmpty()) {
            throw new RuntimeException("请先选择涂布日期时间");
        }

        Equipment equipment = equipmentMapper.selectById(Long.parseLong(equipmentId));
        if (equipment == null) {
            throw new RuntimeException("机台不存在");
        }
        String equipmentCode = equipment.getEquipmentCode();
        if (equipmentCode == null || equipmentCode.trim().isEmpty()) {
            throw new RuntimeException("机台编码为空，无法排程");
        }
        LocalDateTime requestedStart = parseDateTime(coatingDate);
        validateEquipmentAvailabilityForManualSchedule(equipment, equipmentCode, requestedStart.toLocalDate());

        SalesOrderItem item = salesOrderItemMapper.selectById(schedule.getOrderDetailId());
        if (item == null) {
            throw new RuntimeException("订单明细不存在");
        }
        String materialCode = item.getMaterialCode();
        String baseCode = toBaseMaterialCode(materialCode);
        String rdStyleCode = toRdStyleMaterialCode(materialCode);

        ProcessParams params = processParamsService.getByMaterialAndProcess(materialCode, "COATING", equipmentCode);
        if ((params == null || params.getCoatingSpeed() == null || params.getCoatingSpeed().compareTo(BigDecimal.ZERO) <= 0)
                && baseCode != null && !baseCode.equalsIgnoreCase(materialCode)) {
            params = processParamsService.getByMaterialAndProcess(baseCode, "COATING", equipmentCode);
        }
        if ((params == null || params.getCoatingSpeed() == null || params.getCoatingSpeed().compareTo(BigDecimal.ZERO) <= 0)
                && rdStyleCode != null && !rdStyleCode.equalsIgnoreCase(materialCode)) {
            params = processParamsService.getByMaterialAndProcess(rdStyleCode, "COATING", equipmentCode);
        }
        if (params == null || params.getCoatingSpeed() == null || params.getCoatingSpeed().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("未维护该料号在当前机台的涂布速度，请先在工艺参数中维护");
        }

        Double finalLength = null;
        Double finalArea = schedule.getCoatingArea() != null ? schedule.getCoatingArea().doubleValue() : null;
        Double finalWidth = schedule.getCoatingWidth() != null ? schedule.getCoatingWidth().doubleValue() : null;
        if (finalWidth == null || finalWidth <= 0) {
            finalWidth = 1040D;
        }
        if (coatingLength != null && coatingLength > 0) {
            finalLength = coatingLength;
        }
        if ((finalLength == null || finalLength <= 0)
                && schedule.getCoatingLength() != null && schedule.getCoatingLength().compareTo(BigDecimal.ZERO) > 0) {
            finalLength = schedule.getCoatingLength().doubleValue();
        }
        if ((finalLength == null || finalLength <= 0)
                && finalArea != null && finalArea > 0 && finalWidth != null && finalWidth > 0) {
            finalLength = finalArea / (finalWidth / 1000.0);
        }
        int durationMinutes = calcDurationMinutes(finalLength, params.getCoatingSpeed());
        if (durationMinutes <= 0) {
            throw new RuntimeException("涂布长度无效，无法计算机台占用时长");
        }

        LocalDateTime latestEnd = equipmentOccupationMapper.selectLatestEndTime(equipmentCode, "COATING", scheduleId);
        LocalDateTime suggestedStart = resolveAlignedStartByProcess(equipmentCode, "COATING", scheduleId, requestedStart, durationMinutes);
        LocalDateTime suggestedEnd = suggestedStart.plusMinutes(durationMinutes);

        Map<String, Object> result = new HashMap<>();
        result.put("equipmentId", equipment.getId());
        result.put("equipmentCode", equipmentCode);
        result.put("equipmentName", equipment.getEquipmentName());
        result.put("materialCode", materialCode);
        result.put("coatingSpeed", params.getCoatingSpeed());
        result.put("coatingLength", finalLength);
        result.put("coatingArea", finalArea);
        result.put("coatingWidth", finalWidth);
        result.put("durationMinutes", durationMinutes);
        result.put("suggestedStart", suggestedStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        result.put("suggestedEnd", suggestedEnd.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        result.put("latestEnd", latestEnd == null ? null : latestEnd.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return result;
    }
    
    @Override
    @Transactional
    public Long createCoatingSchedule(Long scheduleId, Double coatingArea, String coatingDate, String rewindingDate, String packagingDate, String equipmentId,
                                      Double coatingWidth, Double coatingLength) {
        ManualSchedule schedule = this.getById(scheduleId);
        if (schedule == null) {
            throw new RuntimeException("排程记录不存在");
        }
        ensureOrderDetailSchedulable(schedule.getOrderDetailId());

        Map<String, Object> occupation = previewCoatingOccupation(scheduleId, equipmentId, coatingDate, coatingLength);
        Integer durationMinutes = ((Number) occupation.get("durationMinutes")).intValue();
        String equipmentCode = String.valueOf(occupation.get("equipmentCode"));
        LocalDateTime selectedStart = parseDateTime(String.valueOf(occupation.get("suggestedStart")));
        LocalDateTime selectedEnd = selectedStart.plusMinutes(durationMinutes);

        shiftConflictingCoatingOccupations(equipmentCode, scheduleId, selectedStart, selectedEnd);
        
        // 更新涂布信息（面积以预估结果为唯一口径，避免前后端口径漂移）
        Object calcAreaObj = occupation.get("coatingArea");
        Double finalCoatingArea = null;
        if (calcAreaObj instanceof Number) {
            finalCoatingArea = ((Number) calcAreaObj).doubleValue();
        }
        if (finalCoatingArea == null || finalCoatingArea <= 0) {
            finalCoatingArea = coatingArea;
        }
        if (finalCoatingArea == null || finalCoatingArea <= 0) {
            throw new RuntimeException("涂布面积无效，无法保存排程");
        }
        schedule.setCoatingArea(BigDecimal.valueOf(finalCoatingArea));
        schedule.setCoatingScheduleDate(selectedStart.toLocalDate());
        schedule.setCoatingDate(selectedStart.toLocalDate());
        if (rewindingDate != null && !rewindingDate.isEmpty()) {
            String datePart = rewindingDate.length() > 10 ? rewindingDate.substring(0, 10) : rewindingDate;
            schedule.setRewindingDate(java.time.LocalDate.parse(datePart));
        }
        if (packagingDate != null && !packagingDate.isEmpty()) {
            String datePart = packagingDate.length() > 10 ? packagingDate.substring(0, 10) : packagingDate;
            schedule.setPackagingDate(java.time.LocalDate.parse(datePart));
        }
        if (equipmentId != null && !equipmentId.isEmpty()) {
            schedule.setCoatingEquipment(equipmentId);
        }
        if (coatingWidth != null && coatingWidth > 0) {
            schedule.setCoatingWidth(BigDecimal.valueOf(coatingWidth));
        }
        if (coatingLength != null && coatingLength > 0) {
            schedule.setCoatingLength(BigDecimal.valueOf(coatingLength));
        } else {
            Object calcLengthObj = occupation.get("coatingLength");
            if (calcLengthObj instanceof Number) {
                double calcLength = ((Number) calcLengthObj).doubleValue();
                if (calcLength > 0) {
                    schedule.setCoatingLength(BigDecimal.valueOf(calcLength));
                }
            }
        }
        schedule.setStatus("COATING_SCHEDULED");
        schedule.setScheduleType("COATING");
        
        this.updateById(schedule);

        SalesOrderItem coatingItemForBom = schedule.getOrderDetailId() == null ? null : salesOrderItemMapper.selectById(schedule.getOrderDetailId());
        if (coatingItemForBom != null) {
            createCoatingBomPendingLocksIfNeeded(schedule.getId(), schedule.getOrderNo(), coatingItemForBom.getMaterialCode(), schedule.getCoatingArea());
        }

        // 写入设备占用时间轴（涂布）
        EquipmentOccupation oc = new EquipmentOccupation();
        oc.setScheduleId(scheduleId);
        oc.setProcessType("COATING");
        oc.setEquipmentId(Long.parseLong(equipmentId));
        oc.setEquipmentCode(equipmentCode);
        oc.setStartTime(selectedStart);
        oc.setEndTime(selectedEnd);
        oc.setDurationMinutes(durationMinutes);
        oc.setStatus("PLANNED");
        oc.setRemark("手动排程自动回推机台占用");
        equipmentOccupationMapper.deleteByScheduleAndProcess(scheduleId, "COATING");
        equipmentOccupationMapper.insert(oc);

        // 写入统一排程计划表
        try {
            SchedulePlan plan = new SchedulePlan();
            plan.setOrderDetailId(schedule.getOrderDetailId());
            plan.setOrderNo(schedule.getOrderNo());
            SalesOrderItem item = salesOrderItemMapper.selectById(schedule.getOrderDetailId());
            if (item != null) {
                plan.setMaterialCode(item.getMaterialCode());
                plan.setMaterialName(item.getMaterialName());
                plan.setThickness(item.getThickness() != null ? item.getThickness().intValue() : null);
                if (schedule.getCoatingWidth() != null && schedule.getCoatingWidth().compareTo(BigDecimal.ZERO) > 0) {
                    plan.setWidth(schedule.getCoatingWidth().intValue());
                } else {
                    plan.setWidth(item.getWidth() != null ? item.getWidth().intValue() : null);
                }
                if (schedule.getCoatingLength() != null && schedule.getCoatingLength().compareTo(BigDecimal.ZERO) > 0) {
                    plan.setLength(schedule.getCoatingLength().intValue());
                } else {
                    plan.setLength(item.getLength() != null ? item.getLength().intValue() : null);
                }
            }
            plan.setStage("COATING");
            plan.setPlanDate(selectedStart);
            plan.setEquipment(schedule.getCoatingEquipment());
            plan.setPlanArea(schedule.getCoatingArea());
            plan.setStatus("CONFIRMED");
            schedulePlanService.upsertPlan(plan);
        } catch (Exception e) {
            System.err.println("写入统一排程计划失败: " + e.getMessage());
        }
        
        // 回写涂布日期到关联的销售订单（以计划时间为准）
        try {
            String planDate = null;
            if (schedule.getCoatingScheduleDate() != null) {
                planDate = schedule.getCoatingScheduleDate().toString();
            } else if (coatingDate != null && !coatingDate.isEmpty()) {
                planDate = coatingDate.length() > 10 ? coatingDate.substring(0, 10) : coatingDate;
            }
            if (planDate != null && !planDate.isEmpty()) {
                // 1) 先确保当前订单明细一定回写
                if (schedule.getOrderDetailId() != null) {
                    scheduleMapper.updateSalesOrderCoatingDateByDetailId(schedule.getOrderDetailId(), planDate);
                }

                // 2) 若有涂布覆盖明细，回写所有被纳入关联订单
                scheduleMapper.updateSalesOrderCoatingDateByScheduleAllocation(scheduleId, planDate);

                // 不再按料号+厚度做全局兜底回写，避免跨订单误更新
            }
        } catch (Exception e) {
            // 日志记录但不中断主流程
            System.err.println("回写涂布日期失败: " + e.getMessage());
        }
        
            // 返回涂布排程ID
            return scheduleId;
    }

    @Override
    @Transactional
    public boolean updateRewindingInfo(Long scheduleId, Double rewindingArea, String rewindingDate, String rewindingEquipment, Double rewindingWidth) {
        ManualSchedule schedule = this.getById(scheduleId);
        if (schedule == null) {
            throw new RuntimeException("排程记录不存在");
        }

        Map<String, Object> occupation = previewRewindingOccupation(scheduleId, rewindingEquipment, rewindingDate);
        Double finalRewindingArea = null;
        Object rewindingAreaObj = occupation.get("rewindingArea");
        if (rewindingAreaObj instanceof Number) {
            finalRewindingArea = ((Number) rewindingAreaObj).doubleValue();
        }

        Double finalRewindingWidth = null;
        Object rewindingWidthObj = occupation.get("rewindingWidth");
        if (rewindingWidthObj instanceof Number) {
            finalRewindingWidth = ((Number) rewindingWidthObj).doubleValue();
        }

        int durationMinutes = occupation.get("durationMinutes") instanceof Number
                ? ((Number) occupation.get("durationMinutes")).intValue()
                : 0;
        if (durationMinutes <= 0) {
            throw new RuntimeException("复卷面积/宽度/速度无效，无法计算机台占用时长");
        }
        String equipmentCode = String.valueOf(occupation.get("equipmentCode"));
        LocalDateTime selectedStart = parseDateTime(String.valueOf(occupation.get("suggestedStart")));
        selectedStart = alignStartAvoidRewindingOverlap(equipmentCode, scheduleId, selectedStart, durationMinutes);
        LocalDateTime coatingStart = resolveCoatingStartTime(schedule);
        if (coatingStart != null && selectedStart.isBefore(coatingStart)) {
            selectedStart = alignStartAvoidRewindingOverlap(equipmentCode, scheduleId, coatingStart, durationMinutes);
        }
        LocalDateTime selectedEnd = selectedStart.plusMinutes(durationMinutes);

        shiftConflictingRewindingOccupations(equipmentCode, scheduleId, selectedStart, selectedEnd);

        if (finalRewindingArea == null || finalRewindingArea <= 0) {
            throw new RuntimeException("复卷面积无效，无法保存排程");
        }
        if (finalRewindingWidth == null || finalRewindingWidth <= 0) {
            throw new RuntimeException("复卷宽度无效，无法保存排程");
        }
        schedule.setRewindingScheduledArea(BigDecimal.valueOf(finalRewindingArea));
        schedule.setRewindingWidth(BigDecimal.valueOf(finalRewindingWidth));
        schedule.setRewindingDate(selectedStart.toLocalDate());
        schedule.setRewindingScheduleDate(selectedStart.toLocalDate());
        schedule.setRewindingEquipment(equipmentCode);
        schedule.setStatus("REWINDING_SCHEDULED");
        boolean updated = this.updateById(schedule);

        Equipment eq = resolveEquipmentByIdOrCode(rewindingEquipment);
        EquipmentOccupation oc = new EquipmentOccupation();
        oc.setScheduleId(scheduleId);
        oc.setProcessType("REWINDING");
        oc.setEquipmentId(eq == null ? null : eq.getId());
        oc.setEquipmentCode(equipmentCode);
        oc.setStartTime(selectedStart);
        oc.setEndTime(selectedEnd);
        oc.setDurationMinutes(durationMinutes);
        oc.setStatus("PLANNED");
        oc.setRemark("复卷排程自动回推机台占用");
        equipmentOccupationMapper.deleteByScheduleAndProcess(scheduleId, "REWINDING");
        equipmentOccupationMapper.insert(oc);

        // 写入统一排程计划表（复卷）
        try {
            SchedulePlan plan = new SchedulePlan();
            plan.setOrderDetailId(schedule.getOrderDetailId());
            plan.setOrderNo(schedule.getOrderNo());
            SalesOrderItem item = salesOrderItemMapper.selectById(schedule.getOrderDetailId());
            if (item != null) {
                plan.setMaterialCode(item.getMaterialCode());
                plan.setMaterialName(item.getMaterialName());
            }
            plan.setStage("REWINDING");
            plan.setPlanDate(selectedStart);
            plan.setEquipment(schedule.getRewindingEquipment());
            if (schedule.getRewindingScheduledArea() != null) {
                plan.setPlanArea(schedule.getRewindingScheduledArea());
            }
            plan.setStatus("CONFIRMED");
            schedulePlanService.upsertPlan(plan);
        } catch (Exception e) {
            System.err.println("写入复卷计划失败: " + e.getMessage());
        }

        try {
            if (schedule.getOrderDetailId() != null) {
                scheduleMapper.updateSalesOrderRewindingDateByDetailId(schedule.getOrderDetailId(), selectedStart.toLocalDate().toString());
            }
        } catch (Exception e) {
            System.err.println("回写复卷日期失败: " + e.getMessage());
        }

        return updated;
    }
    
    @Override
    @Transactional
    public boolean confirmSchedule(String orderNo, String materialCode, Integer scheduleQty, Long scheduleId, Long orderDetailId,
                                   String coatingDate, String rewindingDate, String packagingDate) {
        // 统一口径：以manual_schedule中的scheduleQty为准
        Long targetScheduleId = scheduleId;
        if (targetScheduleId == null && orderDetailId != null) {
            targetScheduleId = scheduleMapper.selectLatestScheduleId(orderDetailId);
        }
        ManualSchedule targetSchedule = targetScheduleId == null ? null : this.getById(targetScheduleId);

        Long targetOrderDetailId = orderDetailId;
        if (targetOrderDetailId == null && targetSchedule != null) {
            targetOrderDetailId = targetSchedule.getOrderDetailId();
        }
        if (targetOrderDetailId != null) {
            ensureOrderDetailSchedulable(targetOrderDetailId);
        }

        Integer effectiveScheduleQty = targetSchedule != null ? targetSchedule.getScheduleQty() : null;
        if (effectiveScheduleQty == null || effectiveScheduleQty <= 0) {
            effectiveScheduleQty = scheduleQty;
        }
        if (effectiveScheduleQty == null || effectiveScheduleQty <= 0) {
            throw new RuntimeException("排程数量无效，请先在排程记录中设置有效数量");
        }

        // 根据订单号+料号查找该订单的order_id
        List<Map<String, Object>> orders = scheduleMapper.selectPendingOrders();
        Long orderId = null;
        
        for (Map<String, Object> order : orders) {
            if (order.get("order_no").equals(orderNo) && order.get("material_code").equals(materialCode)) {
                Object orderIdObj = order.get("order_id");
                if (orderIdObj instanceof Number) {
                    orderId = ((Number) orderIdObj).longValue();
                } else if (orderIdObj instanceof String) {
                    orderId = Long.parseLong((String) orderIdObj);
                }
                break;
            }
        }
        
        if (orderId == null) {
            throw new RuntimeException("未找到对应的订单信息");
        }
        
        // 更新订单明细的已排程数量
        try {
            int result;
            if (targetOrderDetailId != null) {
                result = this.baseMapper.updateScheduledQtyByDetailId(targetOrderDetailId, new BigDecimal(effectiveScheduleQty));
            } else {
                result = this.baseMapper.updateScheduledQty(orderId, materialCode, new BigDecimal(effectiveScheduleQty));
            }
            if (result == 0) {
                throw new RuntimeException("未找到可排程订单明细，可能已取消或剩余数量不足");
            }
        } catch (Exception e) {
            throw new RuntimeException("更新已排程数量失败: " + e.getMessage());
        }

        // 同步排程日期到手动排程记录
        try {
            if (targetSchedule != null) {
                if (coatingDate != null && !coatingDate.isEmpty()) {
                    String datePart = coatingDate.length() > 10 ? coatingDate.substring(0, 10) : coatingDate;
                    targetSchedule.setCoatingDate(java.time.LocalDate.parse(datePart));
                }
                if (rewindingDate != null && !rewindingDate.isEmpty()) {
                    String datePart = rewindingDate.length() > 10 ? rewindingDate.substring(0, 10) : rewindingDate;
                    targetSchedule.setRewindingDate(java.time.LocalDate.parse(datePart));
                }
                if (packagingDate != null && !packagingDate.isEmpty()) {
                    String datePart = packagingDate.length() > 10 ? packagingDate.substring(0, 10) : packagingDate;
                    targetSchedule.setPackagingDate(java.time.LocalDate.parse(datePart));
                }
                this.updateById(targetSchedule);
            }
        } catch (Exception e) {
            // 忽略同步异常，不影响主流程
            System.err.println("同步排程日期失败: " + e.getMessage());
        }

        return true;
    }

    @Override
    @Transactional
    public boolean terminateSchedule(Long scheduleId, String reason, String operator) {
        if (scheduleId == null) {
            throw new RuntimeException("scheduleId 不能为空");
        }
        ManualSchedule schedule = this.getById(scheduleId);
        if (schedule == null) {
            throw new RuntimeException("排程记录不存在");
        }
        if ("CANCELLED".equalsIgnoreCase(schedule.getStatus()) || "TERMINATED".equalsIgnoreCase(schedule.getStatus())) {
            throw new RuntimeException("当前排程已取消/终止");
        }
        if ("COMPLETED".equalsIgnoreCase(schedule.getStatus())) {
            throw new RuntimeException("已完成排程不能终止");
        }

        Integer totalQty = schedule.getScheduleQty() == null ? 0 : schedule.getScheduleQty();
        if (totalQty <= 0) {
            schedule.setStatus("TERMINATED");
            schedule.setRemark(appendReason(schedule.getRemark(), "终止排程", reason, operator));
            return this.updateById(schedule);
        }

        int producedQty = estimateProducedQty(schedule);
        int rollbackQty = Math.max(totalQty - producedQty, 0);
        if (rollbackQty > 0 && schedule.getOrderDetailId() != null) {
            scheduleMapper.rollbackScheduledQtyByDetailId(schedule.getOrderDetailId(), BigDecimal.valueOf(rollbackQty));
            releaseStockAllocationsPartial(schedule.getStockAllocations(), ratio(rollbackQty, totalQty));
        }

        // 保留已开工部分，终止剩余部分
        schedule.setScheduleQty(Math.max(producedQty, 0));
        if (schedule.getCoatingArea() != null && totalQty > 0) {
            BigDecimal newArea = schedule.getCoatingArea()
                    .multiply(BigDecimal.valueOf(schedule.getScheduleQty()))
                    .divide(BigDecimal.valueOf(totalQty), 2, BigDecimal.ROUND_HALF_UP);
            schedule.setCoatingArea(newArea);
        }
        schedule.setStatus("TERMINATED");
        schedule.setRemark(appendReason(schedule.getRemark(), "终止排程", reason, operator));
        return this.updateById(schedule);
    }

    @Override
    @Transactional
    public boolean reduceSchedule(Long scheduleId, Integer reduceQty, String reason, String operator) {
        if (scheduleId == null) {
            throw new RuntimeException("scheduleId 不能为空");
        }
        if (reduceQty == null || reduceQty <= 0) {
            throw new RuntimeException("reduceQty 必须大于0");
        }
        ManualSchedule schedule = this.getById(scheduleId);
        if (schedule == null) {
            throw new RuntimeException("排程记录不存在");
        }
        if ("CANCELLED".equalsIgnoreCase(schedule.getStatus()) || "TERMINATED".equalsIgnoreCase(schedule.getStatus())) {
            throw new RuntimeException("当前排程已取消/终止");
        }
        if ("COMPLETED".equalsIgnoreCase(schedule.getStatus())) {
            throw new RuntimeException("已完成排程不能减量");
        }

        int totalQty = schedule.getScheduleQty() == null ? 0 : schedule.getScheduleQty();
        int producedQty = estimateProducedQty(schedule);
        int canReduceQty = Math.max(totalQty - producedQty, 0);
        if (reduceQty > canReduceQty) {
            throw new RuntimeException("可减量数量不足，最多可减 " + canReduceQty + " 卷");
        }

        if (schedule.getOrderDetailId() != null) {
            scheduleMapper.rollbackScheduledQtyByDetailId(schedule.getOrderDetailId(), BigDecimal.valueOf(reduceQty));
        }
        if (totalQty > 0) {
            releaseStockAllocationsPartial(schedule.getStockAllocations(), ratio(reduceQty, totalQty));
        }

        int newQty = totalQty - reduceQty;
        schedule.setScheduleQty(Math.max(newQty, 0));
        if (schedule.getCoatingArea() != null && totalQty > 0) {
            BigDecimal newArea = schedule.getCoatingArea()
                    .multiply(BigDecimal.valueOf(schedule.getScheduleQty()))
                    .divide(BigDecimal.valueOf(totalQty), 2, BigDecimal.ROUND_HALF_UP);
            schedule.setCoatingArea(newArea);
        }
        if (schedule.getScheduleQty() <= 0) {
            schedule.setStatus("TERMINATED");
        }
        schedule.setRemark(appendReason(schedule.getRemark(), "排程减量" + reduceQty + "卷", reason, operator));
        return this.updateById(schedule);
    }

    private int estimateProducedQty(ManualSchedule schedule) {
        int totalQty = schedule.getScheduleQty() == null ? 0 : schedule.getScheduleQty();
        if (totalQty <= 0) {
            return 0;
        }
        if (schedule.getRewindingScheduledArea() == null || schedule.getCoatingArea() == null || schedule.getCoatingArea().compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        BigDecimal ratio = schedule.getRewindingScheduledArea()
                .divide(schedule.getCoatingArea(), 6, BigDecimal.ROUND_HALF_UP);
        if (ratio.compareTo(BigDecimal.ZERO) < 0) {
            ratio = BigDecimal.ZERO;
        }
        if (ratio.compareTo(BigDecimal.ONE) > 0) {
            ratio = BigDecimal.ONE;
        }
        BigDecimal produced = BigDecimal.valueOf(totalQty).multiply(ratio).setScale(0, BigDecimal.ROUND_DOWN);
        return produced.intValue();
    }

    private BigDecimal ratio(int part, int total) {
        if (part <= 0 || total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(part).divide(BigDecimal.valueOf(total), 6, BigDecimal.ROUND_HALF_UP);
    }

    private String appendReason(String existingRemark, String action, String reason, String operator) {
        StringBuilder msg = new StringBuilder(action);
        if (reason != null && !reason.trim().isEmpty()) {
            msg.append("[").append(reason.trim()).append("]");
        }
        if (operator != null && !operator.trim().isEmpty()) {
            msg.append("(操作人:").append(operator.trim()).append(")");
        }
        if (existingRemark == null || existingRemark.trim().isEmpty()) {
            return msg.toString();
        }
        return existingRemark + "；" + msg;
    }

    private void releaseStockAllocationsPartial(String stockAllocationsJson, BigDecimal releaseRatio) {
        if (stockAllocationsJson == null || stockAllocationsJson.trim().isEmpty()) {
            return;
        }
        if (releaseRatio == null || releaseRatio.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (releaseRatio.compareTo(BigDecimal.ONE) > 0) {
            releaseRatio = BigDecimal.ONE;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, Object>> list = mapper.readValue(stockAllocationsJson, List.class);
            for (Map<String, Object> item : list) {
                if (item == null) {
                    continue;
                }
                Object stockIdObj = item.get("stockId");
                Object areaObj = item.get("area");
                if (stockIdObj == null || areaObj == null) {
                    continue;
                }

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
                if (area.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                BigDecimal releaseArea = area.multiply(releaseRatio).setScale(2, BigDecimal.ROUND_HALF_UP);
                if (releaseArea.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                for (int i = 0; i < 3; i++) {
                    TapeStock current = tapeStockMapper.selectById(stockId);
                    if (current == null) {
                        break;
                    }
                    Integer version = current.getVersion() == null ? 0 : current.getVersion();
                    int ok = tapeStockMapper.releaseLock(stockId, releaseArea, version);
                    if (ok > 0) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("按比例释放库存锁定失败: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getUrgentPreemptConfig() {
        return loadUrgentPreemptConfig();
    }

    @Override
    public Map<String, Object> saveUrgentPreemptConfig(Map<String, Object> config, String operator) {
        Map<String, Object> current = loadUrgentPreemptConfig();

        long window = parseLongConfig(
                config == null ? null : config.get("startProtectWindowMinutes"),
                ((Number) current.get("startProtectWindowMinutes")).longValue()
        );
        if (window <= 0) {
            throw new RuntimeException("startProtectWindowMinutes 必须大于0");
        }

        BigDecimal minArea = parseBigDecimalConfig(
                config == null ? null : config.get("minProtectArea"),
                (BigDecimal) current.get("minProtectArea")
        );
        if (minArea.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("minProtectArea 不能小于0");
        }

        BigDecimal minRatio = parseBigDecimalConfig(
                config == null ? null : config.get("minProtectRatio"),
                (BigDecimal) current.get("minProtectRatio")
        );
        if (minRatio.compareTo(BigDecimal.ZERO) < 0 || minRatio.compareTo(BigDecimal.ONE) > 0) {
            throw new RuntimeException("minProtectRatio 必须在0到1之间");
        }

        Map<String, Object> target = new LinkedHashMap<>();
        target.put("startProtectWindowMinutes", window);
        target.put("minProtectArea", minArea.setScale(2, BigDecimal.ROUND_HALF_UP));
        target.put("minProtectRatio", minRatio.setScale(4, BigDecimal.ROUND_HALF_UP));
        target.put("updatedAt", LocalDateTime.now().toString());
        target.put("updatedBy", (operator == null || operator.trim().isEmpty()) ? "system" : operator.trim());

        try {
            String json = new ObjectMapper().writeValueAsString(target);
            redisCache.setCacheObject(URGENT_PREEMPT_CONFIG_CACHE_KEY, json);
        } catch (Exception e) {
            throw new RuntimeException("保存急单抢占参数失败: " + e.getMessage());
        }
        return loadUrgentPreemptConfig();
    }

    private Map<String, Object> loadUrgentPreemptConfig() {
        long defaultWindow = preemptStartProtectWindowMinutes > 0
                ? preemptStartProtectWindowMinutes
                : DEFAULT_PREEMPT_START_PROTECT_WINDOW_MINUTES;
        BigDecimal defaultArea = preemptMinProtectArea == null
                ? DEFAULT_PREEMPT_MIN_PROTECT_AREA
                : preemptMinProtectArea;
        BigDecimal defaultRatio = preemptMinProtectRatio == null
                ? DEFAULT_PREEMPT_MIN_PROTECT_RATIO
                : preemptMinProtectRatio;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("startProtectWindowMinutes", defaultWindow);
        result.put("minProtectArea", defaultArea.setScale(2, BigDecimal.ROUND_HALF_UP));
        result.put("minProtectRatio", defaultRatio.setScale(4, BigDecimal.ROUND_HALF_UP));
        result.put("source", "application");

        try {
            String json = redisCache.getCacheObject(URGENT_PREEMPT_CONFIG_CACHE_KEY);
            if (json != null && !json.trim().isEmpty()) {
                Map<String, Object> cache = new ObjectMapper().readValue(json, Map.class);
                long window = parseLongConfig(cache.get("startProtectWindowMinutes"), defaultWindow);
                BigDecimal minArea = parseBigDecimalConfig(cache.get("minProtectArea"), defaultArea);
                BigDecimal minRatio = parseBigDecimalConfig(cache.get("minProtectRatio"), defaultRatio);
                if (window > 0) {
                    result.put("startProtectWindowMinutes", window);
                }
                if (minArea.compareTo(BigDecimal.ZERO) >= 0) {
                    result.put("minProtectArea", minArea.setScale(2, BigDecimal.ROUND_HALF_UP));
                }
                if (minRatio.compareTo(BigDecimal.ZERO) >= 0 && minRatio.compareTo(BigDecimal.ONE) <= 0) {
                    result.put("minProtectRatio", minRatio.setScale(4, BigDecimal.ROUND_HALF_UP));
                }
                result.put("source", "redis");
                if (cache.containsKey("updatedAt")) {
                    result.put("updatedAt", cache.get("updatedAt"));
                }
                if (cache.containsKey("updatedBy")) {
                    result.put("updatedBy", cache.get("updatedBy"));
                }
            }
        } catch (Exception ignored) {
            // Redis不可用或JSON损坏时回退到application默认值
        }
        return result;
    }

    private long parseLongConfig(Object raw, long fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number) {
            return ((Number) raw).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(raw).trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private BigDecimal parseBigDecimalConfig(Object raw, BigDecimal fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return new BigDecimal(String.valueOf(raw).trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    @Override
    @Transactional
    public Map<String, Object> allocateUrgentOrderMaterial(String orderNo, String materialCode, BigDecimal requiredArea, String operator) {
        if (orderNo == null || orderNo.trim().isEmpty()) {
            throw new RuntimeException("orderNo 不能为空");
        }
        if (materialCode == null || materialCode.trim().isEmpty()) {
            throw new RuntimeException("materialCode 不能为空");
        }
        if (requiredArea == null || requiredArea.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("requiredArea 必须大于0");
        }

        String normalizedOrderNo = orderNo.trim();
        String normalizedMaterial = materialCode.trim();
        BigDecimal targetArea = requiredArea.setScale(2, BigDecimal.ROUND_HALF_UP);

        ManualSchedule urgentSchedule = findLatestScheduleByOrderNoAndMaterial(normalizedOrderNo, normalizedMaterial);
        if (urgentSchedule == null || urgentSchedule.getId() == null) {
            throw new RuntimeException("未找到急单对应排程，请先创建该订单的手动排程");
        }

        BigDecimal alreadyLocked = sumActiveLockedArea(normalizedOrderNo, normalizedMaterial);
        BigDecimal need = targetArea.subtract(alreadyLocked).setScale(2, BigDecimal.ROUND_HALF_UP);
        if (need.compareTo(BigDecimal.ZERO) <= 0) {
            Map<String, Object> done = new LinkedHashMap<>();
            done.put("orderNo", normalizedOrderNo);
            done.put("materialCode", normalizedMaterial);
            done.put("requiredArea", targetArea);
            done.put("alreadyLockedArea", alreadyLocked);
            done.put("directLockedArea", BigDecimal.ZERO);
            done.put("preemptLockedArea", BigDecimal.ZERO);
            done.put("remainArea", BigDecimal.ZERO);
            done.put("releasedOrders", Collections.emptyList());
            done.put("status", "ALREADY_SATISFIED");
            return done;
        }

        BigDecimal directLocked = lockFromAvailableStockForUrgent(
                urgentSchedule.getId(),
                normalizedOrderNo,
                normalizedMaterial,
                need,
                operator
        );
        need = need.subtract(directLocked).setScale(2, BigDecimal.ROUND_HALF_UP);

        BigDecimal preemptLocked = BigDecimal.ZERO;
        LinkedHashSet<String> releasedOrderSet = new LinkedHashSet<>();

        if (need.compareTo(BigDecimal.ZERO) > 0) {
            preemptLocked = preemptFromLowPriorityLocks(
                    urgentSchedule.getId(),
                    normalizedOrderNo,
                    normalizedMaterial,
                    need,
                    operator,
                    releasedOrderSet
            );
            need = need.subtract(preemptLocked).setScale(2, BigDecimal.ROUND_HALF_UP);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderNo", normalizedOrderNo);
        result.put("materialCode", normalizedMaterial);
        result.put("requiredArea", targetArea);
        result.put("alreadyLockedArea", alreadyLocked);
        result.put("directLockedArea", directLocked);
        result.put("preemptLockedArea", preemptLocked);
        result.put("remainArea", need.max(BigDecimal.ZERO));
        result.put("releasedOrders", new ArrayList<>(releasedOrderSet));
        result.put("status", need.compareTo(BigDecimal.ZERO) <= 0 ? "SUCCESS" : "PARTIAL");
        return result;
    }

    private BigDecimal lockFromAvailableStockForUrgent(Long urgentScheduleId,
                                                       String urgentOrderNo,
                                                       String materialCode,
                                                       BigDecimal needArea,
                                                       String operator) {
        if (needArea == null || needArea.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        List<TapeStock> stocks = tapeStockMapper.selectByMaterialCode(materialCode);
        if (stocks == null || stocks.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal remain = needArea;
        BigDecimal totalLocked = BigDecimal.ZERO;

        for (TapeStock stock : stocks) {
            if (stock == null || stock.getId() == null) {
                continue;
            }
            if (remain.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            for (int i = 0; i < 3 && remain.compareTo(BigDecimal.ZERO) > 0; i++) {
                TapeStock current = tapeStockMapper.selectById(stock.getId());
                if (current == null) {
                    break;
                }
                BigDecimal available = current.getAvailableArea() == null ? BigDecimal.ZERO : current.getAvailableArea();
                if (available.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }

                BigDecimal lockArea = remain.min(available).setScale(2, BigDecimal.ROUND_HALF_UP);
                if (lockArea.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }

                Integer version = current.getVersion() == null ? 0 : current.getVersion();
                int ok = tapeStockMapper.updateReservedAreaWithVersion(current.getId(), lockArea, version);
                if (ok <= 0) {
                    continue;
                }

                String rollCode = stringVal(current.getQrCode());
                if (rollCode == null || rollCode.trim().isEmpty()) {
                    rollCode = stringVal(current.getBatchNo());
                }

                createStandardMaterialLock(
                        urgentScheduleId,
                        urgentOrderNo,
                        materialCode,
                        rollCode,
                        lockArea,
                        "urgent-order-direct-lock;op=" + (operator == null ? "system" : operator),
                        null
                );

                totalLocked = totalLocked.add(lockArea).setScale(2, BigDecimal.ROUND_HALF_UP);
                remain = remain.subtract(lockArea).setScale(2, BigDecimal.ROUND_HALF_UP);
                break;
            }
        }
        return totalLocked;
    }

    private BigDecimal preemptFromLowPriorityLocks(Long urgentScheduleId,
                                                   String urgentOrderNo,
                                                   String materialCode,
                                                   BigDecimal needArea,
                                                   String operator,
                                                   LinkedHashSet<String> releasedOrderSet) {
        if (needArea == null || needArea.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        QueryWrapper<ScheduleMaterialLock> q = new QueryWrapper<>();
        q.eq("material_code", materialCode)
                .eq("lock_status", ScheduleMaterialLock.LockStatus.LOCKED)
                .ne("order_no", urgentOrderNo)
                .orderByAsc("locked_time")
                .orderByAsc("id");
        List<ScheduleMaterialLock> candidates = scheduleMaterialLockMapper.selectList(q);
        if (candidates == null || candidates.isEmpty()) {
            return BigDecimal.ZERO;
        }

        Map<String, Object> cfg = loadUrgentPreemptConfig();
        BigDecimal effectiveMinProtectRatio = parseBigDecimalConfig(cfg.get("minProtectRatio"), DEFAULT_PREEMPT_MIN_PROTECT_RATIO)
            .max(BigDecimal.ZERO).min(BigDecimal.ONE);
        BigDecimal effectiveMinProtectArea = parseBigDecimalConfig(cfg.get("minProtectArea"), DEFAULT_PREEMPT_MIN_PROTECT_AREA)
            .max(BigDecimal.ZERO);

        Map<String, BigDecimal> priorityMap = buildOrderPriorityMap();
        BigDecimal urgentPriority = priorityMap.getOrDefault(urgentOrderNo, BigDecimal.ZERO);

        // 按“订单+料号”预计算可让出额度（总锁定 - 保底量）
        Map<String, BigDecimal> totalLockedByOrderMaterial = new HashMap<>();
        for (ScheduleMaterialLock row : candidates) {
            if (row == null) {
                continue;
            }
            String key = buildOrderMaterialKey(row.getOrderNo(), materialCode);
            BigDecimal area = row.getLockedArea() == null ? BigDecimal.ZERO : row.getLockedArea();
            totalLockedByOrderMaterial.merge(key, area, BigDecimal::add);
        }
        Map<String, BigDecimal> preemptableRemainByOrderMaterial = new HashMap<>();
        for (Map.Entry<String, BigDecimal> e : totalLockedByOrderMaterial.entrySet()) {
            BigDecimal total = e.getValue() == null ? BigDecimal.ZERO : e.getValue().setScale(2, BigDecimal.ROUND_HALF_UP);
            BigDecimal protectByRatio = total.multiply(effectiveMinProtectRatio).setScale(2, BigDecimal.ROUND_HALF_UP);
            BigDecimal protect = protectByRatio.max(effectiveMinProtectArea).min(total);
            BigDecimal preemptable = total.subtract(protect).max(BigDecimal.ZERO).setScale(2, BigDecimal.ROUND_HALF_UP);
            preemptableRemainByOrderMaterial.put(e.getKey(), preemptable);
        }

        candidates.sort((a, b) -> {
            BigDecimal pa = priorityMap.getOrDefault(str(a.getOrderNo()), new BigDecimal("-999999"));
            BigDecimal pb = priorityMap.getOrDefault(str(b.getOrderNo()), new BigDecimal("-999999"));
            int c = pa.compareTo(pb);
            if (c != 0) {
                return c;
            }
            Long ia = a.getId() == null ? Long.MAX_VALUE : a.getId();
            Long ib = b.getId() == null ? Long.MAX_VALUE : b.getId();
            return ia.compareTo(ib);
        });

        BigDecimal remain = needArea;
        BigDecimal moved = BigDecimal.ZERO;

        for (ScheduleMaterialLock victim : candidates) {
            if (victim == null || victim.getId() == null) {
                continue;
            }
            if (remain.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            String victimOrderNo = str(victim.getOrderNo());
            BigDecimal victimPriority = priorityMap.getOrDefault(victimOrderNo, new BigDecimal("-999999"));
            // 只允许抢占低于急单优先级的订单
            if (victimPriority.compareTo(urgentPriority) >= 0) {
                continue;
            }
            // 临近开工保护（避免临近执行订单被抽料）
            if (isVictimNearStartWindow(victim, materialCode, cfg)) {
                continue;
            }

            String victimKey = buildOrderMaterialKey(victimOrderNo, materialCode);
            BigDecimal preemptableRemain = preemptableRemainByOrderMaterial.getOrDefault(victimKey, BigDecimal.ZERO);
            if (preemptableRemain.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal victimArea = victim.getLockedArea() == null ? BigDecimal.ZERO : victim.getLockedArea();
            if (victimArea.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal moveArea = remain.min(victimArea.min(preemptableRemain)).setScale(2, BigDecimal.ROUND_HALF_UP);
            if (moveArea.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal left = victimArea.subtract(moveArea).setScale(2, BigDecimal.ROUND_HALF_UP);

            if (left.compareTo(BigDecimal.ZERO) <= 0) {
                victim.setLockedArea(BigDecimal.ZERO.setScale(2, BigDecimal.ROUND_HALF_UP));
                victim.setLockStatus(ScheduleMaterialLock.LockStatus.RELEASED);
                victim.setReleasedTime(LocalDateTime.now());
            } else {
                victim.setLockedArea(left);
            }
            victim.setRemark(appendReason(victim.getRemark(), "急单让料", "让出面积=" + moveArea, operator));
            scheduleMaterialLockMapper.updateById(victim);

            if (victimOrderNo != null && !victimOrderNo.isEmpty()) {
                releasedOrderSet.add(victimOrderNo);
            }

            createPendingMaterialLock(
                    victim.getScheduleId(),
                    victimOrderNo,
                    materialCode,
                    moveArea,
                    "urgent-preempt-shortage"
            );
            markVictimOrderMaterialUnsatisfied(victim, materialCode, operator, moveArea);

            preemptableRemainByOrderMaterial.put(
                    victimKey,
                    preemptableRemain.subtract(moveArea).max(BigDecimal.ZERO).setScale(2, BigDecimal.ROUND_HALF_UP)
            );

            ScheduleMaterialLock urgentLock = new ScheduleMaterialLock();
            urgentLock.setScheduleId(urgentScheduleId);
            SalesOrder urgentOrder = salesOrderMapper.selectByOrderNo(urgentOrderNo);
            urgentLock.setOrderId(urgentOrder == null ? null : urgentOrder.getId());
            urgentLock.setOrderNo(urgentOrderNo);
            urgentLock.setMaterialCode(materialCode);
            urgentLock.setFilmStockId(victim.getFilmStockId());
            urgentLock.setFilmStockDetailId(victim.getFilmStockDetailId());
            urgentLock.setRollCode(victim.getRollCode());
            urgentLock.setLockedArea(moveArea);
            urgentLock.setRequiredArea(moveArea);
            urgentLock.setLockStatus(ScheduleMaterialLock.LockStatus.LOCKED);
            urgentLock.setLockedTime(LocalDateTime.now());
            urgentLock.setLockedByUserId(1L);
            urgentLock.setVersion(1);
            urgentLock.setRemark("source=urgent-preempt-lock;fromLockId=" + victim.getId()
                    + ";fromOrderNo=" + (victimOrderNo == null ? "" : victimOrderNo)
                    + ";op=" + (operator == null ? "system" : operator)
                    + ";ts=" + LocalDateTime.now());
            scheduleMaterialLockMapper.insert(urgentLock);

            consumePendingLocks(urgentOrderNo, materialCode, moveArea, null);

            moved = moved.add(moveArea).setScale(2, BigDecimal.ROUND_HALF_UP);
            remain = remain.subtract(moveArea).setScale(2, BigDecimal.ROUND_HALF_UP);
        }

        return moved;
    }

    private String buildOrderMaterialKey(String orderNo, String materialCode) {
        return (orderNo == null ? "" : orderNo.trim()) + "#" + (materialCode == null ? "" : materialCode.trim());
    }

    private boolean isVictimNearStartWindow(ScheduleMaterialLock victim, String materialCode, Map<String, Object> cfg) {
        if (victim == null) {
            return false;
        }
        ManualSchedule schedule = null;
        if (victim.getScheduleId() != null) {
            schedule = this.getById(victim.getScheduleId());
        }
        if (schedule == null) {
            schedule = findLatestScheduleByOrderNoAndMaterial(victim.getOrderNo(), materialCode);
        }
        if (schedule == null || schedule.getId() == null) {
            return false;
        }

        LocalDateTime nearestStart = resolveNearestPlannedStart(schedule);
        if (nearestStart == null) {
            return false;
        }
        long window = parseLongConfig(
                cfg == null ? null : cfg.get("startProtectWindowMinutes"),
                preemptStartProtectWindowMinutes > 0 ? preemptStartProtectWindowMinutes : DEFAULT_PREEMPT_START_PROTECT_WINDOW_MINUTES
        );
        if (window <= 0) {
            window = DEFAULT_PREEMPT_START_PROTECT_WINDOW_MINUTES;
        }
        LocalDateTime protectDeadline = LocalDateTime.now().plusMinutes(window);
        return !nearestStart.isAfter(protectDeadline);
    }

    private LocalDateTime resolveNearestPlannedStart(ManualSchedule schedule) {
        if (schedule == null || schedule.getId() == null) {
            return null;
        }

        LocalDateTime earliest = null;
        List<String> processes = Arrays.asList("COATING", "REWINDING", "SLITTING");
        for (String process : processes) {
            LocalDateTime dt = equipmentOccupationMapper.selectStartTimeByScheduleAndProcess(schedule.getId(), process);
            if (dt != null && (earliest == null || dt.isBefore(earliest))) {
                earliest = dt;
            }
        }

        if (earliest != null) {
            return earliest.truncatedTo(ChronoUnit.MINUTES);
        }

        List<LocalDate> dateCandidates = Arrays.asList(
                schedule.getCoatingDate(),
                schedule.getRewindingDate(),
                schedule.getPackagingDate()
        );
        for (LocalDate d : dateCandidates) {
            if (d != null) {
                LocalDateTime dt = d.atStartOfDay();
                if (earliest == null || dt.isBefore(earliest)) {
                    earliest = dt;
                }
            }
        }
        return earliest;
    }

    private void markVictimOrderMaterialUnsatisfied(ScheduleMaterialLock victim,
                                                    String materialCode,
                                                    String operator,
                                                    BigDecimal movedArea) {
        if (victim == null) {
            return;
        }

        ManualSchedule schedule = null;
        if (victim.getScheduleId() != null) {
            schedule = this.getById(victim.getScheduleId());
        }
        if (schedule == null) {
            schedule = findLatestScheduleByOrderNoAndMaterial(victim.getOrderNo(), materialCode);
        }
        if (schedule == null || schedule.getId() == null) {
            return;
        }

        String oldStatus = schedule.getStatus() == null ? "" : schedule.getStatus().trim().toUpperCase();
        if ("COMPLETED".equals(oldStatus) || "DONE".equals(oldStatus) || "CANCELLED".equals(oldStatus) || "TERMINATED".equals(oldStatus)) {
            return;
        }

        schedule.setStatus("MATERIAL_UNSATISFIED");
        String reason = "急单抢占让料 " + (movedArea == null ? "0" : movedArea.toPlainString()) + "㎡";
        schedule.setRemark(appendReason(schedule.getRemark(), "物料未满足", reason, operator));
        this.updateById(schedule);
    }

    private BigDecimal sumActiveLockedArea(String orderNo, String materialCode) {
        QueryWrapper<ScheduleMaterialLock> q = new QueryWrapper<>();
        q.eq("order_no", orderNo)
                .eq("material_code", materialCode)
                .in("lock_status", Arrays.asList(
                        ScheduleMaterialLock.LockStatus.LOCKED,
                        ScheduleMaterialLock.LockStatus.ALLOCATED,
                        ScheduleMaterialLock.LockStatus.CONSUMED
                ));
        List<ScheduleMaterialLock> list = scheduleMaterialLockMapper.selectList(q);
        BigDecimal sum = BigDecimal.ZERO;
        if (list != null) {
            for (ScheduleMaterialLock row : list) {
                if (row != null && row.getLockedArea() != null) {
                    sum = sum.add(row.getLockedArea());
                }
            }
        }
        return sum.setScale(2, BigDecimal.ROUND_HALF_UP);
    }

    private ManualSchedule findLatestScheduleByOrderNoAndMaterial(String orderNo, String materialCode) {
        if (orderNo == null || orderNo.trim().isEmpty()) {
            return null;
        }
        QueryWrapper<ManualSchedule> q = new QueryWrapper<>();
        q.eq("order_no", orderNo.trim())
                .orderByDesc("id")
                .last("LIMIT 50");
        List<ManualSchedule> schedules = this.list(q);
        if (schedules == null || schedules.isEmpty()) {
            return null;
        }
        if (materialCode == null || materialCode.trim().isEmpty()) {
            return schedules.get(0);
        }
        String m = materialCode.trim();
        for (ManualSchedule schedule : schedules) {
            if (schedule == null || schedule.getOrderDetailId() == null) {
                continue;
            }
            SalesOrderItem item = salesOrderItemMapper.selectById(schedule.getOrderDetailId());
            if (item != null && item.getMaterialCode() != null && m.equalsIgnoreCase(item.getMaterialCode().trim())) {
                return schedule;
            }
        }
        return schedules.get(0);
    }

    private Map<String, BigDecimal> buildOrderPriorityMap() {
        Map<String, BigDecimal> map = new HashMap<>();
        List<Map<String, Object>> rows = getPendingOrders(true);
        if (rows == null) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            String o = str(row.get("order_no"));
            if (o == null || o.isEmpty()) {
                continue;
            }
            BigDecimal p = toBigDecimal(row.get("priority_score"));
            map.put(o, p == null ? BigDecimal.ZERO : p);
        }
        return map;
    }

    private String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    @Override
    @Transactional
    public boolean resetScheduleByOrderDetailId(Long orderDetailId, String reason, String operator) {
        if (orderDetailId == null) {
            throw new RuntimeException("orderDetailId 不能为空");
        }

        List<ManualSchedule> schedules = this.list(new LambdaQueryWrapper<ManualSchedule>()
                .eq(ManualSchedule::getOrderDetailId, orderDetailId));

        for (ManualSchedule schedule : schedules) {
            if (schedule == null || schedule.getId() == null) {
                continue;
            }
            releaseStockAllocationsPartial(schedule.getStockAllocations(), BigDecimal.ONE);
            try {
                manualScheduleCoatingAllocationMapper.deleteByScheduleId(schedule.getId());
            } catch (Exception ignored) {
            }
            try {
                scheduleMapper.deleteProcessReportsByScheduleId(schedule.getId());
            } catch (Exception ignored) {
            }
            try {
                equipmentOccupationMapper.delete(new LambdaQueryWrapper<EquipmentOccupation>()
                        .eq(EquipmentOccupation::getScheduleId, schedule.getId()));
            } catch (Exception ignored) {
            }
        }

        schedulePlanService.remove(new LambdaQueryWrapper<SchedulePlan>()
                .eq(SchedulePlan::getOrderDetailId, orderDetailId));

        this.remove(new LambdaQueryWrapper<ManualSchedule>()
                .eq(ManualSchedule::getOrderDetailId, orderDetailId));

        int affected = scheduleMapper.resetOrderDetailScheduleFields(orderDetailId);
        if (affected <= 0) {
            throw new RuntimeException("订单明细不存在，无法重置");
        }

        if (reason != null && !reason.trim().isEmpty()) {
            System.out.println("[ResetSchedule] orderDetailId=" + orderDetailId + ", reason=" + reason + ", operator=" + operator);
        }
        return true;
    }
}
