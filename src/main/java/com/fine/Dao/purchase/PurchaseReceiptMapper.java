package com.fine.Dao.purchase;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.modle.purchase.PurchaseReceipt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface PurchaseReceiptMapper extends BaseMapper<PurchaseReceipt> {

    @Select("<script>"
            + "SELECT * FROM purchase_receipts WHERE is_deleted = 0 "
            + "<if test='supplier != null and supplier != &quot;&quot;'>AND supplier LIKE CONCAT('%', #{supplier}, '%')</if> "
            + "<if test='status != null and status != &quot;&quot;'>AND (status = #{status} <if test='status == &quot;received&quot;'>OR status = 'scanned_in' OR status = 'SCANNED_IN'</if>)</if> "
            + "<if test='reconciliationStatus != null and reconciliationStatus != &quot;&quot;'>AND reconciliation_status = #{reconciliationStatus}</if> "
            + "<if test='includeReceived != null and includeReceived == false'>AND (status IS NULL OR (status != 'received' AND status != 'scanned_in' AND status != 'SCANNED_IN'))</if> "
            + "<choose>"
            + "<when test='orderByClause != null and orderByClause != &quot;&quot;'>ORDER BY ${orderByClause}</when>"
            + "<otherwise>ORDER BY created_at DESC</otherwise>"
            + "</choose>"
            + "</script>")
    IPage<PurchaseReceipt> selectPaged(Page<PurchaseReceipt> page,
                                       @Param("supplier") String supplier,
                                       @Param("status") String status,
                                       @Param("reconciliationStatus") String reconciliationStatus,
                                       @Param("includeReceived") Boolean includeReceived,
                                       @Param("orderByClause") String orderByClause);

        @Update("UPDATE purchase_receipts SET is_deleted = 1, updated_at = #{updatedAt} WHERE id = #{id} AND is_deleted = 0")
        int logicDeleteById(@Param("id") Long id, @Param("updatedAt") LocalDateTime updatedAt);
}
