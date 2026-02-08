package com.fine.serviceIMPL.production;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.stock.FilmStockDetailMapper;
import com.fine.Dao.stock.FilmStockMapper;
import com.fine.Dao.stock.ScheduleMaterialLockMapper;
import com.fine.Dao.production.ScheduleCoatingMapper;
import com.fine.Dao.production.ScheduleOrderItemMapper;
import com.fine.model.production.ScheduleCoating;
import com.fine.model.production.ScheduleOrderItem;
import com.fine.model.production.ScheduleMaterialLockDTO;
import com.fine.model.stock.FilmStock;
import com.fine.model.stock.FilmStockDetail;
import com.fine.modle.stock.ScheduleMaterialLock;
import com.fine.service.production.ScheduleMaterialLockService;
import com.fine.service.stock.FilmStockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 排程物料锁定服务实现
 */
@Service
@Slf4j
public class ScheduleMaterialLockServiceImpl extends ServiceImpl<ScheduleMaterialLockMapper, ScheduleMaterialLock>
        implements ScheduleMaterialLockService {
    
    @Autowired
    private ScheduleMaterialLockMapper lockMapper;
    
    @Autowired
    private FilmStockService filmStockService;
    
    @Autowired
    private FilmStockMapper filmStockMapper;
    
    @Autowired
    private FilmStockDetailMapper filmStockDetailMapper;
    
    @Autowired
    private ScheduleCoatingMapper scheduleCoatingMapper;
    
    @Autowired
    private ScheduleOrderItemMapper orderItemMapper;
    
    @Autowired(required = false)
    private com.fine.Dao.SalesOrderItemMapper salesOrderItemMapper;
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean lockMaterialForSchedule(ScheduleMaterialLockDTO lockDTO) {
        log.info("开始锁定物料, scheduleId={}, filmWidth={}, requiredArea={}", 
                lockDTO.getScheduleId(), lockDTO.getFilmWidth(), lockDTO.getRequiredArea());
        
        // 1. 查找指定规格的薄膜库存
        List<FilmStock> stocks = filmStockService.getBySpec(
                lockDTO.getFilmThickness(), 
                lockDTO.getFilmWidth()
        );
        
        if (stocks.isEmpty()) {
            throw new RuntimeException("未找到符合规格的薄膜库存");
        }
        
        // 2. 选择第一个有足够库存的
        FilmStock selectedStock = null;
        for (FilmStock stock : stocks) {
            if (stock.getAvailableArea() != null && 
                stock.getAvailableArea().compareTo(lockDTO.getRequiredArea()) >= 0) {
                selectedStock = stock;
                break;
            }
        }
        
        if (selectedStock == null) {
            throw new RuntimeException("库存不足，无法锁定所需面积");
        }
        
        // 3. 获取可用的明细
        List<FilmStockDetail> availableDetails = filmStockService.getAvailableDetails(selectedStock.getId());
        
        if (availableDetails.isEmpty()) {
            throw new RuntimeException("没有可用的薄膜卷");
        }
        
        // 4. 分配明细（按面积分配，先进先出）
        BigDecimal remainingArea = lockDTO.getRequiredArea();
        List<Long> selectedDetailIds = new ArrayList<>();
        Map<Long, BigDecimal> detailAreaMap = new HashMap<>();  // 记录每个卷的锁定面积
        
        for (FilmStockDetail detail : availableDetails) {
            if (remainingArea.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            selectedDetailIds.add(detail.getId());
            
            // 计算这一卷实际锁定的面积
            BigDecimal lockAreaForThisDetail = detail.getArea();
            if (lockAreaForThisDetail.compareTo(remainingArea) > 0) {
                lockAreaForThisDetail = remainingArea;  // 最后一卷可能只需要部分面积
            }
            detailAreaMap.put(detail.getId(), lockAreaForThisDetail);
            
            if (detail.getArea() != null) {
                remainingArea = remainingArea.subtract(detail.getArea());
            }
        }
        
        // 5. 锁定库存
        boolean locked = filmStockService.lockStock(
                selectedStock.getId(), 
                lockDTO.getRequiredArea(), 
                selectedDetailIds.size(),
                selectedDetailIds
        );
        
        if (!locked) {
            throw new RuntimeException("锁定库存失败");
        }
        
        // 6. 创建锁定记录 - 每条记录保存该卷的实际锁定面积
        for (Long detailId : selectedDetailIds) {
            ScheduleMaterialLock lock = new ScheduleMaterialLock();
            lock.setScheduleId(lockDTO.getScheduleId());
            lock.setOrderId(lockDTO.getOrderId());
            lock.setFilmStockId(selectedStock.getId());
            lock.setFilmStockDetailId(detailId);
            lock.setLockedArea(detailAreaMap.get(detailId));  // 使用该卷的实际锁定面积
            lock.setRequiredArea(lockDTO.getRequiredArea());  // 保留总需求面积作为参考
            lock.setLockStatus("锁定中");
            lock.setLockedTime(LocalDateTime.now());
            lock.setRemark(lockDTO.getRemark());
            lock.setVersion(1);
            
            lockMapper.insert(lock);
        }
        
        // 7. 查询排程的所有订单项（优先使用 schedule_order_item），若为空则尝试使用 sales_order_items
        List<ScheduleOrderItem> orderItems = orderItemMapper.selectList(
                new QueryWrapper<ScheduleOrderItem>().eq("schedule_id", lockDTO.getScheduleId())
        );

        if ((orderItems == null || orderItems.isEmpty()) && lockDTO.getOrderId() != null && salesOrderItemMapper != null) {
            // 回退：从销售订单明细获取（order_id -> sales_order_items）
            List<com.fine.modle.SalesOrderItem> salesItems = salesOrderItemMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.fine.modle.SalesOrderItem>()
                            .eq("order_id", lockDTO.getOrderId())
            );
            if (salesItems != null && !salesItems.isEmpty()) {
                // 将 salesItems 转换为 ScheduleOrderItem 只保留必要字段用于回填
                orderItems = new ArrayList<>();
                for (com.fine.modle.SalesOrderItem s : salesItems) {
                    ScheduleOrderItem soi = new ScheduleOrderItem();
                    soi.setOrderId(s.getOrderId());
                    soi.setOrderItemId(s.getId());
                    soi.setOrderNo(s.getOrderNo());
                    soi.setMaterialCode(s.getMaterialCode());
                    soi.setLength(s.getLength());
                    soi.setWidth(s.getWidth());
                    soi.setThickness(s.getThickness());
                    soi.setOrderQty(s.getRolls() != null ? s.getRolls() : 0);
                    orderItems.add(soi);
                }
            }
        }

        // 8. 更新排程表的jumbo_width字段（根据schedule_id更新所有相关的涂布任务）
        QueryWrapper<ScheduleCoating> wrapper = new QueryWrapper<>();
        wrapper.eq("schedule_id", lockDTO.getScheduleId());
        List<ScheduleCoating> schedules = scheduleCoatingMapper.selectList(wrapper);

        for (ScheduleCoating schedule : schedules) {
            schedule.setJumboWidth(selectedStock.getWidth());
            schedule.setFilmWidth(selectedStock.getWidth()); // 同时设置filmWidth（虽然不存数据库，但前端需要）
            schedule.setFilmThickness(selectedStock.getThickness() != null ? selectedStock.getThickness().intValue() : null);
            schedule.setBaseFilmRolls(selectedDetailIds.size());
            schedule.setBaseFilmArea(lockDTO.getRequiredArea());

            // 尝试根据物料编码匹配订单项并回填字段，减少对单一 orderItem 的耦合
            if (orderItems != null && !orderItems.isEmpty()) {
                ScheduleOrderItem match = null;
                for (ScheduleOrderItem oi : orderItems) {
                    if (oi.getMaterialCode() != null && oi.getMaterialCode().equals(schedule.getMaterialCode())) {
                        match = oi; break;
                    }
                    if (oi.getOrderItemId() != null && oi.getOrderItemId().equals(schedule.getOrderItemId())) {
                        match = oi; break;
                    }
                }
                if (match != null) {
                    schedule.setOrderNo(match.getOrderNo());
                    schedule.setOrderId(match.getOrderId());
                    schedule.setOrderItemId(match.getOrderItemId());
                    // 回填长度/宽度（如果任务缺失）
                    if (schedule.getPlanLength() == null && match.getLength() != null) {
                        schedule.setPlanLength(match.getLength());
                    }
                    if (schedule.getJumboWidth() == null && match.getWidth() != null) {
                        schedule.setJumboWidth(match.getWidth().intValue());
                    }
                    if (schedule.getThickness() == null && match.getThickness() != null) {
                        schedule.setThickness(match.getThickness());
                    }
                }
            }

            // 设置计划面积（从涂布长度计算：长度m * 宽度mm / 1000000），兼容 BigDecimal/整数等
            if (schedule.getPlanLength() != null && selectedStock.getWidth() != null) {
                java.math.BigDecimal planSqm = schedule.getPlanLength()
                    .multiply(new java.math.BigDecimal(selectedStock.getWidth()))
                    .divide(new java.math.BigDecimal(1000000), 2, java.math.RoundingMode.HALF_UP);
                schedule.setPlanSqm(planSqm);
            }
            scheduleCoatingMapper.updateById(schedule);
        }
        
        log.info("更新排程宽度成功, scheduleId={}, jumboWidth={}, 更新任务数={}", 
                lockDTO.getScheduleId(), selectedStock.getWidth(), schedules.size());
        
        log.info("物料锁定成功, 锁定了{}个卷", selectedDetailIds.size());
        return true;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> autoLockMaterial(Long scheduleId, Integer filmWidth, Integer filmThickness) {
        log.info("自动锁定物料, scheduleId={}, filmWidth={}, filmThickness={}", 
                scheduleId, filmWidth, filmThickness);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        
        try {
            // 这里需要根据排程信息计算需求面积
            // 简化实现，假设从排程表获取
            // TODO: 实际需要查询 schedule_coating 表获取计划面积
            
            ScheduleMaterialLockDTO lockDTO = new ScheduleMaterialLockDTO();
            lockDTO.setScheduleId(scheduleId);
            lockDTO.setFilmWidth(filmWidth);
            lockDTO.setFilmThickness(filmThickness);
            // lockDTO.setRequiredArea(计算出的面积);
            
            // 暂时返回需要手动指定面积
            result.put("message", "需要指定需求面积");
            result.put("filmWidth", filmWidth);
            result.put("filmThickness", filmThickness);
            
        } catch (Exception e) {
            log.error("自动锁定物料失败", e);
            result.put("message", e.getMessage());
        }
        
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unlockMaterialForSchedule(Long scheduleId) {
        log.info("释放排程物料锁定, scheduleId={}", scheduleId);
        
        // 1. 查询该排程的所有锁定记录
        List<ScheduleMaterialLock> locks = lockMapper.selectByScheduleId(scheduleId);
        
        if (locks.isEmpty()) {
            log.warn("未找到排程的锁定记录, scheduleId={}", scheduleId);
            // 即使没有锁定记录，也清空涂布任务的jumboWidth（解锁状态）
            this.clearCoatingTaskWidths(scheduleId);
            return true;
        }
        
        // 2. 按物料分组
        Map<Long, List<ScheduleMaterialLock>> groupByStock = new HashMap<>();
        for (ScheduleMaterialLock lock : locks) {
            groupByStock.computeIfAbsent(lock.getFilmStockId(), k -> new ArrayList<>()).add(lock);
        }
        
        // 3. 释放每个物料的锁定
        for (Map.Entry<Long, List<ScheduleMaterialLock>> entry : groupByStock.entrySet()) {
            List<ScheduleMaterialLock> lockList = entry.getValue();
            
            // 使用第一条记录的requiredArea作为总解锁面积（所有记录的requiredArea应该相同）
            BigDecimal unlockArea = lockList.get(0).getRequiredArea();
            if (unlockArea == null) {
                // 兼容旧数据：如果没有requiredArea，则累加各个lockedArea
                unlockArea = lockList.stream()
                        .map(ScheduleMaterialLock::getLockedArea)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            
            // 获取明细ID
            List<Long> detailIds = lockList.stream()
                    .map(ScheduleMaterialLock::getFilmStockDetailId)
                    .collect(Collectors.toList());
            
            // 使用filmStockId直接释放锁定
            Long filmStockId = lockList.get(0).getFilmStockId();
            log.info("解锁物料: filmStockId={}, 面积={}, 卷数={}", filmStockId, unlockArea, detailIds.size());
            filmStockService.unlockStock(filmStockId, unlockArea, detailIds.size(), detailIds);
            
            // 4. 更新锁定记录状态
            for (ScheduleMaterialLock lock : lockList) {
                lock.setLockStatus("已释放");
                lock.setReleasedTime(LocalDateTime.now());
                lockMapper.updateById(lock);
            }
        }
        
        // 5. 清空涂布任务的jumboWidth字段
        this.clearCoatingTaskWidths(scheduleId);
        
        log.info("释放物料锁定成功, scheduleId={}", scheduleId);
        return true;
    }
    
    /**
     * 清空排程的所有涂布任务的jumboWidth字段
     */
    private void clearCoatingTaskWidths(Long scheduleId) {
        try {
            log.info("开始清空排程 {} 的所有涂布任务宽度", scheduleId);
            
            // 先查询该排程下有多少涂布任务
            List<ScheduleCoating> beforeClear = scheduleCoatingMapper.selectByScheduleId(scheduleId);
            log.info("清空前涂布任务数: {}", beforeClear.size());
            int taskWithWidth = 0;
            for (ScheduleCoating task : beforeClear) {
                if (task.getJumboWidth() != null) {
                    taskWithWidth++;
                    log.info("  - taskId={}, jumboWidth={}", task.getId(), task.getJumboWidth());
                }
            }
            log.info("需要清空的涂布任务数(有jumboWidth的): {}", taskWithWidth);
            
            // 使用SQL直接更新，一次性将所有jumboWidth设为NULL
            int updateResult = scheduleCoatingMapper.clearJumboWidthByScheduleId(scheduleId);
            log.info("SQL更新受影响行数: {}", updateResult);
            
            // 验证：再次查询确认是否清空成功
            List<ScheduleCoating> afterClear = scheduleCoatingMapper.selectByScheduleId(scheduleId);
            log.info("清空后涂布任务数: {}", afterClear.size());
            int stillHasWidth = 0;
            for (ScheduleCoating task : afterClear) {
                if (task.getJumboWidth() != null) {
                    stillHasWidth++;
                    log.warn("  - 验证失败！taskId={}, jumboWidth仍然为={}", task.getId(), task.getJumboWidth());
                } else {
                    log.info("  - 验证成功！taskId={} jumboWidth已清空", task.getId());
                }
            }
            
            if (stillHasWidth == 0 && taskWithWidth > 0) {
                log.info("✓ 排程 {} 的所有涂布任务宽度清空成功！", scheduleId);
            } else if (taskWithWidth == 0) {
                log.info("排程 {} 没有需要清空的涂布任务宽度", scheduleId);
            } else {
                log.error("✗ 排程 {} 清空失败！仍有 {} 个任务的jumboWidth未被清空", scheduleId, stillHasWidth);
            }
        } catch (Exception e) {
            log.error("清空涂布任务宽度异常", e);
        }
    }
    
    @Override
    public List<Map<String, Object>> getLockedMaterials(Long scheduleId) {
        List<ScheduleMaterialLock> locks = lockMapper.selectByScheduleId(scheduleId);
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (ScheduleMaterialLock lock : locks) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", lock.getId());
            item.put("scheduleId", lock.getScheduleId());
            item.put("orderId", lock.getOrderId());
            item.put("filmStockId", lock.getFilmStockId());
            item.put("filmStockDetailId", lock.getFilmStockDetailId());
            item.put("lockedArea", lock.getLockedArea());
            item.put("lockStatus", lock.getLockStatus());
            item.put("lockedTime", lock.getLockedTime());
            
            // 查询物料信息
            FilmStockDetail detail = filmStockDetailMapper.selectById(lock.getFilmStockDetailId());
            if (detail != null) {
                // FilmStockDetail 没有 materialCode 字段，需要通过 filmStockId 查询 FilmStock
                item.put("batchNo", detail.getBatchNo());
                item.put("rollNo", detail.getRollNo());
            }
            
            result.add(item);
        }
        
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchLockMaterial(List<ScheduleMaterialLockDTO> lockDTOs) {
        int successCount = 0;
        
        for (ScheduleMaterialLockDTO lockDTO : lockDTOs) {
            try {
                lockMaterialForSchedule(lockDTO);
                successCount++;
            } catch (Exception e) {
                log.error("批量锁定失败, scheduleId={}", lockDTO.getScheduleId(), e);
            }
        }
        
        return successCount;
    }
}
