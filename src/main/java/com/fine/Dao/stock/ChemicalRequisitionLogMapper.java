package com.fine.Dao.stock;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.stock.ChemicalRequisitionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChemicalRequisitionLogMapper extends BaseMapper<ChemicalRequisitionLog> {

    @Select("SELECT * FROM chemical_requisition_log WHERE request_no = #{requestNo} ORDER BY id DESC")
    List<ChemicalRequisitionLog> selectByRequestNo(@Param("requestNo") String requestNo);
}
