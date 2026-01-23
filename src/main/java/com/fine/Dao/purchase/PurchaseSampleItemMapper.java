package com.fine.Dao.purchase;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.purchase.PurchaseSampleItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

@Mapper
public interface PurchaseSampleItemMapper extends BaseMapper<PurchaseSampleItem> {

    @Select("SELECT * FROM purchase_sample_items WHERE sample_no = #{sampleNo} AND is_deleted = 0")
    List<PurchaseSampleItem> selectBySampleNo(@Param("sampleNo") String sampleNo);

    @Delete("UPDATE purchase_sample_items SET is_deleted = 1 WHERE sample_no = #{sampleNo}")
    int deleteBySampleNo(@Param("sampleNo") String sampleNo);
}
