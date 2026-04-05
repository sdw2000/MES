package com.fine.Dao.schedule;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.schedule.ManualScheduleCoatingAllocation;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ManualScheduleCoatingAllocationMapper extends BaseMapper<ManualScheduleCoatingAllocation> {

    @Delete("DELETE FROM manual_schedule_coating_allocation WHERE schedule_id = #{scheduleId}")
    int deleteByScheduleId(@Param("scheduleId") Long scheduleId);
}
