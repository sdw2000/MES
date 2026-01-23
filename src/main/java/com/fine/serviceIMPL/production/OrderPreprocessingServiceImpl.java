package com.fine.serviceIMPL.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fine.mapper.production.PreprocessingMaterialLockMapper;
import com.fine.mapper.production.OrderPreprocessingMapper;
import com.fine.model.production.OrderMaterialLock;
import com.fine.model.production.OrderPreprocessing;
import com.fine.mapper.schedule.PendingCoatingOrderPoolMapper;
import com.fine.model.schedule.PendingCoatingOrderPool;
import com.fine.modle.stock.TapeStock;
import com.fine.modle.SalesOrderItem;
import com.fine.modle.SalesOrder;
import com.fine.Dao.stock.TapeStockMapper;
import com.fine.Dao.SalesOrderItemMapper;
import com.fine.Dao.production.SalesOrderMapper;
import com.fine.service.production.OrderPreprocessingService;
import com.fine.service.production.AvailableMaterialDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单预处理Service实现
 */
@Service
public class OrderPreprocessingServiceImpl extends ServiceImpl<OrderPreprocessingMapper, OrderPreprocessing> implements OrderPreprocessingService {

    @Autowired
    private OrderPreprocessingMapper orderPreprocessingMapper;

    @Autowired
    private PreprocessingMaterialLockMapper preprocessingMaterialLockMapper;

    @Autowired
    private TapeStockMapper tapeStockMapper;

    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;

    @Autowired
    private SalesOrderMapper salesOrderMapper;

    @Autowired
    private PendingCoatingOrderPoolMapper pendingCoatingOrderPoolMapper;

    @Autowired
    private com.fine.mapper.schedule.PendingRewindingOrderPoolMapper pendingRewindingOrderPoolMapper;

    @Autowired
    private com.fine.mapper.schedule.PendingSlittingOrderPoolMapper pendingSlittingOrderPoolMapper;

    @Override
    public IPage<OrderPreprocessing> queryPreprocessingPage(Page<OrderPreprocessing> page, String status, String orderNo, String materialCode) {
        return orderPreprocessingMapper.selectPreprocessingPage(page, status, orderNo, materialCode);
    }

    @Override
    public List<OrderPreprocessing> getByOrderId(Long orderId) {
        return orderPreprocessingMapper.selectByOrderId(orderId);
    }

    @Override
    public OrderPreprocessing getByOrderItemId(Long orderItemId) {
        return orderPreprocessingMapper.selectByOrderItemId(orderItemId);
    }

    @Override
    @Transactional
    public OrderPreprocessing createPreprocessing(Long orderId,
                                                  Long orderItemId,
                                                  String orderNo,
                                                  String orderItemCode,
                                                  String materialCode,
                                                  String materialName,
                                                  String specDesc,
                                                  BigDecimal requiredQty) {
        OrderPreprocessing preprocessing = new OrderPreprocessing();
        preprocessing.setOrderId(orderId);
        preprocessing.setOrderItemId(orderItemId);
        preprocessing.setOrderNo(orderNo);
        preprocessing.setOrderItemCode(orderItemCode);
        preprocessing.setMaterialCode(materialCode);
        preprocessing.setMaterialName(materialName);
        preprocessing.setSpecDesc(specDesc);
        preprocessing.setRequiredQty(requiredQty);
        preprocessing.setLockedQty(BigDecimal.ZERO);
        preprocessing.setStatus(OrderPreprocessing.PreprocessingStatusEnum.PREPROCESSING);
        preprocessing.setLockStatus(OrderPreprocessing.LockStatusEnum.UNLOCKED);
        preprocessing.setScheduleType(OrderPreprocessing.ScheduleTypeEnum.COATING);

        this.save(preprocessing);
        return preprocessing;
    }

    @Override
    public List<AvailableMaterialDTO> getAvailableMaterials(String materialCode, Integer limit, Long orderItemId) {
        if (limit == null) {
            limit = 50;
        }

        // 订单长度（m），用于过滤可用卷（库里已直接存 m）
        java.math.BigDecimal orderLengthM = null;
        if (orderItemId != null) {
            com.fine.modle.SalesOrderItem item = salesOrderItemMapper.selectById(orderItemId);
            if (item != null && item.getLength() != null) {
                orderLengthM = item.getLength();
            }
        }

        // 查询符合条件的胶带库存 (按生产日期+ID FIFO排序)
        List<TapeStock> tapeStocks = tapeStockMapper.selectByMaterialCodeFIFO(materialCode);
        if (tapeStocks == null || tapeStocks.isEmpty()) {
            return Collections.emptyList();
        }

        List<AvailableMaterialDTO> result = new ArrayList<>();
        for (TapeStock tape : tapeStocks) {
            try {
                Long tapeId = tape.getId();
                if (tapeId == null) {
                    // 跳过异常数据行
                    continue;
                }
                // 锁定卷数（按锁定卷数量求和）
                java.math.BigDecimal lockedQty = preprocessingMaterialLockMapper.sumLockedQtyByTapeStockId(tapeId);
                int lockedRolls = lockedQty != null ? lockedQty.intValue() : 0;
                int totalRolls = (tape.getTotalRolls() != null ? tape.getTotalRolls() : 0);
                int availableRolls = Math.max(0, totalRolls - lockedRolls);

                // 卷长（m）：优先 currentLength，否则 length
                Integer lenVal = tape.getCurrentLength() != null ? tape.getCurrentLength() : tape.getLength();
                java.math.BigDecimal tapeLengthM = lenVal != null ? new java.math.BigDecimal(lenVal) : null;

                // 按订单长度过滤：所有卷型均需长度 ≥ 订单长度
                if (orderLengthM != null && tapeLengthM != null) {
                    if (tapeLengthM.compareTo(orderLengthM) < 0) {
                        continue; // 长度不足
                    }
                }

                // 面积兜底：若可用面积为空，则用总面积-已锁定/已消耗推算
                BigDecimal totalArea = tape.getTotalSqm();
                BigDecimal reserved = tape.getReservedArea() != null ? tape.getReservedArea() : BigDecimal.ZERO;
                BigDecimal consumed = tape.getConsumedArea() != null ? tape.getConsumedArea() : BigDecimal.ZERO;
                BigDecimal availableArea = tape.getAvailableArea();
                if (availableArea == null && totalArea != null) {
                    availableArea = totalArea.subtract(reserved).subtract(consumed);
                    if (availableArea.signum() < 0) {
                        availableArea = BigDecimal.ZERO;
                    }
                }

                // 若可用面积或可用卷数不足，则跳过
                if (availableArea == null || availableArea.compareTo(BigDecimal.ZERO) <= 0 || availableRolls <= 0) {
                    continue;
                }

                AvailableMaterialDTO dto = new AvailableMaterialDTO();
                dto.setTapeStockId(tapeId);
                dto.setQrCode(tape.getQrCode());
                dto.setBatchNo(tape.getBatchNo());
                dto.setMaterialCode(tape.getMaterialCode());
                dto.setSpecDesc(tape.getSpecDesc());
                dto.setTotalRolls(tape.getTotalRolls());
                dto.setLockedRolls(lockedRolls);
                dto.setAvailableRolls(availableRolls);
                dto.setTotalArea(totalArea);
                dto.setAvailableArea(availableArea);
                dto.setFifoOrder(tape.getSequenceNo() != null ? tape.getSequenceNo() : 0);
                dto.setProdDate(tape.getProdDate() != null ? tape.getProdDate().format(DateTimeFormatter.ISO_DATE) : "");

                if (availableRolls > 0) {
                    result.add(dto);
                }
            } catch (Exception ignored) {
                // 忽略单条异常，继续处理其他数据，避免接口整体报500
            }
            if (result.size() >= limit) {
                break;
            }
        }
        // FIFO顺序
        result.sort(Comparator.comparing(AvailableMaterialDTO::getFifoOrder));
        return result;
    }

    @Override
    @Transactional
    public void lockMaterials(Long preprocessingId, Long orderId, Long orderItemId, List<OrderMaterialLock> locks) throws Exception {
        if (locks == null || locks.isEmpty()) {
            return;
        }

        OrderPreprocessing preprocessing = this.getById(preprocessingId);
        if (preprocessing == null) {
            throw new Exception("预处理记录不存在");
        }
        BigDecimal alreadyLocked = preprocessing.getLockedQty() != null ? preprocessing.getLockedQty() : BigDecimal.ZERO;
        BigDecimal required = preprocessing.getRequiredQty() != null ? preprocessing.getRequiredQty() : BigDecimal.ZERO;
        if (alreadyLocked.compareTo(required) >= 0) {
            throw new Exception("该订单已满足需求，无需重复锁定");
        }

        // 订单基础信息（必填列）
        SalesOrder order = salesOrderMapper.selectById(orderId);
        String orderNo = order != null ? order.getOrderNo() : "";
        String customerName = order != null ? order.getCustomer() : "";

        // 设置锁定人ID和锁定时间
        LocalDateTime now = LocalDateTime.now();
        for (OrderMaterialLock lock : locks) {
            lock.setOrderId(orderId);
            lock.setOrderItemId(orderItemId);
            lock.setPreprocessingId(preprocessingId);
            lock.setLockedAt(now);
            lock.setLockStatus(OrderMaterialLock.LockStatusEnum.LOCKED);
            lock.setOrderNo(orderNo);
            lock.setCustomerId(null);
            lock.setCustomerName(customerName);
            lock.setCustomerPriorityScore(BigDecimal.ZERO);
            lock.setSharedOrderCount(1);
            lock.setSharedOrderDetails(null);
            lock.setRemark(null);

            // 尝试锁定物料 (乐观锁)
            TapeStock tape = tapeStockMapper.selectById(lock.getTapeStockId());
            if (tape == null) {
                throw new Exception("物料不存在，ID: " + lock.getTapeStockId());
            }

            // 补齐锁定记录的物料元数据，便于前端回显与统计
            lock.setMaterialCode(tape.getMaterialCode());
            lock.setBatchNo(tape.getBatchNo());
            lock.setMaterialSpec(tape.getSpecDesc());
            lock.setQrCode(tape.getQrCode());
            lock.setStockTableName("tape_stock");
            lock.setTapeStockId(tape.getId());
            // rollType -> stock_type 映射
            String rollType = tape.getRollType();
            String stockType = rollType;
            if ("母卷".equals(rollType)) {
                stockType = "jumbo";
            } else if ("复卷".equals(rollType)) {
                stockType = "rewound";
            } else if ("分切卷".equals(rollType)) {
                stockType = "finished";
            }
            lock.setStockType(stockType);
            // 锁定卷数/面积兜底
            if (lock.getLockQty() == null) {
                lock.setLockQty(BigDecimal.ONE);
            }
            if (lock.getLockArea() == null) {
                lock.setLockArea(BigDecimal.ZERO);
            }

            // 检查可用面积，缺省时用总面积-已耗-已预留兜底
            BigDecimal reservedArea = tape.getReservedArea() != null ? tape.getReservedArea() : BigDecimal.ZERO;
            BigDecimal consumedArea = tape.getConsumedArea() != null ? tape.getConsumedArea() : BigDecimal.ZERO;
            BigDecimal availableArea = tape.getAvailableArea();
            if (availableArea == null) {
                BigDecimal totalArea = tape.getTotalSqm() != null ? tape.getTotalSqm() : BigDecimal.ZERO;
                availableArea = totalArea.subtract(consumedArea).subtract(reservedArea);
                if (availableArea.compareTo(BigDecimal.ZERO) < 0) {
                    availableArea = BigDecimal.ZERO;
                }
            }
            if (availableArea.compareTo(lock.getLockArea()) < 0) {
                throw new Exception("物料库存不足，批次号: " + tape.getBatchNo());
            }

            // 更新胶带库存的预留面积 (使用乐观锁)
            int updateResult = tapeStockMapper.updateReservedAreaWithVersion(
                    tape.getId(),
                    lock.getLockArea(),
                    tape.getVersion()
            );

            if (updateResult == 0) {
                throw new Exception("物料库存已被其他订单修改，请重新选择，批次号: " + tape.getBatchNo());
            }
        }

        // 批量插入锁定记录
        preprocessingMaterialLockMapper.insertBatch(locks);

        // 更新预处理记录的锁定状态和已锁定面积
        BigDecimal totalLockedArea = locks.stream()
                .map(OrderMaterialLock::getLockArea)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal currentLockedQty = (preprocessing.getLockedQty() != null ? preprocessing.getLockedQty() : BigDecimal.ZERO)
                .add(totalLockedArea);

        String lockStatus;
        if (currentLockedQty.compareTo(preprocessing.getRequiredQty()) >= 0) {
            lockStatus = OrderPreprocessing.LockStatusEnum.LOCKED;
        } else {
            lockStatus = OrderPreprocessing.LockStatusEnum.PARTIAL;
        }

        // 自动判断排程类型
        String scheduleType = determineScheduleType(preprocessingId);
        orderPreprocessingMapper.updateLockInfo(preprocessingId, lockStatus, currentLockedQty, scheduleType);

        // 同步状态字段，方便前端控制交互
        preprocessing.setLockedQty(currentLockedQty);
        preprocessing.setLockStatus(lockStatus);
        preprocessing.setScheduleType(scheduleType);
        preprocessing.setStatus(OrderPreprocessing.LockStatusEnum.LOCKED.equals(lockStatus)
                ? OrderPreprocessing.PreprocessingStatusEnum.LOCKED
                : OrderPreprocessing.PreprocessingStatusEnum.PREPROCESSING);
        this.updateById(preprocessing);
    }

    @Override
    @Transactional
    public void releaseLocks(Long preprocessingId) throws Exception {
        OrderPreprocessing preprocessing = this.getById(preprocessingId);
        if (preprocessing == null) {
            throw new Exception("预处理记录不存在");
        }

        List<OrderMaterialLock> locks = preprocessingMaterialLockMapper.selectByPreprocessingId(preprocessingId);
        if (locks == null || locks.isEmpty()) {
            return;
        }

        for (OrderMaterialLock lock : locks) {
            if (!OrderMaterialLock.LockStatusEnum.LOCKED.equals(lock.getLockStatus())) {
                continue;
            }
            TapeStock tape = tapeStockMapper.selectById(lock.getTapeStockId());
            if (tape == null) {
                continue; // 缺失的库存行直接跳过
            }

            BigDecimal releaseArea = lock.getLockArea() != null ? lock.getLockArea() : BigDecimal.ZERO;
            if (releaseArea.compareTo(BigDecimal.ZERO) > 0) {
                int updated = tapeStockMapper.releaseLock(tape.getId(), releaseArea, tape.getVersion());
                if (updated == 0) {
                    throw new Exception("释放失败，库存已被修改: " + tape.getBatchNo());
                }
            }

            lock.setLockStatus(OrderMaterialLock.LockStatusEnum.RELEASED);
            preprocessingMaterialLockMapper.updateById(lock);
        }

        BigDecimal remaining = preprocessingMaterialLockMapper.sumLockedAreaByPreprocessingId(preprocessingId);
        if (remaining == null) {
            remaining = BigDecimal.ZERO;
        }

        String lockStatus = OrderPreprocessing.LockStatusEnum.UNLOCKED;
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            lockStatus = remaining.compareTo(preprocessing.getRequiredQty()) >= 0
                    ? OrderPreprocessing.LockStatusEnum.LOCKED
                    : OrderPreprocessing.LockStatusEnum.PARTIAL;
        }

        String scheduleType = determineScheduleType(preprocessingId);
        orderPreprocessingMapper.updateLockInfo(preprocessingId, lockStatus, remaining, scheduleType);

        preprocessing.setLockedQty(remaining);
        preprocessing.setLockStatus(lockStatus);
        preprocessing.setScheduleType(scheduleType);
        preprocessing.setStatus(OrderPreprocessing.PreprocessingStatusEnum.PREPROCESSING);
        this.updateById(preprocessing);
    }

    @Override
    @Transactional
    public void submitPreprocessing(Long preprocessingId) throws Exception {
        OrderPreprocessing preprocessing = this.getById(preprocessingId);
        if (preprocessing == null) {
            throw new Exception("预处理记录不存在");
        }

        // 判断目标待排池
        String targetPool = preprocessing.getTargetPool();
        if (targetPool == null || targetPool.isEmpty()) {
            if (OrderPreprocessing.ScheduleTypeEnum.COATING.equals(preprocessing.getScheduleType())) {
                targetPool = "pending_coating_order_pool";
            } else if (OrderPreprocessing.ScheduleTypeEnum.REWINDING.equals(preprocessing.getScheduleType())) {
                targetPool = "pending_rewinding_order_pool";
            } else if (OrderPreprocessing.ScheduleTypeEnum.SLITTING.equals(preprocessing.getScheduleType())) {
                targetPool = "pending_slitting_order_pool";
            } else {
                targetPool = "production_schedule";
            }
        }

        // 更新状态为已派发
        orderPreprocessingMapper.updateStatusAndPool(
                preprocessingId,
                OrderPreprocessing.PreprocessingStatusEnum.DISPATCHED,
                targetPool
        );

        // 如果目标包含涂布，生成涂布待排池记录
        if (targetPool != null && targetPool.toLowerCase().contains("coat")) {
            buildPendingCoatingPool(preprocessing, targetPool);
        }
        // 复卷待排池
        if (targetPool != null && targetPool.toLowerCase().contains("rewind")) {
            buildPendingRewindingPool(preprocessing, targetPool);
        }
        // 分切待排池
        if (targetPool != null && targetPool.toLowerCase().contains("slit")) {
            buildPendingSlittingPool(preprocessing, targetPool);
        }
    }

    /**
     * 创建涂布待排池记录
     */
    private void buildPendingCoatingPool(OrderPreprocessing preprocessing, String targetPool) {
        BigDecimal required = preprocessing.getRequiredQty() != null ? preprocessing.getRequiredQty() : BigDecimal.ZERO;
        BigDecimal locked = preprocessing.getLockedQty() != null ? preprocessing.getLockedQty() : BigDecimal.ZERO;
        BigDecimal shortageArea = required.subtract(locked);
        if (shortageArea.compareTo(BigDecimal.ZERO) < 0) {
            shortageArea = BigDecimal.ZERO;
        }
        int shortageQty = shortageArea.setScale(0, RoundingMode.CEILING).intValue();

        // 获取客户名称
        String customerName = "未填客户";
        SalesOrder order = salesOrderMapper.selectById(preprocessing.getOrderId());
        if (order != null && order.getCustomer() != null) {
            customerName = order.getCustomer();
        }

        // 避免重复插入同一订单明细的 WAITING 记录
        pendingCoatingOrderPoolMapper.delete(new QueryWrapper<PendingCoatingOrderPool>()
                .eq("order_item_id", preprocessing.getOrderItemId())
                .eq("pool_status", "WAITING"));

        PendingCoatingOrderPool pool = new PendingCoatingOrderPool();
        pool.setPoolNo(preprocessing.getMaterialCode());
        pool.setMaterialCode(preprocessing.getMaterialCode());
        pool.setMaterialName(preprocessing.getMaterialName());
        pool.setOrderId(preprocessing.getOrderId());
        pool.setOrderNo(preprocessing.getOrderNo());
        pool.setOrderItemId(preprocessing.getOrderItemId());
        pool.setCustomerName(customerName);
        pool.setCustomerPriority(BigDecimal.ZERO);
        pool.setShortageQty(shortageQty);
        pool.setShortageArea(shortageArea);
        pool.setPoolStatus("WAITING");
        pool.setAddedAt(new Date());

        pendingCoatingOrderPoolMapper.insert(pool);
    }

    private void buildPendingRewindingPool(OrderPreprocessing preprocessing, String targetPool) {
        BigDecimal required = preprocessing.getRequiredQty() != null ? preprocessing.getRequiredQty() : BigDecimal.ZERO;
        BigDecimal locked = preprocessing.getLockedQty() != null ? preprocessing.getLockedQty() : BigDecimal.ZERO;
        BigDecimal shortageArea = required.subtract(locked);
        if (shortageArea.compareTo(BigDecimal.ZERO) < 0) {
            shortageArea = BigDecimal.ZERO;
        }
        int shortageQty = shortageArea.setScale(0, RoundingMode.CEILING).intValue();

        String customerName = "未填客户";
        SalesOrder order = salesOrderMapper.selectById(preprocessing.getOrderId());
        if (order != null && order.getCustomer() != null) {
            customerName = order.getCustomer();
        }

        pendingRewindingOrderPoolMapper.delete(new QueryWrapper<com.fine.model.schedule.PendingRewindingOrderPool>()
            .eq("order_item_id", preprocessing.getOrderItemId())
            .eq("pool_status", "WAITING"));

        com.fine.model.schedule.PendingRewindingOrderPool pool = new com.fine.model.schedule.PendingRewindingOrderPool();
        pool.setPoolNo(preprocessing.getMaterialCode());
        pool.setMaterialCode(preprocessing.getMaterialCode());
        pool.setMaterialName(preprocessing.getMaterialName());
        pool.setOrderId(preprocessing.getOrderId());
        pool.setOrderNo(preprocessing.getOrderNo());
        pool.setOrderItemId(preprocessing.getOrderItemId());
        pool.setCustomerName(customerName);
        pool.setCustomerPriority(BigDecimal.ZERO);
        pool.setShortageQty(shortageQty);
        pool.setShortageArea(shortageArea);
        pool.setPoolStatus("WAITING");
        pool.setAddedAt(new Date());

        pendingRewindingOrderPoolMapper.insert(pool);
    }

    private void buildPendingSlittingPool(OrderPreprocessing preprocessing, String targetPool) {
        BigDecimal required = preprocessing.getRequiredQty() != null ? preprocessing.getRequiredQty() : BigDecimal.ZERO;
        BigDecimal locked = preprocessing.getLockedQty() != null ? preprocessing.getLockedQty() : BigDecimal.ZERO;
        BigDecimal shortageArea = required.subtract(locked);
        if (shortageArea.compareTo(BigDecimal.ZERO) < 0) {
            shortageArea = BigDecimal.ZERO;
        }
        int shortageQty = shortageArea.setScale(0, RoundingMode.CEILING).intValue();

        String customerName = "未填客户";
        SalesOrder order = salesOrderMapper.selectById(preprocessing.getOrderId());
        if (order != null && order.getCustomer() != null) {
            customerName = order.getCustomer();
        }

        pendingSlittingOrderPoolMapper.delete(new QueryWrapper<com.fine.model.schedule.PendingSlittingOrderPool>()
            .eq("order_item_id", preprocessing.getOrderItemId())
            .eq("pool_status", "WAITING"));

        com.fine.model.schedule.PendingSlittingOrderPool pool = new com.fine.model.schedule.PendingSlittingOrderPool();
        pool.setPoolNo(preprocessing.getMaterialCode());
        pool.setMaterialCode(preprocessing.getMaterialCode());
        pool.setMaterialName(preprocessing.getMaterialName());
        pool.setOrderId(preprocessing.getOrderId());
        pool.setOrderNo(preprocessing.getOrderNo());
        pool.setOrderItemId(preprocessing.getOrderItemId());
        pool.setCustomerName(customerName);
        pool.setCustomerPriority(BigDecimal.ZERO);
        pool.setShortageQty(shortageQty);
        pool.setShortageArea(shortageArea);
        pool.setPoolStatus("WAITING");
        pool.setAddedAt(new Date());

        pendingSlittingOrderPoolMapper.insert(pool);
    }

    @Override
    public BigDecimal getLockedArea(Long preprocessingId) {
        return preprocessingMaterialLockMapper.sumLockedAreaByPreprocessingId(preprocessingId);
    }

    @Override
    public String determineScheduleType(Long preprocessingId) {
        // 查询该预处理是否有锁定的物料
        List<OrderMaterialLock> locks = preprocessingMaterialLockMapper.selectByPreprocessingId(preprocessingId);
        if (locks == null || locks.isEmpty()) {
            return OrderPreprocessing.ScheduleTypeEnum.COATING;  // 无库存，涂布
        }

        List<OrderMaterialLock> activeLocks = locks.stream()
                .filter(l -> OrderMaterialLock.LockStatusEnum.LOCKED.equals(l.getLockStatus()))
                .collect(Collectors.toList());

        if (activeLocks.isEmpty()) {
            return OrderPreprocessing.ScheduleTypeEnum.COATING;
        }

        boolean hasMotherRoll = false;
        boolean hasOtherRoll = false;
        for (OrderMaterialLock lock : activeLocks) {
            TapeStock tape = tapeStockMapper.selectById(lock.getTapeStockId());
            if (tape == null) {
                continue;
            }
            String rollType = tape.getRollType();
            if ("母卷".equals(rollType) || "jumbo".equalsIgnoreCase(lock.getStockType())) {
                hasMotherRoll = true;
            } else {
                hasOtherRoll = true;
            }
        }

        if (hasMotherRoll) {
            return OrderPreprocessing.ScheduleTypeEnum.REWINDING;  // 先复卷
        }
        if (hasOtherRoll) {
            return OrderPreprocessing.ScheduleTypeEnum.SLITTING;   // 直接分切
        }
        return OrderPreprocessing.ScheduleTypeEnum.COATING;
    }
}
