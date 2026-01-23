package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.modle.SalesOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
            "  so.sales, so.documentation_person, " +
            "  so.total_amount, so.total_area, so.required_area, " +
            "  so.thickness, so.width, so.order_date, so.delivery_date, " +
            "  so.delivery_address, so.status, so.created_by, so.updated_by, " +
            "  so.created_at, so.updated_at, so.is_deleted, " +
            "  COALESCE(su.real_name, '') as salesUserName, " +
            "  COALESCE(du.real_name, '') as documentationPersonUserName " +
            "FROM sales_orders so " +
            "LEFT JOIN customers c ON so.customer COLLATE utf8mb4_unicode_ci = c.customer_code COLLATE utf8mb4_unicode_ci " +
            "LEFT JOIN users su ON so.sales = su.id " +
            "LEFT JOIN users du ON so.documentation_person = du.id " +
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
            "ORDER BY so.created_at DESC" +
            "</script>")
    IPage<SalesOrder> selectOrdersWithCustomerSearch(
            Page<SalesOrder> page,
            @Param("orderNo") String orderNo,
            @Param("customerKeyword") String customerKeyword,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate
    );
    
    /**
     * 查询订单基本信息（用于优先级计算）
     * @param orderId 订单ID
     * @return 订单信息Map
     */
    @Select("SELECT so.id, so.order_no, so.customer_id, so.created_at, " +
            "soi.material_code, soi.unit_price " +
            "FROM sales_orders so " +
            "LEFT JOIN sales_order_items soi ON so.id = soi.order_id " +
            "WHERE so.id = #{orderId} AND so.is_deleted = 0 LIMIT 1")
    java.util.Map<String, Object> selectOrderInfoById(@Param("orderId") Long orderId);

        @Select("SELECT * FROM sales_orders WHERE order_no = #{orderNo} AND is_deleted = 0 LIMIT 1")
        com.fine.modle.SalesOrder selectByOrderNo(@Param("orderNo") String orderNo);
}

