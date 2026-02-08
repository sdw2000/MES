package com.fine.Dao.schedule;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.schedule.ManualSchedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 手动排程Mapper
 */
@Mapper
public interface ManualScheduleMapper extends BaseMapper<ManualSchedule> {
    
    /**
     * 查询待排程订单明细（订单数量 > 已排数量）
     */
    @Select("SELECT " +
            "o.id AS order_id, " +
            "o.order_no, " +
            "soi.id AS order_detail_id, " +
            "soi.material_code, " +
            "soi.material_name, " +
            "soi.width, " +
            "soi.length, " +
            "soi.thickness, " +
            "soi.rolls AS order_qty, " +
            "IFNULL(soi.scheduled_qty, 0) AS scheduled_qty, " +
            "(soi.rolls - IFNULL(soi.scheduled_qty, 0)) AS remaining_qty, " +
            "o.order_date, " +
            "o.delivery_date, " +
            "o.customer, " +
            "o.customer AS customer_name, " +
            "ms.coating_date, " +
            "ms.rewinding_date, " +
            "ms.packaging_date, " +
            "'' AS sample_date, " +
            "IFNULL(cp.total_score, 0) AS priority_score, " +
            "IFNULL(soi.remark, '') AS remark " +
            "FROM sales_order_items soi " +
            "JOIN sales_orders o ON soi.order_id = o.id " +
            "LEFT JOIN order_customer_priority cp ON o.id = cp.order_id " +
            "LEFT JOIN (" +
            "  SELECT order_detail_id, " +
            "         MAX(coating_date) AS coating_date, " +
            "         MAX(rewinding_date) AS rewinding_date, " +
            "         MAX(packaging_date) AS packaging_date " +
            "  FROM manual_schedule " +
            "  GROUP BY order_detail_id" +
            ") ms ON ms.order_detail_id = soi.id " +
            "WHERE o.status != 'COMPLETED' AND o.is_deleted = 0 AND soi.is_deleted = 0 " +
            "ORDER BY " +
            "  CASE WHEN (soi.rolls - IFNULL(soi.scheduled_qty, 0)) > 0 THEN 0 ELSE 1 END, " +
            "  o.delivery_date ASC")
    List<Map<String, Object>> selectPendingOrders();
    
    /**
     * 查询已完成涂布待复卷的订单（按涂布日期排序）
     */
    @Select("SELECT " +
            "ms.id AS schedule_id, " +
            "ms.order_detail_id, " +
            "ms.coating_date, " +
            "ms.coating_area, " +
            "ms.coating_equipment AS equipment_name, " +
            "IFNULL(ms.rewinding_scheduled_area, 0) AS rewinding_scheduled_area, " +
            "(ms.coating_area - IFNULL(ms.rewinding_scheduled_area, 0)) AS remaining_coating_area, " +
            "soi.material_code, " +
            "soi.material_name, " +
            "soi.width, " +
            "soi.length, " +
            "soi.thickness, " +
            "o.order_no, " +
            "o.customer AS customer_name, " +
            "o.delivery_date " +
            "FROM manual_schedule ms " +
            "JOIN sales_order_items soi ON ms.order_detail_id = soi.id " +
            "JOIN sales_orders o ON soi.order_id = o.id " +
            "WHERE ms.status = 'COATING_SCHEDULED' " +
            "AND ms.coating_date IS NOT NULL " +
            "AND (ms.coating_area - IFNULL(ms.rewinding_scheduled_area, 0)) > 0 " +
            "ORDER BY ms.coating_date ASC, o.delivery_date ASC")
    List<Map<String, Object>> selectCoatingCompletedOrders();
    
    /**
     * 查询指定料号规格的库存（先进先出排序）
     */
    @Select("SELECT " +
            "id AS stock_id, " +
            "material_code, " +
            "batch_no, " +
            "width, " +
            "length, " +
            "thickness, " +
            "total_rolls AS available_rolls, " +
            "available_area, " +
            "location, " +
            "prod_date, " +
            "spec_desc " +
            "FROM tape_stock " +
            "WHERE material_code LIKE CONCAT(#{materialCode}, '%') " +
            "AND width = #{width} " +
            "AND thickness = #{thickness} " +
            "AND total_rolls > 0 " +
            "AND (available_area > 0 OR available_area IS NULL) " +
            "AND status = 1 " +
            "ORDER BY prod_date ASC, batch_no ASC")
    List<Map<String, Object>> selectAvailableStock(
            @Param("materialCode") String materialCode,
            @Param("width") Integer width,
            @Param("thickness") Integer thickness);
    
    /**
     * 计算涂布需求：聚合此订单及以后订单的相同料号前缀
     */
    @Select("SELECT " +
            "soi.material_code, " +
            "soi.thickness, " +
            "SUM(soi.rolls - IFNULL(soi.scheduled_qty, 0)) AS total_required_qty, " +
            "SUM((soi.width / 1000.0) * soi.length * (soi.rolls - IFNULL(soi.scheduled_qty, 0))) AS total_required_area " +
            "FROM sales_order_items soi " +
            "JOIN sales_orders o ON soi.order_id = o.id " +
            "WHERE soi.material_code = #{materialCode} " +
            "AND soi.thickness = #{thickness} " +
            "AND o.status != 'COMPLETED' " +
            "AND o.is_deleted = 0 AND soi.is_deleted = 0 " +
            "AND (soi.rolls - IFNULL(soi.scheduled_qty, 0)) > 0 " +
            "GROUP BY soi.material_code, soi.thickness " +
            "HAVING total_required_qty > 0")
    Map<String, Object> calculateCoatingRequirement(
            @Param("orderNo") String orderNo,
            @Param("materialCode") String materialCode,
            @Param("thickness") Integer thickness);

    /**
     * 查询涂布排程列表
     */
    @Select("SELECT " +
            "ms.id, " +
            "ms.id AS schedule_id, " +
            "ms.order_no, " +
            "ms.material_code, " +
            "ms.material_name, " +
            "ms.width, " +
            "ms.thickness, " +
            "ms.coating_area, " +
            "ms.coating_schedule_date, " +
            "ms.coating_equipment, " +
            "ms.coating_date, " +
            "ms.rewinding_date, " +
            "ms.packaging_date, " +
            "ms.status, " +
            "GROUP_CONCAT(DISTINCT so.order_no ORDER BY so.order_no SEPARATOR ',') AS order_nos " +
            "FROM manual_schedule ms " +
            "LEFT JOIN sales_order_items soi ON ms.material_code LIKE CONCAT(soi.material_code, '%') " +
            "LEFT JOIN sales_orders so ON soi.order_id = so.id " +
            "WHERE ms.schedule_type = 'COATING' " +
            "GROUP BY ms.id " +
            "ORDER BY ms.created_at DESC")
    List<Map<String, Object>> selectCoatingSchedules();
    
    /**
     * 更新订单明细的已排程数量
     */
    @Update("UPDATE sales_order_items " +
            "SET scheduled_qty = IFNULL(scheduled_qty, 0) + #{qty} " +
            "WHERE order_id = #{orderId} AND material_code = #{materialCode}")
    int updateScheduledQty(
            @Param("orderId") Long orderId,
            @Param("materialCode") String materialCode,
            @Param("qty") BigDecimal qty);
    
    /**
     * 更新销售订单明细的涂布日期
     */
    @Update("UPDATE manual_schedule " +
            "SET coating_date = #{coatingDate} " +
            "WHERE material_code LIKE CONCAT(#{materialCode}, '%') " +
            "AND thickness = #{thickness} " +
            "AND schedule_type = 'COATING'")
    int updateSalesOrderCoatingDate(
            @Param("materialCode") String materialCode,
            @Param("thickness") Integer thickness,
            @Param("coatingDate") String coatingDate);
    
    /**
     * 更新涂布排程的复卷已排程面积
     */
    @Update("UPDATE manual_schedule " +
            "SET rewinding_scheduled_area = IFNULL(rewinding_scheduled_area, 0) + #{area}, " +
            "status = CASE WHEN (coating_area - IFNULL(rewinding_scheduled_area, 0) - #{area}) <= 0 THEN 'REWINDING_SCHEDULED' ELSE status END " +
            "WHERE id = #{scheduleId}")
    int updateRewindingScheduledArea(@Param("scheduleId") Long scheduleId, @Param("area") BigDecimal area);
}
