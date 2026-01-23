package com.fine.controller.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fine.Utils.ResponseResult;
import com.fine.model.production.OrderMaterialLock;
import com.fine.model.production.OrderPreprocessing;
import com.fine.service.production.OrderPreprocessingService;
import com.fine.service.production.AvailableMaterialDTO;
import com.fine.Dao.SalesOrderItemMapper;
import com.fine.modle.SalesOrderItem;
import com.fine.mapper.production.PreprocessingMaterialLockMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 订单预处理Controller
 */
@PreAuthorize("hasAnyAuthority('admin','production')")
@RestController
@RequestMapping("/api/production/preprocessing")
@CrossOrigin
public class OrderPreprocessingController {

    @Autowired
    private OrderPreprocessingService orderPreprocessingService;

    @Autowired
    private PreprocessingMaterialLockMapper orderMaterialLockMapper;

    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;

    /**
     * 分页查询待预处理的订单
     */
    @GetMapping("/list")
    public ResponseResult<IPage<OrderPreprocessing>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String materialCode
    ) {
        Page<OrderPreprocessing> page = new Page<>(current, size);
        IPage<OrderPreprocessing> result = orderPreprocessingService.queryPreprocessingPage(page, status, orderNo, materialCode);
        return new ResponseResult<>(20000, "查询成功", result);
    }

    /**
     * 同步创建预处理记录：从未完成的销售订单明细（按面积口径）生成预处理条目
     */
    @PostMapping("/bootstrap")
    public ResponseResult<java.util.Map<String, Object>> bootstrap(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String materialCode
    ) {
        // 读取未完成明细（分页一次性最多1000条，必要时可循环分页）
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SalesOrderItem> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 1000);
        com.baomidou.mybatisplus.core.metadata.IPage<SalesOrderItem> pending = salesOrderItemMapper.selectPendingItems(page, orderNo, materialCode);

        int created = 0;
        for (SalesOrderItem item : pending.getRecords()) {
            // 已存在则跳过
            OrderPreprocessing existing = orderPreprocessingService.getByOrderItemId(item.getId());
            if (existing != null) {
                continue;
            }
            // 创建预处理记录，需求面积取待排程面积
                java.math.BigDecimal required = item.getPendingArea() != null ? item.getPendingArea() : item.getSqm();
                String spec = buildSpecDesc(item);
                // 直接在创建时填充所有必填展示字段，避免非空约束报错
                try {
                    OrderPreprocessing p = orderPreprocessingService.createPreprocessing(
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
                } catch (org.springframework.dao.DuplicateKeyException dup) {
                    // 已有并发/历史记录，跳过避免500
                    continue;
                }
        }

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("created", created);
        data.put("filteredOrderNo", orderNo);
        data.put("filteredMaterialCode", materialCode);
        return new ResponseResult<>(20000, "同步完成", data);
    }

    private String buildSpecDesc(SalesOrderItem item) {
        String thickness = item.getThickness() != null ? item.getThickness().stripTrailingZeros().toPlainString() + "μm" : "";
        String width = item.getWidth() != null ? item.getWidth().stripTrailingZeros().toPlainString() + "mm" : "";
        if (!thickness.isEmpty() && !width.isEmpty()) {
            return thickness + "/" + width;
        }
        return (thickness + " " + width).trim();
    }

    /**
     * 查询可锁定的物料列表 (FIFO)
     */
    @GetMapping("/available-materials")
    public ResponseResult<List<AvailableMaterialDTO>> getAvailableMaterials(
            @RequestParam String materialCode,
            @RequestParam(required = false, defaultValue = "50") Integer limit,
            @RequestParam(required = false) Long orderItemId
    ) {
        List<AvailableMaterialDTO> materials = orderPreprocessingService.getAvailableMaterials(materialCode, limit, orderItemId);
        return new ResponseResult<>(20000, "查询成功", materials);
    }

    /**
     * 锁定物料
     */
    @PostMapping("/lock-materials")
    public ResponseResult<Map<String, Object>> lockMaterials(@RequestBody LockMaterialsRequest request) {
        try {
            // 转换前端请求数据为实体
            List<OrderMaterialLock> locks = request.convertToLocks();
            
            // 调用Service锁定物料
            orderPreprocessingService.lockMaterials(
                    request.getPreprocessingId(),
                    request.getOrderId(),
                    request.getOrderItemId(),
                    locks
            );

            // 获取更新后的预处理记录信息
            OrderPreprocessing preprocessing = orderPreprocessingService.getById(request.getPreprocessingId());

            Map<String, Object> data = new java.util.HashMap<>();
            data.put("lockedQty", preprocessing.getLockedQty());
            data.put("lockStatus", preprocessing.getLockStatus());
            data.put("scheduleType", preprocessing.getScheduleType());

            return new ResponseResult<>(20000, "锁定成功", data);
        } catch (Exception e) {
            return new ResponseResult<>(40001, "锁定失败: " + e.getMessage(), null);
        }
    }

    /**
     * 解除锁定，释放库存
     */
    @PostMapping("/unlock-materials")
    public ResponseResult<Map<String, Object>> unlockMaterials(@RequestBody UnlockRequest request) {
        try {
            orderPreprocessingService.releaseLocks(request.getPreprocessingId());
            OrderPreprocessing preprocessing = orderPreprocessingService.getById(request.getPreprocessingId());

            Map<String, Object> data = new java.util.HashMap<>();
            data.put("lockedQty", preprocessing.getLockedQty());
            data.put("lockStatus", preprocessing.getLockStatus());
            data.put("scheduleType", preprocessing.getScheduleType());

            return new ResponseResult<>(20000, "解锁成功", data);
        } catch (Exception e) {
            return new ResponseResult<>(40001, "解锁失败: " + e.getMessage(), null);
        }
    }

    /**
     * 提交预处理订单
     */
    @PostMapping("/submit")
    public ResponseResult<Map<String, Object>> submitPreprocessing(@RequestBody SubmitRequest request) {
        try {
            // 更新数量（如果被编辑）
            OrderPreprocessing preprocessing = orderPreprocessingService.getById(request.getPreprocessingId());
            if (request.getRequiredQty() != null) {
                preprocessing.setRequiredQty(request.getRequiredQty());
            }
            if (request.getRemark() != null) {
                preprocessing.setRemark(request.getRemark());
            }

            // 根据选择确定排程类型（不足则强制涂布；提交后按钮改为撤销锁定）
            BigDecimal lockedQty = preprocessing.getLockedQty() != null ? preprocessing.getLockedQty() : BigDecimal.ZERO;
            BigDecimal requiredQty = preprocessing.getRequiredQty() != null ? preprocessing.getRequiredQty() : BigDecimal.ZERO;

            if (lockedQty.compareTo(requiredQty) < 0) {
                // 无库存/锁定不足：强制走涂布→复卷→分切链路
                preprocessing.setScheduleType(OrderPreprocessing.ScheduleTypeEnum.COATING);
                preprocessing.setTargetPool("coat>rewind>slit");
            } else if (request.getScheduleSelections() != null && !request.getScheduleSelections().isEmpty()) {
                preprocessing.setScheduleType(pickScheduleType(request.getScheduleSelections()));
                // 复卷+分切链路
                if (request.getScheduleSelections().contains(OrderPreprocessing.ScheduleTypeEnum.REWINDING)
                        && request.getScheduleSelections().contains(OrderPreprocessing.ScheduleTypeEnum.SLITTING)) {
                    preprocessing.setTargetPool("rewind>slit");
                } else {
                    preprocessing.setTargetPool(null); // 让Service按单一类型决定
                }
            } else if (request.getScheduleType() != null) {
                preprocessing.setScheduleType(request.getScheduleType());
                preprocessing.setTargetPool(null);
            }

            orderPreprocessingService.updateById(preprocessing);

            // 提交到待排池
            orderPreprocessingService.submitPreprocessing(request.getPreprocessingId());

            OrderPreprocessing updated = orderPreprocessingService.getById(request.getPreprocessingId());

            Map<String, Object> data = new java.util.HashMap<>();
            data.put("targetPool", updated.getTargetPool());
            data.put("status", updated.getStatus());
            data.put("dispatchedAt", updated.getUpdatedAt());

            return new ResponseResult<>(20000, "已提交至待排池", data);
        } catch (Exception e) {
            return new ResponseResult<>(40001, "提交失败: " + e.getMessage(), null);
        }
    }

    /**
     * 获取预处理记录详情
     */
    @GetMapping("/{id}")
    public ResponseResult<OrderPreprocessing> getDetail(@PathVariable Long id) {
        OrderPreprocessing preprocessing = orderPreprocessingService.getById(id);
        if (preprocessing == null) {
            return new ResponseResult<>(40004, "记录不存在", null);
        }
        return new ResponseResult<>(20000, "查询成功", preprocessing);
    }

    /**
     * 查询订单的锁定物料列表
     */
    @GetMapping("/locks/{preprocessingId}")
    public ResponseResult<List<OrderMaterialLock>> getLocks(@PathVariable Long preprocessingId) {
        List<OrderMaterialLock> locks = orderMaterialLockMapper
                .selectList(new QueryWrapper<OrderMaterialLock>()
                        .eq("preprocessing_id", preprocessingId));
        return new ResponseResult<>(20000, "查询成功", locks);
    }

    /**
     * 锁定物料请求类
     */
    @lombok.Data
    public static class LockMaterialsRequest {
        private Long preprocessingId;
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
        }

        public List<OrderMaterialLock> convertToLocks() {
            List<OrderMaterialLock> result = new java.util.ArrayList<>();
            if (locks == null) return result;

            for (LockItem item : locks) {
                OrderMaterialLock lock = new OrderMaterialLock();
                lock.setTapeStockId(item.getTapeStockId());
                lock.setLockQty(item.getLockQty());
                lock.setLockArea(item.getLockArea());
                lock.setFifoOrder(item.getFifoOrder());
                result.add(lock);
            }
            return result;
        }
    }

    @lombok.Data
    public static class UnlockRequest {
        private Long preprocessingId;
    }

    /**
     * 提交请求类
     */
    @lombok.Data
    public static class SubmitRequest {
        private Long preprocessingId;
        private BigDecimal requiredQty;
        private String scheduleType;
        private String remark;
        private List<String> scheduleSelections;
    }

    private String pickScheduleType(List<String> selections) {
        if (selections == null || selections.isEmpty()) {
            return OrderPreprocessing.ScheduleTypeEnum.COATING;
        }
        if (selections.contains(OrderPreprocessing.ScheduleTypeEnum.COATING)) {
            return OrderPreprocessing.ScheduleTypeEnum.COATING;
        }
        if (selections.contains(OrderPreprocessing.ScheduleTypeEnum.REWINDING)) {
            return OrderPreprocessing.ScheduleTypeEnum.REWINDING;
        }
        if (selections.contains(OrderPreprocessing.ScheduleTypeEnum.SLITTING)) {
            return OrderPreprocessing.ScheduleTypeEnum.SLITTING;
        }
        return OrderPreprocessing.ScheduleTypeEnum.COATING;
    }
}
