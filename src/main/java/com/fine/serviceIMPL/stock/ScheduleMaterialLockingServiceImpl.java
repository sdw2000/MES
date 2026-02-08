package com.fine.serviceIMPL.stock;

import com.fine.service.stock.ScheduleMaterialLockingService;
import com.fine.Dao.stock.TapeStockMapper;
import com.fine.Dao.stock.ScheduleMaterialLockMapper;
import com.fine.Dao.stock.ScheduleMaterialAllocationMapper;
import com.fine.Dao.production.BatchScheduleMapper;
import com.fine.Dao.production.SalesOrderMapper;
import com.fine.modle.stock.TapeStock;
import com.fine.modle.stock.ScheduleMaterialLock;
import com.fine.modle.stock.ScheduleMaterialAllocation;
import com.fine.modle.production.BatchSchedule;
import com.fine.modle.SalesOrder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

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
                lock.setFilmStockId(reel.getId());
                lock.setFilmStockDetailId(reel.getId());
                lock.setLockedArea(canLock);
                lock.setRequiredArea(requiredArea);
                lock.setLockStatus(ScheduleMaterialLock.LockStatus.LOCKED);
                lock.setLockedTime(LocalDateTime.now());
                lock.setLockedByUserId(getCurrentUserId());
                lock.setVersion(1);
                
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
                if (!ScheduleMaterialLock.LockStatus.ALLOCATED.equals(lock.getLockStatus())) {
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
    
    /**
     * 获取当前用户ID（需要根据实际项目调整）
     */
    private Long getCurrentUserId() {
        // TODO: 从当前登录用户获取ID
        return 1L;
    }
}
