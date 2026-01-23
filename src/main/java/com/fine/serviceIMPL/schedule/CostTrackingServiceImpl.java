package com.fine.serviceIMPL.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fine.mapper.schedule.CostTrackingMapper;
import com.fine.model.production.CostTracking;
import com.fine.service.schedule.CostTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

/**
 * 成本追溯 Service 实现
 */
@Service
public class CostTrackingServiceImpl extends ServiceImpl<CostTrackingMapper, CostTracking> implements CostTrackingService {
    
    @Autowired
    private CostTrackingMapper costMapper;
    
    @Override
    public CostTracking initializeCostTracking(Long orderId) {
        CostTracking tracking = new CostTracking();
        tracking.setOrderId(orderId);
        tracking.setMaterialCost(BigDecimal.ZERO);
        tracking.setSlittingCost(BigDecimal.ZERO);
        tracking.setCoatingCost(BigDecimal.ZERO);
        tracking.setLaborCost(BigDecimal.ZERO);
        tracking.setEquipmentCost(BigDecimal.ZERO);
        tracking.setOtherCost(BigDecimal.ZERO);
        tracking.setTotalCost(BigDecimal.ZERO);
        tracking.setStatus("IN_PROGRESS");
        tracking.setCreatedAt(new Date());
        tracking.setUpdatedAt(new Date());
        
        costMapper.insert(tracking);
        return tracking;
    }
    
    @Override
    public void updateMaterialCost(Long orderId, BigDecimal cost) {
        LambdaQueryWrapper<CostTracking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostTracking::getOrderId, orderId);
        CostTracking tracking = costMapper.selectOne(wrapper);
        
        if (tracking == null) {
            tracking = initializeCostTracking(orderId);
        }
        
        tracking.setMaterialCost(cost);
        tracking.setTotalCost(calculateTotalCost(tracking, cost, null, null));
        tracking.setUpdatedAt(new Date());
        costMapper.updateById(tracking);
    }
    
    @Override
    public void updateSlittingCost(Long orderId, BigDecimal cost) {
        LambdaQueryWrapper<CostTracking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostTracking::getOrderId, orderId);
        CostTracking tracking = costMapper.selectOne(wrapper);
        
        if (tracking == null) {
            tracking = initializeCostTracking(orderId);
        }
        
        tracking.setSlittingCost(cost);
        tracking.setTotalCost(calculateTotalCost(tracking, null, cost, null));
        tracking.setUpdatedAt(new Date());
        costMapper.updateById(tracking);
    }
    
    @Override
    public void updateCoatingCost(Long orderId, BigDecimal cost) {
        LambdaQueryWrapper<CostTracking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostTracking::getOrderId, orderId);
        CostTracking tracking = costMapper.selectOne(wrapper);
        
        if (tracking == null) {
            tracking = initializeCostTracking(orderId);
        }
        
        tracking.setCoatingCost(cost);
        tracking.setTotalCost(calculateTotalCost(tracking, null, null, cost));
        tracking.setUpdatedAt(new Date());
        costMapper.updateById(tracking);
    }
    
    @Override
    public void updateLaborCost(Long orderId, BigDecimal cost) {
        LambdaQueryWrapper<CostTracking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostTracking::getOrderId, orderId);
        CostTracking tracking = costMapper.selectOne(wrapper);
        
        if (tracking == null) {
            tracking = initializeCostTracking(orderId);
        }
        
        tracking.setLaborCost(cost);
        tracking.setTotalCost(calculateTotalCost(tracking, null, null, null));
        tracking.setUpdatedAt(new Date());
        costMapper.updateById(tracking);
    }
    
    @Override
    public void completeCostTracking(Long orderId, Integer finishedQty) {
        LambdaQueryWrapper<CostTracking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostTracking::getOrderId, orderId);
        CostTracking tracking = costMapper.selectOne(wrapper);
        
        if (tracking == null) {
            throw new RuntimeException("成本追溯不存在：" + orderId);
        }
        
        tracking.setFinishedQty(finishedQty);
        BigDecimal unitCost = tracking.getTotalCost().divide(
                new BigDecimal(finishedQty), 4, BigDecimal.ROUND_HALF_UP
        );
        tracking.setUnitCost(unitCost);
        tracking.setStatus("COMPLETED");
        tracking.setUpdatedAt(new Date());
        
        costMapper.updateById(tracking);
    }
    
    @Override
    public IPage<Map<String, Object>> getCostTrackingPage(Integer pageNum, Integer pageSize, String orderNo) {
        LambdaQueryWrapper<CostTracking> wrapper = new LambdaQueryWrapper<>();
        
        if (orderNo != null && !orderNo.isEmpty()) {
            wrapper.like(CostTracking::getOrderNo, orderNo);
        }
        
        wrapper.orderByDesc(CostTracking::getCreatedAt);
        
        IPage<CostTracking> page = this.page(new Page<>(pageNum, pageSize), wrapper);
        
        IPage<Map<String, Object>> resultPage = new Page<>(pageNum, pageSize);
        resultPage.setTotal(page.getTotal());
        
        List<Map<String, Object>> records = new ArrayList<>();
        for (CostTracking tracking : page.getRecords()) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", tracking.getId());
            map.put("orderNo", tracking.getOrderNo());
            map.put("materialCost", tracking.getMaterialCost());
            map.put("slittingCost", tracking.getSlittingCost());
            map.put("coatingCost", tracking.getCoatingCost());
            map.put("laborCost", tracking.getLaborCost());
            map.put("totalCost", tracking.getTotalCost());
            map.put("finishedQty", tracking.getFinishedQty());
            map.put("unitCost", tracking.getUnitCost());
            map.put("status", tracking.getStatus());
            records.add(map);
        }
        resultPage.setRecords(records);
        
        return resultPage;
    }
    
    @Override
    public Map<String, Object> getOrderCost(Long orderId) {
        LambdaQueryWrapper<CostTracking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CostTracking::getOrderId, orderId);
        CostTracking tracking = costMapper.selectOne(wrapper);
        
        if (tracking == null) {
            throw new RuntimeException("成本追溯不存在：" + orderId);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("orderId", orderId);
        result.put("orderNo", tracking.getOrderNo());
        result.put("materialCost", tracking.getMaterialCost());
        result.put("slittingCost", tracking.getSlittingCost());
        result.put("coatingCost", tracking.getCoatingCost());
        result.put("laborCost", tracking.getLaborCost());
        result.put("equipmentCost", tracking.getEquipmentCost());
        result.put("otherCost", tracking.getOtherCost());
        result.put("totalCost", tracking.getTotalCost());
        result.put("finishedQty", tracking.getFinishedQty());
        result.put("unitCost", tracking.getUnitCost());
        result.put("status", tracking.getStatus());
        
        return result;
    }
    
    private BigDecimal calculateTotalCost(CostTracking tracking, BigDecimal materialCost, BigDecimal slittingCost, BigDecimal coatingCost) {
        BigDecimal material = materialCost != null ? materialCost : tracking.getMaterialCost();
        BigDecimal slitting = slittingCost != null ? slittingCost : tracking.getSlittingCost();
        BigDecimal coating = coatingCost != null ? coatingCost : tracking.getCoatingCost();
        BigDecimal labor = tracking.getLaborCost();
        BigDecimal equipment = tracking.getEquipmentCost();
        BigDecimal other = tracking.getOtherCost();
        
        return material.add(slitting).add(coating).add(labor).add(equipment).add(other);
    }
}
