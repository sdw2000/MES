package com.fine.Dao;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.TapeMin;

@Mapper
public interface TapeMinMapper extends BaseMapper<TapeMin> {
	
	
	@Select("SELECT q.customer_id, t.part_number, t.id, t.material_name, t.total_thickness, qd.price " +
	        "FROM erp.tapes t " +
	        "JOIN erp.quotation_details qd ON t.id = qd.material_code " +
	        "JOIN erp.quotation q ON q.id = qd.quotation_id " +
	        "WHERE t.part_number LIKE CONCAT('%', #{query}, '%') AND q.customer_id = #{id}")
	List<TapeMin> selectUserOrdersqueryWithPartNumber(@Param("query") String query, @Param("id") Integer id);
	
	
	

}
