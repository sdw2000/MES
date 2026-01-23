package com.fine.Dao;

import com.fine.entity.PendingScheduleOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 待排程订单Mapper
 */
@Mapper
public interface PendingScheduleOrderMapper {

    /**
     * 查询所有待排程订单
     */
    @Select("SELECT order_item_id AS orderItemId, order_id AS orderId, order_no AS orderNo, " +
            "material_code AS materialCode, material_name AS materialName, " +
            "customer_name AS customer, shortage_qty AS pendingQty, shortage_area AS pendingArea, " +
            "added_at AS deliveryDate " +
            "FROM pending_coating_order_pool " +
            "WHERE pool_status = 'WAITING' " +
            "ORDER BY added_at, material_code")
    List<PendingScheduleOrder> selectAll();

    /**
     * 按物料编号查询待排程订单
     */
    @Select("SELECT order_item_id AS orderItemId, order_id AS orderId, order_no AS orderNo, " +
            "material_code AS materialCode, material_name AS materialName, " +
            "customer_name AS customer, shortage_qty AS pendingQty, shortage_area AS pendingArea, " +
            "added_at AS deliveryDate " +
            "FROM pending_coating_order_pool " +
            "WHERE pool_status = 'WAITING' AND material_code = #{materialCode} " +
            "ORDER BY added_at")
    List<PendingScheduleOrder> selectByMaterialCode(@Param("materialCode") String materialCode);

    /**
     * 按物料编号分组统计待排程数量
     */
    @Select("SELECT material_code AS materialCode, material_name AS materialName, " +
            "COUNT(*) AS orderQty, " +
            "SUM(shortage_qty) AS pendingQty, " +
            "SUM(shortage_area) AS pendingArea, " +
            "MIN(added_at) AS deliveryDate, " +
            "GROUP_CONCAT(DISTINCT customer_name SEPARATOR ', ') AS customer " +
            "FROM pending_coating_order_pool " +
            "WHERE pool_status = 'WAITING' " +
            "GROUP BY material_code, material_name " +
            "ORDER BY MIN(added_at), SUM(shortage_area) DESC")
    List<PendingScheduleOrder> groupByMaterialCode();
}
