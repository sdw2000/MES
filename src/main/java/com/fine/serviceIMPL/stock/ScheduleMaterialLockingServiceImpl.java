package com.fine.serviceIMPL.stock;

import com.fine.service.stock.ScheduleMaterialLockingService;
import com.fine.Dao.stock.TapeStockMapper;
import com.fine.Dao.stock.ScheduleMaterialLockMapper;
import com.fine.Dao.stock.ScheduleMaterialAllocationMapper;
import com.fine.Dao.stock.ScheduleProcurementPlanMapper;
import com.fine.Dao.rd.TapeFormulaMapper;
import com.fine.Dao.purchase.PurchaseOrderMapper;
import com.fine.Dao.purchase.PurchaseOrderItemMapper;
import com.fine.Dao.production.BatchScheduleMapper;
import com.fine.Dao.production.SalesOrderMapper;
import com.fine.modle.rd.TapeFormula;
import com.fine.modle.rd.TapeFormulaItem;
import com.fine.modle.stock.TapeStock;
import com.fine.modle.stock.ScheduleMaterialLock;
import com.fine.modle.stock.ScheduleMaterialAllocation;
import com.fine.modle.stock.ScheduleProcurementPlan;
import com.fine.modle.production.BatchSchedule;
import com.fine.modle.SalesOrder;
import com.fine.modle.PurchaseOrder;
import com.fine.modle.PurchaseOrderItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.math.RoundingMode;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.StringJoiner;

/**
 * 排程物料锁定服务实现
 */
@Slf4j
@Service
public class ScheduleMaterialLockingServiceImpl implements ScheduleMaterialLockingService {
    
    @Autowired
    private TapeStockMapper tapeStockMapper;
    
    @Autowired
    private ScheduleMaterialLockMapper scheduleMaterialLockMapper;
    
    @Autowired
    private ScheduleMaterialAllocationMapper scheduleMaterialAllocationMapper;
    
    @Autowired
    private BatchScheduleMapper batchScheduleMapper;
    
    @Autowired
    private SalesOrderMapper salesOrderMapper;

    @Autowired
    private ScheduleProcurementPlanMapper scheduleProcurementPlanMapper;

    @Autowired
    private PurchaseOrderMapper purchaseOrderMapper;

    @Autowired
    private PurchaseOrderItemMapper purchaseOrderItemMapper;

    @Autowired
    private TapeFormulaMapper tapeFormulaMapper;
    
    /** 一次查询的最大物料卷数 */
    private static final int QUERY_LIMIT = 10;
    
    /**
     * 排程启动时的物料锁定主流程
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LockResult lockMaterialsForSchedule(Long scheduleId) throws MaterialLockException {
        log.info("开始物料锁定流程，排程ID: {}", scheduleId);
        
        LockResult result = new LockResult();
        result.totalRequiredArea = BigDecimal.ZERO;
        result.totalAllocatedArea = BigDecimal.ZERO;
        result.needCoatingOrderIds = new ArrayList<>();
        
        try {
            // 查询排程信息
            BatchSchedule schedule = batchScheduleMapper.selectById(scheduleId);
            if (schedule == null) {
                throw new MaterialLockException("排程不存在，ID: " + scheduleId);
            }
            
            // 查询排程的订单
            List<SalesOrder> orders = salesOrderMapper.selectByBatchScheduleId(scheduleId);
            if (orders == null || orders.isEmpty()) {
                log.warn("排程没有订单，排程ID: {}", scheduleId);
                result.success = true;
                result.totalOrders = 0;
                return result;
            }
            
            result.totalOrders = orders.size();
              // 遍历每个订单进行物料锁定
            for (SalesOrder order : orders) {
                log.info("处理订单 ID: {}, 需求面积: {}", order.getId(), 
                    order.getRequiredArea() != null ? order.getRequiredArea() : order.getTotalArea());
                
                BigDecimal requiredArea = order.getRequiredArea() != null ? 
                    order.getRequiredArea() : 
                    (order.getTotalArea() != null ? order.getTotalArea() : BigDecimal.ZERO);
                BigDecimal allocatedArea = BigDecimal.ZERO;
                
                result.totalRequiredArea = result.totalRequiredArea.add(requiredArea);
                
                // ==========================================
                // 第一步：查询可用复卷
                // ==========================================
                log.debug("第一步：查询可用复卷");
                allocatedArea = lockFromReels(schedule, order, requiredArea, allocatedArea, "复卷");
                
                // ==========================================
                // 第二步：复卷不足，查询可用母卷
                // ==========================================
                if (allocatedArea.compareTo(requiredArea) < 0) {
                    log.debug("第二步：复卷不足，查询可用母卷");
                    allocatedArea = lockFromReels(schedule, order, requiredArea, allocatedArea, "母卷");
                }
                
                // ==========================================
                // 第三步：母卷也不足，触发涂布排程
                // ==========================================
                BigDecimal shortageArea = requiredArea.subtract(allocatedArea);
                
                if (shortageArea.compareTo(BigDecimal.ZERO) > 0) {
                    log.warn("库存不足，需要触发涂布排程。订单ID: {}, 缺口面积: {}", order.getId(), shortageArea);
                    result.needCoatingOrderIds.add(order.getId());
                    
                    // 创建分配记录 - 部分满足
                    createAllocation(schedule.getId(), order.getId(), requiredArea, allocatedArea, 
                                   true, null);
                    result.partiallyLockedOrders++;
                } else {
                    // 创建分配记录 - 完全满足
                    createAllocation(schedule.getId(), order.getId(), requiredArea, allocatedArea, 
                                   false, null);
                    result.fullyLockedOrders++;
                }
                
                result.totalAllocatedArea = result.totalAllocatedArea.add(allocatedArea);
            }
            
            result.success = true;
            result.unlockedOrders = result.totalOrders - result.fullyLockedOrders - result.partiallyLockedOrders;
            
            log.info("物料锁定完成。成功: {}, 完全锁定: {}, 部分锁定: {}, 需涂布订单: {}", 
                    result.totalOrders, result.fullyLockedOrders, result.partiallyLockedOrders, 
                    result.needCoatingOrderIds.size());
            
            return result;
            
        } catch (Exception e) {
            log.error("物料锁定异常，排程ID: {}", scheduleId, e);
            result.success = false;
            result.errorMessage = e.getMessage();
            throw new MaterialLockException("物料锁定失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 从指定类型的库存中锁定物料
     * 
     * @param schedule 排程
     * @param order 订单
     * @param requiredArea 需求面积
     * @param allocatedArea 已锁定面积
     * @param reelType 物料类型（复卷、母卷）
     * @return 新的锁定面积
     */
    private BigDecimal lockFromReels(BatchSchedule schedule, SalesOrder order, 
                                     BigDecimal requiredArea, BigDecimal allocatedArea, 
                                     String reelType) throws MaterialLockException {
        
        BigDecimal remainingNeed = requiredArea.subtract(allocatedArea);
        
        // 查询可用物料
        List<TapeStock> reels = findAvailableReels(order, reelType);
        
        if (reels == null || reels.isEmpty()) {
            log.debug("未找到可用的 {}", reelType);
            return allocatedArea;
        }
        
        // 逐卷进行锁定
        for (TapeStock reel : reels) {
            BigDecimal availableArea = reel.getAvailableArea() != null ? 
                reel.getAvailableArea() : BigDecimal.ZERO;
            
            BigDecimal canLock = availableArea.compareTo(remainingNeed) >= 0 ? 
                remainingNeed : availableArea;
            
            if (canLock.compareTo(BigDecimal.ZERO) > 0) {
                // 创建锁定记录
                ScheduleMaterialLock lock = new ScheduleMaterialLock();
                lock.setScheduleId(schedule.getId());
                lock.setOrderId(order.getId());
                lock.setOrderNo(order.getOrderNo());
                lock.setFilmStockId(reel.getId());
                lock.setFilmStockDetailId(reel.getId());
                lock.setRollCode(reel.getQrCode() != null && !reel.getQrCode().trim().isEmpty() ? reel.getQrCode() : reel.getBatchNo());
                lock.setLockedArea(canLock);
                lock.setRequiredArea(requiredArea);
                lock.setLockStatus(ScheduleMaterialLock.LockStatus.LOCKED);
                lock.setLockedTime(LocalDateTime.now());
                lock.setLockedByUserId(getCurrentUserId());
                lock.setVersion(1);
                lock.setRemark("source=schedule-lock;materialCode=" + (reel.getMaterialCode() == null ? "" : reel.getMaterialCode()));
                
                scheduleMaterialLockMapper.insert(lock);
                log.debug("创建锁定记录，物料ID: {}, 锁定面积: {}", reel.getId(), canLock);
                
                // 更新库存面积
                int updateResult = tapeStockMapper.updateReservedArea(reel.getId(), canLock, reel.getVersion());
                if (updateResult == 0) {
                    throw new MaterialLockException("库存更新失败（乐观锁冲突），物料ID: " + reel.getId());
                }
                
                allocatedArea = allocatedArea.add(canLock);
                remainingNeed = requiredArea.subtract(allocatedArea);
                
                if (remainingNeed.compareTo(BigDecimal.ZERO) <= 0) {
                    break;  // 已满足，退出循环
                }
            }
        }
        
        return allocatedArea;
    }
    
    /**
     * 查询可用的物料卷
     * TODO: 此方法需要重构，SalesOrder不包含thickness和width字段
     * 这些字段在OrderDetail中，需要传入OrderDetail或规格参数
     */
    private List<TapeStock> findAvailableReels(SalesOrder order, String reelType) {
        // 暂时返回空列表，避免编译错误
        // 正确的实现应该从订单明细中获取规格信息
        return new ArrayList<>();
        
        /* 原代码存在问题，SalesOrder没有这些方法：
        Integer thickness = order.getThickness();
        Integer width = order.getWidth();
        BigDecimal minArea = order.getRequiredArea() != null ? 
            order.getRequiredArea() : 
            (order.getTotalArea() != null ? order.getTotalArea() : BigDecimal.ZERO);
        
        if ("复卷".equals(reelType)) {
            return tapeStockMapper.findAvailableReels(thickness, width, minArea, QUERY_LIMIT);
        } else if ("母卷".equals(reelType)) {
            return tapeStockMapper.findAvailableMotherReels(thickness, width, minArea, QUERY_LIMIT);
        }
        
        return new ArrayList<>();
        */
    }
    
    /**
     * 创建分配记录
     */
    private void createAllocation(Long scheduleId, Long orderId, BigDecimal requiredArea,
                                 BigDecimal allocatedArea, boolean needCoating, 
                                 Long coatingScheduleId) {
        ScheduleMaterialAllocation allocation = new ScheduleMaterialAllocation();
        allocation.setScheduleId(scheduleId);
        allocation.setOrderId(orderId);
        allocation.setRequiredArea(requiredArea);
        allocation.setAllocatedArea(allocatedArea);
        allocation.setShortageArea(requiredArea.subtract(allocatedArea));
        
        if (needCoating) {
            allocation.setAllocationStatus(ScheduleMaterialAllocation.AllocationStatus.PARTIALLY_MET);
            allocation.setNeedCoating(1);
        } else {
            allocation.setAllocationStatus(ScheduleMaterialAllocation.AllocationStatus.FULLY_MET);
            allocation.setNeedCoating(0);
        }
        
        allocation.setCoatingScheduleId(coatingScheduleId);
        allocation.setAllocatedTime(LocalDateTime.now());
        
        scheduleMaterialAllocationMapper.insert(allocation);
    }
    
    /**
     * 生产领料时扣减库存
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void allocateLocks(List<Long> lockIds) throws AllocationException {
        log.info("生产领料，更新锁定记录，数量: {}", lockIds.size());
        
        try {
            for (Long lockId : lockIds) {
                ScheduleMaterialLock lock = scheduleMaterialLockMapper.selectById(lockId);
                if (lock == null) {
                    throw new AllocationException("锁定记录不存在，ID: " + lockId);
                }
                if (!isLockedStatus(lock.getLockStatus())) {
                    throw new AllocationException("仅支持锁定中的记录领料，ID: " + lockId + "，当前状态: " + lock.getLockStatus());
                }
                if (lock.getFilmStockId() == null) {
                    throw new AllocationException("锁定记录未关联库存，无法领料，ID: " + lockId);
                }
                
                // 更新锁定状态为 已领料
                int updateResult = scheduleMaterialLockMapper.updateStatus(lockId, 
                    ScheduleMaterialLock.LockStatus.ALLOCATED, lock.getVersion());
                
                if (updateResult == 0) {
                    throw new AllocationException("锁定状态更新失败（乐观锁冲突），ID: " + lockId);
                }
                
                // 扣减库存
                TapeStock tape = tapeStockMapper.selectById(lock.getFilmStockId());
                if (tape == null) {
                    throw new AllocationException("物料不存在，ID: " + lock.getFilmStockId());
                }
                
                int consumeResult = tapeStockMapper.updateConsumedArea(lock.getFilmStockId(), 
                    lock.getLockedArea(), tape.getVersion());
                
                if (consumeResult == 0) {
                    throw new AllocationException("库存扣减失败（乐观锁冲突），物料ID: " + lock.getFilmStockId());
                }
                
                log.debug("领料成功，锁定ID: {}, 扣减面积: {}", lockId, lock.getLockedArea());
            }
        } catch (Exception e) {
            log.error("生产领料异常", e);
            throw new AllocationException("生产领料失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生产退料：对已领料的锁定记录做归还，恢复库存
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnLocks(List<Long> lockIds) throws AllocationException {
        log.info("生产退料，记录数: {}", lockIds.size());
        try {
            for (Long lockId : lockIds) {
                ScheduleMaterialLock lock = scheduleMaterialLockMapper.selectById(lockId);
                if (lock == null) {
                    throw new AllocationException("锁定记录不存在，ID: " + lockId);
                }
                if (!isAllocatedStatus(lock.getLockStatus())) {
                    throw new AllocationException("仅支持已领料的记录退料，ID: " + lockId + "，当前状态: " + lock.getLockStatus());
                }

                TapeStock tape = tapeStockMapper.selectById(lock.getFilmStockId());
                if (tape == null) {
                    throw new AllocationException("物料不存在，ID: " + lock.getFilmStockId());
                }

                int restore = tapeStockMapper.returnConsumedArea(lock.getFilmStockId(), lock.getLockedArea(), tape.getVersion());
                if (restore == 0) {
                    throw new AllocationException("退料失败（乐观锁冲突），物料ID: " + lock.getFilmStockId());
                }

                int update = scheduleMaterialLockMapper.updateStatus(lockId, ScheduleMaterialLock.LockStatus.RELEASED, lock.getVersion());
                if (update == 0) {
                    throw new AllocationException("锁定状态更新失败（乐观锁冲突），ID: " + lockId);
                }

                log.debug("退料成功，锁定ID: {}, 面积: {}", lockId, lock.getLockedArea());
            }
        } catch (Exception e) {
            log.error("生产退料异常", e);
            throw new AllocationException("生产退料失败: " + e.getMessage(), e);
        }
    }

    private boolean isLockedStatus(String status) {
        if (status == null) {
            return false;
        }
        String s = status.trim();
        return ScheduleMaterialLock.LockStatus.LOCKED.equals(s) || "LOCKED".equalsIgnoreCase(s);
    }

    private boolean isAllocatedStatus(String status) {
        if (status == null) {
            return false;
        }
        String s = status.trim();
        return ScheduleMaterialLock.LockStatus.ALLOCATED.equals(s)
                || "ALLOCATED".equalsIgnoreCase(s)
                || "PICKED".equalsIgnoreCase(s);
    }
    
    /**
     * 释放排程的所有锁定
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseLocks(Long scheduleId) throws ReleaseException {
        log.info("释放排程的锁定，排程ID: {}", scheduleId);
        
        try {
            // 查询排程的所有锁定记录
            List<ScheduleMaterialLock> locks = scheduleMaterialLockMapper.selectByScheduleId(scheduleId);
            
            for (ScheduleMaterialLock lock : locks) {
                if (!ScheduleMaterialLock.LockStatus.LOCKED.equals(lock.getLockStatus())) {
                    // 只有 "锁定中" 的记录才能释放
                    log.warn("跳过非锁定中的记录，ID: {}, 状态: {}", lock.getId(), lock.getLockStatus());
                    continue;
                }
                
                // 更新为已释放
                int updateResult = scheduleMaterialLockMapper.updateStatus(lock.getId(), 
                    ScheduleMaterialLock.LockStatus.RELEASED, lock.getVersion());
                
                if (updateResult == 0) {
                    throw new ReleaseException("锁定状态更新失败，ID: " + lock.getId());
                }
                
                // 恢复库存
                TapeStock tape = tapeStockMapper.selectById(lock.getFilmStockId());
                if (tape == null) {
                    throw new ReleaseException("物料不存在，ID: " + lock.getFilmStockId());
                }
                
                int releaseResult = tapeStockMapper.releaseLock(lock.getFilmStockId(), 
                    lock.getLockedArea(), tape.getVersion());
                
                if (releaseResult == 0) {
                    throw new ReleaseException("库存恢复失败，物料ID: " + lock.getFilmStockId());
                }
                
                log.debug("释放成功，锁定ID: {}, 恢复面积: {}", lock.getId(), lock.getLockedArea());
            }
        } catch (Exception e) {
            log.error("释放锁定异常，排程ID: {}", scheduleId, e);
            throw new ReleaseException("释放锁定失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 查询排程的所有分配记录
     */
    @Override
    public List<ScheduleMaterialAllocation> getAllocationsBySchedule(Long scheduleId) {
        return scheduleMaterialAllocationMapper.selectByScheduleId(scheduleId);
    }
    
    /**
     * 查询排程中某订单的分配情况
     */
    @Override
    public ScheduleMaterialAllocation getAllocationByScheduleAndOrder(Long scheduleId, Long orderId) {
        return scheduleMaterialAllocationMapper.selectByScheduleAndOrder(scheduleId, orderId);
    }
    
    /**
     * 查询物料的被锁定情况
     */
    @Override
    public List<ScheduleMaterialLock> getLocksByTapeStock(Long tapeStockId) {
        return scheduleMaterialLockMapper.selectByTapeStockId(tapeStockId);
    }
    
    /**
     * 查询订单的锁定记录
     */
    @Override
    public List<ScheduleMaterialLock> getLocksByOrder(Long orderId) {
        return scheduleMaterialLockMapper.selectByOrderId(orderId);
    }

    @Override
    public List<ScheduleMaterialLock> getLocksByOrderKey(String orderKey) {
        if (orderKey == null || orderKey.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // 优先尝试数字ID
        try {
            Long id = Long.parseLong(orderKey);
            return scheduleMaterialLockMapper.selectByOrderId(id);
        } catch (NumberFormatException ignore) {
            // 非数字则按订单号查找
        }

        com.fine.modle.SalesOrder order = salesOrderMapper.selectByOrderNo(orderKey);
        if (order == null) {
            return new ArrayList<>();
        }
        return scheduleMaterialLockMapper.selectByOrderId(order.getId());
    }

    @Override
    public List<ScheduleMaterialLock> queryOrderLockedStocks(LocalDate planDate,
                                                             String materialCode,
                                                             String orderNo,
                                                             String rollCode,
                                                             String processType,
                                                             Integer requiredLength) {
        LocalDate date = planDate == null ? LocalDate.now() : planDate;
        String mc = materialCode == null ? null : materialCode.trim();
        String ono = orderNo == null ? null : orderNo.trim();
        String rno = rollCode == null ? null : rollCode.trim();
        String pt = processType == null ? null : processType.trim();
        return scheduleMaterialLockMapper.selectOrderLockedStocks(date, mc, ono, rno, pt, requiredLength);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int generateProcurementPlansFromPendingLocks() {
        List<Map<String, Object>> rows = scheduleProcurementPlanMapper.selectPendingShortageForProcurement();
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        int created = 0;
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Long scheduleId = null;
            Object sid = row.get("scheduleId");
            if (sid instanceof Number) {
                scheduleId = ((Number) sid).longValue();
            } else if (sid != null) {
                try { scheduleId = Long.parseLong(String.valueOf(sid)); } catch (Exception ignore) {}
            }
            String orderNo = row.get("orderNo") == null ? null : String.valueOf(row.get("orderNo"));
            String materialCode = row.get("materialCode") == null ? null : String.valueOf(row.get("materialCode"));
            BigDecimal shortage = toScale2Decimal(row.get("shortageArea"));
            if (shortage.compareTo(BigDecimal.ZERO) <= 0 || materialCode == null || materialCode.trim().isEmpty()) {
                continue;
            }

            created += createProcurementPlansByBomOrDirect(scheduleId, orderNo, materialCode.trim(), shortage);
        }
        return created;
    }

    private int createProcurementPlansByBomOrDirect(Long scheduleId,
                                                    String orderNo,
                                                    String finishedMaterialCode,
                                                    BigDecimal shortageArea) {
        TapeFormula formula = tapeFormulaMapper.selectByMaterialCode(finishedMaterialCode);
        if (formula == null || formula.getId() == null) {
            return insertProcurementPlanIfAbsent(
                    scheduleId,
                    orderNo,
                    finishedMaterialCode,
                    shortageArea,
                    "source=pending-supply-auto-generate;mode=direct"
            );
        }

        List<TapeFormulaItem> items = tapeFormulaMapper.selectItemsByFormulaId(formula.getId());
        if (items == null || items.isEmpty()) {
            return insertProcurementPlanIfAbsent(
                    scheduleId,
                    orderNo,
                    finishedMaterialCode,
                    shortageArea,
                    "source=pending-supply-auto-generate;mode=direct;reason=no-formula-items"
            );
        }

        BigDecimal coatingArea = formula.getCoatingArea();
        if (coatingArea == null || coatingArea.compareTo(BigDecimal.ZERO) <= 0) {
            return insertProcurementPlanIfAbsent(
                    scheduleId,
                    orderNo,
                    finishedMaterialCode,
                    shortageArea,
                    "source=pending-supply-auto-generate;mode=direct;reason=invalid-coating-area"
            );
        }

        BigDecimal areaFactor = shortageArea.divide(coatingArea, 8, RoundingMode.HALF_UP);
        BigDecimal totalWeight = formula.getTotalWeight() == null ? BigDecimal.ZERO : formula.getTotalWeight();
        int created = 0;
        boolean hasValidBomQty = false;

        for (TapeFormulaItem item : items) {
            if (item == null || item.getMaterialCode() == null || item.getMaterialCode().trim().isEmpty()) {
                continue;
            }
            BigDecimal qty = BigDecimal.ZERO;
            if (item.getWeight() != null && item.getWeight().compareTo(BigDecimal.ZERO) > 0) {
                qty = item.getWeight().multiply(areaFactor);
            } else if (item.getRatio() != null
                    && item.getRatio().compareTo(BigDecimal.ZERO) > 0
                    && totalWeight.compareTo(BigDecimal.ZERO) > 0) {
                qty = totalWeight
                        .multiply(item.getRatio())
                        .divide(new BigDecimal("100"), 8, RoundingMode.HALF_UP)
                        .multiply(areaFactor);
            }

            qty = qty.setScale(2, RoundingMode.HALF_UP);
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            hasValidBomQty = true;
            String rawCode = item.getMaterialCode().trim();
            String remark = "source=pending-supply-auto-generate;mode=bom;finishedMaterial=" + finishedMaterialCode
                    + ";formulaId=" + formula.getId()
                    + ";shortageArea=" + shortageArea.toPlainString();
            created += insertProcurementPlanIfAbsent(scheduleId, orderNo, rawCode, qty, remark);
        }

        if (!hasValidBomQty) {
            return insertProcurementPlanIfAbsent(
                    scheduleId,
                    orderNo,
                    finishedMaterialCode,
                    shortageArea,
                    "source=pending-supply-auto-generate;mode=direct;reason=no-valid-bom-qty"
            );
        }
        return created;
    }

    private int insertProcurementPlanIfAbsent(Long scheduleId,
                                              String orderNo,
                                              String materialCode,
                                              BigDecimal requiredArea,
                                              String remark) {
        if (materialCode == null || materialCode.trim().isEmpty()) {
            return 0;
        }
        BigDecimal req = requiredArea == null ? BigDecimal.ZERO : requiredArea.setScale(2, RoundingMode.HALF_UP);
        if (req.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }

        int exists = scheduleProcurementPlanMapper.countOpenPlan(scheduleId, orderNo, materialCode.trim());
        if (exists > 0) {
            return 0;
        }

        String ts = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyMMddHHmmss"));
        String planNo = "SPP" + ts + String.format("%03d", (int) (Math.random() * 1000));
        int ok = scheduleProcurementPlanMapper.insertPlan(
                planNo,
                scheduleId,
                orderNo,
                materialCode.trim(),
                req,
                "PENDING",
                remark
        );
        return ok > 0 ? 1 : 0;
    }

    private BigDecimal toScale2Decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        try {
            return new BigDecimal(String.valueOf(value)).setScale(2, RoundingMode.HALF_UP);
        } catch (Exception ignore) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int createPurchaseOrdersFromProcurementPlans() {
        List<ScheduleProcurementPlan> plans = scheduleProcurementPlanMapper.selectPendingPlans();
        if (plans == null || plans.isEmpty()) {
            return 0;
        }

        Map<String, BigDecimal> materialQty = new LinkedHashMap<>();
        Map<String, List<ScheduleProcurementPlan>> materialPlans = new LinkedHashMap<>();
        BigDecimal totalRequired = BigDecimal.ZERO;

        for (ScheduleProcurementPlan plan : plans) {
            if (plan == null || plan.getRequiredArea() == null || plan.getRequiredArea().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String materialCode = plan.getMaterialCode();
            if (materialCode == null || materialCode.trim().isEmpty()) {
                continue;
            }
            String code = materialCode.trim();
            BigDecimal qty = plan.getRequiredArea().setScale(2, RoundingMode.HALF_UP);
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            materialQty.put(code, materialQty.getOrDefault(code, BigDecimal.ZERO).add(qty).setScale(2, RoundingMode.HALF_UP));
            materialPlans.computeIfAbsent(code, k -> new ArrayList<>()).add(plan);
            totalRequired = totalRequired.add(qty).setScale(2, RoundingMode.HALF_UP);
        }

        if (materialQty.isEmpty()) {
            return 0;
        }

        Map<String, Long> materialToItemId = new HashMap<>();
        Map<String, String> materialToOrderNo = new HashMap<>();
        for (Map.Entry<String, BigDecimal> entry : materialQty.entrySet()) {
            String materialCode = entry.getKey();
            BigDecimal qty = entry.getValue();
            List<ScheduleProcurementPlan> linkedPlans = materialPlans.get(materialCode);
            StringJoiner planNoJoiner = new StringJoiner(",");
            StringJoiner orderNoJoiner = new StringJoiner(",");
            if (linkedPlans != null) {
                for (ScheduleProcurementPlan p : linkedPlans) {
                    if (p == null) {
                        continue;
                    }
                    if (p.getPlanNo() != null && !p.getPlanNo().trim().isEmpty()) {
                        planNoJoiner.add(p.getPlanNo().trim());
                    }
                    if (p.getOrderNo() != null && !p.getOrderNo().trim().isEmpty()) {
                        orderNoJoiner.add(p.getOrderNo().trim());
                    }
                }
            }

            PurchaseOrder po = new PurchaseOrder();
            po.setOrderNo(generatePurchaseOrderNo());
            po.setSupplier("AUTO-PROCUREMENT");
            po.setOrderDate(java.util.Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()));
            po.setDeliveryDate(java.util.Date.from(LocalDate.now().plusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant()));
            po.setStatus("pending");
            po.setRemark("source=schedule_procurement_plan;mode=grouped-by-material;materialCode=" + materialCode + ";planCount=" + (linkedPlans == null ? 0 : linkedPlans.size()));
            po.setCreatedBy("system");
            po.setUpdatedBy("system");
            po.setCreatedAt(new java.util.Date());
            po.setUpdatedAt(new java.util.Date());
            po.setIsDeleted(0);
            po.setTotalArea(qty);
            po.setRequiredArea(qty);
            po.setTotalAmount(BigDecimal.ZERO);
            purchaseOrderMapper.insert(po);

            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setOrderId(po.getId());
            item.setMaterialCode(materialCode);
            item.setMaterialName(materialCode);
            item.setSqm(qty);
            item.setRolls(1);
            item.setUnitPrice(BigDecimal.ZERO);
            item.setAmount(BigDecimal.ZERO);
            item.setRemark("linkedPlans=" + planNoJoiner.toString() + ";orderNos=" + orderNoJoiner.toString());
            item.setCreatedBy("system");
            item.setUpdatedBy("system");
            item.setCreatedAt(new java.util.Date());
            item.setUpdatedAt(new java.util.Date());
            item.setIsDeleted(0);
            purchaseOrderItemMapper.insert(item);
            materialToItemId.put(materialCode, item.getId());
            materialToOrderNo.put(materialCode, po.getOrderNo());
        }

        for (ScheduleProcurementPlan plan : plans) {
            if (plan == null || plan.getMaterialCode() == null) {
                continue;
            }
            String code = plan.getMaterialCode().trim();
            Long itemId = materialToItemId.get(code);
            String poNo = materialToOrderNo.get(code);
            if (itemId == null || poNo == null) {
                continue;
            }
            scheduleProcurementPlanMapper.updateLinkInfo(
                    plan.getId(),
                    "LINKED",
                    poNo,
                    itemId
            );
        }
        return materialQty.size();
    }

    private String generatePurchaseOrderNo() {
        String dateCode = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd"));
        String prefix = "CD" + dateCode;
        String last = purchaseOrderMapper.selectLastOrderNoByPrefix(prefix);
        int nextSeq = 1;
        if (last != null && last.length() > prefix.length()) {
            try {
                nextSeq = Integer.parseInt(last.substring(prefix.length())) + 1;
            } catch (Exception ignore) {
                nextSeq = 1;
            }
        }
        return prefix + String.format("%02d", nextSeq);
    }
    
    /**
     * 获取当前用户ID（需要根据实际项目调整）
     */
    private Long getCurrentUserId() {
        // TODO: 从当前登录用户获取ID
        return 1L;
    }
}
