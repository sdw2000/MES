package com.fine.Dao.purchase;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.modle.purchase.PurchaseSample;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PurchaseSampleMapper extends BaseMapper<PurchaseSample> {

    @Select("<script>"
            + "SELECT * FROM purchase_samples WHERE is_deleted = 0 "
            + "<if test='supplier != null and supplier != &quot;&quot;'>AND supplier LIKE CONCAT('%', #{supplier}, '%')</if> "
            + "<if test='status != null and status != &quot;&quot;'>AND status = #{status}</if> "
            + "<if test='trackingNumber != null and trackingNumber != &quot;&quot;'>AND tracking_number LIKE CONCAT('%', #{trackingNumber}, '%')</if> "
            + "ORDER BY created_at DESC"
            + "</script>")
    IPage<PurchaseSample> selectPaged(Page<PurchaseSample> page,
                                      @Param("supplier") String supplier,
                                      @Param("status") String status,
                                      @Param("trackingNumber") String trackingNumber);

    @Select("SELECT CONCAT('PS', DATE_FORMAT(NOW(), '%Y%m%d'), LPAD((SELECT COALESCE(MAX(SUBSTRING(sample_no, 11, 3)),0)+1 FROM purchase_samples WHERE sample_no LIKE CONCAT('PS', DATE_FORMAT(NOW(), '%Y%m%d'), '%')), 3, '0'))")
    String generateSampleNo();
}
