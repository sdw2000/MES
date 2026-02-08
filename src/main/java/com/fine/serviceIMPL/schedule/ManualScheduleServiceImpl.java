package com.fine.serviceIMPL.schedule;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.Dao.schedule.ManualScheduleMapper;
import com.fine.modle.schedule.ManualSchedule;
import com.fine.modle.stock.TapeStock;
import com.fine.service.schedule.ManualScheduleService;
import com.fine.service.stock.TapeStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * 手动排程服务实现
 */
@Service
public class ManualScheduleServiceImpl extends ServiceImpl<ManualScheduleMapper, ManualSchedule> implements ManualScheduleService {
    
    @Autowired
    private ManualScheduleMapper scheduleMapper;
    
    @Autowired
    private TapeStockService tapeStockService;
    
    @Override
    public List<Map<String, Object>> getPendingOrders() {
        return scheduleMapper.selectPendingOrders();
    }
    
    @Override
    public List<Map<String, Object>> getCoatingCompletedOrders() {
        return scheduleMapper.selectCoatingCompletedOrders();
    }
    
    @Override
    public Map<String, Object> matchStock(String materialCode, Integer width, Integer thickness, Integer requiredQty) {
        // 查询可用库存（先进先出排序）
        List<Map<String, Object>> stockList = scheduleMapper.selectAvailableStock(materialCode, width, thickness);
        
        Map<String, Object> result = new HashMap<>();
        result.put("stockList", stockList);
        
        // 计算总可用数量
        int totalAvailable = stockList.stream()
                .mapToInt(s -> ((Number) s.get("available_rolls")).intValue())
                .sum();
        
        result.put("totalAvailable", totalAvailable);
        result.put("requiredQty", requiredQty);
        result.put("isSufficient", totalAvailable >= requiredQty);
        result.put("shortage", Math.max(0, requiredQty - totalAvailable));
        
        return result;
    }
    
    @Override
    public Map<String, Object> calculateCoatingRequirement(String orderNo, String materialCode) {
        // 直接查询，由于涂布需求是按厚度分组的，所以需要先查出这个订单的厚度
        // 简化做法：不按厚度过滤，返回所有相同前缀的聚合需求
        
        // 先查询该订单的厚度
        List<Map<String, Object>> orders = scheduleMapper.selectPendingOrders();
        Integer thickness = null;
        
        for (Map<String, Object> order : orders) {
            if (materialCode.equals(order.get("material_code"))) {
                Object thicknessObj = order.get("thickness");
                if (thicknessObj != null) {
                    thickness = ((Number) thicknessObj).intValue();
                    break;
                }
            }
        }
        
        if (thickness == null) {
            thickness = 100; // 默认值
        }
        
        Map<String, Object> requirement = scheduleMapper.calculateCoatingRequirement(orderNo, materialCode, thickness);
        
        if (requirement == null) {
            requirement = new HashMap<>();
            requirement.put("total_required_qty", 0);
            requirement.put("total_required_area", 0.0);
        }
        
        return requirement;
    }

    @Override
    public List<Map<String, Object>> getCoatingSchedules() {
        return scheduleMapper.selectCoatingSchedules();
    }
    
    @Override
    @Transactional
    public boolean createSchedule(ManualSchedule schedule) {
        schedule.setStatus("PENDING");
        schedule.setCreatedAt(java.time.LocalDateTime.now());
        return this.save(schedule);
    }
    
    @Override
    @Transactional
    public Long createRewindingSchedule(Long scheduleId, List<Map<String, Object>> stockAllocations) {
        ManualSchedule schedule = this.getById(scheduleId);
        if (schedule == null) {
            throw new RuntimeException("排程记录不存在");
        }
        
        // 验证库存充足性
        for (Map<String, Object> allocation : stockAllocations) {
            Long stockId = ((Number) allocation.get("stockId")).longValue();
            Integer qty = ((Number) allocation.get("qty")).intValue();
            
            TapeStock stock = tapeStockService.getStockById(stockId);
            if (stock == null || stock.getTotalRolls() < qty) {
                throw new RuntimeException("库存不足或已被占用");
            }
            
            // TODO: 实际库存锁定操作，应调用库存锁定服务
            // MaterialLockService.lockInventory(stockId, qty)
        }
        
        // 更新手动排程状态
        schedule.setStatus("REWINDING_SCHEDULED");
        schedule.setScheduleType("STOCK");
        this.updateById(schedule);
        
        // TODO: 创建复卷排程任务（根据实际业务逻辑）
        // RewindingScheduleService.createTask(schedule, stockAllocations)
        
        // 返回排程ID（待实际创建复卷记录后返回真实ID）
        return scheduleId;
    }
    
    @Override
    @Transactional
    public Long createCoatingSchedule(Long scheduleId, Double coatingArea, String coatingDate, String rewindingDate, String packagingDate, String equipmentId) {
        ManualSchedule schedule = this.getById(scheduleId);
        if (schedule == null) {
            throw new RuntimeException("排程记录不存在");
        }
        
        // 更新涂布信息
        schedule.setCoatingArea(BigDecimal.valueOf(coatingArea));
        if (coatingDate != null && !coatingDate.isEmpty()) {
            String datePart = coatingDate.length() > 10 ? coatingDate.substring(0, 10) : coatingDate;
            schedule.setCoatingScheduleDate(java.time.LocalDate.parse(datePart));
        }
        if (coatingDate != null && !coatingDate.isEmpty()) {
            String datePart = coatingDate.length() > 10 ? coatingDate.substring(0, 10) : coatingDate;
            schedule.setCoatingDate(java.time.LocalDate.parse(datePart));
        }
        if (rewindingDate != null && !rewindingDate.isEmpty()) {
            String datePart = rewindingDate.length() > 10 ? rewindingDate.substring(0, 10) : rewindingDate;
            schedule.setRewindingDate(java.time.LocalDate.parse(datePart));
        }
        if (packagingDate != null && !packagingDate.isEmpty()) {
            String datePart = packagingDate.length() > 10 ? packagingDate.substring(0, 10) : packagingDate;
            schedule.setPackagingDate(java.time.LocalDate.parse(datePart));
        }
        if (equipmentId != null && !equipmentId.isEmpty()) {
            schedule.setCoatingEquipment(equipmentId);
        }
        schedule.setStatus("COATING_SCHEDULED");
        schedule.setScheduleType("COATING");
        
        this.updateById(schedule);
        
            // 回写涂布日期到关联的销售订单（以计划时间为准）
            try {
                String planDate = null;
                if (schedule.getCoatingScheduleDate() != null) {
                    planDate = schedule.getCoatingScheduleDate().toString();
                } else if (coatingDate != null && !coatingDate.isEmpty()) {
                    planDate = coatingDate.length() > 10 ? coatingDate.substring(0, 10) : coatingDate;
                }
                if (planDate != null && !planDate.isEmpty()) {
                    // 查询与此涂布排程关联的所有订单（通过料号前缀+厚度匹配）
                    scheduleMapper.updateSalesOrderCoatingDate(
                            schedule.getMaterialCode(),
                            schedule.getThickness(),
                            planDate
                    );
                }
            } catch (Exception e) {
                // 日志记录但不中断主流程
                System.err.println("回写涂布日期失败: " + e.getMessage());
            }
        
            // 返回涂布排程ID
            return scheduleId;
    }
    
    @Override
    @Transactional
    public boolean confirmSchedule(String orderNo, String materialCode, Integer scheduleQty) {
        // 根据订单号+料号查找该订单的order_id
        List<Map<String, Object>> orders = scheduleMapper.selectPendingOrders();
        Long orderId = null;
        
        for (Map<String, Object> order : orders) {
            if (order.get("order_no").equals(orderNo) && order.get("material_code").equals(materialCode)) {
                Object orderIdObj = order.get("order_id");
                if (orderIdObj instanceof Number) {
                    orderId = ((Number) orderIdObj).longValue();
                } else if (orderIdObj instanceof String) {
                    orderId = Long.parseLong((String) orderIdObj);
                }
                break;
            }
        }
        
        if (orderId == null) {
            throw new RuntimeException("未找到对应的订单信息");
        }
        
        // 更新订单明细的已排程数量
        try {
            int result = this.baseMapper.updateScheduledQty(orderId, materialCode, new BigDecimal(scheduleQty));
            if (result == 0) {
                throw new RuntimeException("未找到对应的订单明细记录");
            }
            return true;
        } catch (Exception e) {
            throw new RuntimeException("更新已排程数量失败: " + e.getMessage());
        }
    }
}
