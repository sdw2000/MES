package com.fine.Dao.purchase;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.modle.PurchaseOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PurchaseOrderMapper extends BaseMapper<PurchaseOrder> {

    @Select("<script>"
            + "SELECT "
            + "  po.id, po.order_no, po.supplier, po.supplier_order_no, "
            + "  po.buyer AS buyerUserId, po.handler AS handlerUserId, "
            + "  po.contact_name, po.contact_phone, "
            + "  COALESCE(agg.total_amount, po.total_amount, 0) AS total_amount, "
            + "  COALESCE(agg.total_qty, po.total_area, 0) AS total_area, "
            + "  po.required_area, "
            + "  po.reconciliation_status, "
            + "  po.thickness, po.width, po.order_date, po.delivery_date, "
            + "  po.delivery_address, po.status, po.remark, "
            + "  po.created_by, po.updated_by, po.created_at, po.updated_at, po.is_deleted, "
            + "  COALESCE(bu.real_name, '') AS buyerUserName, "
            + "  COALESCE(hu.real_name, '') AS handlerUserName "
            + "FROM purchase_orders po "
            + "LEFT JOIN ("
            + "  SELECT poi.order_id, "
            + "         IFNULL(SUM(IFNULL(poi.amount, 0)), 0) AS total_amount, "
            + "         IFNULL(SUM(COALESCE(poi.stock_qty, poi.sqm, 0)), 0) AS total_qty "
            + "  FROM purchase_order_items poi "
            + "  WHERE poi.is_deleted = 0 "
            + "  GROUP BY poi.order_id"
            + ") agg ON agg.order_id = po.id "
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
            + "<if test='reconciliationStatus != null and reconciliationStatus != \"\"'> "
            + "  AND po.reconciliation_status = #{reconciliationStatus} "
            + "</if>"
            + "ORDER BY po.created_at DESC"
            + "</script>")
    IPage<PurchaseOrder> selectOrdersWithSupplierSearch(
            Page<PurchaseOrder> page,
            @Param("orderNo") String orderNo,
            @Param("supplierKeyword") String supplierKeyword,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("reconciliationStatus") String reconciliationStatus
    );

    @Select("SELECT * FROM purchase_orders WHERE order_no = #{orderNo} AND is_deleted = 0 LIMIT 1")
    PurchaseOrder selectByOrderNo(@Param("orderNo") String orderNo);

    @Select("<script>"
            + "SELECT po.* FROM purchase_orders po "
            + "WHERE po.is_deleted = 0 "
            + "AND NOT EXISTS ("
            + "  SELECT 1 FROM purchase_receipts pr "
            + "  WHERE pr.is_deleted = 0 "
            + "    AND pr.purchase_order_no = po.order_no"
            + ") "
            + "<if test='orderNo != null and orderNo != \"\"'> "
            + "  AND po.order_no LIKE CONCAT('%', #{orderNo}, '%') "
            + "</if>"
            + "<if test='supplierKeyword != null and supplierKeyword != \"\"'> "
            + "  AND po.supplier LIKE CONCAT('%', #{supplierKeyword}, '%') "
            + "</if>"
            + "ORDER BY po.created_at DESC"
            + "</script>")
    IPage<PurchaseOrder> selectOrdersWithoutReceipt(
            Page<PurchaseOrder> page,
            @Param("orderNo") String orderNo,
            @Param("supplierKeyword") String supplierKeyword
    );

    @Select("SELECT DISTINCT po.* FROM purchase_orders po "
            + "INNER JOIN purchase_order_items poi ON po.id = poi.order_id "
            + "WHERE poi.is_deleted = 0 AND po.is_deleted = 0 AND poi.id = #{itemId}")
    List<PurchaseOrder> selectByItemId(@Param("itemId") Long itemId);

    @Update("UPDATE purchase_orders "
            + "SET is_deleted = 1, updated_by = #{updatedBy}, updated_at = NOW() "
            + "WHERE order_no = #{orderNo} AND is_deleted = 0")
    int logicDeleteByOrderNo(@Param("orderNo") String orderNo,
                             @Param("updatedBy") String updatedBy);

    @Select("SELECT order_no FROM purchase_orders WHERE order_no LIKE CONCAT(#{prefix}, '%') ORDER BY order_no DESC LIMIT 1")
    String selectLastOrderNoByPrefix(@Param("prefix") String prefix);

    @Select("SELECT COUNT(1) FROM purchase_orders WHERE order_no = #{orderNo}")
    int countByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 获取对账数据汇总记录（改进点7：性能优化）
     * 通过 SQL 聚合减少 Java 循环和多次单表查询
     */
    @Select("SELECT " +
            "  -1 AS id, " +
            "  poi.material_code AS materialCode, " +
            "  MAX(poi.material_name) AS materialName, " +
            "  MAX(poi.purchase_uom_code) AS purchaseUomCode, " +
            "  MAX(poi.price_uom_code) AS priceUomCode, " +
            "  SUM(COALESCE(poi.price_qty, poi.stock_qty, 0)) AS orderQty, " +
            "  SUM(COALESCE(poi.amount, 0)) AS orderAmount, " +
            "  IFNULL(SUM(ri.receiptQty), 0) AS receiptQty, " +
            "  IFNULL(SUM(ri.receiptAmount), 0) AS receiptAmount " +
            "FROM purchase_order_items poi " +
            "JOIN purchase_orders po ON poi.order_id = po.id " +
            "LEFT JOIN (" +
            "  SELECT pri.material_code, " +
            "         SUM(COALESCE(pri.price_qty, pri.stock_qty, 0)) AS receiptQty, " +
            "         SUM(COALESCE(pri.amount, 0)) AS receiptAmount " +
            "  FROM purchase_receipt_items pri " +
            "  JOIN purchase_receipts pr ON pri.receipt_id = pr.id " +
            "  WHERE pr.purchase_order_no = #{orderNo} AND pr.is_deleted = 0 AND pri.is_deleted = 0 " +
            "  GROUP BY pri.material_code " +
            ") ri ON poi.material_code = ri.material_code " +
            "WHERE po.order_no = #{orderNo} AND po.is_deleted = 0 AND poi.is_deleted = 0 " +
            "GROUP BY poi.material_code")
    List<java.util.Map<String, Object>> selectReconciliationAggregate(@Param("orderNo") String orderNo);
}
