package com.fine.Dao.sales;

import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 销售交付记录Mapper
 */
@Mapper
public interface SalesDeliveryRecordsMapper {
    
    /**
     * 查询客户某料号最近3个月的平均月销量(卷数)
     */
    @Select("<script>" +
            "SELECT COALESCE(AVG(total_delivered), 0) as monthly_avg " +
            "FROM (" +
            "  SELECT SUM(delivered_qty) as total_delivered " +
            "  FROM sales_delivery_records " +
            "  WHERE customer_name = #{customerName} " +
            "  AND material_code = #{materialCode} " +
            "  AND delivery_month >= DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 3 MONTH), '%Y-%m') " +
            "  GROUP BY delivery_month" +
            ") t" +
            "</script>")
    BigDecimal selectMonthlyAvgByCustomerAndMaterial(@Param("customerName") String customerName,
                                                     @Param("materialCode") String materialCode);
    
    /**
     * 查询最近3个月的交付记录数据
     */
    @Select("<script>" +
            "SELECT delivery_month, SUM(delivered_qty) as monthly_qty, SUM(delivered_sqm) as monthly_sqm " +
            "FROM sales_delivery_records " +
            "WHERE customer_name = #{customerName} " +
            "AND material_code = #{materialCode} " +
            "AND delivery_month >= DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 3 MONTH), '%Y-%m') " +
            "GROUP BY delivery_month " +
            "ORDER BY delivery_month DESC" +
            "</script>")
    List<Map<String, Object>> selectRecentDeliveryData(@Param("customerName") String customerName,
                                                       @Param("materialCode") String materialCode);
    
    /**
     * 查询同料号所有客户最近3个月的平均月销量(平方米)
     */
    @Select("<script>" +
            "SELECT COALESCE(AVG(total_delivered), 0) as monthly_avg_sqm " +
            "FROM (" +
            "  SELECT SUM(delivered_sqm) as total_delivered " +
            "  FROM sales_delivery_records " +
            "  WHERE material_code = #{materialCode} " +
            "  AND delivery_month >= DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 3 MONTH), '%Y-%m') " +
            "  GROUP BY delivery_month" +
            ") t" +
            "</script>")
    BigDecimal selectMonthlyAvgSqmByMaterial(@Param("materialCode") String materialCode);
}
