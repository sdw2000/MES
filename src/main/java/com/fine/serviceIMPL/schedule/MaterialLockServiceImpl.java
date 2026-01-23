package com.fine.serviceIMPL.schedule;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fine.mapper.schedule.OrderMaterialLockMapper;
import com.fine.mapper.schedule.MaterialLockShortageMapper;
import com.fine.model.schedule.OrderMaterialLock;
import com.fine.model.production.MaterialLockShortage;
import com.fine.service.schedule.MaterialLockService;
import com.fine.Dao.stock.TapeStockMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 物料锁定服务实现
 * 
 * 核心功能：
 * 1. 支持多单共用同一物料（按优先级分配）
 * 2. 支持一单锁定多卷物料（组合满足需求）
 * 3. 高优先级可抢占低优先级的锁定
 */
@Service
public class MaterialLockServiceImpl implements MaterialLockService {
    
    @Autowired
    private OrderMaterialLockMapper lockMapper;
    
    @Autowired(required = false)
    private TapeStockMapper tapeStockMapper;
    
    @Autowired(required = false)
    private MaterialLockShortageMapper shortageMapper;
    
    @Override
    @Transactional
    public List<OrderMaterialLock> lockMaterial(Long orderId, String orderNo, Long orderItemId,
                                                String materialCode, Integer requiredQty,
                                                BigDecimal customerPriority, String operator) {
        
        List<OrderMaterialLock> lockRecords = new ArrayList<>();
        
        // 1. 查询可用库存（优先未锁定，其次低优先级已锁定）
        List<Map<String, Object>> availableStocks = queryAvailableStock(materialCode, null, customerPriority);
        
        if (availableStocks.isEmpty()) {
            System.out.println("无可用库存：" + materialCode);
            return lockRecords; // 无库存可锁定
        }
        
        // 2. 按库存逐卷锁定，直到满足需求数量
        int remainingQty = requiredQty;
        String customerName = "客户" + orderId; // 实际应从订单查询
        
        for (Map<String, Object> stock : availableStocks) {
            if (remainingQty <= 0) {
                break;
            }
            
            Long stockId = ((Number) stock.get("id")).longValue();
            String qrCode = (String) stock.get("qr_code");
            Integer availableQty = ((Number) stock.get("available_qty")).intValue();
            String status = (String) stock.get("status");
            
            // 计算本次锁定数量
            int lockQty = Math.min(remainingQty, availableQty);
            
            // 3. 如果该库存已被低优先级锁定，先释放低优先级锁定
            if ("LOCKED_LOW_PRIORITY".equals(status)) {
                releaseLowPriorityLocks(stockId, customerPriority);
            }
            
            // 4. 创建锁定记录
            OrderMaterialLock lock = new OrderMaterialLock();
            lock.setLockNo(generateLockNo());
            lock.setOrderId(orderId);
            lock.setOrderNo(orderNo);
            lock.setOrderItemId(orderItemId);
            lock.setCustomerName(customerName);
            lock.setCustomerPriority(customerPriority);
            lock.setMaterialCode(materialCode);
            lock.setStockId(stockId);
            lock.setStockQrCode(qrCode);
            lock.setLockedQty(lockQty);
            lock.setSharedOrderCount(1); // 初始为1
            lock.setLockStatus("LOCKED");
            lock.setIssueStatus("PENDING");
            lock.setLockedBy(operator);
            lock.setLockedAt(new Date());
            
            lockMapper.insert(lock);
            lockRecords.add(lock);
            
            remainingQty -= lockQty;
            
            System.out.println("锁定物料：" + qrCode + " 数量：" + lockQty + " 剩余需求：" + remainingQty);
        }
        
        // 5. 如果仍有未满足的数量，记录缺口
        if (remainingQty > 0) {
            System.out.println("⚠️ 物料不足，缺口数量：" + remainingQty);
            // 创建物料缺口记录，触发分切排程
            if (shortageMapper != null) {
                MaterialLockShortage shortage = new MaterialLockShortage();
                shortage.setOrderId(orderId);
                shortage.setOrderNo(orderNo);
                shortage.setMaterialCode(materialCode);
                shortage.setShortageQty(remainingQty);
                shortage.setCustomerPriority(customerPriority);
                shortage.setStatus("PENDING");
                shortage.setCreatedAt(new Date());
                shortage.setUpdatedAt(new Date());
                shortage.setRemark("自动创建：物料锁定时缺口" + remainingQty + "卷");
                shortageMapper.insert(shortage);
                System.out.println("✅ 物料缺口记录已创建，缺口ID：" + shortage.getId());
            }
        }
        
        return lockRecords;
    }
    
    @Override
    public List<Map<String, Object>> queryAvailableStock(String materialCode, String materialSpec,
                                                          BigDecimal currentPriority) {
        List<Map<String, Object>> result = new ArrayList<>();
        
        // 如果没有TapeStockMapper（可能还未实现），返回空列表
        if (tapeStockMapper == null) {
            System.out.println("⚠️ TapeStockMapper未注入，无法查询库存");
            return result;
        }
        
        // 1. 查询该料号的所有库存
        QueryWrapper<com.fine.modle.stock.TapeStock> wrapper = new QueryWrapper<>();
        wrapper.eq("material_code", materialCode);
        wrapper.eq("is_deleted", 0);
        wrapper.orderByAsc("created_at"); // 先进先出
        
        List<com.fine.modle.stock.TapeStock> stocks = tapeStockMapper.selectList(wrapper);
        
        for (com.fine.modle.stock.TapeStock stock : stocks) {
            // 2. 查询该库存的所有锁定记录
            List<OrderMaterialLock> locks = lockMapper.selectLockedByQrCode(stock.getQrCode());
            
            int totalLocked = locks.stream()
                .mapToInt(OrderMaterialLock::getLockedQty)
                .sum();
            
            int availableQty = stock.getTotalRolls() - totalLocked;
            
            if (availableQty > 0) {
                // 未锁定部分可直接使用
                Map<String, Object> item = new HashMap<>();
                item.put("id", stock.getId());
                item.put("qr_code", stock.getQrCode());
                item.put("available_qty", availableQty);
                item.put("status", "AVAILABLE");
                result.add(item);
            }
            
            // 3. 检查是否有低优先级锁定（可抢占）
            if (currentPriority != null) {
                for (OrderMaterialLock lock : locks) {
                    if (lock.getCustomerPriority().compareTo(currentPriority) < 0) {
                        // 发现低优先级锁定，可抢占
                        Map<String, Object> item = new HashMap<>();
                        item.put("id", stock.getId());
                        item.put("qr_code", stock.getQrCode());
                        item.put("available_qty", lock.getLockedQty());
                        item.put("status", "LOCKED_LOW_PRIORITY");
                        item.put("low_priority_lock_id", lock.getId());
                        result.add(item);
                    }
                }
            }
        }
        
        return result;
    }
    
    @Override
    @Transactional
    public int releaseOrderLocks(Long orderId, String operator) {
        return lockMapper.releaseOrderLocks(orderId);
    }
    
    @Override
    @Transactional
    public boolean issueMaterial(Long lockId, String operator) {
        int rows = lockMapper.issueMaterial(lockId);
        return rows > 0;
    }
    
    @Override
    public List<OrderMaterialLock> getOrderLocks(Long orderId) {
        return lockMapper.selectLockedByOrderId(orderId);
    }
    
    @Override
    public String generateLockNo() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String dateStr = sdf.format(new Date());
        
        // 使用时间戳后4位作为序号（简化实现）
        String seq = String.format("%04d", (int)(System.currentTimeMillis() % 10000));
        
        return "LOCK-" + dateStr + "-" + seq;
    }
    
    /**
     * 释放低优先级锁定（当高优先级订单需要抢占时）
     */
    @Transactional
    private void releaseLowPriorityLocks(Long stockId, BigDecimal higherPriority) {
        List<OrderMaterialLock> locks = lockMapper.selectList(
            new QueryWrapper<OrderMaterialLock>()
                .eq("stock_id", stockId)
                .eq("lock_status", "LOCKED")
                .lt("customer_priority", higherPriority)
        );
        
        for (OrderMaterialLock lock : locks) {
            System.out.println("释放低优先级锁定：锁定ID=" + lock.getId() + " 订单=" + lock.getOrderNo());
            lockMapper.releaseLock(lock.getId());
            
            // TODO: 将被释放的订单加入待涂布池（如果需要）
        }
    }

    @Override
    public com.baomidou.mybatisplus.core.metadata.IPage<OrderMaterialLock> getOrderMaterialLockPage(Map<String, Object> params) {
        Integer pageNum = (Integer) params.getOrDefault("pageNum", 1);
        Integer pageSize = (Integer) params.getOrDefault("pageSize", 20);
        String orderNo = (String) params.get("orderNo");
        String customerName = (String) params.get("customerName");
        String qrCode = (String) params.get("qrCode");
        String lockStatus = (String) params.get("lockStatus");
        
        QueryWrapper<OrderMaterialLock> wrapper = new QueryWrapper<>();
        if (orderNo != null && !orderNo.isEmpty()) {
            wrapper.like("order_no", orderNo);
        }
        if (customerName != null && !customerName.isEmpty()) {
            wrapper.like("customer_name", customerName);
        }
        if (qrCode != null && !qrCode.isEmpty()) {
            wrapper.like("stock_qr_code", qrCode);
        }
        if (lockStatus != null && !lockStatus.isEmpty()) {
            wrapper.eq("lock_status", lockStatus);
        }
        wrapper.orderByDesc("locked_at");
        
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<OrderMaterialLock> page = 
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        return lockMapper.selectPage(page, wrapper);
    }

    @Override
    public OrderMaterialLock lockOrderMaterial(OrderMaterialLock lockData) {
        lockData.setLockNo(generateLockNo());
        lockData.setLockedAt(new Date());
        lockData.setLockStatus("LOCKED");
        lockMapper.insert(lockData);
        return lockData;
    }

    @Override
    public void releaseOrderMaterialLock(Long lockId, String operator) {
        OrderMaterialLock lock = lockMapper.selectById(lockId);
        if (lock != null) {
            lock.setLockStatus("RELEASED");
            lock.setReleasedBy(operator);
            lock.setReleasedAt(new Date());
            lockMapper.updateById(lock);
        }
    }

    @Override
    public void batchReleaseOrderMaterialLocks(List<Long> lockIds, String operator) {
        for (Long lockId : lockIds) {
            releaseOrderMaterialLock(lockId, operator);
        }
    }

    @Override
    public void triggerMaterialPicking(Long lockId, String operator) {
        OrderMaterialLock lock = lockMapper.selectById(lockId);
        if (lock != null) {
            lock.setIssueStatus("PICKING");
            lock.setPickedBy(operator);
            lock.setPickedAt(new Date());
            lockMapper.updateById(lock);
        }
    }

    @Override
    public Map<String, Object> getMaterialSharedLocks(String qrCode) {
        Map<String, Object> result = new HashMap<>();
        List<OrderMaterialLock> locks = lockMapper.selectList(
            new QueryWrapper<OrderMaterialLock>()
                .eq("stock_qr_code", qrCode)
                .eq("lock_status", "LOCKED")
        );
        result.put("qrCode", qrCode);
        result.put("lockCount", locks.size());
        result.put("locks", locks);
        return result;
    }

    @Override
    public List<OrderMaterialLock> getOrderMaterialLocksByOrderId(Long orderId) {
        return lockMapper.selectList(
            new QueryWrapper<OrderMaterialLock>()
                .eq("order_id", orderId)
        );
    }

    @Override
    public boolean isMaterialLocked(Long orderId, String materialCode) {
        Long count = lockMapper.selectCount(
            new QueryWrapper<OrderMaterialLock>()
                .eq("order_id", orderId)
                .eq("material_code", materialCode)
                .eq("lock_status", "LOCKED")
        );
        return count != null && count > 0;
    }

    @Override
    public Long selectCount(com.baomidou.mybatisplus.core.conditions.Wrapper<OrderMaterialLock> queryWrapper) {
        return lockMapper.selectCount(queryWrapper);
    }
}
