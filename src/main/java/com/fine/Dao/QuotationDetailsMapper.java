package com.fine.Dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.QuotationDetail;

@Mapper
public interface QuotationDetailsMapper extends BaseMapper<QuotationDetail>{
	
	
	@Select("SELECT t.material_name,t.total_thickness, t.part_number, qd.* " +
	        "FROM quotation_details qd " +
	        "LEFT JOIN tapes t ON qd.material_code = t.id " +
	        "WHERE qd.quotation_id = #{id}")
	List<QuotationDetail> selectList(@Param("id") String id);

}
