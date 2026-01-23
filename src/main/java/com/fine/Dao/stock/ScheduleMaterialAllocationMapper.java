package com.fine.Dao.stock;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.stock.ScheduleMaterialAllocation;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 排程物料分配 Mapper
 */
@Mapper
public interface ScheduleMaterialAllocationMapper extends BaseMapper<ScheduleMaterialAllocation> {
    
    /**
     * 查询排程的所有分配记录
     */
    @Select("SELECT * FROM schedule_material_allocation " +
            "WHERE schedule_id = #{scheduleId} " +
            "ORDER BY allocated_time DESC")
    List<ScheduleMaterialAllocation> selectByScheduleId(@Param("scheduleId") Long scheduleId);
    
    /**
     * 查询排程中某订单的分配情况
     */
    @Select("SELECT * FROM schedule_material_allocation " +
            "WHERE schedule_id = #{scheduleId} AND order_id = #{orderId}")
    ScheduleMaterialAllocation selectByScheduleAndOrder(@Param("scheduleId") Long scheduleId,
                                                        @Param("orderId") Long orderId);
    
    /**
     * 查询需要涂布排程的订单
     */
    @Select("SELECT * FROM schedule_material_allocation " +
            "WHERE schedule_id = #{scheduleId} AND need_coating = 1 " +
            "ORDER BY allocated_time ASC")
    List<ScheduleMaterialAllocation> selectNeedCoating(@Param("scheduleId") Long scheduleId);
    
    /**
     * 更新分配状态
     */
    @Update("UPDATE schedule_material_allocation " +
            "SET allocation_status = #{status}, " +
            "    allocated_area = #{allocatedArea}, " +
            "    shortage_area = #{shortageArea} " +
            "WHERE schedule_id = #{scheduleId} AND order_id = #{orderId}")
    int updateAllocation(@Param("scheduleId") Long scheduleId,
                        @Param("orderId") Long orderId,
                        @Param("status") String status,
                        @Param("allocatedArea") java.math.BigDecimal allocatedArea,
                        @Param("shortageArea") java.math.BigDecimal shortageArea);
    
    /**
     * 关联涂布排程
     */
    @Update("UPDATE schedule_material_allocation " +
            "SET coating_schedule_id = #{coatingScheduleId}, " +
            "    need_coating = 1 " +
            "WHERE schedule_id = #{scheduleId} AND order_id = #{orderId}")
    int updateCoatingScheduleId(@Param("scheduleId") Long scheduleId,
                               @Param("orderId") Long orderId,
                               @Param("coatingScheduleId") Long coatingScheduleId);
    
    /**
     * 查询完全满足的订单数
     */
    @Select("SELECT COUNT(*) FROM schedule_material_allocation " +
            "WHERE schedule_id = #{scheduleId} AND allocation_status = '完全满足'")
    int countFullyMet(@Param("scheduleId") Long scheduleId);
    
    /**
     * 查询部分满足的订单数
     */
    @Select("SELECT COUNT(*) FROM schedule_material_allocation " +
            "WHERE schedule_id = #{scheduleId} AND allocation_status = '部分满足'")
    int countPartiallyMet(@Param("scheduleId") Long scheduleId);
    
    /**
     * 查询未满足的订单数
     */
    @Select("SELECT COUNT(*) FROM schedule_material_allocation " +
            "WHERE schedule_id = #{scheduleId} AND allocation_status = '未满足'")
    int countNotMet(@Param("scheduleId") Long scheduleId);
}
