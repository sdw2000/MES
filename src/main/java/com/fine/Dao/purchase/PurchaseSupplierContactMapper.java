package com.fine.Dao.purchase;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.purchase.PurchaseSupplierContact;

@Mapper
public interface PurchaseSupplierContactMapper extends BaseMapper<PurchaseSupplierContact> {

    @Select("SELECT * FROM purchase_supplier_contacts WHERE supplier_id = #{supplierId} AND is_deleted = 0 ORDER BY id ASC")
    List<PurchaseSupplierContact> selectBySupplierId(@Param("supplierId") Long supplierId);

    @Update("UPDATE purchase_supplier_contacts SET is_primary = 0 WHERE supplier_id = #{supplierId}")
    int clearPrimary(@Param("supplierId") Long supplierId);

    @Update("DELETE FROM purchase_supplier_contacts WHERE supplier_id = #{supplierId}")
    int deleteBySupplierId(@Param("supplierId") Long supplierId);
}
