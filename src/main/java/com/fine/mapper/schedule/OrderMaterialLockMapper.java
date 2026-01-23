package com.fine.mapper.schedule;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.schedule.OrderMaterialLock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 订单物料锁定Mapper
 */
@Mapper
public interface OrderMaterialLockMapper extends BaseMapper<OrderMaterialLock> {
    
    /**
     * 查询订单的所有锁定物料
     */
    @Select("SELECT * FROM order_material_lock WHERE order_id = #{orderId} AND lock_status = 'LOCKED'")
    List<OrderMaterialLock> selectLockedByOrderId(@Param("orderId") Long orderId);
    
    /**
     * 查询物料的所有锁定记录（按优先级降序）
     */
    @Select("SELECT * FROM order_material_lock WHERE stock_qr_code = #{qrCode} AND lock_status = 'LOCKED' " +
            "ORDER BY customer_priority DESC")
    List<OrderMaterialLock> selectLockedByQrCode(@Param("qrCode") String qrCode);
    
    /**
     * 释放锁定
     */
    @Update("UPDATE order_material_lock SET lock_status = 'RELEASED', released_at = NOW() " +
            "WHERE id = #{id}")
    int releaseLock(@Param("id") Long id);
    
    /**
     * 批量释放订单的所有锁定
     */
    @Update("UPDATE order_material_lock SET lock_status = 'RELEASED', released_at = NOW() " +
            "WHERE order_id = #{orderId} AND lock_status = 'LOCKED'")
    int releaseOrderLocks(@Param("orderId") Long orderId);
    
    /**
     * 领料
     */
    @Update("UPDATE order_material_lock SET issue_status = 'ISSUED', issued_at = NOW() " +
            "WHERE id = #{id}")
    int issueMaterial(@Param("id") Long id);
}
