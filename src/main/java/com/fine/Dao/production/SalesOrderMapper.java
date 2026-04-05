package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.modle.SalesOrder;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 销售订单数据访问层
 */
@Mapper
public interface SalesOrderMapper extends BaseMapper<SalesOrder> {
    
    /**
     * 根据排程ID查询关联的销售订单
     * @param scheduleId 排程ID
     * @return 销售订单列表
     */
    @Select("SELECT DISTINCT so.* FROM sales_orders so " +
            "INNER JOIN schedule_material_allocations sma ON so.id = sma.sales_order_id " +
            "WHERE sma.schedule_id = #{scheduleId} AND so.is_deleted = 0")
    List<SalesOrder> selectByBatchScheduleId(Long scheduleId);
    
    /**
     * 分页查询订单（支持客户名称搜索，包含销售和跟单员信息）
     * @param page 分页对象
     * @param orderNo 订单号
     * @param customerKeyword 客户关键字（支持客户代码、客户名称、简称）
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 分页结果
     */
    @Select("<script>" +
            "SELECT " +
            "  so.id, so.order_no, so.customer, so.customer_order_no, " +
            "  COALESCE(so.sales, c.sales) AS sales, COALESCE(so.documentation_person, c.documentation_person) AS documentation_person, " +
            "  so.total_amount, so.total_area, " +
            "  so.order_date, so.delivery_date, " +
            "  so.delivery_address, so.status, so.created_by, so.updated_by, " +
            "  so.created_at, so.updated_at, so.is_deleted, " +
            "  IFNULL(rp.completed_rolls, 0) AS shipped_rolls, " +
            "  IFNULL(rp.remaining_rolls, 0) AS remaining_rolls, " +
            "  COALESCE(su.real_name, '') as salesUserName, " +
            "  COALESCE(du.real_name, '') as documentationPersonUserName " +
            "FROM sales_orders so " +
            "LEFT JOIN customers c ON so.customer COLLATE utf8mb4_unicode_ci = c.customer_code COLLATE utf8mb4_unicode_ci " +
            "LEFT JOIN (" +
            "  SELECT soi.order_id, " +
            "         IFNULL(SUM(IFNULL(soi.delivered_qty, 0)), 0) AS completed_rolls, " +
            "         IFNULL(SUM(IFNULL(soi.remaining_qty, GREATEST(IFNULL(soi.rolls, 0) - IFNULL(soi.delivered_qty, 0), 0))), 0) AS remaining_rolls " +
            "  FROM sales_order_items soi " +
            "  WHERE soi.is_deleted = 0 " +
            "  GROUP BY soi.order_id" +
            ") rp ON rp.order_id = so.id " +
            "LEFT JOIN users su ON COALESCE(so.sales, c.sales) = su.id " +
            "LEFT JOIN users du ON COALESCE(so.documentation_person, c.documentation_person) = du.id " +
            "WHERE so.is_deleted = 0 " +
            "<if test='orderNo != null and orderNo != \"\"'> " +
            "  AND so.order_no LIKE CONCAT('%', #{orderNo}, '%') " +
            "</if>" +
            "<if test='customerKeyword != null and customerKeyword != \"\"'> " +
            "  AND (so.customer LIKE CONCAT('%', #{customerKeyword}, '%') " +
            "       OR c.customer_name LIKE CONCAT('%', #{customerKeyword}, '%') " +
            "       OR c.short_name LIKE CONCAT('%', #{customerKeyword}, '%')) " +
            "</if>" +
            "<if test='startDate != null and startDate != \"\"'> " +
            "  AND so.order_date &gt;= #{startDate} " +
            "</if>" +
            "<if test='endDate != null and endDate != \"\"'> " +
            "  AND so.order_date &lt;= #{endDate} " +
            "</if>" +
            "<if test='salesUserId != null and documentationPersonUserId != null'> " +
            "  AND (" +
            "    so.sales = #{salesUserId} " +
            "    OR so.documentation_person = #{documentationPersonUserId} " +
            "    OR ((so.sales IS NULL AND so.documentation_person IS NULL) " +
            "        AND (c.sales = #{salesUserId} OR c.documentation_person = #{documentationPersonUserId}))" +
            "  ) " +
            "</if>" +
            "<if test='salesUserId != null and documentationPersonUserId == null'> " +
            "  AND (" +
            "    so.sales = #{salesUserId} " +
            "    OR ((so.sales IS NULL AND so.documentation_person IS NULL) AND c.sales = #{salesUserId})" +
            "  ) " +
            "</if>" +
            "<if test='salesUserId == null and documentationPersonUserId != null'> " +
            "  AND (" +
            "    so.documentation_person = #{documentationPersonUserId} " +
            "    OR ((so.sales IS NULL AND so.documentation_person IS NULL) AND c.documentation_person = #{documentationPersonUserId})" +
            "  ) " +
            "</if>" +
            "<if test='completionStatus != null and completionStatus != \"\"'> " +
            "  AND (" +
            "    (#{completionStatus} = 'completed' AND " +
            "      IFNULL(rp.remaining_rolls, 0) &lt;= 0" +
            "    ) OR " +
            "    (#{completionStatus} = 'not_started' AND " +
            "      IFNULL(rp.remaining_rolls, 0) &gt; 0 " +
            "      AND IFNULL(rp.completed_rolls, 0) &lt;= 0" +
            "    ) OR " +
            "    (#{completionStatus} = 'partial' AND " +
            "      IFNULL(rp.remaining_rolls, 0) &gt; 0 " +
            "      AND IFNULL(rp.completed_rolls, 0) &gt; 0" +
            "    )" +
            "  ) " +
            "</if>" +
            "<if test='(completionStatus == null or completionStatus == \"\") and (showCompleted == null or showCompleted == false)'> " +
            "  AND IFNULL(rp.remaining_rolls, 0) &gt; 0 " +
            "</if>" +
            "<choose>" +
            "  <when test='sortField == \"customerDisplay\"'> ORDER BY so.customer </when>" +
            "  <when test='sortField == \"orderNo\"'> ORDER BY so.order_no </when>" +
            "  <when test='sortField == \"totalAmount\"'> ORDER BY so.total_amount </when>" +
            "  <when test='sortField == \"totalArea\"'> ORDER BY so.total_area </when>" +
            "  <when test='sortField == \"orderDate\"'> ORDER BY so.order_date </when>" +
            "  <when test='sortField == \"deliveryDate\"'> ORDER BY so.delivery_date </when>" +
            "  <when test='sortField == \"completionStatus\"'> ORDER BY " +
            "    (CASE " +
            "      WHEN IFNULL(rp.remaining_rolls, 0) &lt;= 0 THEN 2 " +
            "      WHEN IFNULL(rp.completed_rolls, 0) &lt;= 0 THEN 0 " +
            "      ELSE 1 END) " +
            "  </when>" +
            "  <otherwise> ORDER BY so.created_at </otherwise>" +
            "</choose>" +
            "<choose>" +
            "  <when test='sortOrder == \"ascending\"'> ASC </when>" +
            "  <otherwise> DESC </otherwise>" +
            "</choose>" +
            "</script>")
    IPage<SalesOrder> selectOrdersWithCustomerSearch(
            Page<SalesOrder> page,
            @Param("orderNo") String orderNo,
            @Param("customerKeyword") String customerKeyword,
            @Param("completionStatus") String completionStatus,
            @Param("showCompleted") Boolean showCompleted,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("salesUserId") Long salesUserId,
            @Param("documentationPersonUserId") Long documentationPersonUserId,
            @Param("sortField") String sortField,
            @Param("sortOrder") String sortOrder
    );
    
    /**
     * 查询订单基本信息（用于优先级计算）
     * @param orderId 订单ID
     * @return 订单信息Map
     */
    @Select("SELECT so.id, so.order_no, c.id AS customer_id, so.customer AS customer_code, so.created_at, " +
            "soi.material_code, soi.unit_price " +
            "FROM sales_orders so " +
            "LEFT JOIN customers c ON so.customer COLLATE utf8mb4_unicode_ci = c.customer_code COLLATE utf8mb4_unicode_ci " +
            "LEFT JOIN sales_order_items soi ON so.id = soi.order_id " +
            "WHERE so.id = #{orderId} AND so.is_deleted = 0 LIMIT 1")
    java.util.Map<String, Object> selectOrderInfoById(@Param("orderId") Long orderId);

        @Select("SELECT id FROM sales_orders WHERE is_deleted = 0")
        List<Long> selectAllOrderIds();

    @Select("SELECT " +
            "  IFNULL(SUM(so.total_amount),0) AS last3m_amount, " +
            "  COUNT(*) AS last3m_order_count, " +
            "  IFNULL(SUM(so.total_amount)/3,0) AS avg_monthly_amount " +
            "FROM sales_orders so " +
            "WHERE so.is_deleted = 0 AND so.customer = #{customerCode} " +
            "  AND so.order_date >= DATE_SUB(CURDATE(), INTERVAL 3 MONTH)")
    java.util.Map<String, Object> selectCustomerTransactionStatsByCode(@Param("customerCode") String customerCode);

    @Select("SELECT " +
            "  IFNULL(SUM(soi.rolls),0) AS last3m_total_qty, " +
            "  IFNULL(SUM(soi.amount),0) AS last3m_total_amount, " +
            "  CASE WHEN IFNULL(SUM(soi.rolls),0) = 0 THEN 0 " +
            "       ELSE IFNULL(SUM(soi.amount),0) / IFNULL(SUM(soi.rolls),0) END AS avg_unit_price " +
            "FROM sales_order_items soi " +
            "INNER JOIN sales_orders so ON so.id = soi.order_id " +
            "WHERE so.is_deleted = 0 AND so.customer = #{customerCode} " +
            "  AND soi.material_code = #{materialCode} " +
            "  AND so.order_date >= DATE_SUB(CURDATE(), INTERVAL 3 MONTH)")
    java.util.Map<String, Object> selectCustomerMaterialPriceStatsByCode(
            @Param("customerCode") String customerCode,
            @Param("materialCode") String materialCode
    );

    @Select("SELECT " +
            "  IFNULL(SUM(soi.rolls),0) AS last3m_total_qty, " +
            "  IFNULL(SUM(soi.amount),0) AS last3m_total_amount, " +
            "  CASE WHEN IFNULL(SUM(soi.rolls),0) = 0 THEN 0 " +
            "       ELSE IFNULL(SUM(soi.amount),0) / IFNULL(SUM(soi.rolls),0) END AS avg_unit_price " +
            "FROM sales_order_items soi " +
            "INNER JOIN sales_orders so ON so.id = soi.order_id " +
            "WHERE so.is_deleted = 0 AND so.customer = #{customerCode} " +
            "  AND so.order_date >= DATE_SUB(CURDATE(), INTERVAL 3 MONTH)")
    java.util.Map<String, Object> selectCustomerPriceStatsByCode(@Param("customerCode") String customerCode);

    @Select("SELECT TRIM(so.remark) AS remark, COUNT(1) AS useCount, MAX(so.updated_at) AS lastUsedAt " +
            "FROM sales_orders so " +
            "WHERE so.is_deleted = 0 " +
            "  AND so.customer = #{customerCode} " +
            "  AND so.remark IS NOT NULL " +
            "  AND TRIM(so.remark) <> '' " +
            "GROUP BY TRIM(so.remark) " +
            "ORDER BY lastUsedAt DESC " +
            "LIMIT #{limit}")
    List<java.util.Map<String, Object>> selectCustomerOrderRemarkHistory(
            @Param("customerCode") String customerCode,
            @Param("limit") Integer limit
    );

        @Select("SELECT * FROM sales_orders WHERE order_no = #{orderNo} AND is_deleted = 0 LIMIT 1")
        com.fine.modle.SalesOrder selectByOrderNo(@Param("orderNo") String orderNo);

        @Select("SELECT order_no FROM sales_orders " +
                "WHERE is_deleted = 0 " +
                "  AND order_no LIKE CONCAT(#{base}, '%') " +
                "  AND SUBSTRING(order_no, CHAR_LENGTH(#{base}) + 1) REGEXP '^[0-9]+$' " +
                "ORDER BY CAST(SUBSTRING(order_no, CHAR_LENGTH(#{base}) + 1) AS UNSIGNED) DESC " +
                "LIMIT 1")
        String selectLastOrderNoByBase(@Param("base") String base);

        @Select("SELECT order_no FROM sales_orders " +
                "WHERE is_deleted = 0 " +
                "  AND LOCATE(CONCAT(#{datePart}, '-', #{suffix}), order_no) > 0 " +
                "  AND SUBSTRING(order_no, LOCATE(CONCAT(#{datePart}, '-', #{suffix}), order_no) + CHAR_LENGTH(CONCAT(#{datePart}, '-', #{suffix}))) REGEXP '^[0-9]+$' " +
                "ORDER BY CAST(SUBSTRING(order_no, LOCATE(CONCAT(#{datePart}, '-', #{suffix}), order_no) + CHAR_LENGTH(CONCAT(#{datePart}, '-', #{suffix}))) AS UNSIGNED) DESC " +
                "LIMIT 1")
        String selectLastOrderNoByDateAndSuffix(@Param("datePart") String datePart, @Param("suffix") String suffix);

        @Delete("DELETE FROM sales_orders")
        int deleteAllPhysical();

        @Update("ALTER TABLE sales_orders AUTO_INCREMENT = 1")
        int resetAutoIncrement();
}

