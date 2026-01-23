package com.fine.service.stock;

import com.fine.modle.stock.ScheduleMaterialLock;
import com.fine.modle.stock.ScheduleMaterialAllocation;
import java.util.List;

/**
 * 排程物料锁定服务接口
 */
public interface ScheduleMaterialLockingService {
    
    /**
     * 排程启动时的物料锁定主流程
     * 
     * @param scheduleId 批排程ID
     * @return 锁定结果
     * @throws MaterialLockException 锁定失败异常
     */
    LockResult lockMaterialsForSchedule(Long scheduleId) throws MaterialLockException;
    
    /**
     * 生产领料时扣减库存
     * 
     * @param lockIds 锁定记录IDs
     * @throws AllocationException 分配失败异常
     */
    void allocateLocks(List<Long> lockIds) throws AllocationException;
    
    /**
     * 释放排程的所有锁定（排程取消时）
     * 
     * @param scheduleId 批排程ID
     * @throws ReleaseException 释放失败异常
     */
    void releaseLocks(Long scheduleId) throws ReleaseException;
    
    /**
     * 查询排程的物料分配汇总
     * 
     * @param scheduleId 批排程ID
     * @return 分配记录列表
     */
    List<ScheduleMaterialAllocation> getAllocationsBySchedule(Long scheduleId);
    
    /**
     * 查询排程中某订单的分配情况
     * 
     * @param scheduleId 批排程ID
     * @param orderId 订单ID
     * @return 分配记录
     */
    ScheduleMaterialAllocation getAllocationByScheduleAndOrder(Long scheduleId, Long orderId);
    
    /**
     * 查询物料的被锁定情况
     * 
     * @param tapeStockId 物料ID
     * @return 锁定记录列表
     */
    List<ScheduleMaterialLock> getLocksByTapeStock(Long tapeStockId);
    
    /**
     * 查询订单的锁定记录
     * 
     * @param orderId 订单ID
     * @return 锁定记录列表
     */
    List<ScheduleMaterialLock> getLocksByOrder(Long orderId);

    /**
     * 查询订单锁定记录（支持订单ID或订单号）
     * @param orderKey 订单ID或订单号
     * @return 锁定记录列表
     */
    List<ScheduleMaterialLock> getLocksByOrderKey(String orderKey);

    /**
     * 生产退料：对已领料的锁定记录做归还，恢复库存
     * @param lockIds 锁定记录IDs
     */
    void returnLocks(List<Long> lockIds) throws AllocationException;
    
    /**
     * 锁定结果DTO
     */
    class LockResult {
        /** 是否成功 */
        public boolean success;
        
        /** 总订单数 */
        public int totalOrders;
        
        /** 完全锁定的订单数 */
        public int fullyLockedOrders;
        
        /** 部分锁定的订单数 */
        public int partiallyLockedOrders;
        
        /** 未锁定的订单数 */
        public int unlockedOrders;
        
        /** 总需求面积(m²) */
        public java.math.BigDecimal totalRequiredArea;
        
        /** 实际锁定面积(m²) */
        public java.math.BigDecimal totalAllocatedArea;
        
        /** 需要涂布的订单IDs */
        public java.util.List<Long> needCoatingOrderIds;
        
        /** 错误信息 */
        public String errorMessage;
        
        public LockResult() {
            this.success = false;
            this.totalOrders = 0;
            this.fullyLockedOrders = 0;
            this.partiallyLockedOrders = 0;
            this.unlockedOrders = 0;
            this.totalRequiredArea = java.math.BigDecimal.ZERO;
            this.totalAllocatedArea = java.math.BigDecimal.ZERO;
            this.needCoatingOrderIds = new java.util.ArrayList<>();
        }
    }
    
    /**
     * 物料锁定异常
     */
    class MaterialLockException extends Exception {
        public MaterialLockException(String message) {
            super(message);
        }
        
        public MaterialLockException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * 物料分配异常
     */
    class AllocationException extends Exception {
        public AllocationException(String message) {
            super(message);
        }
        
        public AllocationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * 物料释放异常
     */
    class ReleaseException extends Exception {
        public ReleaseException(String message) {
            super(message);
        }
        
        public ReleaseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
