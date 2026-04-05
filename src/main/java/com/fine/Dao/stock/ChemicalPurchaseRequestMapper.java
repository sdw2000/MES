package com.fine.Dao.stock;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.model.stock.ChemicalPurchaseRequest;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ChemicalPurchaseRequestMapper extends BaseMapper<ChemicalPurchaseRequest> {

    @Select("<script>" +
            "SELECT * FROM chemical_purchase_request WHERE is_deleted = 0 " +
            "<if test='status != null and status != \"\"'> AND status = #{status} </if> " +
            "ORDER BY create_time DESC" +
            "</script>")
    IPage<ChemicalPurchaseRequest> selectPageByStatus(Page<ChemicalPurchaseRequest> page,
                                                      @Param("status") String status);

    @Select("SELECT * FROM chemical_purchase_request WHERE request_no = #{requestNo} AND is_deleted = 0 LIMIT 1")
    ChemicalPurchaseRequest selectByRequestNo(@Param("requestNo") String requestNo);

    @Update("UPDATE chemical_purchase_request SET status = #{status}, update_time = NOW(), update_by = #{operator} WHERE request_no = #{requestNo} AND is_deleted = 0")
    int updateStatus(@Param("requestNo") String requestNo,
                     @Param("status") String status,
                     @Param("operator") String operator);

    @Update("UPDATE chemical_purchase_request SET purchase_order_no = #{purchaseOrderNo}, update_time = NOW(), update_by = #{operator} WHERE request_no = #{requestNo} AND is_deleted = 0")
    int updatePurchaseOrderNo(@Param("requestNo") String requestNo,
                              @Param("purchaseOrderNo") String purchaseOrderNo,
                              @Param("operator") String operator);
}
