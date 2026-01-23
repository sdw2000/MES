package com.fine.service.schedule;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.model.schedule.OrderMaterialLock;
import java.util.List;
import java.util.Map;

/**
 * 物料锁定服务接口
 */
public interface MaterialLockService {
    
    /**
     * 获取订单物料锁定分页列表
     */
    IPage<OrderMaterialLock> getOrderMaterialLockPage(Map<String, Object> params);
    
    /**
     * 锁定订单物料
     */
    OrderMaterialLock lockOrderMaterial(OrderMaterialLock lockData);
    
    /**
     * 释放订单物料锁定
     */
    void releaseOrderMaterialLock(Long lockId, String operator);
    
    /**
     * 批量释放订单物料锁定
     */
    void batchReleaseOrderMaterialLocks(List<Long> lockIds, String operator);
    
    /**
     * 触发领料
     */
    void triggerMaterialPicking(Long lockId, String operator);
    
    /**
     * 获取物料的多单共用情况
     */
    Map<String, Object> getMaterialSharedLocks(String qrCode);
    
    /**
     * 获取指定订单的物料锁定详情
     */
    List<OrderMaterialLock> getOrderMaterialLocksByOrderId(Long orderId);
    
    /**
     * 检查物料是否已锁定
     */
    boolean isMaterialLocked(Long orderId, String materialCode);
    
    /**
     * 锁定物料（支持多单共用、一单多卷）
     * 
     * @param orderId 订单ID
     * @param orderNo 订单编号
     * @param orderItemId 订单明细ID
     * @param materialCode 物料编号
     * @param requiredQty 需求数量
     * @param customerPriority 客户优先级得分
     * @param operator 操作人
     * @return 锁定记录列表（可能锁定多卷）
     */
    List<OrderMaterialLock> lockMaterial(Long orderId, String orderNo, Long orderItemId,
                                         String materialCode, Integer requiredQty,
                                         java.math.BigDecimal customerPriority, String operator);
    
    /**
     * 查询可用库存（未锁定或优先级更低的物料）
     * 
     * @param materialCode 物料编号
     * @param materialSpec 物料规格
     * @param currentPriority 当前订单优先级（用于抢占低优先级锁定）
     * @return 可用库存列表
     */
    List<Map<String, Object>> queryAvailableStock(String materialCode, String materialSpec, 
                                                   java.math.BigDecimal currentPriority);
    
    /**
     * 释放订单的所有物料锁定
     * 
     * @param orderId 订单ID
     * @param operator 操作人
     * @return 释放数量
     */
    int releaseOrderLocks(Long orderId, String operator);
    
    /**
     * 领料触发（更新锁定状态）
     * 
     * @param lockId 锁定ID
     * @param operator 操作人
     * @return 是否成功
     */
    boolean issueMaterial(Long lockId, String operator);
    
    /**
     * 查询订单的锁定物料清单
     * 
     * @param orderId 订单ID
     * @return 锁定物料列表
     */
    List<OrderMaterialLock> getOrderLocks(Long orderId);
    
    /**
     * 生成锁定单号
     */
    String generateLockNo();
    
    /**
     * 统计满足条件的物料锁定数量
     */
    Long selectCount(Wrapper<OrderMaterialLock> queryWrapper);
}

