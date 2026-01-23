package com.fine.Dao.stock;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.stock.ScheduleMaterialLock;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * 排程物料锁定 Mapper
 */
@Mapper
public interface ScheduleMaterialLockMapper extends BaseMapper<ScheduleMaterialLock> {
    
    /**
     * 查询排程的所有锁定记录
     */
    @Select("SELECT * FROM schedule_material_lock " +
            "WHERE schedule_id = #{scheduleId} AND lock_status IN ('锁定中', '已领料') " +
            "ORDER BY locked_time DESC")
    List<ScheduleMaterialLock> selectByScheduleId(@Param("scheduleId") Long scheduleId);
    
    /**
     * 查询某物料的被锁定情况（被哪些订单锁定了）
     */
    @Select("SELECT * FROM schedule_material_lock " +
            "WHERE tape_stock_id = #{tapeStockId} AND lock_status IN ('锁定中', '已领料') " +
            "ORDER BY locked_time ASC")
    List<ScheduleMaterialLock> selectByTapeStockId(@Param("tapeStockId") Long tapeStockId);
    
    /**
     * 查询订单的锁定记录
     */
    @Select("SELECT * FROM schedule_material_lock " +
            "WHERE order_id = #{orderId} AND lock_status IN ('锁定中', '已领料') " +
            "ORDER BY locked_time DESC")
    List<ScheduleMaterialLock> selectByOrderId(@Param("orderId") Long orderId);
    
    /**
     * 查询排程中某订单的锁定记录
     */
    @Select("SELECT * FROM schedule_material_lock " +
            "WHERE schedule_id = #{scheduleId} AND order_id = #{orderId} " +
            "ORDER BY locked_time DESC")
    List<ScheduleMaterialLock> selectByScheduleAndOrder(@Param("scheduleId") Long scheduleId,
                                                        @Param("orderId") Long orderId);
    
    /**
     * 更新锁定状态（乐观锁）
     */
    @Update("UPDATE schedule_material_lock " +
            "SET lock_status = #{newStatus}, " +
            "    allocated_time = CASE WHEN #{newStatus} = '已领料' THEN NOW() ELSE allocated_time END, " +
            "    version = version + 1 " +
            "WHERE id = #{id} AND version = #{version}")
    int updateStatus(@Param("id") Long id,
                     @Param("newStatus") String newStatus,
                     @Param("version") Integer version);
    
    /**
     * 批量更新锁定状态
     */
    @Update("<script>" +
            "UPDATE schedule_material_lock " +
            "SET lock_status = #{newStatus}, version = version + 1 " +
            "WHERE schedule_id = #{scheduleId} AND lock_status = #{oldStatus} " +
            "</script>")
    int updateStatusBySchedule(@Param("scheduleId") Long scheduleId,
                              @Param("oldStatus") String oldStatus,
                              @Param("newStatus") String newStatus);
    
    /**
     * 查询排程的锁定统计
     */
    @Select("SELECT lock_status as status, COUNT(*) as count " +
            "FROM schedule_material_lock " +
            "WHERE schedule_id = #{scheduleId} " +
            "GROUP BY lock_status")
    List<Map<String, Object>> selectStatusCount(@Param("scheduleId") Long scheduleId);
    
    /**
     * 查询排程的总锁定面积
     */
    @Select("SELECT COALESCE(SUM(locked_area), 0) as totalArea " +
            "FROM schedule_material_lock " +
            "WHERE schedule_id = #{scheduleId} AND lock_status IN ('锁定中', '已领料')")
    java.util.Map<String, Object> selectTotalLockedArea(@Param("scheduleId") Long scheduleId);
}
