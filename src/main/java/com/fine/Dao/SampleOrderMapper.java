package com.fine.Dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.SampleOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 送样订单Mapper接口
 * @author AI Assistant
 * @date 2026-01-05
 */
@Mapper
public interface SampleOrderMapper extends BaseMapper<SampleOrder> {
    
    /**
     * 生成送样编号
     * 格式：SP + 年月日(8位) + 流水号(3位)
     * 例如：SP20260105001
     */
    @Select("SELECT CONCAT('SP', DATE_FORMAT(NOW(), '%Y%m%d'), " +
            "LPAD(IFNULL(MAX(CAST(SUBSTRING(sample_no, 11) AS UNSIGNED)), 0) + 1, 3, '0')) " +
            "FROM sample_orders " +
            "WHERE sample_no LIKE CONCAT('SP', DATE_FORMAT(NOW(), '%Y%m%d'), '%') " +
            "AND is_deleted = 0")
    String generateSampleNo();
}
