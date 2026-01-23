package com.fine.controller.production;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.model.schedule.OrderMaterialLock;
import com.fine.service.schedule.MaterialLockService;
import com.fine.Utils.ResponseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 物料锁定Controller
 */
@RestController
@PreAuthorize("hasAnyAuthority('admin','production')")
@RequestMapping("/api/production/material-lock")
public class MaterialLockController {

    @Autowired
    private MaterialLockService materialLockService;

    /**
     * 获取订单物料锁定列表
     */
    @GetMapping("/order-locks")
    public ResponseResult<Map<String, Object>> getOrderMaterialLocks(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String qrCode,
            @RequestParam(required = false) String lockStatus
    ) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("pageNum", pageNum);
            params.put("pageSize", pageSize);
            params.put("orderNo", orderNo);
            params.put("customerName", customerName);
            params.put("qrCode", qrCode);
            params.put("lockStatus", lockStatus);

            IPage<OrderMaterialLock> page = materialLockService.getOrderMaterialLockPage(params);
            
            Map<String, Object> result = new HashMap<>();
            result.put("list", page.getRecords());
            result.put("total", page.getTotal());
            result.put("pageNum", pageNum);
            result.put("pageSize", pageSize);
            
            return ResponseResult.success(result);
        } catch (Exception e) {
            return ResponseResult.error("获取物料锁定列表失败：" + e.getMessage());
        }
    }

    /**
     * 锁定订单物料
     */
    @PostMapping("/lock")
    public ResponseResult<OrderMaterialLock> lockOrderMaterial(@RequestBody OrderMaterialLock lockData) {
        try {
            OrderMaterialLock result = materialLockService.lockOrderMaterial(lockData);
            return ResponseResult.success(result);
        } catch (Exception e) {
            return ResponseResult.error("锁定物料失败：" + e.getMessage());
        }
    }

    /**
     * 释放订单物料锁定
     */
    @PostMapping("/release/{lockId}")
    public ResponseResult<String> releaseOrderMaterialLock(
            @PathVariable Long lockId,
            @RequestParam String operator
    ) {
        try {
            materialLockService.releaseOrderMaterialLock(lockId, operator);
            return ResponseResult.success("物料锁定已释放");
        } catch (Exception e) {
            return ResponseResult.error("释放物料锁定失败：" + e.getMessage());
        }
    }

    /**
     * 批量释放订单物料锁定
     */
    @PostMapping("/batch-release")
    public ResponseResult<String> batchReleaseOrderMaterialLocks(
            @RequestBody Map<String, Object> request
    ) {
        try {
            @SuppressWarnings("unchecked")
            List<Long> lockIds = (List<Long>) request.get("lockIds");
            String operator = (String) request.get("operator");
            
            materialLockService.batchReleaseOrderMaterialLocks(lockIds, operator);
            return ResponseResult.success("物料锁定已批量释放");
        } catch (Exception e) {
            return ResponseResult.error("批量释放物料锁定失败：" + e.getMessage());
        }
    }

    /**
     * 触发领料
     */
    @PostMapping("/trigger-picking/{lockId}")
    public ResponseResult<String> triggerMaterialPicking(
            @PathVariable Long lockId,
            @RequestParam String operator
    ) {
        try {
            materialLockService.triggerMaterialPicking(lockId, operator);
            return ResponseResult.success("领料已触发");
        } catch (Exception e) {
            return ResponseResult.error("触发领料失败：" + e.getMessage());
        }
    }

    /**
     * 获取物料的多单共用情况
     */
    @GetMapping("/shared-locks")
    public ResponseResult<Map<String, Object>> getMaterialSharedLocks(
            @RequestParam String qrCode
    ) {
        try {
            Map<String, Object> stats = materialLockService.getMaterialSharedLocks(qrCode);
            return ResponseResult.success(stats);
        } catch (Exception e) {
            return ResponseResult.error("获取物料共用情况失败：" + e.getMessage());
        }
    }

    /**
     * 获取指定订单的物料锁定详情
     */
    @GetMapping("/order/{orderId}")
    public ResponseResult<List<OrderMaterialLock>> getOrderMaterialLocksByOrderId(
            @PathVariable Long orderId
    ) {
        try {
            List<OrderMaterialLock> locks = materialLockService.getOrderMaterialLocksByOrderId(orderId);
            return ResponseResult.success(locks);
        } catch (Exception e) {
            return ResponseResult.error("获取订单物料锁定失败：" + e.getMessage());
        }
    }

    /**
     * 检查物料是否已锁定
     */
    @GetMapping("/check-locked")
    public ResponseResult<Map<String, Object>> checkMaterialLocked(
            @RequestParam Long orderId,
            @RequestParam String materialCode
    ) {
        try {
            boolean locked = materialLockService.isMaterialLocked(orderId, materialCode);
            Map<String, Object> result = new HashMap<>();
            result.put("locked", locked);
            return ResponseResult.success(result);
        } catch (Exception e) {
            return ResponseResult.error("检查物料锁定状态失败：" + e.getMessage());
        }
    }

    /**
     * 获取物料锁定统计
     */
    @GetMapping("/stats")
    public ResponseResult<Map<String, Object>> getMaterialLockStats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            
            // 统计各状态的物料锁定数量
            long lockedCount = materialLockService.selectCount(new LambdaQueryWrapper<OrderMaterialLock>()
                    .eq(OrderMaterialLock::getLockStatus, "LOCKED"));
            long pickedCount = materialLockService.selectCount(new LambdaQueryWrapper<OrderMaterialLock>()
                    .eq(OrderMaterialLock::getLockStatus, "PICKED"));
            long releasedCount = materialLockService.selectCount(new LambdaQueryWrapper<OrderMaterialLock>()
                    .eq(OrderMaterialLock::getLockStatus, "RELEASED"));
            
            // 统计多单共用物料数量（sharedOrderCount > 1）
            long sharedCount = materialLockService.selectCount(new LambdaQueryWrapper<OrderMaterialLock>()
                    .gt(OrderMaterialLock::getSharedOrderCount, 1));
            
            stats.put("locked", lockedCount);
            stats.put("picked", pickedCount);
            stats.put("released", releasedCount);
            stats.put("shared", sharedCount);
            
            return ResponseResult.success(stats);
        } catch (Exception e) {
            return ResponseResult.error("获取统计信息失败：" + e.getMessage());
        }
    }
}
