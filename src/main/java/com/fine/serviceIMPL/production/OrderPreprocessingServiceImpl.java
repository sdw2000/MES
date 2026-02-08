package com.fine.serviceIMPL.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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
import com.fine.Dao.production.ScheduleCoatingMapper;
import com.fine.Dao.production.ScheduleRewindingMapper;
import com.fine.Dao.production.ScheduleSlittingMapper;
import com.fine.model.production.ScheduleCoating;
import com.fine.model.production.ScheduleRewinding;
import com.fine.model.production.ScheduleSlitting;
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
    private com.fine.Dao.stock.TapeRollMapper tapeRollMapper;

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

    @Autowired
    private ScheduleCoatingMapper scheduleCoatingMapper;

    @Autowired
    private ScheduleRewindingMapper scheduleRewindingMapper;

    @Autowired
    private ScheduleSlittingMapper scheduleSlittingMapper;

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
    public List<AvailableMaterialDTO> getAvailableMaterials(String materialCode, Integer limit, Long orderItemId,
                                                            Integer requiredRolls, java.math.BigDecimal requiredArea) {
        if (limit == null) {
            limit = 50;
        }

        // 订单长度（m），用于过滤可用卷（库里已直接存 m）
        java.math.BigDecimal orderLengthM = null;
        java.math.BigDecimal remainingArea = null;
        int remainingRolls = 0;
        if (orderItemId != null) {
            com.fine.modle.SalesOrderItem item = salesOrderItemMapper.selectById(orderItemId);
            if (item != null && item.getLength() != null) {
                orderLengthM = item.getLength();
            }
            if (item != null) {
                java.math.BigDecimal pendingArea = item.getPendingArea();
                if (pendingArea == null && item.getSqm() != null) {
                    java.math.BigDecimal scheduledArea = item.getScheduledArea() != null ? item.getScheduledArea() : java.math.BigDecimal.ZERO;
                    java.math.BigDecimal deliveredArea = item.getDeliveredArea() != null ? item.getDeliveredArea() : java.math.BigDecimal.ZERO;
                    pendingArea = item.getSqm().subtract(scheduledArea).subtract(deliveredArea);
                }
                remainingArea = pendingArea != null ? pendingArea.max(java.math.BigDecimal.ZERO) : null;
                int rolls = item.getRolls() != null ? item.getRolls() : 0;
                int scheduledQty = item.getScheduledQty() != null ? item.getScheduledQty() : 0;
                remainingRolls = Math.max(rolls - scheduledQty, 0);
            }
        }

        // 前端指定需求量时，优先按前端需求裁剪
        if (requiredRolls != null) {
            remainingRolls = Math.max(requiredRolls, 0);
        }
        if (requiredArea != null) {
            remainingArea = requiredArea.max(java.math.BigDecimal.ZERO);
            if (remainingArea.compareTo(java.math.BigDecimal.ZERO) > 0) {
                remainingRolls = 0; // 有面积需求时优先按面积裁剪
            }
        }

        // 优先按“每卷”明细表查询（一卷一行）
        List<com.fine.modle.stock.TapeRoll> rolls = tapeRollMapper.selectAvailableByMaterial(materialCode, orderLengthM, limit);
        List<AvailableMaterialDTO> result = new ArrayList<>();
        if (rolls != null && !rolls.isEmpty()) {
            for (com.fine.modle.stock.TapeRoll r : rolls) {
                AvailableMaterialDTO dto = new AvailableMaterialDTO();
                dto.setTapeStockId(r.getId()); // 前端沿用字段名作为“卷ID”
                dto.setQrCode(r.getQrCode());
                dto.setBatchNo(r.getBatchNo());
                dto.setMaterialCode(r.getMaterialCode());
                dto.setSpecDesc(r.getSpecDesc());
                dto.setTotalRolls(1);
                dto.setLockedRolls(0);
                dto.setAvailableRolls(1);
                dto.setTotalArea(r.getAvailableArea());
                dto.setAvailableArea(r.getAvailableArea());
                dto.setFifoOrder(r.getFifoOrder() != null ? r.getFifoOrder() : 0);
                dto.setProdDate(r.getProdDate() != null ? r.getProdDate().toString() : "");
                dto.setStockTableName("tape_stock_rolls");
                result.add(dto);
                if (result.size() >= limit) break;
            }
            return trimByNeed(result, remainingRolls, remainingArea);
        }

        // 兜底：仍按批次行返回
        List<TapeStock> tapeStocks = tapeStockMapper.selectByMaterialCodeFIFO(materialCode);
        if (tapeStocks == null || tapeStocks.isEmpty()) {
            return Collections.emptyList();
        }
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
                dto.setStockTableName("tape_stock");

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
        return trimByNeed(result, remainingRolls, remainingArea);
    }

    private List<AvailableMaterialDTO> trimByNeed(List<AvailableMaterialDTO> list, int remainingRolls, java.math.BigDecimal remainingArea) {
        if (list == null || list.isEmpty()) {
            return list;
        }
        if ((remainingRolls <= 0) && (remainingArea == null || remainingArea.compareTo(java.math.BigDecimal.ZERO) <= 0)) {
            return list;
        }
        List<AvailableMaterialDTO> picked = new ArrayList<>();
        java.math.BigDecimal areaLeft = remainingArea != null ? remainingArea : java.math.BigDecimal.ZERO;
        int rollsLeft = remainingRolls;
        for (AvailableMaterialDTO dto : list) {
            if (dto == null) continue;
            if (rollsLeft <= 0 && areaLeft.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                break;
            }
            int availRolls = dto.getAvailableRolls() != null ? dto.getAvailableRolls() : 0;
            java.math.BigDecimal availArea = dto.getAvailableArea() != null ? dto.getAvailableArea() : java.math.BigDecimal.ZERO;
            if (availRolls <= 0 || availArea.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                continue;
            }
            picked.add(dto);
            if (areaLeft.compareTo(java.math.BigDecimal.ZERO) > 0) {
                areaLeft = areaLeft.subtract(availArea.max(java.math.BigDecimal.ZERO));
            } else if (rollsLeft > 0) {
                rollsLeft -= Math.min(rollsLeft, availRolls);
            }
        }
        return picked;
    }

    @Override
    @Transactional
    public void lockMaterials(Long orderItemId, Long orderId, List<OrderMaterialLock> locks) throws Exception {
        // 允许前端未传锁定面积，后端按“剩余需求+可用面积”自动分配

        OrderPreprocessing preprocessing = this.getByOrderItemId(orderItemId);
        if (preprocessing == null) {
            SalesOrderItem item = salesOrderItemMapper.selectById(orderItemId);
            if (item == null) {
                throw new Exception("预处理记录不存在，且未找到订单明细");
            }
            SalesOrder order = salesOrderMapper.selectById(orderId);
            String orderNo = order != null ? order.getOrderNo() : "";
            BigDecimal required = item.getPendingArea() != null ? item.getPendingArea() : item.getSqm();
            String spec = buildSpecDesc(item);
            try {
                preprocessing = createPreprocessing(
                        orderId,
                        orderItemId,
                        orderNo,
                        String.valueOf(orderItemId),
                        item.getMaterialCode(),
                        item.getMaterialName(),
                        spec,
                        required
                );
            } catch (org.springframework.dao.DuplicateKeyException dup) {
                preprocessing = this.getByOrderItemId(orderItemId);
                if (preprocessing == null) {
                    throw new Exception("预处理记录已存在但未读取到，请重试");
                }
            }
        }

        Long preprocessingId = preprocessing.getId();
        BigDecimal required = preprocessing.getRequiredQty() != null ? preprocessing.getRequiredQty() : BigDecimal.ZERO;
        if (required.compareTo(BigDecimal.ZERO) <= 0) {
            SalesOrderItem item = salesOrderItemMapper.selectById(orderItemId);
            if (item != null) {
                required = item.getPendingArea() != null ? item.getPendingArea()
                        : (item.getSqm() != null ? item.getSqm() : BigDecimal.ZERO);
            }
        }
        BigDecimal lockedFromDb = preprocessingMaterialLockMapper.sumLockedAreaByOrderItemId(orderItemId);
        if (lockedFromDb == null) {
            lockedFromDb = preprocessing.getLockedQty() != null ? preprocessing.getLockedQty() : BigDecimal.ZERO;
        }
        if (required.compareTo(BigDecimal.ZERO) > 0 && lockedFromDb.compareTo(required) >= 0) {
            try {
                releaseLocks(orderItemId);
            } catch (Exception ignored) {
            }
            lockedFromDb = preprocessingMaterialLockMapper.sumLockedAreaByOrderItemId(orderItemId);
            if (lockedFromDb == null) {
                lockedFromDb = BigDecimal.ZERO;
            }
            preprocessing.setLockedQty(lockedFromDb);
            preprocessing.setLockStatus(lockedFromDb.compareTo(required) >= 0 ? OrderPreprocessing.LockStatusEnum.LOCKED : OrderPreprocessing.LockStatusEnum.PARTIAL);
            this.updateById(preprocessing);
        }

        // 订单基础信息（必填列）
        SalesOrder order = salesOrderMapper.selectById(orderId);
        String orderNo = order != null ? order.getOrderNo() : preprocessing.getOrderNo();
        String customerName = order != null ? order.getCustomer() : "";

        BigDecimal remainingNeed = required.subtract(lockedFromDb != null ? lockedFromDb : BigDecimal.ZERO);
        if (remainingNeed.compareTo(BigDecimal.ZERO) < 0) {
            remainingNeed = BigDecimal.ZERO;
        }

        // 若前端未传锁定行，则自动按FIFO选卷并锁定
        if (locks == null || locks.isEmpty()) {
            String materialCode = preprocessing.getMaterialCode();
            if ((materialCode == null || materialCode.isEmpty())) {
                SalesOrderItem item = salesOrderItemMapper.selectById(orderItemId);
                if (item != null) {
                    materialCode = item.getMaterialCode();
                }
            }
            if (remainingNeed.compareTo(BigDecimal.ZERO) > 0 && materialCode != null && !materialCode.isEmpty()) {
                List<AvailableMaterialDTO> materials = getAvailableMaterials(materialCode, 200, orderItemId, null, null);
                List<OrderMaterialLock> autoLocks = new ArrayList<>();
                BigDecimal need = remainingNeed;
                for (AvailableMaterialDTO m : materials) {
                    if (need.compareTo(BigDecimal.ZERO) <= 0) break;
                    BigDecimal available = m.getAvailableArea() != null ? m.getAvailableArea() : BigDecimal.ZERO;
                    if (available.compareTo(BigDecimal.ZERO) <= 0) continue;
                    BigDecimal lockArea = need.min(available);
                    if (lockArea.compareTo(BigDecimal.ZERO) <= 0) continue;
                    OrderMaterialLock lock = new OrderMaterialLock();
                    lock.setTapeStockId(m.getTapeStockId());
                    lock.setLockArea(lockArea);
                    lock.setLockQty(BigDecimal.ONE);
                    lock.setFifoOrder(m.getFifoOrder());
                    autoLocks.add(lock);
                    need = need.subtract(lockArea);
                }
                locks = autoLocks;
            }
        }

        if (locks == null || locks.isEmpty()) {
            return;
        }

        // 设置锁定人ID和锁定时间
        LocalDateTime now = LocalDateTime.now();
        List<OrderMaterialLock> lockedRecords = new ArrayList<>();
        for (OrderMaterialLock lock : locks) {
            if (remainingNeed.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
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
            // 先按“每卷”详情尝试锁定
            BigDecimal requestedArea = lock.getLockArea();
            String stockTableHint = lock.getStockTableName();
            com.fine.modle.stock.TapeRoll roll = null;
            if (!"tape_stock".equalsIgnoreCase(stockTableHint)) {
                roll = tapeRollMapper.selectWithStock(lock.getTapeStockId());
            }
            if (roll != null) {
                // 元数据填充
                lock.setMaterialCode(roll.getMaterialCode());
                lock.setBatchNo(roll.getBatchNo());
                lock.setMaterialSpec(roll.getSpecDesc());
                String rt = roll.getRollType();
                boolean motherOrRewind = "母卷".equals(rt) || "复卷".equals(rt);
                lock.setQrCode(motherOrRewind ? roll.getQrCode() : null);
                lock.setStockTableName("tape_stock_rolls");
                lock.setTapeStockId(roll.getId());
                String stockType = rt;
                if ("母卷".equals(rt)) stockType = "jumbo";
                else if ("复卷".equals(rt)) stockType = "rewound";
                else if ("分切卷".equals(rt)) stockType = "finished";
                lock.setStockType(stockType);

                // 校验可用面积，若有历史残留 reserved 但锁定表已无对应记录，则自动回滚 reserved 至 available
                BigDecimal availableArea = roll.getAvailableArea() != null ? roll.getAvailableArea() : BigDecimal.ZERO;
                BigDecimal reservedArea = roll.getReservedArea() != null ? roll.getReservedArea() : BigDecimal.ZERO;
                if (availableArea.compareTo(lock.getLockArea()) < 0) {
                    BigDecimal lockedAreaOnRoll = preprocessingMaterialLockMapper.sumLockedAreaByRollId(roll.getId());
                    if (lockedAreaOnRoll == null) {
                        lockedAreaOnRoll = BigDecimal.ZERO;
                    }
                    // 如果 reserved_area 存在历史残留且大于当前锁定占用，则将多余部分释放回 available
                    if (reservedArea.compareTo(lockedAreaOnRoll) > 0) {
                        BigDecimal release = reservedArea.subtract(lockedAreaOnRoll);
                        roll.setReservedArea(reservedArea.subtract(release));
                        roll.setAvailableArea(availableArea.add(release));
                        roll.setVersion((roll.getVersion() == null ? 0 : roll.getVersion()) + 1);
                        tapeRollMapper.updateById(roll);
                        availableArea = roll.getAvailableArea();
                        reservedArea = roll.getReservedArea();
                    }
                }
                BigDecimal canLock = (requestedArea != null && requestedArea.compareTo(BigDecimal.ZERO) > 0)
                        ? requestedArea
                        : remainingNeed;
                if (canLock.compareTo(remainingNeed) > 0) {
                    canLock = remainingNeed;
                }
                if (canLock.compareTo(availableArea) > 0) {
                    canLock = availableArea;
                }
                if (canLock.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                lock.setLockArea(canLock);

                if (availableArea.compareTo(lock.getLockArea()) < 0) {
                    // 有料先锁定，剩余缺口走涂布
                    lock.setLockArea(availableArea);
                }
                if (lock.getLockArea() == null || lock.getLockArea().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                // 乐观锁更新卷的预留面积
                Integer version = roll.getVersion();
                int updateResult;
                if (version == null) {
                    // 初始化version为0，直接更新该行
                    roll.setReservedArea((roll.getReservedArea() != null ? roll.getReservedArea() : BigDecimal.ZERO).add(lock.getLockArea()));
                    roll.setAvailableArea((roll.getAvailableArea() != null ? roll.getAvailableArea() : BigDecimal.ZERO).subtract(lock.getLockArea()));
                    roll.setVersion(0);
                    updateResult = tapeRollMapper.updateById(roll);
                } else {
                    updateResult = tapeRollMapper.updateReservedAreaWithVersion(roll.getId(), lock.getLockArea(), version);
                }
                if (updateResult == 0) {
                    throw new Exception("该卷已被其他订单修改，请重新选择: " + roll.getQrCode());
                }
                lockedRecords.add(lock);
                remainingNeed = remainingNeed.subtract(lock.getLockArea());
            } else {
                // 兼容旧数据：按批次行锁定（非逐卷）
                TapeStock tape = tapeStockMapper.selectById(lock.getTapeStockId());
                if (tape == null) {
                    throw new Exception("物料不存在，ID: " + lock.getTapeStockId());
                }
                lock.setMaterialCode(tape.getMaterialCode());
                lock.setBatchNo(tape.getBatchNo());
                lock.setMaterialSpec(tape.getSpecDesc());
                String rollType = tape.getRollType();
                boolean isMotherOrRewind = "母卷".equals(rollType) || "复卷".equals(rollType);
                lock.setQrCode(isMotherOrRewind ? tape.getQrCode() : null);
                lock.setStockTableName("tape_stock");
                lock.setTapeStockId(tape.getId());
                String stockType = rollType;
                if ("母卷".equals(rollType)) stockType = "jumbo";
                else if ("复卷".equals(rollType)) stockType = "rewound";
                else if ("分切卷".equals(rollType)) stockType = "finished";
                lock.setStockType(stockType);

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
                BigDecimal canLock = (requestedArea != null && requestedArea.compareTo(BigDecimal.ZERO) > 0)
                        ? requestedArea
                        : remainingNeed;
                if (canLock.compareTo(remainingNeed) > 0) {
                    canLock = remainingNeed;
                }
                if (canLock.compareTo(availableArea) > 0) {
                    canLock = availableArea;
                }
                if (canLock.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                lock.setLockArea(canLock);

                if (availableArea.compareTo(lock.getLockArea()) < 0) {
                    // 有料先锁定，剩余缺口走涂布
                    lock.setLockArea(availableArea);
                }
                if (lock.getLockArea() == null || lock.getLockArea().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                // 乐观锁更新批次的预留面积
                Integer version = tape.getVersion();
                int updateResult;
                if (version == null) {
                    BigDecimal currentReserved = tape.getReservedArea() != null ? tape.getReservedArea() : BigDecimal.ZERO;
                    BigDecimal currentAvailable = tape.getAvailableArea() != null ? tape.getAvailableArea() : BigDecimal.ZERO;
                    tape.setReservedArea(currentReserved.add(lock.getLockArea()));
                    tape.setAvailableArea(currentAvailable.subtract(lock.getLockArea()));
                    tape.setVersion(0);
                    updateResult = tapeStockMapper.updateById(tape);
                } else {
                    updateResult = tapeStockMapper.updateReservedAreaWithVersion(tape.getId(), lock.getLockArea(), version);
                }
                if (updateResult == 0) {
                    throw new Exception("物料库存已被其他订单修改，请重新选择，批次号: " + tape.getBatchNo());
                }
                lockedRecords.add(lock);
                remainingNeed = remainingNeed.subtract(lock.getLockArea());
            }
            // 锁定卷数/面积兜底（与卷级/批次级逻辑无关）
            if (lock.getLockQty() == null) {
                lock.setLockQty(BigDecimal.ONE);
            }
            if (lock.getLockArea() == null) {
                lock.setLockArea(BigDecimal.ZERO);
            }
        }

        // 批量插入锁定记录
        if (!lockedRecords.isEmpty()) {
            preprocessingMaterialLockMapper.insertBatch(lockedRecords);
        }

        // 更新预处理记录的锁定状态和已锁定面积
        // 重新从DB汇总已锁定面积（已包含本次插入的锁定记录，避免重复累加导致翻倍）
        BigDecimal currentLockedQty = preprocessingMaterialLockMapper.sumLockedAreaByOrderItemId(orderItemId);
        if (currentLockedQty == null) {
            currentLockedQty = BigDecimal.ZERO;
        }
        // 裁剪为不超过需求量，防止历史异常导致显示过大
        if (preprocessing.getRequiredQty() != null && preprocessing.getRequiredQty().compareTo(BigDecimal.ZERO) > 0
                && currentLockedQty.compareTo(preprocessing.getRequiredQty()) > 0) {
            currentLockedQty = preprocessing.getRequiredQty();
        }

        String lockStatus;
        if (currentLockedQty.compareTo(preprocessing.getRequiredQty()) >= 0) {
            lockStatus = OrderPreprocessing.LockStatusEnum.LOCKED;
        } else if (currentLockedQty.compareTo(BigDecimal.ZERO) > 0) {
            lockStatus = OrderPreprocessing.LockStatusEnum.PARTIAL;
        } else {
            lockStatus = OrderPreprocessing.LockStatusEnum.UNLOCKED;
        }

        // 自动判断排程类型
        String scheduleType = determineScheduleType(orderItemId);
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
    public void releaseLocks(Long orderItemId) throws Exception {
        OrderPreprocessing preprocessing = this.getByOrderItemId(orderItemId);
        if (preprocessing == null) {
            // 兼容已有的预处理主键传入
            preprocessing = this.getById(orderItemId);
        }
        if (preprocessing == null) {
            throw new Exception("预处理记录不存在");
        }
        Long preprocessingId = preprocessing.getId();

        List<OrderMaterialLock> locks = preprocessingMaterialLockMapper.selectByPreprocessingId(preprocessingId);
        if (locks == null || locks.isEmpty()) {
            return;
        }

        for (OrderMaterialLock lock : locks) {
            if (!OrderMaterialLock.LockStatusEnum.LOCKED.equals(lock.getLockStatus())) {
                continue;
            }

            BigDecimal releaseArea = lock.getLockArea() != null ? lock.getLockArea() : BigDecimal.ZERO;
            String stockTable = lock.getStockTableName();

            if ("tape_stock_rolls".equalsIgnoreCase(stockTable)) {
                com.fine.modle.stock.TapeRoll roll = tapeRollMapper.selectById(lock.getTapeStockId());
                if (roll != null && releaseArea.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal reserved = roll.getReservedArea() != null ? roll.getReservedArea() : BigDecimal.ZERO;
                    BigDecimal actualRelease = reserved.compareTo(releaseArea) < 0 ? reserved.max(BigDecimal.ZERO) : releaseArea;
                    if (actualRelease.compareTo(BigDecimal.ZERO) > 0) {
                        Integer version = roll.getVersion();
                        int updated;
                        if (version == null) {
                            BigDecimal available = roll.getAvailableArea() != null ? roll.getAvailableArea() : BigDecimal.ZERO;
                            roll.setReservedArea(reserved.subtract(actualRelease));
                            roll.setAvailableArea(available.add(actualRelease));
                            roll.setVersion(0);
                            updated = tapeRollMapper.updateById(roll);
                        } else {
                            updated = tapeRollMapper.releaseReservedAreaWithVersion(roll.getId(), actualRelease, version);
                        }
                        if (updated == 0) {
                            throw new Exception("释放失败，卷已被修改: " + roll.getQrCode());
                        }
                    }
                }
            } else {
                TapeStock tape = tapeStockMapper.selectById(lock.getTapeStockId());
                if (tape != null && releaseArea.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal reserved = tape.getReservedArea() != null ? tape.getReservedArea() : BigDecimal.ZERO;
                    BigDecimal actualRelease = reserved.compareTo(releaseArea) < 0 ? reserved.max(BigDecimal.ZERO) : releaseArea;
                    if (actualRelease.compareTo(BigDecimal.ZERO) > 0) {
                        Integer version = tape.getVersion();
                        int updated;
                        if (version == null) {
                            BigDecimal available = tape.getAvailableArea() != null ? tape.getAvailableArea() : BigDecimal.ZERO;
                            tape.setReservedArea(reserved.subtract(actualRelease));
                            tape.setAvailableArea(available.add(actualRelease));
                            tape.setVersion(0);
                            updated = tapeStockMapper.updateById(tape);
                        } else {
                            updated = tapeStockMapper.releaseLock(tape.getId(), actualRelease, version);
                        }
                        if (updated == 0) {
                            throw new Exception("释放失败，库存已被修改: " + tape.getBatchNo());
                        }
                    }
                }
            }

            // 即使库存行缺失，也要标记锁为已释放，避免重复累积
            lock.setLockStatus(OrderMaterialLock.LockStatusEnum.RELEASED);
            preprocessingMaterialLockMapper.updateById(lock);
        }

        BigDecimal remaining = preprocessingMaterialLockMapper.sumLockedAreaByOrderItemId(preprocessing.getOrderItemId());
        if (remaining == null) {
            remaining = BigDecimal.ZERO;
        }

        String lockStatus = OrderPreprocessing.LockStatusEnum.UNLOCKED;
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            lockStatus = remaining.compareTo(preprocessing.getRequiredQty()) >= 0
                    ? OrderPreprocessing.LockStatusEnum.LOCKED
                    : OrderPreprocessing.LockStatusEnum.PARTIAL;
        }

        String scheduleType = determineScheduleType(preprocessing.getOrderItemId());
        orderPreprocessingMapper.updateLockInfo(preprocessingId, lockStatus, remaining, scheduleType);

        preprocessing.setLockedQty(remaining);
        preprocessing.setLockStatus(lockStatus);
        preprocessing.setScheduleType(scheduleType);
        preprocessing.setStatus(OrderPreprocessing.PreprocessingStatusEnum.PREPROCESSING);
        this.updateById(preprocessing);
    }

    @Override
    @Transactional
    public void submitPreprocessing(Long orderItemId) throws Exception {
        OrderPreprocessing preprocessing = this.getByOrderItemId(orderItemId);
        if (preprocessing == null) {
            // 兼容传入预处理主键
            preprocessing = this.getById(orderItemId);
        }
        if (preprocessing == null) {
            throw new Exception("预处理记录不存在");
        }
        Long preprocessingId = preprocessing.getId();

        // 提交前重新汇总已锁定面积，避免历史脏数据导致缺口为0
        BigDecimal lockedFromDb = preprocessingMaterialLockMapper.sumLockedAreaByOrderItemId(orderItemId);
        if (lockedFromDb == null) {
            lockedFromDb = BigDecimal.ZERO;
        }
        preprocessing.setLockedQty(lockedFromDb);
        // 同步锁定状态，防止 lockedQty 与状态不一致
        BigDecimal required = preprocessing.getRequiredQty() != null ? preprocessing.getRequiredQty() : BigDecimal.ZERO;
        String lockStatus = OrderPreprocessing.LockStatusEnum.UNLOCKED;
        if (lockedFromDb.compareTo(required) >= 0) {
            lockStatus = OrderPreprocessing.LockStatusEnum.LOCKED;
        } else if (lockedFromDb.compareTo(BigDecimal.ZERO) > 0) {
            lockStatus = OrderPreprocessing.LockStatusEnum.PARTIAL;
        }
        preprocessing.setLockStatus(lockStatus);
        this.updateById(preprocessing);

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

        if (shortageArea.compareTo(BigDecimal.ZERO) <= 0) {
            // 已有料满足涂布需求，直接走后续工序
            pendingCoatingOrderPoolMapper.delete(new QueryWrapper<PendingCoatingOrderPool>()
                    .eq("order_item_id", preprocessing.getOrderItemId())
                    .eq("pool_status", "WAITING"));
            // 复卷/分切按照锁定面积（或需求）推进
            preprocessing.setLockedQty(locked);
            preprocessing.setRequiredQty(required);
            String downstreamPool = targetPool;
            if (downstreamPool == null || downstreamPool.isEmpty()) {
                downstreamPool = "rewind>slit"; // 默认走复卷/分切
            }
            if (downstreamPool.toLowerCase().contains("rewind")) {
                buildPendingRewindingPool(preprocessing, downstreamPool);
            }
            if (downstreamPool.toLowerCase().contains("slit")) {
                buildPendingSlittingPool(preprocessing, downstreamPool);
            }
            return;
        }

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
        BigDecimal processArea = shortageArea.compareTo(BigDecimal.ZERO) > 0
                ? shortageArea
                : (locked.compareTo(BigDecimal.ZERO) > 0 ? locked : required);
        int shortageQty = processArea.setScale(0, RoundingMode.CEILING).intValue();

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
        pool.setShortageArea(processArea);
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
        BigDecimal processArea = shortageArea.compareTo(BigDecimal.ZERO) > 0
                ? shortageArea
                : (locked.compareTo(BigDecimal.ZERO) > 0 ? locked : required);
        int shortageQty = processArea.setScale(0, RoundingMode.CEILING).intValue();

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
        pool.setShortageArea(processArea);
        pool.setPoolStatus("WAITING");
        pool.setAddedAt(new Date());

        pendingSlittingOrderPoolMapper.insert(pool);
    }

    @Override
    public BigDecimal getLockedArea(Long orderItemId) {
        BigDecimal val = preprocessingMaterialLockMapper.sumLockedAreaByOrderItemId(orderItemId);
        return val != null ? val : BigDecimal.ZERO;
    }

    @Override
    public String determineScheduleType(Long orderItemId) {
        OrderPreprocessing preprocessing = this.getByOrderItemId(orderItemId);
        Long preprocessingId = preprocessing != null ? preprocessing.getId() : null;

        List<OrderMaterialLock> locks = preprocessingId != null
                ? preprocessingMaterialLockMapper.selectByPreprocessingId(preprocessingId)
                : preprocessingMaterialLockMapper.selectByOrderItemId(orderItemId);
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

    @Override
    @Transactional
    public void cancelOrderItem(Long orderItemId) throws Exception {
        if (orderItemId == null) {
            throw new Exception("缺少订单明细ID");
        }

        OrderPreprocessing preprocessing = this.getByOrderItemId(orderItemId);
        if (preprocessing == null) {
            // 兼容传入预处理主键
            preprocessing = this.getById(orderItemId);
        }
        if (preprocessing == null) {
            throw new Exception("预处理记录不存在");
        }

        // 1) 释放锁定
        try {
            releaseLocks(orderItemId);
        } catch (Exception ex) {
            // 记录但不中断后续清理
        }

        // 2) 清理待排池记录（涂布/复卷/分切）
        pendingCoatingOrderPoolMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.fine.model.schedule.PendingCoatingOrderPool>()
                .eq("order_item_id", orderItemId)
                .eq("pool_status", "WAITING"));
        pendingRewindingOrderPoolMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.fine.model.schedule.PendingRewindingOrderPool>()
                .eq("order_item_id", orderItemId)
                .eq("pool_status", "WAITING"));
        pendingSlittingOrderPoolMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.fine.model.schedule.PendingSlittingOrderPool>()
                .eq("order_item_id", orderItemId)
                .eq("pool_status", "WAITING"));

        // 2.5) 取消已生成的排程任务（未完成）
        scheduleCoatingMapper.update(null, new UpdateWrapper<ScheduleCoating>()
            .eq("order_item_id", orderItemId)
            .ne("status", "completed")
            .set("status", "cancelled"));
        if (preprocessing.getOrderNo() != null && !preprocessing.getOrderNo().isEmpty()) {
            scheduleRewindingMapper.update(null, new UpdateWrapper<ScheduleRewinding>()
                .eq("order_nos", preprocessing.getOrderNo())
                .ne("status", "completed")
                .set("status", "cancelled"));
        }
        scheduleSlittingMapper.update(null, new UpdateWrapper<ScheduleSlitting>()
            .eq("order_item_id", orderItemId)
            .ne("status", "completed")
            .set("status", "cancelled"));

        // 3) 更新预处理状态为取消，锁定清零
        preprocessing.setLockedQty(BigDecimal.ZERO);
        preprocessing.setLockStatus(OrderPreprocessing.LockStatusEnum.UNLOCKED);
        preprocessing.setStatus(OrderPreprocessing.PreprocessingStatusEnum.CANCELLED);
        this.updateById(preprocessing);

        // 4) TODO: 推送通知（需接入站内信/WS模块）
    }

    private String buildSpecDesc(SalesOrderItem item) {
        String thickness = item.getThickness() != null ? item.getThickness().stripTrailingZeros().toPlainString() : "";
        String width = item.getWidth() != null ? item.getWidth().stripTrailingZeros().toPlainString() : "";
        String length = item.getLength() != null ? item.getLength().stripTrailingZeros().toPlainString() : "";
        List<String> parts = new ArrayList<>();
        if (!thickness.isEmpty()) parts.add(thickness);
        if (!width.isEmpty()) parts.add(width);
        if (!length.isEmpty()) parts.add(length);
        return parts.isEmpty() ? "" : String.join(" * ", parts);
    }
}
