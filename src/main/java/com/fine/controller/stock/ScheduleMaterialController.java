package com.fine.controller.stock;

import com.fine.service.stock.ScheduleMaterialLockingService;
import com.fine.modle.stock.ScheduleMaterialLock;
import com.fine.modle.stock.ScheduleMaterialAllocation;
import com.fine.Utils.ResponseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * 排程物料管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/production/schedule-material")
public class ScheduleMaterialController {
    
    @Autowired
    private ScheduleMaterialLockingService scheduleMaterialLockingService;
    
    /**
     * 排程启动时锁定物料
     * POST /production/schedule-material/lock/{scheduleId}
     * 
     * @param scheduleId 排程ID
     * @return 锁定结果
     */
    @PostMapping("/lock/{scheduleId}")
    public ResponseResult<Map<String, Object>> lockMaterials(@PathVariable Long scheduleId) {
        log.info("排程物料锁定请求，排程ID: {}", scheduleId);
        
        try {
            ScheduleMaterialLockingService.LockResult result = 
                scheduleMaterialLockingService.lockMaterialsForSchedule(scheduleId);
            
            // 构建返回数据
            Map<String, Object> data = new HashMap<>();
            data.put("success", result.success);
            data.put("totalOrders", result.totalOrders);
            data.put("fullyLockedOrders", result.fullyLockedOrders);
            data.put("partiallyLockedOrders", result.partiallyLockedOrders);
            data.put("unlockedOrders", result.unlockedOrders);
            data.put("totalRequiredArea", result.totalRequiredArea);
            data.put("totalAllocatedArea", result.totalAllocatedArea);
            data.put("needCoatingOrderCount", result.needCoatingOrderIds.size());
            data.put("needCoatingOrderIds", result.needCoatingOrderIds);
              if (!result.success) {
                return ResponseResult.error(result.errorMessage);
            }
            
            return ResponseResult.success("物料锁定成功", data);
            
        } catch (ScheduleMaterialLockingService.MaterialLockException e) {
            log.error("物料锁定异常", e);
            return ResponseResult.error("物料锁定失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("未知异常", e);
            return ResponseResult.error("系统错误: " + e.getMessage());
        }
    }
    
    /**
     * 查询排程的物料分配情况
     * GET /production/schedule-material/allocation/{scheduleId}
     * 
     * @param scheduleId 排程ID
     * @return 分配记录列表
     */
    @GetMapping("/allocation/{scheduleId}")
    public ResponseResult<List<ScheduleMaterialAllocation>> queryAllocation(@PathVariable Long scheduleId) {
        log.debug("查询物料分配，排程ID: {}", scheduleId);
        
        try {            List<ScheduleMaterialAllocation> allocations = 
                scheduleMaterialLockingService.getAllocationsBySchedule(scheduleId);
            
            return ResponseResult.success("查询成功", allocations);
            
        } catch (Exception e) {
            log.error("查询分配异常", e);
            return ResponseResult.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询排程中某订单的分配情况
     * GET /production/schedule-material/allocation/{scheduleId}/{orderId}
     * 
     * @param scheduleId 排程ID
     * @param orderId 订单ID
     * @return 分配记录
     */
    @GetMapping("/allocation/{scheduleId}/{orderId}")
    public ResponseResult<ScheduleMaterialAllocation> queryAllocationByOrder(
            @PathVariable Long scheduleId,
            @PathVariable Long orderId) {
        log.debug("查询订单分配，排程ID: {}, 订单ID: {}", scheduleId, orderId);
        
        try {
            ScheduleMaterialAllocation allocation = 
                scheduleMaterialLockingService.getAllocationByScheduleAndOrder(scheduleId, orderId);
              if (allocation == null) {
                return ResponseResult.error("该订单的分配记录不存在");
            }
            
            return ResponseResult.success("查询成功", allocation);
            
        } catch (Exception e) {
            log.error("查询分配异常", e);
            return ResponseResult.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询物料被锁定的情况
     * GET /production/schedule-material/tape-locks/{tapeId}
     * 
     * @param tapeId 物料ID
     * @return 锁定记录列表
     */
    @GetMapping("/tape-locks/{tapeId}")
    public ResponseResult<List<ScheduleMaterialLock>> queryTapeLocks(@PathVariable Long tapeId) {
        log.debug("查询物料锁定情况，物料ID: {}", tapeId);
        
        try {            List<ScheduleMaterialLock> locks = 
                scheduleMaterialLockingService.getLocksByTapeStock(tapeId);
            
            return ResponseResult.success("查询成功", locks);
            
        } catch (Exception e) {
            log.error("查询锁定异常", e);
            return ResponseResult.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询订单的锁定记录
     * GET /production/schedule-material/order-locks/{orderId}
     * 
     * @param orderId 订单ID
     * @return 锁定记录列表
     */
    @GetMapping("/order-locks/{orderKey}")
    public ResponseResult<List<ScheduleMaterialLock>> queryOrderLocks(@PathVariable String orderKey) {
        log.debug("查询订单锁定，订单标识: {}", orderKey);
        try {
            List<ScheduleMaterialLock> locks = scheduleMaterialLockingService.getLocksByOrderKey(orderKey);
            return ResponseResult.success("查询成功", locks);
        } catch (Exception e) {
            log.error("查询锁定异常", e);
            return ResponseResult.error("查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 生产领料（扣减库存）
     * POST /production/schedule-material/allocate
     * 
     * 请求体格式：
     * {
     *   "lockIds": [1, 2, 3]
     * }
     * 
     * @param payload 包含锁定记录IDs的对象
     * @return 操作结果
     */
    @PostMapping("/allocate")
    public ResponseResult<Void> allocateMaterials(@RequestBody Map<String, List<Long>> payload) {
        log.info("生产领料请求，锁定记录数: {}", payload.get("lockIds").size());
        
        try {
            List<Long> lockIds = payload.get("lockIds");
            if (lockIds == null || lockIds.isEmpty()) {
                return ResponseResult.error("锁定记录不能为空");
            }
              scheduleMaterialLockingService.allocateLocks(lockIds);
            
            return ResponseResult.success("领料成功", null);
            
        } catch (ScheduleMaterialLockingService.AllocationException e) {
            log.error("领料异常", e);
            return ResponseResult.error("领料失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("未知异常", e);
            return ResponseResult.error("系统错误: " + e.getMessage());
        }
    }

    /**
     * 生产退料（库存归还）
     * POST /production/schedule-material/return
     * 请求体：{"lockIds": [1,2,3]}
     */
    @PostMapping("/return")
    public ResponseResult<Void> returnMaterials(@RequestBody Map<String, List<Long>> payload) {
        List<Long> lockIds = payload.get("lockIds");
        log.info("生产退料请求，锁定记录数: {}", lockIds != null ? lockIds.size() : 0);
        try {
            if (lockIds == null || lockIds.isEmpty()) {
                return ResponseResult.error("锁定记录不能为空");
            }
            scheduleMaterialLockingService.returnLocks(lockIds);
            return ResponseResult.success("退料成功", null);
        } catch (ScheduleMaterialLockingService.AllocationException e) {
            log.error("退料异常", e);
            return ResponseResult.error("退料失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("未知异常", e);
            return ResponseResult.error("系统错误: " + e.getMessage());
        }
    }
    
    /**
     * 释放排程的锁定（排程取消时）
     * POST /production/schedule-material/release/{scheduleId}
     * 
     * @param scheduleId 排程ID
     * @return 操作结果
     */
    @PostMapping("/release/{scheduleId}")
    public ResponseResult<Void> releaseLocks(@PathVariable Long scheduleId) {
        log.info("释放排程锁定请求，排程ID: {}", scheduleId);
        
        try {            scheduleMaterialLockingService.releaseLocks(scheduleId);
            
            return ResponseResult.success("释放成功", null);
            
        } catch (ScheduleMaterialLockingService.ReleaseException e) {
            log.error("释放异常", e);
            return ResponseResult.error("释放失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("未知异常", e);
            return ResponseResult.error("系统错误: " + e.getMessage());
        }
    }
}
