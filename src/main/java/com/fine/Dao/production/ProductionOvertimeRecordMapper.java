package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.production.ProductionOvertimeRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

@Mapper
public interface ProductionOvertimeRecordMapper extends BaseMapper<ProductionOvertimeRecord> {

    @Select("<script>" +
            "SELECT * FROM production_overtime_record WHERE is_deleted = 0 " +
            "<if test='staffId != null'> AND staff_id = #{staffId} </if>" +
            "<if test='status != null and status != \"\"'> AND status = #{status} </if>" +
            "<if test='startDate != null'> AND overtime_date <![CDATA[>=]]> #{startDate} </if>" +
            "<if test='endDate != null'> AND overtime_date <![CDATA[<=]]> #{endDate} </if>" +
            "ORDER BY overtime_date DESC, create_time DESC" +
            "</script>")
    List<ProductionOvertimeRecord> selectOvertimeList(@Param("staffId") Long staffId,
                                                      @Param("status") String status,
                                                      @Param("startDate") Date startDate,
                                                      @Param("endDate") Date endDate);
}
