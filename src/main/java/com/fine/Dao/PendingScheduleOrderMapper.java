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
    @Select({
            "<script>",
            "SELECT t.orderItemId, t.orderId, t.orderNo, t.materialCode, t.materialName, t.customer, t.customerId, t.customerCode, ",
            "       t.pendingQty, t.pendingArea, t.deliveryDate ",
            "FROM ( ",
            "  SELECT p.order_item_id AS orderItemId, p.order_id AS orderId, p.order_no AS orderNo, ",
            "         p.material_code AS materialCode, p.material_name AS materialName, ",
            "         p.customer_name AS customer, c.id AS customerId, c.customer_code AS customerCode, ",
            "         p.shortage_qty AS pendingQty, p.shortage_area AS pendingArea, ",
            "         p.added_at AS deliveryDate ",
            "  FROM pending_coating_order_pool p ",
            "  LEFT JOIN sales_orders so ON so.id = p.order_id ",
            "  LEFT JOIN customers c ON so.customer COLLATE utf8mb4_unicode_ci = c.customer_code COLLATE utf8mb4_unicode_ci ",
            "  WHERE p.pool_status = 'WAITING' AND p.shortage_area > 0 ",
            "  UNION ALL ",
            "  SELECT p.order_item_id AS orderItemId, p.order_id AS orderId, p.order_no AS orderNo, ",
            "         p.material_code AS materialCode, p.material_name AS materialName, ",
            "         p.customer_name AS customer, c.id AS customerId, c.customer_code AS customerCode, ",
            "         p.shortage_qty AS pendingQty, p.shortage_area AS pendingArea, ",
            "         p.added_at AS deliveryDate ",
            "  FROM pending_rewinding_order_pool p ",
            "  LEFT JOIN sales_orders so ON so.id = p.order_id ",
            "  LEFT JOIN customers c ON so.customer COLLATE utf8mb4_unicode_ci = c.customer_code COLLATE utf8mb4_unicode_ci ",
            "  WHERE p.pool_status = 'WAITING' AND p.shortage_area > 0 ",
            "  UNION ALL ",
            "  SELECT p.order_item_id AS orderItemId, p.order_id AS orderId, p.order_no AS orderNo, ",
            "         p.material_code AS materialCode, p.material_name AS materialName, ",
            "         p.customer_name AS customer, c.id AS customerId, c.customer_code AS customerCode, ",
            "         p.shortage_qty AS pendingQty, p.shortage_area AS pendingArea, ",
            "         p.added_at AS deliveryDate ",
            "  FROM pending_slitting_order_pool p ",
            "  LEFT JOIN sales_orders so ON so.id = p.order_id ",
            "  LEFT JOIN customers c ON so.customer COLLATE utf8mb4_unicode_ci = c.customer_code COLLATE utf8mb4_unicode_ci ",
            "  WHERE p.pool_status = 'WAITING' AND p.shortage_area > 0 ",
            ") t ",
            "GROUP BY t.orderItemId, t.orderId, t.orderNo, t.materialCode, t.materialName, t.customer, t.customerId, t.customerCode ",
            "ORDER BY MIN(t.deliveryDate), t.materialCode",
            "</script>"
    })
    List<PendingScheduleOrder> selectAll();

    /**
     * 按物料编号查询待排程订单
     */
    @Select({
            "<script>",
            "SELECT t.orderItemId, t.orderId, t.orderNo, t.materialCode, t.materialName, t.customer, t.customerId, t.customerCode, ",
            "       t.pendingQty, t.pendingArea, t.deliveryDate ",
            "FROM ( ",
            "  SELECT p.order_item_id AS orderItemId, p.order_id AS orderId, p.order_no AS orderNo, ",
            "         p.material_code AS materialCode, p.material_name AS materialName, ",
            "         p.customer_name AS customer, c.id AS customerId, c.customer_code AS customerCode, ",
            "         p.shortage_qty AS pendingQty, p.shortage_area AS pendingArea, ",
            "         p.added_at AS deliveryDate ",
            "  FROM pending_coating_order_pool p ",
            "  LEFT JOIN sales_orders so ON so.id = p.order_id ",
            "  LEFT JOIN customers c ON so.customer COLLATE utf8mb4_unicode_ci = c.customer_code COLLATE utf8mb4_unicode_ci ",
            "  WHERE p.pool_status = 'WAITING' AND p.shortage_area > 0 AND p.material_code = #{materialCode} ",
            "  UNION ALL ",
            "  SELECT p.order_item_id AS orderItemId, p.order_id AS orderId, p.order_no AS orderNo, ",
            "         p.material_code AS materialCode, p.material_name AS materialName, ",
            "         p.customer_name AS customer, c.id AS customerId, c.customer_code AS customerCode, ",
            "         p.shortage_qty AS pendingQty, p.shortage_area AS pendingArea, ",
            "         p.added_at AS deliveryDate ",
            "  FROM pending_rewinding_order_pool p ",
            "  LEFT JOIN sales_orders so ON so.id = p.order_id ",
            "  LEFT JOIN customers c ON so.customer COLLATE utf8mb4_unicode_ci = c.customer_code COLLATE utf8mb4_unicode_ci ",
            "  WHERE p.pool_status = 'WAITING' AND p.shortage_area > 0 AND p.material_code = #{materialCode} ",
            "  UNION ALL ",
            "  SELECT p.order_item_id AS orderItemId, p.order_id AS orderId, p.order_no AS orderNo, ",
            "         p.material_code AS materialCode, p.material_name AS materialName, ",
            "         p.customer_name AS customer, c.id AS customerId, c.customer_code AS customerCode, ",
            "         p.shortage_qty AS pendingQty, p.shortage_area AS pendingArea, ",
            "         p.added_at AS deliveryDate ",
            "  FROM pending_slitting_order_pool p ",
            "  LEFT JOIN sales_orders so ON so.id = p.order_id ",
            "  LEFT JOIN customers c ON so.customer COLLATE utf8mb4_unicode_ci = c.customer_code COLLATE utf8mb4_unicode_ci ",
            "  WHERE p.pool_status = 'WAITING' AND p.shortage_area > 0 AND p.material_code = #{materialCode} ",
            ") t ",
            "GROUP BY t.orderItemId, t.orderId, t.orderNo, t.materialCode, t.materialName, t.customer, t.customerId, t.customerCode ",
            "ORDER BY MIN(t.deliveryDate)",
            "</script>"
    })
    List<PendingScheduleOrder> selectByMaterialCode(@Param("materialCode") String materialCode);

    /**
     * 按物料编号分组统计待排程数量
     */
    @Select({
            "<script>",
            "SELECT materialCode, materialName, COUNT(*) AS orderQty, SUM(pendingQty) AS pendingQty, SUM(pendingArea) AS pendingArea, ",
            "       MIN(deliveryDate) AS deliveryDate, GROUP_CONCAT(DISTINCT customer SEPARATOR ', ') AS customer ",
            "FROM ( ",
            "  SELECT material_code AS materialCode, material_name AS materialName, customer_name AS customer, shortage_qty AS pendingQty, shortage_area AS pendingArea, added_at AS deliveryDate ",
            "  FROM pending_coating_order_pool WHERE pool_status = 'WAITING' AND shortage_area > 0 ",
            "  UNION ALL ",
            "  SELECT material_code AS materialCode, material_name AS materialName, customer_name AS customer, shortage_qty AS pendingQty, shortage_area AS pendingArea, added_at AS deliveryDate ",
            "  FROM pending_rewinding_order_pool WHERE pool_status = 'WAITING' AND shortage_area > 0 ",
            "  UNION ALL ",
            "  SELECT material_code AS materialCode, material_name AS materialName, customer_name AS customer, shortage_qty AS pendingQty, shortage_area AS pendingArea, added_at AS deliveryDate ",
            "  FROM pending_slitting_order_pool WHERE pool_status = 'WAITING' AND shortage_area > 0 ",
            ") t ",
            "GROUP BY materialCode, materialName ",
            "ORDER BY MIN(deliveryDate), SUM(pendingArea) DESC",
            "</script>"
    })
    List<PendingScheduleOrder> groupByMaterialCode();
}
