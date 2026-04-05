package com.fine.controller.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fine.Utils.ResponseResult;
import com.fine.model.production.OrderMaterialLock;
import com.fine.model.production.OrderPreprocessing;
import com.fine.service.production.OrderPreprocessingService;
import com.fine.service.production.MaterialReadinessService;
import com.fine.model.production.readiness.ReadinessStatus;
import com.fine.service.production.AvailableMaterialDTO;
import com.fine.Dao.SalesOrderItemMapper;
import com.fine.modle.SalesOrderItem;
import com.fine.mapper.production.PreprocessingMaterialLockMapper;
import com.fine.Dao.stock.TapeStockMapper;
import com.fine.Dao.stock.TapeRollMapper;
import com.fine.modle.stock.TapeStock;
import com.fine.modle.stock.TapeRoll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单预处理Controller（整合列表/锁定/提交等接口）
 */
@PreAuthorize("permitAll()")
@RestController
@RequestMapping("/api/production/preprocessing")
@CrossOrigin
public class OrderPreprocessingController {

    @Autowired
    private OrderPreprocessingService orderPreprocessingService;

    @Autowired
    private MaterialReadinessService materialReadinessService;

    @Value("${mes.readiness.allow-ready-by-eta:false}")
    private boolean allowReadyByEta;

    @Autowired
    private PreprocessingMaterialLockMapper orderMaterialLockMapper;

    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;

    @Autowired
    private TapeStockMapper tapeStockMapper;

    @Autowired
    private TapeRollMapper tapeRollMapper;

    /**
     * 分页查询未排程订单明细（直接来源于 sales_order_items），不返回价格字段
     */
    @GetMapping("/list")
    public ResponseResult<Map<String, Object>> list(
            @RequestParam(value = "current", required = false, defaultValue = "1") Integer current,
            @RequestParam(value = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String materialCode,
            @RequestParam(value = "showDispatched", required = false, defaultValue = "false") Boolean showDispatched
    ) {
        Page<SalesOrderItem> page = new Page<>(current, size);
        IPage<SalesOrderItem> items = salesOrderItemMapper.selectPendingItems(page, orderNo, materialCode);

        // 预先批量查询已存在的预处理记录，避免逐条查询
        java.util.Map<Long, OrderPreprocessing> preprocessingMap = new java.util.HashMap<>();
        if (items.getRecords() != null && !items.getRecords().isEmpty()) {
            java.util.List<Long> orderItemIds = new java.util.ArrayList<>();
            for (SalesOrderItem soi : items.getRecords()) {
                if (soi.getId() != null) {
                    orderItemIds.add(soi.getId());
                }
            }
            if (!orderItemIds.isEmpty()) {
                List<OrderPreprocessing> preprocessings = orderPreprocessingService.list(new QueryWrapper<OrderPreprocessing>().in("order_item_id", orderItemIds));
                for (OrderPreprocessing p : preprocessings) {
                    preprocessingMap.put(p.getOrderItemId(), p);
                }
            }
        }

        List<Map<String, Object>> records = new ArrayList<>();
        if (items.getRecords() != null) {
            for (SalesOrderItem soi : items.getRecords()) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", soi.getId());
                m.put("orderItemId", soi.getId());
                m.put("orderId", soi.getOrderId());
                m.put("orderNo", soi.getOrderNo());
                m.put("orderItemCode", String.valueOf(soi.getId()));
                m.put("materialCode", soi.getMaterialCode());
                m.put("materialName", soi.getMaterialName());
                m.put("colorCode", soi.getColorCode());

                String specDesc = buildSpecDesc(soi);
                m.put("specDesc", specDesc.isEmpty() ? "-" : specDesc);

                int rolls = soi.getRolls() != null ? soi.getRolls() : 0;
                int scheduledQty = soi.getScheduledQty() != null ? soi.getScheduledQty() : 0;
                int pendingQty = Math.max(rolls - scheduledQty, 0);
                m.put("rolls", rolls);
                m.put("pending_qty", pendingQty);
                java.math.BigDecimal required = soi.getPendingArea() != null ? soi.getPendingArea() : java.math.BigDecimal.ZERO;
                m.put("requiredQty", required);
                OrderPreprocessing p = preprocessingMap.get(soi.getId());
                if (p != null) {
                    m.put("preprocessingId", p.getId());
                    // 动态汇总锁定面积，避免锁定成功但列表仍显示未锁定
                    java.math.BigDecimal lockedQty = orderMaterialLockMapper.sumLockedAreaByOrderItemId(soi.getId());
                    if (lockedQty == null) lockedQty = p.getLockedQty() != null ? p.getLockedQty() : java.math.BigDecimal.ZERO;
                    // 需求量优先使用订单剩余需求，其次使用预处理记录
                    java.math.BigDecimal req = required != null ? required : (p.getRequiredQty() != null ? p.getRequiredQty() : java.math.BigDecimal.ZERO);
                    String lockStatus = OrderPreprocessing.LockStatusEnum.UNLOCKED;
                    if (req.compareTo(java.math.BigDecimal.ZERO) > 0 && lockedQty.compareTo(req) >= 0) {
                        lockStatus = OrderPreprocessing.LockStatusEnum.LOCKED;
                    } else if (lockedQty.compareTo(java.math.BigDecimal.ZERO) > 0) {
                        lockStatus = OrderPreprocessing.LockStatusEnum.PARTIAL;
                    }
                    m.put("lockedQty", lockedQty);
                    m.put("status", p.getStatus());
                    m.put("lockStatus", lockStatus);
                } else {
                    m.put("preprocessingId", null);
                    m.put("lockedQty", java.math.BigDecimal.ZERO);
                    m.put("status", "new");
                    m.put("lockStatus", OrderPreprocessing.LockStatusEnum.UNLOCKED);
                }

                // 默认隐藏已提交(待排池)的明细，避免前端分页显示“有总数但无数据”的困惑
                if (Boolean.FALSE.equals(showDispatched)) {
                    Object statusVal = m.get("status");
                    if (statusVal != null && "dispatched".equals(String.valueOf(statusVal))) {
                        continue;
                    }
                }

                m.put("scheduleStage", "COATING");
                m.put("createdAt", soi.getCreatedAt());
                m.put("updatedAt", soi.getUpdatedAt());
                records.add(m);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        // 过滤掉已提交后，按照过滤结果返回总数，避免前端分页与数据条数不符
        result.put("total", records.size());
        result.put("current", items.getCurrent());
        result.put("size", items.getSize());
        return ResponseResult.success(result);
    }

    /**
     * 同步创建预处理记录：从未完成的销售订单明细（按面积口径）生成预处理条目
     */
    @PostMapping("/bootstrap")
    public ResponseResult<java.util.Map<String, Object>> bootstrap(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String materialCode
    ) {
        Page<SalesOrderItem> page = new Page<>(1, 1000);
        IPage<SalesOrderItem> pending = salesOrderItemMapper.selectPendingItems(page, orderNo, materialCode);

        int created = 0;
        for (SalesOrderItem item : pending.getRecords()) {
            OrderPreprocessing existing = orderPreprocessingService.getByOrderItemId(item.getId());
            if (existing != null) continue;

            java.math.BigDecimal required = item.getPendingArea() != null ? item.getPendingArea() : item.getSqm();
            String spec = buildSpecDesc(item);
            try {
                orderPreprocessingService.createPreprocessing(
                        item.getOrderId(),
                        item.getId(),
                        item.getOrderNo(),
                        String.valueOf(item.getId()),
                        item.getMaterialCode(),
                        item.getMaterialName(),
                        spec,
                        required
                );
                created++;
            } catch (org.springframework.dao.DuplicateKeyException ignore) { }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("created", created);
        data.put("filteredOrderNo", orderNo);
        data.put("filteredMaterialCode", materialCode);
        return new ResponseResult<>(20000, "同步完成", data);
    }

    private String buildSpecDesc(SalesOrderItem item) {
        String thickness = item.getThickness() != null ? item.getThickness().stripTrailingZeros().toPlainString() : "";
        String width = item.getWidth() != null ? item.getWidth().stripTrailingZeros().toPlainString() : "";
        String length = item.getLength() != null ? item.getLength().stripTrailingZeros().toPlainString() : "";

        java.util.List<String> parts = new java.util.ArrayList<>();
        if (!thickness.isEmpty()) parts.add(thickness);
        if (!width.isEmpty()) parts.add(width);
        if (!length.isEmpty()) parts.add(length);
        return parts.isEmpty() ? "" : String.join(" * ", parts);
    }

    /** 可用物料查询(FIFO) */
    @GetMapping("/available-materials")
    public ResponseResult<List<AvailableMaterialDTO>> getAvailableMaterials(
            @RequestParam String materialCode,
            @RequestParam(required = false, defaultValue = "50") Integer limit,
            @RequestParam(required = false) Long orderItemId,
            @RequestParam(required = false) Integer requiredRolls,
            @RequestParam(required = false) java.math.BigDecimal requiredArea
    ) {
        List<AvailableMaterialDTO> materials = orderPreprocessingService.getAvailableMaterials(materialCode, limit, orderItemId, requiredRolls, requiredArea);
        return new ResponseResult<>(20000, "查询成功", materials);
    }

    /** 锁定物料 */
    @PostMapping("/lock-materials")
    public ResponseResult<Map<String, Object>> lockMaterials(@RequestBody LockMaterialsRequest request) {
        try {
            List<OrderMaterialLock> locks = request.convertToLocks();
            if (request.getOrderItemId() == null) {
                return new ResponseResult<>(40001, "缺少订单明细ID", null);
            }
            orderPreprocessingService.lockMaterials(request.getOrderItemId(), request.getOrderId(), locks);
            OrderPreprocessing preprocessing = orderPreprocessingService.getByOrderItemId(request.getOrderItemId());
            // 重新计算已锁定面积，确保前端显示最新数值
            BigDecimal lockedArea = orderPreprocessingService.getLockedArea(preprocessing.getOrderItemId());
            preprocessing.setLockedQty(lockedArea);
            // 同步锁定状态（避免锁定成功但状态仍为未锁定）
            BigDecimal required = preprocessing.getRequiredQty() != null ? preprocessing.getRequiredQty() : BigDecimal.ZERO;
            String lockStatus = OrderPreprocessing.LockStatusEnum.UNLOCKED;
            if (required.compareTo(BigDecimal.ZERO) > 0 && lockedArea.compareTo(required) >= 0) {
                lockStatus = OrderPreprocessing.LockStatusEnum.LOCKED;
            } else if (lockedArea.compareTo(BigDecimal.ZERO) > 0) {
                lockStatus = OrderPreprocessing.LockStatusEnum.PARTIAL;
            }
            preprocessing.setLockStatus(lockStatus);
            Map<String, Object> data = new HashMap<>();
            data.put("lockedQty", preprocessing.getLockedQty());
            data.put("lockStatus", preprocessing.getLockStatus());
            data.put("scheduleType", preprocessing.getScheduleType());
            data.put("preprocessingId", preprocessing.getId());
            data.put("orderItemId", preprocessing.getOrderItemId());
            return new ResponseResult<>(20000, "锁定成功", data);
        } catch (Exception e) {
            return new ResponseResult<>(40001, "锁定失败: " + e.getMessage(), null);
        }
    }

    /** 解除锁定 */
    @PostMapping("/unlock-materials")
    public ResponseResult<Map<String, Object>> unlockMaterials(@RequestBody UnlockRequest request) {
        try {
            if (request.getOrderItemId() == null) {
                return new ResponseResult<>(40001, "缺少订单明细ID", null);
            }

            orderPreprocessingService.releaseLocks(request.getOrderItemId());
            OrderPreprocessing preprocessing = orderPreprocessingService.getByOrderItemId(request.getOrderItemId());
            Map<String, Object> data = new HashMap<>();
            data.put("lockedQty", preprocessing.getLockedQty());
            data.put("lockStatus", preprocessing.getLockStatus());
            data.put("scheduleType", preprocessing.getScheduleType());
            data.put("preprocessingId", preprocessing.getId());
            data.put("orderItemId", preprocessing.getOrderItemId());
            return new ResponseResult<>(20000, "解锁成功", data);
        } catch (Exception e) {
            return new ResponseResult<>(40001, "解锁失败: " + e.getMessage(), null);
        }
    }

    /** 提交预处理订单 */
    @PostMapping("/submit")
    public ResponseResult<Map<String, Object>> submitPreprocessing(@RequestBody SubmitRequest request) {
        try {
            if (request.getOrderItemId() == null) {
                return new ResponseResult<>(40001, "缺少订单明细ID", null);
            }

            // 齐套门禁：缺料状态不允许进入生产排程
            Map<String, Object> readiness = materialReadinessService.getOrderItemReadiness(request.getOrderItemId());
            String readinessCode = readiness == null ? null : String.valueOf(readiness.get("statusCode"));
            if (ReadinessStatus.SHORTAGE.equals(readinessCode)) {
                return new ResponseResult<>(40001, "当前订单明细存在原料缺口，请先完成采购/到料后再提交排程", readiness);
            }
            if (ReadinessStatus.READY_BY_ETA.equals(readinessCode) && !allowReadyByEta) {
                return new ResponseResult<>(40001, "当前订单明细为预计齐套，尚未达到放行策略。请确认到料或开启READY_BY_ETA放行", readiness);
            }

            OrderPreprocessing preprocessing = orderPreprocessingService.getByOrderItemId(request.getOrderItemId());
            if (preprocessing == null) {
                return new ResponseResult<>(40004, "预处理记录不存在", null);
            }
            if (request.getRequiredQty() != null) preprocessing.setRequiredQty(request.getRequiredQty());
            if (request.getRemark() != null) preprocessing.setRemark(request.getRemark());

            // 提交前刷新已锁定面积，避免脏数据导致缺口=0
            BigDecimal lockedFromDb = orderMaterialLockMapper.sumLockedAreaByOrderItemId(request.getOrderItemId());
            if (lockedFromDb == null) {
                lockedFromDb = BigDecimal.ZERO;
            }
            BigDecimal requiredQty = preprocessing.getRequiredQty() != null ? preprocessing.getRequiredQty() : BigDecimal.ZERO;
            if (lockedFromDb.compareTo(requiredQty) > 0) {
                lockedFromDb = requiredQty; // 不超过需求量
            }
            preprocessing.setLockedQty(lockedFromDb);
            String lockStatus = OrderPreprocessing.LockStatusEnum.UNLOCKED;
            if (lockedFromDb.compareTo(requiredQty) >= 0) {
                lockStatus = OrderPreprocessing.LockStatusEnum.LOCKED;
            } else if (lockedFromDb.compareTo(BigDecimal.ZERO) > 0) {
                lockStatus = OrderPreprocessing.LockStatusEnum.PARTIAL;
            }
            preprocessing.setLockStatus(lockStatus);

            BigDecimal lockedQty = lockedFromDb;
            if (lockedQty.compareTo(requiredQty) < 0) {
                preprocessing.setScheduleType(OrderPreprocessing.ScheduleTypeEnum.COATING);
                preprocessing.setTargetPool("coat>rewind>slit");
            } else if (request.getScheduleSelections() != null && !request.getScheduleSelections().isEmpty()) {
                preprocessing.setScheduleType(pickScheduleType(request.getScheduleSelections()));
                if (request.getScheduleSelections().contains(OrderPreprocessing.ScheduleTypeEnum.REWINDING)
                        && request.getScheduleSelections().contains(OrderPreprocessing.ScheduleTypeEnum.SLITTING)) {
                    preprocessing.setTargetPool("rewind>slit");
                } else {
                    preprocessing.setTargetPool(null);
                }
            } else if (request.getScheduleType() != null) {
                preprocessing.setScheduleType(request.getScheduleType());
                preprocessing.setTargetPool(null);
            } else {
                // 默认锁足后直接进入复卷/分切链路
                preprocessing.setScheduleType(OrderPreprocessing.ScheduleTypeEnum.REWINDING);
                preprocessing.setTargetPool("rewind>slit");
            }

            orderPreprocessingService.updateById(preprocessing);
            orderPreprocessingService.submitPreprocessing(request.getOrderItemId());
            OrderPreprocessing updated = orderPreprocessingService.getByOrderItemId(request.getOrderItemId());

            Map<String, Object> data = new HashMap<>();
            data.put("targetPool", updated.getTargetPool());
            data.put("status", updated.getStatus());
            data.put("dispatchedAt", updated.getUpdatedAt());
            data.put("preprocessingId", updated.getId());
            data.put("orderItemId", updated.getOrderItemId());
            return new ResponseResult<>(20000, "已提交至待排池", data);
        } catch (Exception e) {
            return new ResponseResult<>(40001, "提交失败: " + e.getMessage(), null);
        }
    }

    /** 取消订单明细：释放锁定并清理待排池 */
    @PostMapping("/cancel-order-item")
    public ResponseResult<Void> cancelOrderItem(@RequestBody CancelRequest request) {
        try {
            if (request.getOrderItemId() == null) {
                return new ResponseResult<>(40001, "缺少订单明细ID", null);
            }
            orderPreprocessingService.cancelOrderItem(request.getOrderItemId());
            return new ResponseResult<>(20000, "取消成功，已释放锁定并清理排程池", null);
        } catch (Exception e) {
            return new ResponseResult<>(40001, "取消失败: " + e.getMessage(), null);
        }
    }

    /** 详情 */
    @GetMapping("/{id}")
    public ResponseResult<OrderPreprocessing> detail(@PathVariable Long id) {
        OrderPreprocessing preprocessing = orderPreprocessingService.getById(id);
        if (preprocessing == null) return new ResponseResult<>(40004, "记录不存在", null);
        return new ResponseResult<>(20000, "查询成功", preprocessing);
    }

    /** 锁定记录 */
    @GetMapping("/locks/{orderItemId}")
    public ResponseResult<List<OrderMaterialLock>> getLocks(@PathVariable Long orderItemId) {
        OrderPreprocessing preprocessing = orderPreprocessingService.getByOrderItemId(orderItemId);
        Long preprocessingId = preprocessing != null ? preprocessing.getId() : null;

        List<OrderMaterialLock> rawLocks = preprocessingId != null
                ? orderMaterialLockMapper.selectByPreprocessingId(preprocessingId)
                : Collections.emptyList();
        if (rawLocks == null || rawLocks.isEmpty()) {
            rawLocks = orderMaterialLockMapper.selectByOrderItemId(orderItemId);
        }
        if (rawLocks == null) {
            rawLocks = Collections.emptyList();
        }

        // 仅返回已锁定的记录，并补充库存详情字段，保证与库存详表一致
        List<OrderMaterialLock> enriched = new ArrayList<>();
        for (OrderMaterialLock lock : rawLocks) {
            if (lock == null) continue;
            if (!"locked".equalsIgnoreCase(lock.getLockStatus())) {
                continue; // 只显示锁定中的库存
            }
            try {
                String table = lock.getStockTableName();
                Long stockRecordId = lock.getTapeStockId();
                if (table != null && table.equalsIgnoreCase("tape_stock_rolls")) {
                    TapeRoll roll = tapeRollMapper.selectWithStock(stockRecordId);
                    if (roll != null) {
                        // 批次、规格、二维码等展示字段
                        lock.setBatchNo(roll.getBatchNo());
                        lock.setMaterialSpec(roll.getSpecDesc());
                        if (lock.getQrCode() == null || lock.getQrCode().isEmpty()) {
                            lock.setQrCode(roll.getQrCode());
                        }
                        if (lock.getMaterialCode() == null || lock.getMaterialCode().isEmpty()) {
                            lock.setMaterialCode(roll.getMaterialCode());
                        }
                    }
                } else { // 默认按照批次库存表补充
                    TapeStock stock = tapeStockMapper.selectById(stockRecordId);
                    if (stock != null) {
                        lock.setBatchNo(stock.getBatchNo());
                        lock.setMaterialSpec(stock.getSpecDesc());
                        if (lock.getQrCode() == null || lock.getQrCode().isEmpty()) {
                            lock.setQrCode(stock.getQrCode());
                        }
                        if (lock.getMaterialCode() == null || lock.getMaterialCode().isEmpty()) {
                            lock.setMaterialCode(stock.getMaterialCode());
                        }
                    }
                }
            } catch (Exception ignore) {
                // 展示字段补充失败不影响主流程
            }
            enriched.add(lock);
        }

        return new ResponseResult<>(20000, "查询成功", enriched);
    }

    // 请求模型
    @lombok.Data
    public static class LockMaterialsRequest {
        private Long orderId;
        private Long orderItemId;
        private String materialCode;
        private List<LockItem> locks;

        @lombok.Data
        public static class LockItem {
            private Long tapeStockId;
            private BigDecimal lockQty;
            private BigDecimal lockArea;
            private Integer fifoOrder;
            private String stockTableName;
        }

        public List<OrderMaterialLock> convertToLocks() {
            List<OrderMaterialLock> result = new ArrayList<>();
            if (locks == null) return result;
            for (LockItem item : locks) {
                OrderMaterialLock lock = new OrderMaterialLock();
                lock.setTapeStockId(item.getTapeStockId());
                lock.setLockQty(item.getLockQty());
                lock.setLockArea(item.getLockArea());
                lock.setFifoOrder(item.getFifoOrder());
                lock.setStockTableName(item.getStockTableName());
                result.add(lock);
            }
            return result;
        }
    }

    @lombok.Data
    public static class UnlockRequest {
        private Long orderItemId;
    }

    @lombok.Data
    public static class SubmitRequest {
        private Long orderItemId;
        private BigDecimal requiredQty;
        private String scheduleType;
        private String remark;
        private List<String> scheduleSelections;
    }

    @lombok.Data
    public static class CancelRequest {
        private Long orderItemId;
    }

    private String pickScheduleType(List<String> selections) {
        if (selections == null || selections.isEmpty()) return OrderPreprocessing.ScheduleTypeEnum.COATING;
        if (selections.contains(OrderPreprocessing.ScheduleTypeEnum.COATING)) return OrderPreprocessing.ScheduleTypeEnum.COATING;
        if (selections.contains(OrderPreprocessing.ScheduleTypeEnum.REWINDING)) return OrderPreprocessing.ScheduleTypeEnum.REWINDING;
        if (selections.contains(OrderPreprocessing.ScheduleTypeEnum.SLITTING)) return OrderPreprocessing.ScheduleTypeEnum.SLITTING;
        return OrderPreprocessing.ScheduleTypeEnum.COATING;
    }
}
