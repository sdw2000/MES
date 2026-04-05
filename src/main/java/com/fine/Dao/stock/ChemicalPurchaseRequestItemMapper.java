package com.fine.Dao.stock;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.stock.ChemicalPurchaseRequestItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ChemicalPurchaseRequestItemMapper extends BaseMapper<ChemicalPurchaseRequestItem> {

    @Select("SELECT * FROM chemical_purchase_request_item WHERE request_id = #{requestId} ORDER BY id ASC")
    List<ChemicalPurchaseRequestItem> selectByRequestId(@Param("requestId") Long requestId);

    @Update("UPDATE chemical_purchase_request_item SET requested_qty = #{requestedQty}, update_time = NOW() WHERE id = #{id}")
    int updateRequestedQty(@Param("id") Long id, @Param("requestedQty") Integer requestedQty);

    @Update("UPDATE chemical_purchase_request_item SET purchase_order_item_id = #{purchaseOrderItemId}, update_time = NOW() WHERE id = #{id}")
    int updatePurchaseOrderItemId(@Param("id") Long id, @Param("purchaseOrderItemId") Long purchaseOrderItemId);

    @Update("UPDATE chemical_purchase_request_item SET received_qty = #{receivedQty}, update_time = NOW() WHERE id = #{id}")
    int updateReceivedQty(@Param("id") Long id, @Param("receivedQty") Integer receivedQty);
}
