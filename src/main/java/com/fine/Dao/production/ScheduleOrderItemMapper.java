package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.model.production.ScheduleOrderItem;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

/**
 * 排程订单关联Mapper
 */
@Mapper
public interface ScheduleOrderItemMapper extends BaseMapper<ScheduleOrderItem> {
    
    /**
     * 根据排程ID查询订单关联
     */
    @Select("SELECT * FROM schedule_order_item WHERE schedule_id = #{scheduleId} ORDER BY priority ASC")
    List<ScheduleOrderItem> selectByScheduleId(@Param("scheduleId") Long scheduleId);
    
    /**
     * 根据ID查询
     */
    @Select("SELECT * FROM schedule_order_item WHERE id = #{id}")
    ScheduleOrderItem selectById(@Param("id") Long id);
    
    /**
     * 批量插入
     */
    @Insert("<script>" +
            "INSERT INTO schedule_order_item (schedule_id, order_id, order_item_id, order_no, customer, " +
            "customer_level, material_code, material_name, color_code, thickness, width, length, " +
            "order_qty, schedule_qty, delivery_date, priority, source_type, stock_id, status) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.scheduleId}, #{item.orderId}, #{item.orderItemId}, #{item.orderNo}, #{item.customer}, " +
            "#{item.customerLevel}, #{item.materialCode}, #{item.materialName}, #{item.colorCode}, " +
            "#{item.thickness}, #{item.width}, #{item.length}, #{item.orderQty}, #{item.scheduleQty}, " +
            "#{item.deliveryDate}, #{item.priority}, #{item.sourceType}, #{item.stockId}, #{item.status})" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("list") List<ScheduleOrderItem> items);
    
    /**
     * 插入单条
     */
    @Insert("INSERT INTO schedule_order_item (schedule_id, order_id, order_item_id, order_no, customer, " +
            "customer_level, material_code, material_name, color_code, thickness, width, length, " +
            "order_qty, schedule_qty, delivery_date, priority, source_type, stock_id, status) VALUES " +
            "(#{scheduleId}, #{orderId}, #{orderItemId}, #{orderNo}, #{customer}, #{customerLevel}, " +
            "#{materialCode}, #{materialName}, #{colorCode}, #{thickness}, #{width}, #{length}, " +
            "#{orderQty}, #{scheduleQty}, #{deliveryDate}, #{priority}, #{sourceType}, #{stockId}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ScheduleOrderItem item);
    
    /**
     * 更新状态
     */
    @Update("UPDATE schedule_order_item SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);
    
    /**
     * 根据排程ID删除
     */
    @Delete("DELETE FROM schedule_order_item WHERE schedule_id = #{scheduleId}")
    int deleteByScheduleId(@Param("scheduleId") Long scheduleId);    /**
     * 查询待排程的订单明细
     * 注意：从tape_spec表关联查询颜色代码
     */    @Select("<script>" +
            "SELECT soi.id as order_item_id, soi.order_id, so.order_no, " +
            "so.customer, c.customer_level, soi.material_code, soi.material_name, " +
            "COALESCE(ts.color_code, soi.color_code) as color_code, " +
            "COALESCE(ts.color_name, '') as color_name, " +
            "soi.thickness, soi.width, soi.length, " +
            "soi.rolls as order_qty, soi.rolls as pending_qty, " +
            "so.delivery_date " +
            "FROM sales_order_items soi " +
            "LEFT JOIN sales_orders so ON soi.order_id = so.id " +
            "LEFT JOIN customers c ON so.customer COLLATE utf8mb4_unicode_ci = c.customer_code COLLATE utf8mb4_unicode_ci " +
            "LEFT JOIN tape_spec ts ON soi.material_code COLLATE utf8mb4_unicode_ci = ts.material_code COLLATE utf8mb4_unicode_ci " +
            "WHERE so.status != 'cancelled' " +
            "AND soi.is_deleted = 0 " +
            "AND so.is_deleted = 0 " +
            "AND soi.rolls > 0 " +
            "<if test='params.customerLevel != null and params.customerLevel != \"\"'>" +
            "AND c.customer_level = #{params.customerLevel} " +
            "</if>" +
            "<if test='params.materialCode != null and params.materialCode != \"\"'>" +
            "AND soi.material_code LIKE CONCAT('%', #{params.materialCode}, '%') " +
            "</if>" +
            "ORDER BY " +
            "CASE c.customer_level WHEN 'VIP' THEN 1 WHEN 'A' THEN 2 WHEN 'B' THEN 3 ELSE 4 END, " +
            "so.delivery_date ASC " +
            "</script>")
    List<Map<String, Object>> selectPendingOrderItems(@Param("params") Map<String, Object> params);

    /**
     * 查询待排程的订单明细 (分页)
     */
    @Select("<script>" +
            "SELECT soi.id as order_item_id, soi.order_id, so.order_no, " +
            "so.customer, c.customer_level, soi.material_code, soi.material_name, " +
            "COALESCE(ts.color_code, soi.color_code) as color_code, " +
            "COALESCE(ts.color_name, '') as color_name, " +
            "soi.thickness, soi.width, soi.length, " +
            "soi.rolls as order_qty, soi.rolls as pending_qty, " +
            "so.delivery_date " +
            "FROM sales_order_items soi " +
            "LEFT JOIN sales_orders so ON soi.order_id = so.id " +
            "LEFT JOIN customers c ON so.customer COLLATE utf8mb4_unicode_ci = c.customer_code COLLATE utf8mb4_unicode_ci " +
            "LEFT JOIN tape_spec ts ON soi.material_code COLLATE utf8mb4_unicode_ci = ts.material_code COLLATE utf8mb4_unicode_ci " +
            "WHERE so.status != 'cancelled' " +
            "AND soi.is_deleted = 0 " +
            "AND so.is_deleted = 0 " +
            "AND soi.rolls > 0 " +
            "<if test='params.customerLevel != null and params.customerLevel != \"\"'>" +
            "AND c.customer_level = #{params.customerLevel} " +
            "</if>" +
            "<if test='params.materialCode != null and params.materialCode != \"\"'>" +
            "AND soi.material_code LIKE CONCAT('%', #{params.materialCode}, '%') " +
            "</if>" +
            "ORDER BY " +
            "CASE c.customer_level WHEN 'VIP' THEN 1 WHEN 'A' THEN 2 WHEN 'B' THEN 3 ELSE 4 END, " +
            "so.delivery_date ASC " +
            "</script>")
    IPage<Map<String, Object>> selectPendingOrderItemsPage(IPage<Map<String, Object>> page, @Param("params") Map<String, Object> params);

    /**
     * MyBatis-Plus分页查询排程明细（标准方式）
     */
    @Select("<script>" +
            "SELECT * FROM schedule_order_item " +
            "WHERE 1=1 " +
            "<if test='params.scheduleId != null'>AND schedule_id = #{params.scheduleId}</if>" +
            "<if test='params.status != null and params.status != \"\"'>AND status = #{params.status}</if>" +
            "<if test='params.materialCode != null and params.materialCode != \"\"'>AND material_code LIKE CONCAT('%', #{params.materialCode}, '%')</if>" +
            "ORDER BY priority ASC " +
            "</script>")
    IPage<ScheduleOrderItem> selectPage(Page<ScheduleOrderItem> page, @Param("params") Map<String, Object> params);
    
    /**
     * 统计排程中不同订单的数量
     */
    @Select("SELECT COUNT(DISTINCT order_id) FROM schedule_order_item WHERE schedule_id = #{scheduleId}")
    Integer countDistinctOrders(@Param("scheduleId") Long scheduleId);
    
    /**
     * 统计排程中订单明细的数量
     */
    @Select("SELECT COUNT(*) FROM schedule_order_item WHERE schedule_id = #{scheduleId}")
    Integer countItems(@Param("scheduleId") Long scheduleId);
}
