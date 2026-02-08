package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.production.ScheduleTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;

@Mapper
public interface ScheduleTaskMapper extends BaseMapper<ScheduleTask> {

    @Select("SELECT MAX(plan_end_time) FROM schedule_task WHERE equipment_id = #{equipmentId} AND process_type = #{processType} AND status IN ('SCHEDULED','IN_PROGRESS','COMPLETED')")
    Date selectMaxEndTime(@Param("equipmentId") Long equipmentId, @Param("processType") String processType);
}
