package com.fine.Dao.purchase;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.purchase.PurchaseReceiptItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

@Mapper
public interface PurchaseReceiptItemMapper extends BaseMapper<PurchaseReceiptItem> {

    @Select("SELECT * FROM purchase_receipt_items WHERE receipt_id = #{receiptId} AND is_deleted = 0")
    List<PurchaseReceiptItem> selectByReceiptId(@Param("receiptId") Long receiptId);

    @Delete("UPDATE purchase_receipt_items SET is_deleted = 1 WHERE receipt_id = #{receiptId}")
    int deleteByReceiptId(@Param("receiptId") Long receiptId);
}
