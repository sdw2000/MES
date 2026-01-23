package com.fine.Dao.purchase;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.modle.purchase.PurchaseReceipt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PurchaseReceiptMapper extends BaseMapper<PurchaseReceipt> {

    @Select("<script>"
            + "SELECT * FROM purchase_receipts WHERE is_deleted = 0 "
            + "<if test='supplier != null and supplier != &quot;&quot;'>AND supplier LIKE CONCAT('%', #{supplier}, '%')</if> "
            + "<if test='status != null and status != &quot;&quot;'>AND status = #{status}</if> "
            + "ORDER BY created_at DESC"
            + "</script>")
    IPage<PurchaseReceipt> selectPaged(Page<PurchaseReceipt> page,
                                       @Param("supplier") String supplier,
                                       @Param("status") String status);
}
