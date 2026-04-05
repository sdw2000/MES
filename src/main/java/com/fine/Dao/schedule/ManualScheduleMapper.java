package com.fine.Dao.schedule;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.modle.schedule.ManualSchedule;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 手动排程Mapper
 */
@Mapper
public interface ManualScheduleMapper extends BaseMapper<ManualSchedule> {

    /**
     * 按计划日期查询涂布计划（用于配方分解 -> 化工锁定/请购）
     */
    @Select("<script>" +
            "SELECT ms.id AS schedule_id, ms.order_no, soi.material_code, soi.material_name, " +
            "ms.coating_area AS coating_area " +
            "FROM manual_schedule ms " +
            "LEFT JOIN sales_order_items soi ON soi.id = ms.order_detail_id " +
            "WHERE ms.schedule_type = 'COATING' " +
            "AND ms.status IN ('PENDING','COATING_SCHEDULED','REWINDING_SCHEDULED','CONFIRMED') " +
            "AND ms.coating_area IS NOT NULL AND ms.coating_area > 0 " +
            "AND DATE(ms.coating_schedule_date) = #{planDate} " +
            "<if test='orderNo != null and orderNo != \"\"'> AND ms.order_no LIKE CONCAT('%', #{orderNo}, '%') </if> " +
            "<if test='materialCode != null and materialCode != \"\"'> AND soi.material_code = #{materialCode} </if> " +
            "ORDER BY ms.coating_schedule_date ASC, ms.id ASC" +
            "</script>")
    List<Map<String, Object>> selectCoatingPlansForChemical(@Param("planDate") LocalDate planDate,
                                                            @Param("orderNo") String orderNo,
                                                            @Param("materialCode") String materialCode);
    
    /**
     * 查询待排程订单明细（订单数量 > 已排数量）
     */
        @Select("SELECT " +
            "o.id AS order_id, " +
            "o.order_no, " +
            "soi.id AS order_detail_id, " +
            "soi.material_code, " +
            "soi.material_name, " +
            "soi.color_code, " +
            "soi.width, " +
            "soi.length, " +
            "soi.thickness, " +
            "soi.rolls AS order_qty, " +
            "IFNULL(soi.delivered_qty, 0) AS completed_qty, " +
            "IFNULL(soi.scheduled_qty, 0) AS scheduled_qty, " +
            "IFNULL(soi.remaining_qty, (soi.rolls - IFNULL(soi.scheduled_qty, 0))) AS remaining_qty, " +
            "o.order_date, " +
            "o.delivery_date, " +
            "o.customer, " +
            "o.customer AS customer_name, " +
            "ms.id AS schedule_id, " +
            "ms.schedule_type, " +
            "ms.status AS schedule_status, " +
            "COALESCE(ms.coating_date, soi.coating_date) AS coating_date, " +
            "ms.rewinding_date, " +
            "ms.packaging_date, " +
            "IFNULL((SELECT SUM(r.produced_qty) FROM manual_schedule_process_report r WHERE r.schedule_id = ms.id AND r.process_type = 'COATING' AND r.is_deleted = 0), 0) AS coating_report_qty, " +
            "IFNULL((SELECT SUM(r.produced_qty) FROM manual_schedule_process_report r WHERE r.schedule_id = ms.id AND r.process_type = 'REWINDING' AND r.is_deleted = 0), 0) AS rewinding_report_qty, " +
            "IFNULL((SELECT SUM(r.produced_qty) FROM manual_schedule_process_report r WHERE r.schedule_id = ms.id AND r.process_type = 'SLITTING' AND r.is_deleted = 0), 0) AS slitting_report_qty, " +
            "IFNULL((SELECT MAX(CASE WHEN r.proceed_next_process = 0 THEN 1 ELSE 0 END) FROM manual_schedule_process_report r WHERE r.schedule_id = ms.id AND r.process_type = 'COATING' AND r.is_deleted = 0), 0) AS coating_stop_next, " +
            "IFNULL((SELECT MAX(CASE WHEN r.proceed_next_process = 0 THEN 1 ELSE 0 END) FROM manual_schedule_process_report r WHERE r.schedule_id = ms.id AND r.process_type = 'REWINDING' AND r.is_deleted = 0), 0) AS rewinding_stop_next, " +
            "IFNULL((SELECT MAX(CASE WHEN r.proceed_next_process = 0 THEN 1 ELSE 0 END) FROM manual_schedule_process_report r WHERE r.schedule_id = ms.id AND r.process_type = 'SLITTING' AND r.is_deleted = 0), 0) AS slitting_stop_next, " +
            "CASE WHEN EXISTS (" +
            "  SELECT 1 FROM manual_schedule_coating_allocation ca2 " +
            "  JOIN manual_schedule ms2 ON ms2.id = ca2.schedule_id " +
            "  WHERE ca2.included_flag = 1 " +
            "    AND ca2.order_no COLLATE utf8mb4_unicode_ci = o.order_no COLLATE utf8mb4_unicode_ci " +
            "    AND ca2.material_code COLLATE utf8mb4_unicode_ci = soi.material_code COLLATE utf8mb4_unicode_ci " +
            "    AND (ca2.thickness IS NULL OR ca2.thickness = soi.thickness) " +
            "    AND ms2.schedule_type = 'COATING' " +
            "    AND ms2.status IN ('PENDING','COATING_SCHEDULED','REWINDING_SCHEDULED')" +
            ") THEN 1 ELSE 0 END AS covered_by_active_coating, " +
            "'' AS sample_date, " +
            "ROUND(IFNULL(cp.total_score, 0) + IFNULL(DATEDIFF(CURDATE(), o.order_date), 0), 1) AS priority_score, " +
            "IFNULL(soi.remark, '') AS remark " +
            "FROM sales_order_items soi " +
            "JOIN sales_orders o ON soi.order_id = o.id " +
            "LEFT JOIN customers c ON o.customer COLLATE utf8mb4_unicode_ci = c.customer_code COLLATE utf8mb4_unicode_ci " +
            "LEFT JOIN (" +
            "  SELECT p1.customer_id, p1.total_score " +
            "  FROM order_customer_priority p1 " +
            "  INNER JOIN (" +
            "    SELECT customer_id, MAX(id) AS max_id " +
            "    FROM order_customer_priority " +
            "    GROUP BY customer_id" +
            "  ) p2 ON p1.customer_id = p2.customer_id AND p1.id = p2.max_id" +
            ") cp ON cp.customer_id = c.id " +
            "LEFT JOIN (" +
            "  SELECT m1.* " +
            "  FROM manual_schedule m1 " +
            "  INNER JOIN (" +
            "    SELECT order_detail_id, MAX(id) AS max_id " +
            "    FROM manual_schedule " +
            "    GROUP BY order_detail_id" +
            "  ) m2 ON m1.order_detail_id = m2.order_detail_id AND m1.id = m2.max_id" +
            ") ms ON ms.order_detail_id = soi.id " +
            "WHERE (o.status IS NULL OR LOWER(o.status) NOT IN ('completed','cancelled','canceled','closed')) " +
            "AND o.is_deleted = 0 AND soi.is_deleted = 0 " +
            "AND IFNULL(soi.remaining_qty, (soi.rolls - IFNULL(soi.scheduled_qty, 0))) > 0 " +
            "AND (ms.packaging_date IS NULL AND ms.slitting_schedule_date IS NULL) " +
            "ORDER BY " +
            "  CASE WHEN IFNULL(soi.remaining_qty, (soi.rolls - IFNULL(soi.scheduled_qty, 0))) > 0 THEN 0 ELSE 1 END, " +
            "  priority_score DESC, " +
            "  o.delivery_date ASC")
    List<Map<String, Object>> selectPendingOrders();

    /**
     * 分页查询待排程订单明细（MyBatis-Plus）
     */
    @Select("SELECT " +
            "o.id AS order_id, " +
            "o.order_no, " +
            "soi.id AS order_detail_id, " +
            "soi.material_code, " +
            "soi.material_name, " +
            "soi.color_code, " +
            "soi.width, " +
            "soi.length, " +
            "soi.thickness, " +
            "soi.rolls AS order_qty, " +
            "IFNULL(soi.delivered_qty, 0) AS completed_qty, " +
            "IFNULL(soi.scheduled_qty, 0) AS scheduled_qty, " +
            "IFNULL(soi.remaining_qty, (soi.rolls - IFNULL(soi.scheduled_qty, 0))) AS remaining_qty, " +
            "o.order_date, " +
            "o.delivery_date, " +
            "o.customer, " +
            "o.customer AS customer_name, " +
            "ms.id AS schedule_id, " +
            "ms.schedule_type, " +
            "ms.status AS schedule_status, " +
            "COALESCE(ms.coating_date, soi.coating_date) AS coating_date, " +
            "ms.rewinding_date, " +
            "ms.packaging_date, " +
            "IFNULL((SELECT SUM(r.produced_qty) FROM manual_schedule_process_report r WHERE r.schedule_id = ms.id AND r.process_type = 'COATING' AND r.is_deleted = 0), 0) AS coating_report_qty, " +
            "IFNULL((SELECT SUM(r.produced_qty) FROM manual_schedule_process_report r WHERE r.schedule_id = ms.id AND r.process_type = 'REWINDING' AND r.is_deleted = 0), 0) AS rewinding_report_qty, " +
            "IFNULL((SELECT SUM(r.produced_qty) FROM manual_schedule_process_report r WHERE r.schedule_id = ms.id AND r.process_type = 'SLITTING' AND r.is_deleted = 0), 0) AS slitting_report_qty, " +
            "IFNULL((SELECT MAX(CASE WHEN r.proceed_next_process = 0 THEN 1 ELSE 0 END) FROM manual_schedule_process_report r WHERE r.schedule_id = ms.id AND r.process_type = 'COATING' AND r.is_deleted = 0), 0) AS coating_stop_next, " +
            "IFNULL((SELECT MAX(CASE WHEN r.proceed_next_process = 0 THEN 1 ELSE 0 END) FROM manual_schedule_process_report r WHERE r.schedule_id = ms.id AND r.process_type = 'REWINDING' AND r.is_deleted = 0), 0) AS rewinding_stop_next, " +
            "IFNULL((SELECT MAX(CASE WHEN r.proceed_next_process = 0 THEN 1 ELSE 0 END) FROM manual_schedule_process_report r WHERE r.schedule_id = ms.id AND r.process_type = 'SLITTING' AND r.is_deleted = 0), 0) AS slitting_stop_next, " +
            "CASE WHEN EXISTS (" +
            "  SELECT 1 FROM manual_schedule_coating_allocation ca2 " +
            "  JOIN manual_schedule ms2 ON ms2.id = ca2.schedule_id " +
            "  WHERE ca2.included_flag = 1 " +
            "    AND ca2.order_no COLLATE utf8mb4_unicode_ci = o.order_no COLLATE utf8mb4_unicode_ci " +
            "    AND ca2.material_code COLLATE utf8mb4_unicode_ci = soi.material_code COLLATE utf8mb4_unicode_ci " +
            "    AND (ca2.thickness IS NULL OR ca2.thickness = soi.thickness) " +
            "    AND ms2.schedule_type = 'COATING' " +
            "    AND ms2.status IN ('PENDING','COATING_SCHEDULED','REWINDING_SCHEDULED')" +
            ") THEN 1 ELSE 0 END AS covered_by_active_coating, " +
            "'' AS sample_date, " +
            "ROUND(IFNULL(cp.total_score, 0) + IFNULL(DATEDIFF(CURDATE(), o.order_date), 0), 1) AS priority_score, " +
            "IFNULL(soi.remark, '') AS remark " +
            "FROM sales_order_items soi " +
            "JOIN sales_orders o ON soi.order_id = o.id " +
            "LEFT JOIN customers c ON o.customer COLLATE utf8mb4_unicode_ci = c.customer_code COLLATE utf8mb4_unicode_ci " +
            "LEFT JOIN (" +
            "  SELECT p1.customer_id, p1.total_score " +
            "  FROM order_customer_priority p1 " +
            "  INNER JOIN (" +
            "    SELECT customer_id, MAX(id) AS max_id " +
            "    FROM order_customer_priority " +
            "    GROUP BY customer_id" +
            "  ) p2 ON p1.customer_id = p2.customer_id AND p1.id = p2.max_id" +
            ") cp ON cp.customer_id = c.id " +
            "LEFT JOIN (" +
            "  SELECT m1.* " +
            "  FROM manual_schedule m1 " +
            "  INNER JOIN (" +
            "    SELECT order_detail_id, MAX(id) AS max_id " +
            "    FROM manual_schedule " +
            "    GROUP BY order_detail_id" +
            "  ) m2 ON m1.order_detail_id = m2.order_detail_id AND m1.id = m2.max_id" +
            ") ms ON ms.order_detail_id = soi.id " +
            "WHERE (o.status IS NULL OR LOWER(o.status) NOT IN ('completed','cancelled','canceled','closed')) " +
            "AND o.is_deleted = 0 AND soi.is_deleted = 0 " +
            "AND IFNULL(soi.remaining_qty, (soi.rolls - IFNULL(soi.scheduled_qty, 0))) > 0 " +
            "AND (ms.packaging_date IS NULL AND ms.slitting_schedule_date IS NULL) " +
            "ORDER BY " +
            "  CASE WHEN IFNULL(soi.remaining_qty, (soi.rolls - IFNULL(soi.scheduled_qty, 0))) > 0 THEN 0 ELSE 1 END, " +
            "  priority_score DESC, " +
            "  o.delivery_date ASC")
    List<Map<String, Object>> selectPendingOrdersPage(Page<Map<String, Object>> page);

    /**
     * 统计待排程订单总数
     */
    @Select("SELECT COUNT(1) " +
            "FROM sales_order_items soi " +
            "JOIN sales_orders o ON soi.order_id = o.id " +
            "LEFT JOIN (" +
            "  SELECT m1.* " +
            "  FROM manual_schedule m1 " +
            "  INNER JOIN (" +
            "    SELECT order_detail_id, MAX(id) AS max_id " +
            "    FROM manual_schedule " +
            "    GROUP BY order_detail_id" +
            "  ) m2 ON m1.order_detail_id = m2.order_detail_id AND m1.id = m2.max_id" +
            ") ms ON ms.order_detail_id = soi.id " +
            "WHERE (o.status IS NULL OR LOWER(o.status) NOT IN ('completed','cancelled','canceled','closed')) " +
            "AND o.is_deleted = 0 AND soi.is_deleted = 0 " +
            "AND IFNULL(soi.remaining_qty, (soi.rolls - IFNULL(soi.scheduled_qty, 0))) > 0 " +
            "AND (ms.packaging_date IS NULL AND ms.slitting_schedule_date IS NULL)")
    Long selectPendingOrdersCount();

    /**
     * 查询待排程订单明细（包含已拍完）
     */
    @Select("SELECT " +
            "o.id AS order_id, " +
            "o.order_no, " +
            "soi.id AS order_detail_id, " +
            "soi.material_code, " +
            "soi.material_name, " +
            "soi.color_code, " +
            "soi.width, " +
            "soi.length, " +
            "soi.thickness, " +
            "soi.rolls AS order_qty, " +
            "IFNULL(soi.delivered_qty, 0) AS completed_qty, " +
            "IFNULL(soi.scheduled_qty, 0) AS scheduled_qty, " +
            "IFNULL(soi.remaining_qty, (soi.rolls - IFNULL(soi.scheduled_qty, 0))) AS remaining_qty, " +
            "o.order_date, " +
            "o.delivery_date, " +
            "o.customer, " +
            "o.customer AS customer_name, " +
            "ms.id AS schedule_id, " +
            "ms.schedule_type, " +
            "ms.status AS schedule_status, " +
            "COALESCE(ms.coating_date, soi.coating_date) AS coating_date, " +
            "ms.rewinding_date, " +
            "ms.packaging_date, " +
            "IFNULL((SELECT SUM(r.produced_qty) FROM manual_schedule_process_report r WHERE r.schedule_id = ms.id AND r.process_type = 'COATING' AND r.is_deleted = 0), 0) AS coating_report_qty, " +
            "IFNULL((SELECT SUM(r.produced_qty) FROM manual_schedule_process_report r WHERE r.schedule_id = ms.id AND r.process_type = 'REWINDING' AND r.is_deleted = 0), 0) AS rewinding_report_qty, " +
            "IFNULL((SELECT SUM(r.produced_qty) FROM manual_schedule_process_report r WHERE r.schedule_id = ms.id AND r.process_type = 'SLITTING' AND r.is_deleted = 0), 0) AS slitting_report_qty, " +
            "IFNULL((SELECT MAX(CASE WHEN r.proceed_next_process = 0 THEN 1 ELSE 0 END) FROM manual_schedule_process_report r WHERE r.schedule_id = ms.id AND r.process_type = 'COATING' AND r.is_deleted = 0), 0) AS coating_stop_next, " +
            "IFNULL((SELECT MAX(CASE WHEN r.proceed_next_process = 0 THEN 1 ELSE 0 END) FROM manual_schedule_process_report r WHERE r.schedule_id = ms.id AND r.process_type = 'REWINDING' AND r.is_deleted = 0), 0) AS rewinding_stop_next, " +
            "IFNULL((SELECT MAX(CASE WHEN r.proceed_next_process = 0 THEN 1 ELSE 0 END) FROM manual_schedule_process_report r WHERE r.schedule_id = ms.id AND r.process_type = 'SLITTING' AND r.is_deleted = 0), 0) AS slitting_stop_next, " +
            "CASE WHEN EXISTS (" +
            "  SELECT 1 FROM manual_schedule_coating_allocation ca2 " +
            "  JOIN manual_schedule ms2 ON ms2.id = ca2.schedule_id " +
            "  WHERE ca2.included_flag = 1 " +
            "    AND ca2.order_no COLLATE utf8mb4_unicode_ci = o.order_no COLLATE utf8mb4_unicode_ci " +
            "    AND ca2.material_code COLLATE utf8mb4_unicode_ci = soi.material_code COLLATE utf8mb4_unicode_ci " +
            "    AND (ca2.thickness IS NULL OR ca2.thickness = soi.thickness) " +
            "    AND ms2.schedule_type = 'COATING' " +
            "    AND ms2.status IN ('PENDING','COATING_SCHEDULED','REWINDING_SCHEDULED')" +
            ") THEN 1 ELSE 0 END AS covered_by_active_coating, " +
            "'' AS sample_date, " +
            "ROUND(IFNULL(cp.total_score, 0) + IFNULL(DATEDIFF(CURDATE(), o.order_date), 0), 1) AS priority_score, " +
            "IFNULL(soi.remark, '') AS remark " +
            "FROM sales_order_items soi " +
            "JOIN sales_orders o ON soi.order_id = o.id " +
            "LEFT JOIN customers c ON o.customer COLLATE utf8mb4_unicode_ci = c.customer_code COLLATE utf8mb4_unicode_ci " +
            "LEFT JOIN (" +
            "  SELECT p1.customer_id, p1.total_score " +
            "  FROM order_customer_priority p1 " +
            "  INNER JOIN (" +
            "    SELECT customer_id, MAX(id) AS max_id " +
            "    FROM order_customer_priority " +
            "    GROUP BY customer_id" +
            "  ) p2 ON p1.customer_id = p2.customer_id AND p1.id = p2.max_id" +
            ") cp ON cp.customer_id = c.id " +
            "LEFT JOIN (" +
            "  SELECT m1.* " +
            "  FROM manual_schedule m1 " +
            "  INNER JOIN (" +
            "    SELECT order_detail_id, MAX(id) AS max_id " +
            "    FROM manual_schedule " +
            "    GROUP BY order_detail_id" +
            "  ) m2 ON m1.order_detail_id = m2.order_detail_id AND m1.id = m2.max_id" +
            ") ms ON ms.order_detail_id = soi.id " +
            "WHERE (o.status IS NULL OR LOWER(o.status) NOT IN ('cancelled','canceled','closed')) " +
            "AND o.is_deleted = 0 AND soi.is_deleted = 0 " +
            "ORDER BY " +
            "  CASE WHEN IFNULL(soi.remaining_qty, (soi.rolls - IFNULL(soi.scheduled_qty, 0))) > 0 THEN 0 ELSE 1 END, " +
            "  priority_score DESC, " +
            "  o.delivery_date ASC")
    List<Map<String, Object>> selectPendingOrdersIncludeCompleted();

    /**
     * 分页查询待排程订单明细（包含已拍完）
     */
    @Select("SELECT " +
            "o.id AS order_id, " +
            "o.order_no, " +
            "soi.id AS order_detail_id, " +
            "soi.material_code, " +
            "soi.material_name, " +
            "soi.color_code, " +
            "soi.width, " +
            "soi.length, " +
            "soi.thickness, " +
            "soi.rolls AS order_qty, " +
            "IFNULL(soi.delivered_qty, 0) AS completed_qty, " +
            "IFNULL(soi.scheduled_qty, 0) AS scheduled_qty, " +
            "IFNULL(soi.remaining_qty, (soi.rolls - IFNULL(soi.scheduled_qty, 0))) AS remaining_qty, " +
            "o.order_date, " +
            "o.delivery_date, " +
            "o.customer, " +
            "o.customer AS customer_name, " +
            "ms.id AS schedule_id, " +
            "ms.schedule_type, " +
            "ms.status AS schedule_status, " +
            "COALESCE(ms.coating_date, soi.coating_date) AS coating_date, " +
            "ms.rewinding_date, " +
            "ms.packaging_date, " +
            "IFNULL((SELECT SUM(r.produced_qty) FROM manual_schedule_process_report r WHERE r.schedule_id = ms.id AND r.process_type = 'COATING' AND r.is_deleted = 0), 0) AS coating_report_qty, " +
            "IFNULL((SELECT SUM(r.produced_qty) FROM manual_schedule_process_report r WHERE r.schedule_id = ms.id AND r.process_type = 'REWINDING' AND r.is_deleted = 0), 0) AS rewinding_report_qty, " +
            "IFNULL((SELECT SUM(r.produced_qty) FROM manual_schedule_process_report r WHERE r.schedule_id = ms.id AND r.process_type = 'SLITTING' AND r.is_deleted = 0), 0) AS slitting_report_qty, " +
            "IFNULL((SELECT MAX(CASE WHEN r.proceed_next_process = 0 THEN 1 ELSE 0 END) FROM manual_schedule_process_report r WHERE r.schedule_id = ms.id AND r.process_type = 'COATING' AND r.is_deleted = 0), 0) AS coating_stop_next, " +
            "IFNULL((SELECT MAX(CASE WHEN r.proceed_next_process = 0 THEN 1 ELSE 0 END) FROM manual_schedule_process_report r WHERE r.schedule_id = ms.id AND r.process_type = 'REWINDING' AND r.is_deleted = 0), 0) AS rewinding_stop_next, " +
            "IFNULL((SELECT MAX(CASE WHEN r.proceed_next_process = 0 THEN 1 ELSE 0 END) FROM manual_schedule_process_report r WHERE r.schedule_id = ms.id AND r.process_type = 'SLITTING' AND r.is_deleted = 0), 0) AS slitting_stop_next, " +
            "CASE WHEN EXISTS (" +
            "  SELECT 1 FROM manual_schedule_coating_allocation ca2 " +
            "  JOIN manual_schedule ms2 ON ms2.id = ca2.schedule_id " +
            "  WHERE ca2.included_flag = 1 " +
            "    AND ca2.order_no COLLATE utf8mb4_unicode_ci = o.order_no COLLATE utf8mb4_unicode_ci " +
            "    AND ca2.material_code COLLATE utf8mb4_unicode_ci = soi.material_code COLLATE utf8mb4_unicode_ci " +
            "    AND (ca2.thickness IS NULL OR ca2.thickness = soi.thickness) " +
            "    AND ms2.schedule_type = 'COATING' " +
            "    AND ms2.status IN ('PENDING','COATING_SCHEDULED','REWINDING_SCHEDULED')" +
            ") THEN 1 ELSE 0 END AS covered_by_active_coating, " +
            "'' AS sample_date, " +
            "ROUND(IFNULL(cp.total_score, 0) + IFNULL(DATEDIFF(CURDATE(), o.order_date), 0), 1) AS priority_score, " +
            "IFNULL(soi.remark, '') AS remark " +
            "FROM sales_order_items soi " +
            "JOIN sales_orders o ON soi.order_id = o.id " +
            "LEFT JOIN customers c ON o.customer COLLATE utf8mb4_unicode_ci = c.customer_code COLLATE utf8mb4_unicode_ci " +
            "LEFT JOIN (" +
            "  SELECT p1.customer_id, p1.total_score " +
            "  FROM order_customer_priority p1 " +
            "  INNER JOIN (" +
            "    SELECT customer_id, MAX(id) AS max_id " +
            "    FROM order_customer_priority " +
            "    GROUP BY customer_id" +
            "  ) p2 ON p1.customer_id = p2.customer_id AND p1.id = p2.max_id" +
            ") cp ON cp.customer_id = c.id " +
            "LEFT JOIN (" +
            "  SELECT m1.* " +
            "  FROM manual_schedule m1 " +
            "  INNER JOIN (" +
            "    SELECT order_detail_id, MAX(id) AS max_id " +
            "    FROM manual_schedule " +
            "    GROUP BY order_detail_id" +
            "  ) m2 ON m1.order_detail_id = m2.order_detail_id AND m1.id = m2.max_id" +
            ") ms ON ms.order_detail_id = soi.id " +
            "WHERE (o.status IS NULL OR LOWER(o.status) NOT IN ('cancelled','canceled','closed')) " +
            "AND o.is_deleted = 0 AND soi.is_deleted = 0 " +
            "AND (#{orderNo} IS NULL OR #{orderNo} = '' OR REPLACE(UPPER(o.order_no), ' ', '') LIKE CONCAT('%', REPLACE(UPPER(#{orderNo}), ' ', ''), '%')) " +
            "ORDER BY " +
            "  CASE WHEN IFNULL(soi.remaining_qty, (soi.rolls - IFNULL(soi.scheduled_qty, 0))) > 0 THEN 0 ELSE 1 END, " +
            "  priority_score DESC, " +
            "  o.delivery_date ASC")
    List<Map<String, Object>> selectPendingOrdersPageIncludeCompleted(Page<Map<String, Object>> page, @Param("orderNo") String orderNo);

    /**
     * 统计待排程订单总数（包含已拍完）
     */
    @Select("SELECT COUNT(1) " +
            "FROM sales_order_items soi " +
            "JOIN sales_orders o ON soi.order_id = o.id " +
            "WHERE (o.status IS NULL OR LOWER(o.status) NOT IN ('cancelled','canceled','closed')) " +
            "AND o.is_deleted = 0 AND soi.is_deleted = 0 " +
            "AND (#{orderNo} IS NULL OR #{orderNo} = '' OR REPLACE(UPPER(o.order_no), ' ', '') LIKE CONCAT('%', REPLACE(UPPER(#{orderNo}), ' ', ''), '%'))")
    Long selectPendingOrdersCountIncludeCompleted(@Param("orderNo") String orderNo);
    
    /**
     * 查询已完成涂布待复卷的订单（按涂布日期排序）
     */
        @Select("SELECT " +
            "ms.id AS schedule_id, " +
            "ms.order_detail_id, " +
            "ms.status, " +
            "ms.schedule_type, " +
            "ms.schedule_qty, " +
            "ms.coating_date, " +
            "ms.coating_schedule_date, " +
            "eo.start_time AS coating_start_time, " +
            "eo.end_time AS coating_end_time, " +
            "COALESCE(ms.coating_area, (soi.width / 1000.0) * soi.length * ms.schedule_qty) AS coating_area, " +
            "COALESCE(ms.coating_length, soi.length) AS coating_length, " +
            "COALESCE(ms.coating_width, 1040) AS coating_width, " +
            "ms.coating_equipment, " +
            "ms.coating_equipment AS equipment_name, " +
            "ms.rewinding_equipment, " +
            "ms.rewinding_width, " +
            "eo_rw.start_time AS rewinding_start_time, " +
            "eo_rw.end_time AS rewinding_end_time, " +
            "eo_rw.duration_minutes AS rewinding_duration_minutes, " +
            "IFNULL(ms.rewinding_scheduled_area, 0) AS rewinding_scheduled_area, " +
            "(COALESCE(ms.coating_area, (soi.width / 1000.0) * soi.length * ms.schedule_qty) - IFNULL(ms.rewinding_scheduled_area, 0)) AS remaining_coating_area, " +
            "ms.stock_allocations, " +
            "soi.material_code, " +
            "soi.material_name, " +
            "soi.width, " +
            "soi.length, " +
            "soi.thickness, " +
            "o.order_no, " +
            "COALESCE(GROUP_CONCAT(DISTINCT ca.order_no ORDER BY ca.sort_no SEPARATOR ','), o.order_no) AS related_order_nos, " +
            "o.customer AS customer_name, " +
            "o.delivery_date " +
            "FROM manual_schedule ms " +
            "JOIN (" +
            "  SELECT MAX(id) AS id " +
            "  FROM manual_schedule " +
            "  WHERE (" +
            "    (schedule_type = 'COATING' AND status = 'COATING_SCHEDULED' AND coating_date IS NOT NULL) " +
            "    OR (schedule_type = 'COATING' AND status = 'REWINDING_SCHEDULED') " +
            "    OR (schedule_type = 'STOCK' AND status = 'REWINDING_SCHEDULED')" +
            "  ) " +
            "  GROUP BY order_detail_id" +
            ") latest ON latest.id = ms.id " +
            "JOIN sales_order_items soi ON ms.order_detail_id = soi.id " +
            "JOIN sales_orders o ON soi.order_id = o.id " +
            "LEFT JOIN equipment_occupation eo ON eo.schedule_id = ms.id AND eo.process_type = 'COATING' AND eo.status IN ('PLANNED','RUNNING','FINISHED') " +
            "LEFT JOIN equipment_occupation eo_rw ON eo_rw.schedule_id = ms.id AND eo_rw.process_type = 'REWINDING' AND eo_rw.status IN ('PLANNED','RUNNING','FINISHED') " +
            "LEFT JOIN manual_schedule_coating_allocation ca ON ca.schedule_id = ms.id AND ca.included_flag = 1 " +
            "WHERE ((COALESCE(ms.coating_area, (soi.width / 1000.0) * soi.length * ms.schedule_qty) - IFNULL(ms.rewinding_scheduled_area, 0)) > 0 " +
            "   OR ms.status = 'REWINDING_SCHEDULED') " +
            "GROUP BY ms.id " +
            "ORDER BY " +
            "  CASE WHEN ms.schedule_type = 'STOCK' THEN 0 ELSE 1 END ASC, " +
            "  CASE WHEN ms.schedule_type = 'STOCK' THEN ms.id END ASC, " +
            "  COALESCE(eo.start_time, CONCAT(ms.coating_schedule_date, ' 08:00:00'), ms.coating_date, ms.rewinding_date, ms.created_at) ASC, " +
            "  o.delivery_date ASC")
    List<Map<String, Object>> selectCoatingCompletedOrders();

    /**
     * 分页查询已完成涂布待复卷的订单
     */
    @Select("SELECT " +
            "ms.id AS schedule_id, " +
            "ms.order_detail_id, " +
            "ms.status, " +
            "ms.schedule_type, " +
            "ms.schedule_qty, " +
            "ms.coating_date, " +
            "ms.coating_schedule_date, " +
            "eo.start_time AS coating_start_time, " +
            "eo.end_time AS coating_end_time, " +
            "COALESCE(ms.coating_area, (soi.width / 1000.0) * soi.length * ms.schedule_qty) AS coating_area, " +
            "COALESCE(ms.coating_length, soi.length) AS coating_length, " +
            "COALESCE(ms.coating_width, 1040) AS coating_width, " +
            "ms.coating_equipment, " +
            "ms.coating_equipment AS equipment_name, " +
            "ms.rewinding_equipment, " +
            "ms.rewinding_width, " +
            "eo_rw.start_time AS rewinding_start_time, " +
            "eo_rw.end_time AS rewinding_end_time, " +
            "eo_rw.duration_minutes AS rewinding_duration_minutes, " +
            "IFNULL(ms.rewinding_scheduled_area, 0) AS rewinding_scheduled_area, " +
            "(COALESCE(ms.coating_area, (soi.width / 1000.0) * soi.length * ms.schedule_qty) - IFNULL(ms.rewinding_scheduled_area, 0)) AS remaining_coating_area, " +
            "ms.stock_allocations, " +
            "soi.material_code, " +
            "soi.material_name, " +
            "soi.width, " +
            "soi.length, " +
            "soi.thickness, " +
            "o.order_no, " +
            "COALESCE(GROUP_CONCAT(DISTINCT ca.order_no ORDER BY ca.sort_no SEPARATOR ','), o.order_no) AS related_order_nos, " +
            "o.customer AS customer_name, " +
            "o.delivery_date " +
            "FROM manual_schedule ms " +
            "JOIN (" +
            "  SELECT MAX(id) AS id " +
            "  FROM manual_schedule " +
            "  WHERE (" +
            "    (schedule_type = 'COATING' AND status = 'COATING_SCHEDULED' AND coating_date IS NOT NULL) " +
            "    OR (schedule_type = 'COATING' AND status = 'REWINDING_SCHEDULED') " +
            "    OR (schedule_type = 'STOCK' AND status = 'REWINDING_SCHEDULED')" +
            "  ) " +
            "  GROUP BY order_detail_id" +
            ") latest ON latest.id = ms.id " +
            "JOIN sales_order_items soi ON ms.order_detail_id = soi.id " +
            "JOIN sales_orders o ON soi.order_id = o.id " +
            "LEFT JOIN equipment_occupation eo ON eo.schedule_id = ms.id AND eo.process_type = 'COATING' AND eo.status IN ('PLANNED','RUNNING','FINISHED') " +
            "LEFT JOIN equipment_occupation eo_rw ON eo_rw.schedule_id = ms.id AND eo_rw.process_type = 'REWINDING' AND eo_rw.status IN ('PLANNED','RUNNING','FINISHED') " +
            "LEFT JOIN manual_schedule_coating_allocation ca ON ca.schedule_id = ms.id AND ca.included_flag = 1 " +
            "WHERE ((COALESCE(ms.coating_area, (soi.width / 1000.0) * soi.length * ms.schedule_qty) - IFNULL(ms.rewinding_scheduled_area, 0)) > 0 " +
            "   OR ms.status = 'REWINDING_SCHEDULED') " +
            "GROUP BY ms.id " +
            "ORDER BY " +
            "  CASE WHEN ms.schedule_type = 'STOCK' THEN 0 ELSE 1 END ASC, " +
            "  CASE WHEN ms.schedule_type = 'STOCK' THEN ms.id END ASC, " +
            "  COALESCE(eo.start_time, CONCAT(ms.coating_schedule_date, ' 08:00:00'), ms.coating_date, ms.rewinding_date, ms.created_at) ASC, " +
            "  o.delivery_date ASC")
    List<Map<String, Object>> selectCoatingCompletedOrdersPage(Page<Map<String, Object>> page);

    /**
     * 统计已完成涂布待复卷订单总数
     */
    @Select("SELECT COUNT(1) " +
            "FROM manual_schedule ms " +
            "JOIN (" +
            "  SELECT MAX(id) AS id " +
            "  FROM manual_schedule " +
            "  WHERE ((schedule_type = 'COATING' AND status = 'COATING_SCHEDULED' AND coating_date IS NOT NULL) " +
            "     OR (schedule_type = 'COATING' AND status = 'REWINDING_SCHEDULED') " +
            "     OR (schedule_type = 'STOCK' AND status = 'REWINDING_SCHEDULED')) " +
            "  GROUP BY order_detail_id" +
            ") latest ON latest.id = ms.id " +
            "JOIN sales_order_items soi ON ms.order_detail_id = soi.id " +
            "WHERE ((COALESCE(ms.coating_area, (soi.width / 1000.0) * soi.length * ms.schedule_qty) - IFNULL(ms.rewinding_scheduled_area, 0)) > 0 " +
            "   OR ms.status = 'REWINDING_SCHEDULED')")
    Long selectCoatingCompletedOrdersCount();

    /**
     * 查询已锁定库存的订单列表
     */
    @Select("SELECT " +
            "ms.id AS schedule_id, " +
            "ms.order_no, " +
            "ms.order_detail_id, " +
            "ms.schedule_type, " +
            "ms.status, " +
            "ms.stock_allocations, " +
            "ms.rewinding_date, " +
            "ms.created_at, " +
            "soi.material_code, " +
            "soi.material_name, " +
            "soi.thickness, " +
            "soi.width, " +
            "soi.length, " +
            "o.customer AS customer_name " +
            "FROM manual_schedule ms " +
            "JOIN sales_order_items soi ON ms.order_detail_id = soi.id " +
            "JOIN sales_orders o ON soi.order_id = o.id " +
            "WHERE ms.schedule_type = 'STOCK' " +
            "AND ms.stock_allocations IS NOT NULL " +
            "AND ms.stock_allocations <> '' " +
            "ORDER BY ms.created_at DESC")
    List<Map<String, Object>> selectLockedStocks();

    /**
     * 查询复卷已排列表（不限制剩余面积）
     */
    @Select("SELECT " +
            "ms.id AS schedule_id, " +
            "ms.order_detail_id, " +
            "ms.schedule_type, " +
            "ms.schedule_qty, " +
            "ms.rewinding_date, " +
            "eo.start_time AS rewinding_start_time, " +
            "eo.end_time AS rewinding_end_time, " +
            "eo.duration_minutes AS rewinding_duration_minutes, " +
            "ms.rewinding_equipment, " +
            "COALESCE(ms.coating_area, (soi.width / 1000.0) * soi.length * ms.schedule_qty) AS coating_area, " +
            "ms.stock_allocations, " +
            "soi.material_code, " +
            "soi.material_name, " +
            "soi.width, " +
            "soi.length, " +
            "soi.thickness, " +
            "o.order_no, " +
            "o.customer AS customer_name, " +
            "o.delivery_date " +
            "FROM manual_schedule ms " +
            "JOIN (" +
            "  SELECT MAX(id) AS id " +
            "  FROM manual_schedule " +
            "  WHERE status = 'REWINDING_SCHEDULED' " +
            "  GROUP BY order_detail_id" +
            ") latest ON latest.id = ms.id " +
            "JOIN sales_order_items soi ON ms.order_detail_id = soi.id " +
            "JOIN sales_orders o ON soi.order_id = o.id " +
            "LEFT JOIN equipment_occupation eo ON eo.schedule_id = ms.id AND eo.process_type = 'REWINDING' AND eo.status IN ('PLANNED','RUNNING','FINISHED') " +
            "ORDER BY COALESCE(eo.start_time, ms.rewinding_date, ms.created_at) ASC, o.delivery_date ASC")
    List<Map<String, Object>> selectRewindingSchedules();

    /**
     * 分页查询复卷已排列表
     */
    @Select("SELECT " +
            "ms.id AS schedule_id, " +
            "ms.order_detail_id, " +
            "ms.schedule_type, " +
            "ms.schedule_qty, " +
            "ms.rewinding_date, " +
            "eo.start_time AS rewinding_start_time, " +
            "eo.end_time AS rewinding_end_time, " +
            "eo.duration_minutes AS rewinding_duration_minutes, " +
            "ms.rewinding_equipment, " +
            "COALESCE(ms.coating_area, (soi.width / 1000.0) * soi.length * ms.schedule_qty) AS coating_area, " +
            "ms.stock_allocations, " +
            "soi.material_code, " +
            "soi.material_name, " +
            "soi.width, " +
            "soi.length, " +
            "soi.thickness, " +
            "o.order_no, " +
            "o.customer AS customer_name, " +
            "o.delivery_date " +
            "FROM manual_schedule ms " +
            "JOIN (" +
            "  SELECT MAX(id) AS id " +
            "  FROM manual_schedule " +
            "  WHERE status = 'REWINDING_SCHEDULED' " +
            "  GROUP BY order_detail_id" +
            ") latest ON latest.id = ms.id " +
            "JOIN sales_order_items soi ON ms.order_detail_id = soi.id " +
            "JOIN sales_orders o ON soi.order_id = o.id " +
            "LEFT JOIN equipment_occupation eo ON eo.schedule_id = ms.id AND eo.process_type = 'REWINDING' AND eo.status IN ('PLANNED','RUNNING','FINISHED') " +
            "ORDER BY COALESCE(eo.start_time, ms.rewinding_date, ms.created_at) ASC, o.delivery_date ASC")
    List<Map<String, Object>> selectRewindingSchedulesPage(Page<Map<String, Object>> page);

    /**
     * 按订单明细ID回写复卷日期
     */
    @Update("UPDATE sales_order_items " +
            "SET rewinding_date = #{rewindingDate} " +
            "WHERE id = #{orderDetailId} AND is_deleted = 0")
    int updateSalesOrderRewindingDateByDetailId(
            @Param("orderDetailId") Long orderDetailId,
            @Param("rewindingDate") String rewindingDate);

    /**
     * 按订单明细ID回写分切/包装日期
     */
    @Update("UPDATE sales_order_items " +
            "SET packaging_date = #{packagingDate} " +
            "WHERE id = #{orderDetailId} AND is_deleted = 0")
    int updateSalesOrderPackagingDateByDetailId(
            @Param("orderDetailId") Long orderDetailId,
            @Param("packagingDate") String packagingDate);

    /**
     * 统计复卷已排总数
     */
    @Select("SELECT COUNT(1) " +
            "FROM manual_schedule ms " +
            "JOIN (" +
            "  SELECT MAX(id) AS id " +
            "  FROM manual_schedule " +
            "  WHERE status = 'REWINDING_SCHEDULED' " +
            "  GROUP BY order_detail_id" +
            ") latest ON latest.id = ms.id")
    Long selectRewindingSchedulesCount();

    /**
     * 查询分切已排（包装日期已选择）订单列表
     */
    @Select("SELECT " +
            "ms.id AS schedule_id, " +
            "ms.order_detail_id, " +
            "ms.status, " +
            "ms.schedule_qty, " +
            "ms.rewinding_date, " +
            "ms.rewinding_equipment, " +
            "eo_sl.equipment_code AS slitting_equipment, " +
            "ms.packaging_date, " +
            "ms.slitting_schedule_date, " +
            "(SELECT COALESCE(sp.production_speed, sp.slitting_speed) " +
            "   FROM slitting_process_params sp " +
            "  WHERE sp.status = 1 " +
            "    AND sp.total_thickness = soi.thickness " +
            "    AND sp.process_length = soi.length " +
            "    AND sp.process_width = soi.width " +
            "  ORDER BY sp.id DESC LIMIT 1) AS slitting_speed, " +
            "eo_rw.start_time AS rewinding_start_time, " +
            "eo_rw.end_time AS rewinding_end_time, " +
            "eo_rw.duration_minutes AS rewinding_duration_minutes, " +
            "eo_sl.start_time AS slitting_start_time, " +
            "eo_sl.end_time AS slitting_end_time, " +
            "eo_sl.duration_minutes AS slitting_duration_minutes, " +
            "soi.material_code, " +
            "soi.material_name, " +
            "soi.width, " +
            "soi.length, " +
            "soi.thickness, " +
            "o.order_no, " +
            "o.customer AS customer_name, " +
            "o.delivery_date " +
            "FROM manual_schedule ms " +
            "JOIN (" +
            "  SELECT MAX(id) AS id " +
            "  FROM manual_schedule " +
            "  WHERE status IN ('REWINDING_SCHEDULED','CONFIRMED') " +
            "     OR (IFNULL(rewinding_scheduled_area, 0) > 0 AND (coating_area IS NULL OR IFNULL(rewinding_scheduled_area, 0) >= coating_area)) " +
            "     OR packaging_date IS NOT NULL OR slitting_schedule_date IS NOT NULL " +
            "  GROUP BY order_detail_id" +
            ") latest ON latest.id = ms.id " +
            "JOIN sales_order_items soi ON ms.order_detail_id = soi.id " +
            "JOIN sales_orders o ON soi.order_id = o.id " +
            "LEFT JOIN equipment_occupation eo_rw ON eo_rw.schedule_id = ms.id AND eo_rw.process_type = 'REWINDING' AND eo_rw.status IN ('PLANNED','RUNNING','FINISHED') " +
            "LEFT JOIN equipment_occupation eo_sl ON eo_sl.schedule_id = ms.id AND eo_sl.process_type = 'SLITTING' AND eo_sl.status IN ('PLANNED','RUNNING','FINISHED') " +
            "ORDER BY COALESCE(ms.packaging_date, ms.slitting_schedule_date) ASC, o.delivery_date ASC")
    List<Map<String, Object>> selectSlittingSchedules();

    /**
     * 分页查询分切已排列表
     */
    @Select("SELECT " +
            "ms.id AS schedule_id, " +
            "ms.order_detail_id, " +
            "ms.status, " +
            "ms.schedule_qty, " +
            "ms.rewinding_date, " +
            "ms.rewinding_equipment, " +
            "eo_sl.equipment_code AS slitting_equipment, " +
            "ms.packaging_date, " +
            "ms.slitting_schedule_date, " +
            "(SELECT COALESCE(sp.production_speed, sp.slitting_speed) " +
            "   FROM slitting_process_params sp " +
            "  WHERE sp.status = 1 " +
            "    AND sp.total_thickness = soi.thickness " +
            "    AND sp.process_length = soi.length " +
            "    AND sp.process_width = soi.width " +
            "  ORDER BY sp.id DESC LIMIT 1) AS slitting_speed, " +
            "eo_rw.start_time AS rewinding_start_time, " +
            "eo_rw.end_time AS rewinding_end_time, " +
            "eo_rw.duration_minutes AS rewinding_duration_minutes, " +
            "eo_sl.start_time AS slitting_start_time, " +
            "eo_sl.end_time AS slitting_end_time, " +
            "eo_sl.duration_minutes AS slitting_duration_minutes, " +
            "soi.material_code, " +
            "soi.material_name, " +
            "soi.width, " +
            "soi.length, " +
            "soi.thickness, " +
            "o.order_no, " +
            "o.customer AS customer_name, " +
            "o.delivery_date " +
            "FROM manual_schedule ms " +
            "JOIN (" +
            "  SELECT MAX(id) AS id " +
            "  FROM manual_schedule " +
            "  WHERE status IN ('REWINDING_SCHEDULED','CONFIRMED') " +
            "     OR (IFNULL(rewinding_scheduled_area, 0) > 0 AND (coating_area IS NULL OR IFNULL(rewinding_scheduled_area, 0) >= coating_area)) " +
            "     OR packaging_date IS NOT NULL OR slitting_schedule_date IS NOT NULL " +
            "  GROUP BY order_detail_id" +
            ") latest ON latest.id = ms.id " +
            "JOIN sales_order_items soi ON ms.order_detail_id = soi.id " +
            "JOIN sales_orders o ON soi.order_id = o.id " +
            "LEFT JOIN equipment_occupation eo_rw ON eo_rw.schedule_id = ms.id AND eo_rw.process_type = 'REWINDING' AND eo_rw.status IN ('PLANNED','RUNNING','FINISHED') " +
            "LEFT JOIN equipment_occupation eo_sl ON eo_sl.schedule_id = ms.id AND eo_sl.process_type = 'SLITTING' AND eo_sl.status IN ('PLANNED','RUNNING','FINISHED') " +
            "WHERE o.is_deleted = 0 AND soi.is_deleted = 0 " +
            "AND (o.status IS NULL OR LOWER(o.status) NOT IN ('cancelled','canceled','closed')) " +
            "AND (#{orderNo} IS NULL OR #{orderNo} = '' OR o.order_no LIKE CONCAT('%', #{orderNo}, '%')) " +
            "ORDER BY COALESCE(ms.packaging_date, ms.slitting_schedule_date) ASC, o.delivery_date ASC")
    List<Map<String, Object>> selectSlittingSchedulesPage(Page<Map<String, Object>> page,
                                                          @Param("orderNo") String orderNo);

    /**
     * 统计分切已排总数
     */
    @Select("SELECT COUNT(1) " +
            "FROM manual_schedule ms " +
            "JOIN (" +
            "  SELECT MAX(id) AS id " +
            "  FROM manual_schedule " +
            "  WHERE status IN ('REWINDING_SCHEDULED','CONFIRMED') " +
            "     OR (IFNULL(rewinding_scheduled_area, 0) > 0 AND (coating_area IS NULL OR IFNULL(rewinding_scheduled_area, 0) >= coating_area)) " +
            "     OR packaging_date IS NOT NULL OR slitting_schedule_date IS NOT NULL " +
            "  GROUP BY order_detail_id" +
            ") latest ON latest.id = ms.id " +
            "JOIN sales_order_items soi ON ms.order_detail_id = soi.id " +
            "JOIN sales_orders o ON soi.order_id = o.id " +
            "WHERE o.is_deleted = 0 AND soi.is_deleted = 0 " +
            "AND (o.status IS NULL OR LOWER(o.status) NOT IN ('cancelled','canceled','closed')) " +
            "AND (#{orderNo} IS NULL OR #{orderNo} = '' OR o.order_no LIKE CONCAT('%', #{orderNo}, '%'))")
    Long selectSlittingSchedulesCount(@Param("orderNo") String orderNo);

        /**
         * 查询订单明细最新排程ID
         */
        @Select("SELECT MAX(id) FROM manual_schedule WHERE order_detail_id = #{orderDetailId}")
        Long selectLatestScheduleId(@Param("orderDetailId") Long orderDetailId);
    
    /**
     * 查询指定料号规格的库存（先进先出排序）
     */
    @Select("SELECT " +
            "id AS stock_id, " +
            "material_code, " +
            "batch_no, " +
            "qr_code, " +
            "width, " +
            "length, " +
            "thickness, " +
            "CASE " +
            "  WHEN IFNULL(available_area, 0) > 0 AND width > 0 AND length > 0 " +
            "    THEN LEAST(total_rolls, FLOOR(IFNULL(available_area, 0) / ((width / 1000.0) * length))) " +
            "  ELSE 0 " +
            "END AS available_rolls, " +
            "IFNULL(available_area, 0) AS available_area, " +
            "location, " +
            "prod_date, " +
            "spec_desc " +
            "FROM tape_stock " +
            "WHERE material_code LIKE CONCAT(#{materialCode}, '%') " +
            "AND width >= #{width} " +
            "AND thickness = #{thickness} " +
            "AND total_rolls > 0 " +
            "AND IFNULL(available_area, 0) > 0 " +
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
            "AND (o.status IS NULL OR LOWER(o.status) NOT IN ('completed','cancelled','canceled','closed')) " +
            "AND o.is_deleted = 0 AND soi.is_deleted = 0 " +
            "AND (soi.rolls - IFNULL(soi.scheduled_qty, 0)) > 0 " +
            "AND NOT EXISTS (" +
            "  SELECT 1 FROM manual_schedule_coating_allocation ca2 " +
            "  JOIN manual_schedule ms2 ON ms2.id = ca2.schedule_id " +
            "  WHERE ca2.included_flag = 1 " +
            "    AND ca2.order_no COLLATE utf8mb4_unicode_ci = o.order_no COLLATE utf8mb4_unicode_ci " +
            "    AND ca2.material_code COLLATE utf8mb4_unicode_ci = soi.material_code COLLATE utf8mb4_unicode_ci " +
            "    AND (ca2.thickness IS NULL OR ca2.thickness = soi.thickness) " +
            "    AND ms2.schedule_type = 'COATING' " +
            "    AND ms2.status IN ('PENDING','COATING_SCHEDULED','REWINDING_SCHEDULED')" +
            ") " +
            "GROUP BY soi.material_code, soi.thickness " +
            "HAVING total_required_qty > 0")
    Map<String, Object> calculateCoatingRequirement(
            @Param("orderNo") String orderNo,
            @Param("materialCode") String materialCode,
            @Param("thickness") Integer thickness);

    /**
     * 查询涂布需求明细（按订单展开）
     */
    @Select("SELECT " +
            "o.order_no, " +
            "soi.material_code, " +
            "soi.thickness, " +
            "(soi.rolls - IFNULL(soi.scheduled_qty, 0)) AS remaining_qty, " +
            "ROUND((soi.width / 1000.0) * soi.length * (soi.rolls - IFNULL(soi.scheduled_qty, 0)), 2) AS remaining_area, " +
            "ROUND(IFNULL(cp.total_score, 0) + IFNULL(DATEDIFF(CURDATE(), o.order_date), 0), 1) AS priority_score " +
            "FROM sales_order_items soi " +
            "JOIN sales_orders o ON soi.order_id = o.id " +
            "LEFT JOIN customers c ON o.customer COLLATE utf8mb4_unicode_ci = c.customer_code COLLATE utf8mb4_unicode_ci " +
            "LEFT JOIN (" +
            "  SELECT p1.customer_id, p1.total_score " +
            "  FROM order_customer_priority p1 " +
            "  INNER JOIN (" +
            "    SELECT customer_id, MAX(id) AS max_id " +
            "    FROM order_customer_priority " +
            "    GROUP BY customer_id" +
            "  ) p2 ON p1.customer_id = p2.customer_id AND p1.id = p2.max_id" +
            ") cp ON cp.customer_id = c.id " +
            "WHERE soi.material_code = #{materialCode} " +
            "AND soi.thickness = #{thickness} " +
            "AND (o.status IS NULL OR LOWER(o.status) NOT IN ('completed','cancelled','canceled','closed')) " +
            "AND o.is_deleted = 0 AND soi.is_deleted = 0 " +
            "AND (soi.rolls - IFNULL(soi.scheduled_qty, 0)) > 0 " +
            "AND (" +
            "  NOT EXISTS (" +
            "    SELECT 1 FROM manual_schedule_coating_allocation ca2 " +
            "    JOIN manual_schedule ms2 ON ms2.id = ca2.schedule_id " +
            "    WHERE ca2.included_flag = 1 " +
            "      AND ca2.order_no COLLATE utf8mb4_unicode_ci = o.order_no COLLATE utf8mb4_unicode_ci " +
            "      AND ca2.material_code COLLATE utf8mb4_unicode_ci = soi.material_code COLLATE utf8mb4_unicode_ci " +
            "      AND (ca2.thickness IS NULL OR ca2.thickness = soi.thickness) " +
            "      AND ms2.schedule_type = 'COATING' " +
            "      AND ms2.status IN ('PENDING','COATING_SCHEDULED','REWINDING_SCHEDULED')" +
            "  ) " +
            "  OR (#{orderNo} IS NOT NULL AND #{orderNo} <> '' AND o.order_no COLLATE utf8mb4_unicode_ci = #{orderNo} COLLATE utf8mb4_unicode_ci)" +
            ") " +
            "ORDER BY priority_score DESC, o.delivery_date ASC, o.order_no ASC")
    List<Map<String, Object>> selectCoatingRequirementDetails(
            @Param("orderNo") String orderNo,
            @Param("materialCode") String materialCode,
            @Param("thickness") Integer thickness);

    /**
     * 校验同一订单/料号/厚度是否已被其他有效涂布计划覆盖，防止重复建计划
     */
    @Select("SELECT COUNT(1) " +
            "FROM manual_schedule_coating_allocation ca " +
            "JOIN manual_schedule ms ON ms.id = ca.schedule_id " +
            "WHERE ca.included_flag = 1 " +
            "AND ca.order_no COLLATE utf8mb4_unicode_ci = #{orderNo} " +
            "AND ca.material_code COLLATE utf8mb4_unicode_ci = #{materialCode} " +
            "AND (ca.thickness IS NULL OR ca.thickness = #{thickness}) " +
            "AND ms.schedule_type = 'COATING' " +
            "AND ms.status IN ('PENDING','COATING_SCHEDULED','REWINDING_SCHEDULED') " +
            "AND ms.id <> #{excludeScheduleId}")
    int countActiveCoatingAllocationOverlap(@Param("orderNo") String orderNo,
                                            @Param("materialCode") String materialCode,
                                            @Param("thickness") BigDecimal thickness,
                                            @Param("excludeScheduleId") Long excludeScheduleId);

    /**
     * 查询涂布排程列表
     */
    @Select("SELECT " +
            "ms.id, " +
            "ms.id AS schedule_id, " +
            "ms.order_no, " +
            "soi.material_code, " +
            "soi.material_name, " +
            "ts.color_name AS color_name, " +
            "ts.color_code AS color_code, " +
            "soi.width, " +
            "soi.length, " +
            "COALESCE(ms.coating_width, soi.width) AS coating_width, " +
            "COALESCE(ms.coating_length, soi.length) AS coating_length, " +
            "soi.thickness, " +
            "ms.schedule_qty, " +
            "COALESCE(NULLIF(ms.coating_area, 0), (soi.width / 1000.0) * soi.length * IFNULL(ms.schedule_qty, 0)) AS coating_area, " +
            "IFNULL((SELECT SUM(r.produced_qty) FROM manual_schedule_process_report r WHERE r.schedule_id = ms.id AND r.process_type = 'COATING' AND r.is_deleted = 0), 0) AS coating_report_qty, " +
            "IFNULL((SELECT SUM(IFNULL(l.locked_area, 0)) FROM schedule_material_lock l WHERE l.schedule_id = ms.id AND l.lock_status IN ('锁定中','已领料','已消耗','已补锁')), 0) AS locked_area, " +
            "GREATEST(COALESCE(NULLIF(ms.coating_area, 0), (soi.width / 1000.0) * soi.length * IFNULL(ms.schedule_qty, 0)) - IFNULL((SELECT SUM(IFNULL(l.locked_area, 0)) FROM schedule_material_lock l WHERE l.schedule_id = ms.id AND l.lock_status IN ('锁定中','已领料','已消耗','已补锁')), 0), 0) AS unlocked_area, " +
            "COALESCE(DATE_FORMAT(eo.start_time, '%Y-%m-%d %H:%i:%s'), CONCAT(ms.coating_schedule_date, ' 08:00:00')) AS coating_schedule_date, " +
            "DATE_FORMAT(eo.end_time, '%Y-%m-%d %H:%i:%s') AS coating_end_time, " +
            "eo.duration_minutes AS coating_duration_minutes, " +
            "ms.coating_equipment, " +
            "ms.coating_date, " +
            "ms.rewinding_date, " +
            "ms.packaging_date, " +
            "ms.status, " +
            "GROUP_CONCAT(DISTINCT so.order_no ORDER BY so.order_no SEPARATOR ',') AS order_nos, " +
            "COALESCE(" +
            "  GROUP_CONCAT(DISTINCT ca.order_no ORDER BY ca.sort_no SEPARATOR ','), " +
            "  GROUP_CONCAT(DISTINCT so.order_no ORDER BY so.order_no SEPARATOR ',')" +
            ") AS related_order_nos " +
            "FROM manual_schedule ms " +
            "JOIN (" +
            "  SELECT MAX(id) AS id " +
            "  FROM manual_schedule " +
            "  WHERE schedule_type = 'COATING' " +
            "  GROUP BY order_detail_id" +
            ") latest ON latest.id = ms.id " +
            "LEFT JOIN sales_order_items soi ON ms.order_detail_id = soi.id " +
            "LEFT JOIN tape_spec ts ON soi.material_code = ts.material_code " +
            "LEFT JOIN sales_orders so ON soi.order_id = so.id " +
            "LEFT JOIN equipment_occupation eo ON eo.schedule_id = ms.id AND eo.process_type = 'COATING' AND eo.status IN ('PLANNED','RUNNING','FINISHED') " +
            "LEFT JOIN manual_schedule_coating_allocation ca ON ca.schedule_id = ms.id AND ca.included_flag = 1 " +
            "WHERE ms.schedule_type = 'COATING' " +
            "AND ms.status IN ('PENDING','COATING_SCHEDULED','REWINDING_SCHEDULED','CONFIRMED') " +
            "AND (COALESCE(ms.coating_area, 0) > 0 OR (ms.coating_area IS NULL AND (soi.width / 1000.0) * soi.length * IFNULL(ms.schedule_qty, 0) > 0)) " +
            "GROUP BY ms.id " +
            "ORDER BY ms.created_at DESC")
    List<Map<String, Object>> selectCoatingSchedules();

    /**
     * 分页查询涂布排程列表
     */
    @Select("SELECT " +
            "ms.id, " +
            "ms.id AS schedule_id, " +
            "ms.order_no, " +
            "soi.material_code, " +
            "soi.material_name, " +
            "ts.color_name AS color_name, " +
            "ts.color_code AS color_code, " +
            "soi.width, " +
            "soi.length, " +
            "COALESCE(ms.coating_width, soi.width) AS coating_width, " +
            "COALESCE(ms.coating_length, soi.length) AS coating_length, " +
            "soi.thickness, " +
            "ms.schedule_qty, " +
            "COALESCE(NULLIF(ms.coating_area, 0), (soi.width / 1000.0) * soi.length * IFNULL(ms.schedule_qty, 0)) AS coating_area, " +
            "IFNULL((SELECT SUM(r.produced_qty) FROM manual_schedule_process_report r WHERE r.schedule_id = ms.id AND r.process_type = 'COATING' AND r.is_deleted = 0), 0) AS coating_report_qty, " +
            "IFNULL((SELECT SUM(IFNULL(l.locked_area, 0)) FROM schedule_material_lock l WHERE l.schedule_id = ms.id AND l.lock_status IN ('锁定中','已领料','已消耗','已补锁')), 0) AS locked_area, " +
            "GREATEST(COALESCE(NULLIF(ms.coating_area, 0), (soi.width / 1000.0) * soi.length * IFNULL(ms.schedule_qty, 0)) - IFNULL((SELECT SUM(IFNULL(l.locked_area, 0)) FROM schedule_material_lock l WHERE l.schedule_id = ms.id AND l.lock_status IN ('锁定中','已领料','已消耗','已补锁')), 0), 0) AS unlocked_area, " +
            "COALESCE(DATE_FORMAT(eo.start_time, '%Y-%m-%d %H:%i:%s'), CONCAT(ms.coating_schedule_date, ' 08:00:00')) AS coating_schedule_date, " +
            "DATE_FORMAT(eo.end_time, '%Y-%m-%d %H:%i:%s') AS coating_end_time, " +
            "eo.duration_minutes AS coating_duration_minutes, " +
            "ms.coating_equipment, " +
            "ms.coating_date, " +
            "ms.rewinding_date, " +
            "ms.packaging_date, " +
            "ms.status, " +
            "GROUP_CONCAT(DISTINCT so.order_no ORDER BY so.order_no SEPARATOR ',') AS order_nos, " +
            "COALESCE(" +
            "  GROUP_CONCAT(DISTINCT ca.order_no ORDER BY ca.sort_no SEPARATOR ','), " +
            "  GROUP_CONCAT(DISTINCT so.order_no ORDER BY so.order_no SEPARATOR ',')" +
            ") AS related_order_nos " +
            "FROM manual_schedule ms " +
            "JOIN (" +
            "  SELECT MAX(id) AS id " +
            "  FROM manual_schedule " +
            "  WHERE schedule_type = 'COATING' " +
            "  GROUP BY order_detail_id" +
            ") latest ON latest.id = ms.id " +
            "LEFT JOIN sales_order_items soi ON ms.order_detail_id = soi.id " +
            "LEFT JOIN tape_spec ts ON soi.material_code = ts.material_code " +
            "LEFT JOIN sales_orders so ON soi.order_id = so.id " +
            "LEFT JOIN equipment_occupation eo ON eo.schedule_id = ms.id AND eo.process_type = 'COATING' AND eo.status IN ('PLANNED','RUNNING','FINISHED') " +
            "LEFT JOIN manual_schedule_coating_allocation ca ON ca.schedule_id = ms.id AND ca.included_flag = 1 " +
            "WHERE ms.schedule_type = 'COATING' " +
            "AND ms.status IN ('PENDING','COATING_SCHEDULED','REWINDING_SCHEDULED','CONFIRMED') " +
            "AND (COALESCE(ms.coating_area, 0) > 0 OR (ms.coating_area IS NULL AND (soi.width / 1000.0) * soi.length * IFNULL(ms.schedule_qty, 0) > 0)) " +
            "GROUP BY ms.id " +
            "ORDER BY ms.created_at DESC")
    List<Map<String, Object>> selectCoatingSchedulesPage(Page<Map<String, Object>> page);

    /**
     * 统计涂布排程总数
     */
    @Select("SELECT COUNT(1) " +
            "FROM manual_schedule ms " +
            "LEFT JOIN sales_order_items soi ON ms.order_detail_id = soi.id " +
            "JOIN (" +
            "  SELECT MAX(id) AS id " +
            "  FROM manual_schedule " +
            "  WHERE schedule_type = 'COATING' " +
            "  GROUP BY order_detail_id" +
            ") latest ON latest.id = ms.id " +
            "WHERE ms.schedule_type = 'COATING' AND ms.status IN ('PENDING','COATING_SCHEDULED','REWINDING_SCHEDULED','CONFIRMED') " +
            "AND (COALESCE(ms.coating_area, 0) > 0 OR (ms.coating_area IS NULL AND (soi.width / 1000.0) * soi.length * IFNULL(ms.schedule_qty, 0) > 0))")
    Long selectCoatingSchedulesCount();

    /**
     * 计划管理-订单->母卷对应关系分页
     */
    @Select("SELECT " +
            "ol.order_no, " +
            "MAX(ol.material_code) AS material_code, " +
            "COUNT(DISTINCT ol.roll_code) AS roll_count, " +
            "ROUND(SUM(IFNULL(ol.locked_area, 0)), 2) AS locked_area_total, " +
            "ROUND(IFNULL((SELECT SUM(IFNULL(ca.included_area, 0)) FROM manual_schedule_coating_allocation ca WHERE ca.order_no = ol.order_no AND ca.included_flag = 1), 0), 2) AS planned_area_total, " +
            "ROUND(GREATEST(IFNULL((SELECT SUM(IFNULL(ca.included_area, 0)) FROM manual_schedule_coating_allocation ca WHERE ca.order_no = ol.order_no AND ca.included_flag = 1), 0) - SUM(IFNULL(ol.locked_area, 0)), 0), 2) AS unlocked_area_total, " +
            "ROUND(IFNULL((SELECT SUM(IFNULL(mi.actual_area, 0)) FROM manual_schedule_process_material_issue mi WHERE mi.roll_code IN (SELECT ol2.roll_code FROM manual_schedule_coating_order_lock ol2 WHERE ol2.order_no = ol.order_no AND ol2.is_deleted = 0) AND mi.is_deleted = 0), 0), 2) AS issue_area_total, " +
            "ROUND(IFNULL((SELECT SUM(IFNULL(mi.loss_area, 0)) FROM manual_schedule_process_material_issue mi WHERE mi.roll_code IN (SELECT ol2.roll_code FROM manual_schedule_coating_order_lock ol2 WHERE ol2.order_no = ol.order_no AND ol2.is_deleted = 0) AND mi.is_deleted = 0), 0), 2) AS loss_area_total " +
            "FROM manual_schedule_coating_order_lock ol " +
            "WHERE ol.is_deleted = 0 " +
            "AND (#{orderNo} IS NULL OR #{orderNo} = '' OR ol.order_no LIKE CONCAT('%', #{orderNo}, '%')) " +
            "AND (#{materialCode} IS NULL OR #{materialCode} = '' OR ol.material_code LIKE CONCAT('%', #{materialCode}, '%')) " +
            "AND (#{rollCode} IS NULL OR #{rollCode} = '' OR ol.roll_code LIKE CONCAT('%', #{rollCode}, '%')) " +
            "GROUP BY ol.order_no " +
            "ORDER BY MAX(ol.id) DESC")
    List<Map<String, Object>> selectOrderMaterialRelationPage(Page<Map<String, Object>> page,
                                                               @Param("orderNo") String orderNo,
                                                               @Param("materialCode") String materialCode,
                                                               @Param("rollCode") String rollCode);

    @Select("SELECT COUNT(1) FROM (" +
            "SELECT ol.order_no " +
            "FROM manual_schedule_coating_order_lock ol " +
            "WHERE ol.is_deleted = 0 " +
            "AND (#{orderNo} IS NULL OR #{orderNo} = '' OR ol.order_no LIKE CONCAT('%', #{orderNo}, '%')) " +
            "AND (#{materialCode} IS NULL OR #{materialCode} = '' OR ol.material_code LIKE CONCAT('%', #{materialCode}, '%')) " +
            "AND (#{rollCode} IS NULL OR #{rollCode} = '' OR ol.roll_code LIKE CONCAT('%', #{rollCode}, '%')) " +
            "GROUP BY ol.order_no" +
            ") t")
    Long selectOrderMaterialRelationCount(@Param("orderNo") String orderNo,
                                          @Param("materialCode") String materialCode,
                                          @Param("rollCode") String rollCode);

    /**
     * 计划管理-母卷->订单对应关系分页
     */
    @Select("SELECT " +
            "ol.roll_code, " +
            "MAX(ol.material_code) AS material_code, " +
            "ROUND(IFNULL(MAX(cr.area), 0), 2) AS roll_area, " +
            "COUNT(DISTINCT ol.order_no) AS order_count, " +
            "ROUND(SUM(IFNULL(ol.locked_area, 0)), 2) AS locked_area_total, " +
            "ROUND(GREATEST(IFNULL(MAX(cr.area), 0) - SUM(IFNULL(ol.locked_area, 0)), 0), 2) AS unallocated_area, " +
            "GROUP_CONCAT(DISTINCT ol.order_no ORDER BY ol.order_no SEPARATOR ',') AS order_nos, " +
            "ROUND(IFNULL((SELECT SUM(IFNULL(mi.actual_area, 0)) FROM manual_schedule_process_material_issue mi WHERE mi.roll_code = ol.roll_code AND mi.is_deleted = 0), 0), 2) AS issue_area_total, " +
            "ROUND(IFNULL((SELECT SUM(IFNULL(mi.loss_area, 0)) FROM manual_schedule_process_material_issue mi WHERE mi.roll_code = ol.roll_code AND mi.is_deleted = 0), 0), 2) AS loss_area_total " +
            "FROM manual_schedule_coating_order_lock ol " +
            "LEFT JOIN manual_schedule_coating_roll cr ON cr.report_id = ol.report_id AND cr.roll_code = ol.roll_code AND cr.is_deleted = 0 " +
            "WHERE ol.is_deleted = 0 " +
            "AND (#{orderNo} IS NULL OR #{orderNo} = '' OR ol.order_no LIKE CONCAT('%', #{orderNo}, '%')) " +
            "AND (#{materialCode} IS NULL OR #{materialCode} = '' OR ol.material_code LIKE CONCAT('%', #{materialCode}, '%')) " +
            "AND (#{rollCode} IS NULL OR #{rollCode} = '' OR ol.roll_code LIKE CONCAT('%', #{rollCode}, '%')) " +
            "GROUP BY ol.roll_code " +
            "ORDER BY MAX(ol.id) DESC")
    List<Map<String, Object>> selectMaterialOrderRelationPage(Page<Map<String, Object>> page,
                                                               @Param("orderNo") String orderNo,
                                                               @Param("materialCode") String materialCode,
                                                               @Param("rollCode") String rollCode);

    @Select("SELECT COUNT(1) FROM (" +
            "SELECT ol.roll_code " +
            "FROM manual_schedule_coating_order_lock ol " +
            "WHERE ol.is_deleted = 0 " +
            "AND (#{orderNo} IS NULL OR #{orderNo} = '' OR ol.order_no LIKE CONCAT('%', #{orderNo}, '%')) " +
            "AND (#{materialCode} IS NULL OR #{materialCode} = '' OR ol.material_code LIKE CONCAT('%', #{materialCode}, '%')) " +
            "AND (#{rollCode} IS NULL OR #{rollCode} = '' OR ol.roll_code LIKE CONCAT('%', #{rollCode}, '%')) " +
            "GROUP BY ol.roll_code" +
            ") t")
    Long selectMaterialOrderRelationCount(@Param("orderNo") String orderNo,
                                          @Param("materialCode") String materialCode,
                                          @Param("rollCode") String rollCode);

    /**
     * 计划管理-关系汇总指标
     */
    @Select("SELECT " +
            "IFNULL((SELECT COUNT(DISTINCT order_no) FROM manual_schedule_coating_order_lock WHERE is_deleted = 0), 0) AS order_count, " +
            "IFNULL((SELECT COUNT(DISTINCT roll_code) FROM manual_schedule_coating_order_lock WHERE is_deleted = 0), 0) AS roll_count, " +
            "ROUND(IFNULL((SELECT SUM(IFNULL(locked_area, 0)) FROM manual_schedule_coating_order_lock WHERE is_deleted = 0), 0), 2) AS locked_area_total, " +
            "ROUND(IFNULL((SELECT SUM(IFNULL(included_area, 0)) FROM manual_schedule_coating_allocation WHERE included_flag = 1), 0), 2) AS planned_area_total, " +
            "ROUND(IFNULL((SELECT SUM(IFNULL(actual_area, 0)) FROM manual_schedule_process_material_issue WHERE is_deleted = 0), 0), 2) AS issue_area_total, " +
            "ROUND(IFNULL((SELECT SUM(IFNULL(loss_area, 0)) FROM manual_schedule_process_material_issue WHERE is_deleted = 0), 0), 2) AS loss_area_total")
    Map<String, Object> selectPlanRelationSummary();

    /**
     * 新增工序报工记录
     */
    @Insert("INSERT INTO manual_schedule_process_report(" +
            "schedule_id, process_type, start_time, end_time, produced_qty, operator_name, proceed_next_process, remark, created_at, updated_at, is_deleted" +
            ") VALUES (" +
            "#{scheduleId}, #{processType}, #{startTime}, #{endTime}, #{producedQty}, #{operatorName}, #{proceedNextProcess}, #{remark}, NOW(), NOW(), 0" +
            ")")
    int insertProcessReport(@Param("scheduleId") Long scheduleId,
                            @Param("processType") String processType,
                            @Param("startTime") java.time.LocalDateTime startTime,
                            @Param("endTime") java.time.LocalDateTime endTime,
                            @Param("producedQty") BigDecimal producedQty,
                            @Param("operatorName") String operatorName,
                            @Param("proceedNextProcess") Integer proceedNextProcess,
                            @Param("remark") String remark);

        @Select("SELECT LAST_INSERT_ID()")
        Long selectLastInsertId();

    /**
     * 查询工序报工记录
     */
    @Select("SELECT " +
            "id, schedule_id, process_type, " +
            "DATE_FORMAT(start_time, '%Y-%m-%d %H:%i:%s') AS start_time, " +
            "DATE_FORMAT(end_time, '%Y-%m-%d %H:%i:%s') AS end_time, " +
            "produced_qty, operator_name, proceed_next_process, remark, " +
            "(SELECT COUNT(1) FROM manual_schedule_coating_roll cr WHERE cr.report_id = manual_schedule_process_report.id AND cr.is_deleted = 0) AS produced_roll_count, " +
            "(SELECT COUNT(1) FROM manual_schedule_process_material_issue mi WHERE mi.report_id = manual_schedule_process_report.id AND mi.is_deleted = 0) AS material_issue_count, " +
            "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at " +
            "FROM manual_schedule_process_report " +
            "WHERE schedule_id = #{scheduleId} " +
            "AND process_type = #{processType} " +
            "AND is_deleted = 0 " +
            "ORDER BY start_time DESC, id DESC")
    List<Map<String, Object>> selectProcessReports(@Param("scheduleId") Long scheduleId,
                                                   @Param("processType") String processType);

    /**
     * 查询单条报工记录
     */
    @Select("SELECT id, schedule_id, process_type, start_time, end_time, produced_qty, operator_name, proceed_next_process, remark " +
            "FROM manual_schedule_process_report WHERE id = #{reportId} AND is_deleted = 0")
    Map<String, Object> selectProcessReportById(@Param("reportId") Long reportId);

    /**
     * 更新工序报工记录
     */
    @Update("UPDATE manual_schedule_process_report SET " +
            "start_time = #{startTime}, end_time = #{endTime}, produced_qty = #{producedQty}, " +
            "operator_name = #{operatorName}, proceed_next_process = #{proceedNextProcess}, remark = #{remark}, " +
            "updated_at = NOW() WHERE id = #{reportId} AND is_deleted = 0")
    int updateProcessReport(@Param("reportId") Long reportId,
                            @Param("startTime") java.time.LocalDateTime startTime,
                            @Param("endTime") java.time.LocalDateTime endTime,
                            @Param("producedQty") BigDecimal producedQty,
                            @Param("operatorName") String operatorName,
                            @Param("proceedNextProcess") Integer proceedNextProcess,
                            @Param("remark") String remark);

    /**
     * 删除工序报工记录（软删除）
     */
    @Update("UPDATE manual_schedule_process_report SET is_deleted = 1, updated_at = NOW() WHERE id = #{reportId} AND is_deleted = 0")
    int deleteProcessReport(@Param("reportId") Long reportId);

    @Update("UPDATE manual_schedule_coating_roll SET is_deleted = 1, updated_at = NOW() WHERE report_id = #{reportId} AND is_deleted = 0")
    int deleteCoatingRollsByReportId(@Param("reportId") Long reportId);

    @Update("UPDATE manual_schedule_process_material_issue SET is_deleted = 1, updated_at = NOW() WHERE report_id = #{reportId} AND is_deleted = 0")
    int deleteMaterialIssuesByReportId(@Param("reportId") Long reportId);

    @Update("UPDATE manual_schedule_coating_order_lock SET is_deleted = 1, updated_at = NOW() WHERE report_id = #{reportId} AND is_deleted = 0")
    int deleteCoatingOrderLocksByReportId(@Param("reportId") Long reportId);

    /**
     * 汇总工序已报工数量
     */
    @Select("SELECT IFNULL(SUM(produced_qty), 0) FROM manual_schedule_process_report " +
            "WHERE schedule_id = #{scheduleId} AND process_type = #{processType} AND is_deleted = 0")
    BigDecimal sumProcessReportedQty(@Param("scheduleId") Long scheduleId,
                                     @Param("processType") String processType);

    @Select("SELECT IFNULL(SUM(r.produced_qty), 0) " +
            "FROM manual_schedule_process_report r " +
            "JOIN manual_schedule ms ON ms.id = r.schedule_id " +
            "WHERE ms.order_detail_id = #{orderDetailId} " +
            "AND r.process_type = #{processType} " +
            "AND r.is_deleted = 0")
    BigDecimal sumProcessReportedQtyByOrderDetail(@Param("orderDetailId") Long orderDetailId,
                                                  @Param("processType") String processType);

    @Select("SELECT IFNULL(MAX(CASE WHEN r.proceed_next_process = 0 THEN 1 ELSE 0 END), 0) " +
            "FROM manual_schedule_process_report r " +
            "JOIN manual_schedule ms ON ms.id = r.schedule_id " +
            "WHERE ms.order_detail_id = #{orderDetailId} " +
            "AND r.process_type = #{processType} " +
            "AND r.is_deleted = 0")
    Integer maxStopNextByOrderDetail(@Param("orderDetailId") Long orderDetailId,
                                     @Param("processType") String processType);

    /**
     * 按订单汇总各明细报工完成统计（用于快速判断订单是否全部完成）
     */
    @Select("SELECT " +
            "soi.id AS order_detail_id, " +
            "IFNULL(soi.rolls, 0) AS order_qty, " +
            "IFNULL(agg.coating_qty, 0) AS coating_qty, " +
            "IFNULL(agg.rewinding_qty, 0) AS rewinding_qty, " +
            "IFNULL(agg.slitting_qty, 0) AS slitting_qty, " +
            "IFNULL(agg.coating_stop, 0) AS coating_stop, " +
            "IFNULL(agg.rewinding_stop, 0) AS rewinding_stop, " +
            "IFNULL(agg.slitting_stop, 0) AS slitting_stop " +
            "FROM sales_order_items soi " +
            "LEFT JOIN (" +
            "  SELECT " +
            "    ms.order_detail_id AS order_detail_id, " +
            "    SUM(CASE WHEN r.process_type = 'COATING' THEN IFNULL(r.produced_qty, 0) ELSE 0 END) AS coating_qty, " +
            "    SUM(CASE WHEN r.process_type = 'REWINDING' THEN IFNULL(r.produced_qty, 0) ELSE 0 END) AS rewinding_qty, " +
            "    SUM(CASE WHEN r.process_type = 'SLITTING' THEN IFNULL(r.produced_qty, 0) ELSE 0 END) AS slitting_qty, " +
            "    MAX(CASE WHEN r.process_type = 'COATING' AND r.proceed_next_process = 0 THEN 1 ELSE 0 END) AS coating_stop, " +
            "    MAX(CASE WHEN r.process_type = 'REWINDING' AND r.proceed_next_process = 0 THEN 1 ELSE 0 END) AS rewinding_stop, " +
            "    MAX(CASE WHEN r.process_type = 'SLITTING' AND r.proceed_next_process = 0 THEN 1 ELSE 0 END) AS slitting_stop " +
            "  FROM manual_schedule_process_report r " +
            "  JOIN manual_schedule ms ON ms.id = r.schedule_id " +
            "  WHERE r.is_deleted = 0 " +
            "  GROUP BY ms.order_detail_id" +
            ") agg ON agg.order_detail_id = soi.id " +
            "WHERE soi.order_id = #{orderId} AND soi.is_deleted = 0")
    List<Map<String, Object>> selectOrderDetailCompletionStatsByOrderId(@Param("orderId") Long orderId);

    @Insert("INSERT INTO manual_schedule_coating_roll(" +
            "schedule_id, report_id, roll_code, batch_no, width_mm, length_m, area, weight_kg, remark, created_at, updated_at, is_deleted" +
            ") VALUES (" +
            "#{scheduleId}, #{reportId}, #{rollCode}, #{batchNo}, #{widthMm}, #{lengthM}, #{area}, #{weightKg}, #{remark}, NOW(), NOW(), 0" +
            ")")
    int insertCoatingRoll(@Param("scheduleId") Long scheduleId,
                          @Param("reportId") Long reportId,
                          @Param("rollCode") String rollCode,
                          @Param("batchNo") String batchNo,
                          @Param("widthMm") BigDecimal widthMm,
                          @Param("lengthM") BigDecimal lengthM,
                          @Param("area") BigDecimal area,
                          @Param("weightKg") BigDecimal weightKg,
                          @Param("remark") String remark);

    @Insert("INSERT INTO manual_schedule_coating_order_lock(" +
            "schedule_id, report_id, order_no, material_code, roll_code, locked_area, lock_status, created_at, updated_at, is_deleted" +
            ") VALUES (" +
            "#{scheduleId}, #{reportId}, #{orderNo}, #{materialCode}, #{rollCode}, #{lockedArea}, #{lockStatus}, NOW(), NOW(), 0" +
            ")")
    int insertCoatingOrderLock(@Param("scheduleId") Long scheduleId,
                               @Param("reportId") Long reportId,
                               @Param("orderNo") String orderNo,
                               @Param("materialCode") String materialCode,
                               @Param("rollCode") String rollCode,
                               @Param("lockedArea") BigDecimal lockedArea,
                               @Param("lockStatus") String lockStatus);

    @Insert("INSERT INTO manual_schedule_process_material_issue(" +
            "schedule_id, report_id, process_type, material_type, material_code, stock_id, roll_code, plan_area, actual_area, loss_area, operator_name, issue_time, remark, created_at, updated_at, is_deleted" +
            ") VALUES (" +
            "#{scheduleId}, #{reportId}, #{processType}, #{materialType}, #{materialCode}, #{stockId}, #{rollCode}, #{planArea}, #{actualArea}, #{lossArea}, #{operatorName}, #{issueTime}, #{remark}, NOW(), NOW(), 0" +
            ")")
    int insertProcessMaterialIssue(@Param("scheduleId") Long scheduleId,
                                   @Param("reportId") Long reportId,
                                   @Param("processType") String processType,
                                   @Param("materialType") String materialType,
                                   @Param("materialCode") String materialCode,
                                   @Param("stockId") Long stockId,
                                   @Param("rollCode") String rollCode,
                                   @Param("planArea") BigDecimal planArea,
                                   @Param("actualArea") BigDecimal actualArea,
                                   @Param("lossArea") BigDecimal lossArea,
                                   @Param("operatorName") String operatorName,
                                   @Param("issueTime") java.time.LocalDateTime issueTime,
                                   @Param("remark") String remark);

    @Select("SELECT order_no, material_code, IFNULL(included_area, 0) AS included_area, IFNULL(sort_no, 0) AS sort_no " +
            "FROM manual_schedule_coating_allocation " +
            "WHERE schedule_id = #{scheduleId} AND included_flag = 1 " +
            "ORDER BY sort_no ASC, id ASC")
    List<Map<String, Object>> selectIncludedCoatingAllocations(@Param("scheduleId") Long scheduleId);

    @Select("SELECT roll_code, batch_no, area, width_mm, length_m, weight_kg, remark " +
            "FROM manual_schedule_coating_roll " +
            "WHERE report_id = #{reportId} AND is_deleted = 0 " +
            "ORDER BY id ASC")
    List<Map<String, Object>> selectCoatingRollsByReportId(@Param("reportId") Long reportId);

    @Select("SELECT id, schedule_id, report_id, order_no, material_code, roll_code, locked_area, lock_status, " +
            "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at " +
            "FROM manual_schedule_coating_order_lock " +
            "WHERE order_no = #{orderNo} AND is_deleted = 0 " +
            "ORDER BY id DESC")
    List<Map<String, Object>> selectCoatingRollLocksByOrderNo(@Param("orderNo") String orderNo);

    @Select("SELECT id, schedule_id, report_id, process_type, material_type, material_code, stock_id, roll_code, " +
            "plan_area, actual_area, loss_area, operator_name, DATE_FORMAT(issue_time, '%Y-%m-%d %H:%i:%s') AS issue_time, remark " +
            "FROM manual_schedule_process_material_issue " +
            "WHERE schedule_id = #{scheduleId} AND process_type = #{processType} AND is_deleted = 0 " +
            "ORDER BY id DESC")
    List<Map<String, Object>> selectProcessMaterialIssues(@Param("scheduleId") Long scheduleId,
                                                          @Param("processType") String processType);

    @Select("SELECT IFNULL(MAX(CAST(SUBSTRING(roll_code, CHAR_LENGTH(#{prefix}) + 1) AS UNSIGNED)), 0) " +
            "FROM manual_schedule_coating_roll " +
            "WHERE is_deleted = 0 AND roll_code LIKE CONCAT(#{prefix}, '%')")
    Integer selectMaxCoatingRollSeqByPrefix(@Param("prefix") String prefix);
    
    /**
     * 更新订单明细的已排程数量
     */
    @Update("UPDATE sales_order_items soi " +
            "JOIN sales_orders o ON soi.order_id = o.id " +
            "SET soi.scheduled_qty = IFNULL(soi.scheduled_qty, 0) + #{qty} " +
            "WHERE soi.order_id = #{orderId} AND soi.material_code = #{materialCode} " +
            "AND soi.is_deleted = 0 AND o.is_deleted = 0 " +
            "AND (o.status IS NULL OR LOWER(o.status) NOT IN ('completed','cancelled','canceled','closed')) " +
            "AND (soi.rolls - IFNULL(soi.scheduled_qty, 0)) >= #{qty}")
    int updateScheduledQty(
            @Param("orderId") Long orderId,
            @Param("materialCode") String materialCode,
            @Param("qty") BigDecimal qty);

    /**
     * 更新订单明细的已排程数量（按明细ID）
     */
    @Update("UPDATE sales_order_items soi " +
            "JOIN sales_orders o ON soi.order_id = o.id " +
            "SET soi.scheduled_qty = IFNULL(soi.scheduled_qty, 0) + #{qty} " +
            "WHERE soi.id = #{orderDetailId} " +
            "AND soi.is_deleted = 0 AND o.is_deleted = 0 " +
            "AND (o.status IS NULL OR LOWER(o.status) NOT IN ('completed','cancelled','canceled','closed')) " +
            "AND (soi.rolls - IFNULL(soi.scheduled_qty, 0)) >= #{qty}")
    int updateScheduledQtyByDetailId(
            @Param("orderDetailId") Long orderDetailId,
            @Param("qty") BigDecimal qty);
    
    /**
     * 更新销售订单明细的涂布日期
     */
    @Update("UPDATE sales_order_items soi " +
            "JOIN sales_orders o ON soi.order_id = o.id " +
            "SET soi.coating_date = #{coatingDate} " +
            "WHERE soi.material_code LIKE CONCAT(#{materialCode}, '%') " +
            "AND soi.thickness = #{thickness} " +
            "AND soi.is_deleted = 0 " +
            "AND o.is_deleted = 0 " +
            "AND (o.status IS NULL OR LOWER(o.status) NOT IN ('completed','cancelled','canceled','closed'))")
    int updateSalesOrderCoatingDate(
            @Param("materialCode") String materialCode,
            @Param("thickness") Integer thickness,
            @Param("coatingDate") String coatingDate);

    /**
     * 按订单明细ID回写涂布日期
     */
    @Update("UPDATE sales_order_items " +
            "SET coating_date = #{coatingDate} " +
            "WHERE id = #{orderDetailId} AND is_deleted = 0")
    int updateSalesOrderCoatingDateByDetailId(
            @Param("orderDetailId") Long orderDetailId,
            @Param("coatingDate") String coatingDate);

    /**
     * 按涂布覆盖明细回写关联订单的涂布日期
     */
    @Update("UPDATE sales_order_items soi " +
            "JOIN sales_orders o ON soi.order_id = o.id " +
            "JOIN manual_schedule_coating_allocation ca ON ca.schedule_id = #{scheduleId} " +
            "  AND ca.included_flag = 1 " +
            "  AND ca.order_no = o.order_no " +
            "SET soi.coating_date = #{coatingDate} " +
            "WHERE soi.material_code = ca.material_code " +
            "AND (ca.thickness IS NULL OR soi.thickness = ca.thickness) " +
            "AND soi.is_deleted = 0 " +
            "AND o.is_deleted = 0 " +
            "AND (o.status IS NULL OR LOWER(o.status) NOT IN ('completed','cancelled','canceled','closed'))")
    int updateSalesOrderCoatingDateByScheduleAllocation(
            @Param("scheduleId") Long scheduleId,
            @Param("coatingDate") String coatingDate);
    
    /**
     * 更新涂布排程的复卷已排程面积
     */
    @Update("UPDATE manual_schedule " +
            "SET rewinding_scheduled_area = IFNULL(rewinding_scheduled_area, 0) + #{area}, " +
            "status = CASE WHEN (coating_area - IFNULL(rewinding_scheduled_area, 0) - #{area}) <= 0 THEN 'REWINDING_SCHEDULED' ELSE status END " +
            "WHERE id = #{scheduleId}")
    int updateRewindingScheduledArea(@Param("scheduleId") Long scheduleId, @Param("area") BigDecimal area);

    /**
     * 校验订单明细是否可排程（未删除、未取消、未关闭）
     */
    @Select("SELECT COUNT(1) " +
            "FROM sales_order_items soi " +
            "JOIN sales_orders o ON soi.order_id = o.id " +
            "WHERE soi.id = #{orderDetailId} " +
            "AND soi.is_deleted = 0 AND o.is_deleted = 0 " +
            "AND (o.status IS NULL OR LOWER(o.status) NOT IN ('cancelled','canceled','closed'))")
    int countSchedulableOrderDetail(@Param("orderDetailId") Long orderDetailId);

    /**
     * 查询订单明细下仍有效的手动排程（用于取消联动）
     */
    @Select("SELECT id, order_detail_id, schedule_qty, status, stock_allocations " +
            "FROM manual_schedule " +
            "WHERE order_detail_id = #{orderDetailId} " +
            "AND status IN ('PENDING','COATING_SCHEDULED','REWINDING_SCHEDULED')")
    List<Map<String, Object>> selectCancelableSchedulesByOrderDetailId(@Param("orderDetailId") Long orderDetailId);

    /**
     * 取消订单明细下的有效排程
     */
    @Update("UPDATE manual_schedule " +
            "SET status = 'CANCELLED', " +
            "remark = CONCAT(IFNULL(remark, ''), CASE WHEN IFNULL(remark, '') = '' THEN '' ELSE '；' END, #{reason}), " +
            "updated_at = NOW() " +
            "WHERE order_detail_id = #{orderDetailId} " +
            "AND status IN ('PENDING','COATING_SCHEDULED','REWINDING_SCHEDULED')")
    int cancelSchedulesByOrderDetailId(@Param("orderDetailId") Long orderDetailId,
                                       @Param("reason") String reason);

    /**
     * 回滚订单明细已排程数量
     */
    @Update("UPDATE sales_order_items " +
            "SET scheduled_qty = GREATEST(IFNULL(scheduled_qty, 0) - #{qty}, 0) " +
            "WHERE id = #{orderDetailId}")
    int rollbackScheduledQtyByDetailId(@Param("orderDetailId") Long orderDetailId,
                                       @Param("qty") BigDecimal qty);

    /**
     * 判断订单明细是否已有开工/不可直接撤销的排程
     * 规则：存在已排复卷面积、已填写包装/分切日期、或已完成状态
     */
    @Select("SELECT COUNT(1) FROM manual_schedule " +
            "WHERE order_detail_id = #{orderDetailId} " +
            "AND (IFNULL(rewinding_scheduled_area, 0) > 0 " +
            "  OR packaging_date IS NOT NULL " +
            "  OR slitting_schedule_date IS NOT NULL " +
            "  OR status = 'COMPLETED')")
    int countStartedSchedulesByOrderDetailId(@Param("orderDetailId") Long orderDetailId);

    /**
     * 删除指定排程的报工记录
     */
    @Delete("DELETE FROM manual_schedule_process_report WHERE schedule_id = #{scheduleId}")
    int deleteProcessReportsByScheduleId(@Param("scheduleId") Long scheduleId);

    /**
     * 重置订单明细的排程累计与工序日期
     */
    @Update("UPDATE sales_order_items " +
            "SET scheduled_qty = 0, " +
            "coating_date = NULL, " +
            "rewinding_date = NULL, " +
            "packaging_date = NULL " +
            "WHERE id = #{orderDetailId}")
    int resetOrderDetailScheduleFields(@Param("orderDetailId") Long orderDetailId);
}
