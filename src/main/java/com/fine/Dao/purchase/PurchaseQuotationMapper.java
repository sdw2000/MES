package com.fine.Dao.purchase;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.modle.purchase.PurchaseQuotation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PurchaseQuotationMapper extends BaseMapper<PurchaseQuotation> {

    @Select("<script>"
            + "SELECT * FROM purchase_quotations WHERE is_deleted = 0 "
            + "<if test='supplier != null and supplier != \"\"'>AND supplier LIKE CONCAT('%', #{supplier}, '%')</if> "
            + "<if test='status != null and status != \"\"'>AND status = #{status}</if> "
            + "<if test='materialCode != null and materialCode != \"\"'>"
            + "  AND EXISTS ("
            + "    SELECT 1 FROM purchase_quotation_items qi"
            + "    WHERE qi.quotation_id = purchase_quotations.id"
            + "      AND qi.is_deleted = 0"
            + "      AND qi.material_code LIKE CONCAT('%', #{materialCode}, '%')"
            + "  )"
            + "</if> "
            + "ORDER BY created_at DESC"
            + "</script>")
    IPage<PurchaseQuotation> selectPaged(Page<PurchaseQuotation> page,
                                         @Param("supplier") String supplier,
                                         @Param("status") String status,
                                         @Param("materialCode") String materialCode);
}
