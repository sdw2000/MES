package com.fine.Dao.purchase;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.modle.PurchaseOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PurchaseOrderMapper extends BaseMapper<PurchaseOrder> {

    @Select("<script>"
            + "SELECT "
            + "  po.id, po.order_no, po.supplier, po.supplier_order_no, "
            + "  po.buyer AS buyerUserId, po.handler AS handlerUserId, "
            + "  po.contact_name, po.contact_phone, "
            + "  po.total_amount, po.total_area, po.required_area, "
            + "  po.thickness, po.width, po.order_date, po.delivery_date, "
            + "  po.delivery_address, po.status, po.remark, "
            + "  po.created_by, po.updated_by, po.created_at, po.updated_at, po.is_deleted, "
            + "  COALESCE(bu.real_name, '') AS buyerUserName, "
            + "  COALESCE(hu.real_name, '') AS handlerUserName "
            + "FROM purchase_orders po "
            + "LEFT JOIN customers c ON po.supplier COLLATE utf8mb4_unicode_ci = c.customer_code COLLATE utf8mb4_unicode_ci "
            + "LEFT JOIN users bu ON po.buyer = bu.id "
            + "LEFT JOIN users hu ON po.handler = hu.id "
            + "WHERE po.is_deleted = 0 "
            + "<if test='orderNo != null and orderNo != \"\"'> "
            + "  AND po.order_no LIKE CONCAT('%', #{orderNo}, '%') "
            + "</if>"
            + "<if test='supplierKeyword != null and supplierKeyword != \"\"'> "
            + "  AND (po.supplier LIKE CONCAT('%', #{supplierKeyword}, '%') "
            + "       OR c.customer_name LIKE CONCAT('%', #{supplierKeyword}, '%') "
            + "       OR c.short_name LIKE CONCAT('%', #{supplierKeyword}, '%')) "
            + "</if>"
            + "<if test='startDate != null and startDate != \"\"'> "
            + "  AND po.order_date &gt;= #{startDate} "
            + "</if>"
            + "<if test='endDate != null and endDate != \"\"'> "
            + "  AND po.order_date &lt;= #{endDate} "
            + "</if>"
            + "ORDER BY po.created_at DESC"
            + "</script>")
    IPage<PurchaseOrder> selectOrdersWithSupplierSearch(
            Page<PurchaseOrder> page,
            @Param("orderNo") String orderNo,
            @Param("supplierKeyword") String supplierKeyword,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate
    );

    @Select("SELECT * FROM purchase_orders WHERE order_no = #{orderNo} AND is_deleted = 0 LIMIT 1")
    PurchaseOrder selectByOrderNo(@Param("orderNo") String orderNo);

    @Select("SELECT DISTINCT po.* FROM purchase_orders po "
            + "INNER JOIN purchase_order_items poi ON po.id = poi.order_id "
            + "WHERE poi.is_deleted = 0 AND po.is_deleted = 0 AND poi.id = #{itemId}")
    List<PurchaseOrder> selectByItemId(@Param("itemId") Long itemId);
}
