package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.production.EquipmentOccupation;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface EquipmentOccupationMapper extends BaseMapper<EquipmentOccupation> {

    @Select("SELECT end_time FROM equipment_occupation " +
            "WHERE equipment_code = #{equipmentCode} AND process_type = #{processType} " +
            "AND status IN ('PLANNED','RUNNING','FINISHED') " +
            "AND schedule_id IN (SELECT id FROM manual_schedule) " +
            "AND (#{excludeScheduleId} IS NULL OR schedule_id <> #{excludeScheduleId}) " +
            "ORDER BY end_time DESC LIMIT 1")
    LocalDateTime selectLatestEndTime(@Param("equipmentCode") String equipmentCode,
                                      @Param("processType") String processType,
                                      @Param("excludeScheduleId") Long excludeScheduleId);

    @Delete("DELETE FROM equipment_occupation WHERE schedule_id = #{scheduleId} AND process_type = #{processType}")
    int deleteByScheduleAndProcess(@Param("scheduleId") Long scheduleId,
                                   @Param("processType") String processType);

    @Select("SELECT start_time FROM equipment_occupation " +
            "WHERE schedule_id = #{scheduleId} AND process_type = #{processType} " +
            "AND status IN ('PLANNED','RUNNING','FINISHED') " +
            "ORDER BY start_time ASC LIMIT 1")
    LocalDateTime selectStartTimeByScheduleAndProcess(@Param("scheduleId") Long scheduleId,
                                                      @Param("processType") String processType);

    @Select("SELECT end_time FROM equipment_occupation " +
            "WHERE schedule_id = #{scheduleId} AND process_type = #{processType} " +
            "AND status IN ('PLANNED','RUNNING','FINISHED') " +
            "ORDER BY end_time DESC LIMIT 1")
    LocalDateTime selectEndTimeByScheduleAndProcess(@Param("scheduleId") Long scheduleId,
                                                    @Param("processType") String processType);

    @Select("SELECT duration_minutes FROM equipment_occupation " +
            "WHERE schedule_id = #{scheduleId} AND process_type = #{processType} " +
            "AND status IN ('PLANNED','RUNNING','FINISHED') " +
            "ORDER BY id DESC LIMIT 1")
    Integer selectDurationMinutesByScheduleAndProcess(@Param("scheduleId") Long scheduleId,
                                                      @Param("processType") String processType);
}
