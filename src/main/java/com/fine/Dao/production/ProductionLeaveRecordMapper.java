package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.production.ProductionLeaveRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

@Mapper
public interface ProductionLeaveRecordMapper extends BaseMapper<ProductionLeaveRecord> {

    @Select("<script>" +
            "SELECT * FROM production_leave_record WHERE is_deleted = 0 " +
            "<if test='staffId != null'> AND staff_id = #{staffId} </if>" +
            "<if test='status != null and status != \"\"'> AND status = #{status} </if>" +
            "<if test='startDate != null'> AND end_date <![CDATA[>=]]> #{startDate} </if>" +
            "<if test='endDate != null'> AND start_date <![CDATA[<=]]> #{endDate} </if>" +
            "ORDER BY start_date DESC, create_time DESC" +
            "</script>")
    List<ProductionLeaveRecord> selectLeaveList(@Param("staffId") Long staffId,
                                                @Param("status") String status,
                                                @Param("startDate") Date startDate,
                                                @Param("endDate") Date endDate);
}
