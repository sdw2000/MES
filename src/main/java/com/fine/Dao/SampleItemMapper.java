package com.fine.Dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.SampleItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/**
 * 送样明细Mapper接口
 * @author AI Assistant
 * @date 2026-01-05
 */
@Mapper
public interface SampleItemMapper extends BaseMapper<SampleItem> {
    
    /**
     * 根据送样编号查询明细列表
     */
    @Select("SELECT * FROM sample_items WHERE sample_no = #{sampleNo} ORDER BY id")
    List<SampleItem> selectBySampleNo(String sampleNo);
    
    /**
     * 根据送样编号删除明细
     */
    @Delete("DELETE FROM sample_items WHERE sample_no = #{sampleNo}")
    int deleteBySampleNo(String sampleNo);
}
