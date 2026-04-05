package com.fine.controller.stock;

import com.fine.service.stock.ScheduleMaterialLockingService;
import com.fine.service.stock.MaterialIssueOrderService;
import com.fine.Dao.stock.ScheduleMaterialLockMapper;
import com.fine.modle.stock.ScheduleMaterialLock;
import com.fine.modle.stock.ScheduleMaterialAllocation;
import com.fine.modle.stock.MaterialIssueOrder;
import com.fine.Utils.ResponseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;

/**
 * 排程物料管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/production/schedule-material")
public class ScheduleMaterialController {
    
    @Autowired
    private ScheduleMaterialLockingService scheduleMaterialLockingService;

    @Autowired
    private MaterialIssueOrderService materialIssueOrderService;

    @Autowired
    private ScheduleMaterialLockMapper scheduleMaterialLockMapper;
    
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
     * 按订单维度查询仓库已锁定物料（支持工序与长度过滤）
     * GET /production/schedule-material/order-locked-stocks
     */
    @GetMapping("/order-locked-stocks")
    public ResponseResult<List<ScheduleMaterialLock>> queryOrderLockedStocks(
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String rollCode,
            @RequestParam(required = false) String processType,
            @RequestParam(required = false) Integer requiredLength,
            @RequestParam(required = false) String planDate) {
        try {
            LocalDate date = (planDate == null || planDate.trim().isEmpty()) ? LocalDate.now() : LocalDate.parse(planDate);
            List<ScheduleMaterialLock> locks = scheduleMaterialLockingService.queryOrderLockedStocks(date, materialCode, orderNo, rollCode, processType, requiredLength);
            return ResponseResult.success("查询成功", locks);
        } catch (Exception e) {
            log.error("按订单查询锁定物料异常", e);
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
        List<Long> lockIds = payload == null ? null : payload.get("lockIds");
        log.info("生产领料请求，锁定记录数: {}", lockIds != null ? lockIds.size() : 0);
        
        try {
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
     * 创建并落库领料单（事务：领料+单据）
     * POST /production/schedule-material/issue-order/create
     */
    @PostMapping("/issue-order/create")
    public ResponseResult<MaterialIssueOrder> createIssueOrder(@RequestBody Map<String, Object> payload) {
        try {
            Object lockIdsObj = payload.get("lockIds");
            List<Long> lockIds = Collections.emptyList();
            if (lockIdsObj instanceof List) {
                lockIds = ((List<?>) lockIdsObj).stream()
                        .map(x -> {
                            if (x == null) {
                                return null;
                            }
                            if (x instanceof Number) {
                                return ((Number) x).longValue();
                            }
                            try {
                                return Long.parseLong(String.valueOf(x));
                            } catch (Exception e) {
                                return null;
                            }
                        })
                        .filter(x -> x != null && x > 0)
                        .collect(java.util.stream.Collectors.toList());
            }

            String planDateStr = payload.get("planDate") == null ? null : String.valueOf(payload.get("planDate"));
            LocalDate planDate = (planDateStr == null || planDateStr.trim().isEmpty()) ? null : LocalDate.parse(planDateStr);
            Long scheduleId = null;
            Object scheduleIdObj = payload.get("scheduleId");
            if (scheduleIdObj instanceof Number) {
                scheduleId = ((Number) scheduleIdObj).longValue();
            } else if (scheduleIdObj != null) {
                try {
                    scheduleId = Long.parseLong(String.valueOf(scheduleIdObj));
                } catch (Exception ignored) {
                }
            }
            String processType = payload.get("processType") == null ? null : String.valueOf(payload.get("processType"));
            String materialCode = payload.get("materialCode") == null ? null : String.valueOf(payload.get("materialCode"));
            String orderNo = payload.get("orderNo") == null ? null : String.valueOf(payload.get("orderNo"));
            String remark = payload.get("remark") == null ? null : String.valueOf(payload.get("remark"));

            MaterialIssueOrder order;
            if (lockIds != null && !lockIds.isEmpty()) {
                order = materialIssueOrderService.createIssueOrder(lockIds, planDate, materialCode, orderNo, remark);
            } else {
                order = materialIssueOrderService.createIssueOrderBySchedule(scheduleId, processType, planDate, materialCode, orderNo, remark);
            }
            return ResponseResult.success("领料成功，领料单已落库", order);
        } catch (Exception e) {
            log.error("创建领料单失败", e);
            return ResponseResult.error("创建领料单失败: " + e.getMessage());
        }
    }

    /**
     * 根据待补锁需求生成采购计划
     * POST /production/schedule-material/procurement-plan/generate
     */
    @PostMapping("/procurement-plan/generate")
    public ResponseResult<Map<String, Object>> generateProcurementPlanFromPending() {
        try {
            int created = scheduleMaterialLockingService.generateProcurementPlansFromPendingLocks();
            Map<String, Object> data = new HashMap<>();
            data.put("created", created);
            return ResponseResult.success("生成成功", data);
        } catch (Exception e) {
            log.error("生成采购计划异常", e);
            return ResponseResult.error("生成失败: " + e.getMessage());
        }
    }

    /**
     * 将排程采购计划自动转为采购订单
     * POST /production/schedule-material/procurement-order/create
     */
    @PostMapping("/procurement-order/create")
    public ResponseResult<Map<String, Object>> createPurchaseOrdersFromPlans() {
        try {
            int created = scheduleMaterialLockingService.createPurchaseOrdersFromProcurementPlans();
            Map<String, Object> data = new HashMap<>();
            data.put("created", created);
            return ResponseResult.success("生成成功", data);
        } catch (Exception e) {
            log.error("生成采购订单异常", e);
            return ResponseResult.error("生成失败: " + e.getMessage());
        }
    }

    /**
     * 查询领料单详情（可重打）
     * GET /production/schedule-material/issue-order/{issueNo}
     */
    @GetMapping("/issue-order/{issueNo}")
    public ResponseResult<MaterialIssueOrder> getIssueOrder(@PathVariable String issueNo) {
        try {
            MaterialIssueOrder order = materialIssueOrderService.getIssueOrderDetail(issueNo);
            if (order == null) {
                return ResponseResult.error("领料单不存在: " + issueNo);
            }
            return ResponseResult.success("查询成功", order);
        } catch (Exception e) {
            log.error("查询领料单失败", e);
            return ResponseResult.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 领料单分页查询（追溯）
     * GET /production/schedule-material/issue-order/page
     */
    @GetMapping("/issue-order/page")
    public ResponseResult<Map<String, Object>> getIssueOrderPage(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String planDate,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String materialCode) {
        try {
            LocalDate date = (planDate == null || planDate.trim().isEmpty()) ? null : LocalDate.parse(planDate);
            Map<String, Object> data = materialIssueOrderService.getIssueOrderPage(current, size, date, orderNo, materialCode);
            return ResponseResult.success("查询成功", data);
        } catch (Exception e) {
            if (isMissingTableException(e, "material_issue_order")) {
                log.warn("领料单表不存在，返回空分页结果。请先执行建表脚本: material_issue_order", e);
                Map<String, Object> empty = new HashMap<>();
                empty.put("records", Collections.emptyList());
                empty.put("total", 0);
                empty.put("current", Math.max(current, 1));
                empty.put("size", Math.max(size, 1));
                return ResponseResult.success("领料单表未初始化，已返回空数据", empty);
            }
            log.error("分页查询领料单失败", e);
            return ResponseResult.error("查询失败: " + e.getMessage());
        }
    }

    private boolean isMissingTableException(Throwable e, String tableName) {
        Throwable cur = e;
        String target = tableName == null ? "" : tableName.toLowerCase();
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if ((lower.contains("doesn't exist") || lower.contains("does not exist"))
                        && (target.isEmpty() || lower.contains(target))) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }

    /**
     * 锁定/领料/退料历史分页查询
     * GET /production/schedule-material/lock-history/page
     */
    @GetMapping("/lock-history/page")
    public ResponseResult<Map<String, Object>> getLockHistoryPage(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String planDate,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String materialCode,
            @RequestParam(required = false) String rollCode,
            @RequestParam(required = false) String lockStatus) {
        try {
            int safeCurrent = Math.max(current, 1);
            int safeSize = Math.max(size, 1);
            int offset = (safeCurrent - 1) * safeSize;

            List<ScheduleMaterialLock> records = scheduleMaterialLockMapper.selectLockHistoryPage(
                    planDate, orderNo, materialCode, rollCode, lockStatus, offset, safeSize);
            int total = scheduleMaterialLockMapper.countLockHistory(
                    planDate, orderNo, materialCode, rollCode, lockStatus);

            Map<String, Object> data = new HashMap<>();
            data.put("records", records);
            data.put("total", total);
            data.put("current", safeCurrent);
            data.put("size", safeSize);
            return ResponseResult.success("查询成功", data);
        } catch (Exception e) {
            log.error("查询锁定历史分页失败", e);
            return ResponseResult.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 生产退料（库存归还）
     * POST /production/schedule-material/return
     * 请求体：{"lockIds": [1,2,3]}
     */
    @PostMapping("/return")
    public ResponseResult<Void> returnMaterials(@RequestBody Map<String, List<Long>> payload) {
        List<Long> lockIds = payload == null ? null : payload.get("lockIds");
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
