package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.production.EquipmentScheduleConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EquipmentScheduleConfigMapper extends BaseMapper<EquipmentScheduleConfig> {

    @Select("<script>" +
            "SELECT cfg.id, " +
            "e.id AS equipment_id, " +
            "e.equipment_code, " +
            "cfg.initial_schedule_time, " +
            "cfg.cycle_end_time, " +
            "COALESCE(cfg.next_week_start_time, '08:00:00') AS next_week_start_time, " +
            "COALESCE(cfg.weekend_rest, 1) AS weekend_rest, " +
            "COALESCE(cfg.sunday_disabled, 1) AS sunday_disabled, " +
            "COALESCE(cfg.enabled, 1) AS enabled, " +
            "COALESCE(cfg.min_staff_required, 1) AS min_staff_required, " +
            "cfg.required_skill_level, " +
            "cfg.remark, " +
            "cfg.create_time, " +
            "cfg.update_time, " +
            "e.equipment_name, " +
            "e.equipment_type, " +
            "et.type_name AS equipment_type_name, " +
            "w.workshop_name, " +
            "e.status AS equipment_status " +
            "FROM equipment e " +
            "LEFT JOIN equipment_schedule_config cfg ON cfg.equipment_id = e.id " +
            "LEFT JOIN equipment_type et ON e.equipment_type = et.type_code " +
            "LEFT JOIN workshop w ON e.workshop_id = w.id " +
            "WHERE e.is_deleted = 0 " +
            "<if test='equipmentType != null and equipmentType != \"\"'> AND e.equipment_type = #{equipmentType} </if>" +
            "<if test='keyword != null and keyword != \"\"'> AND (e.equipment_code LIKE CONCAT('%', #{keyword}, '%') OR e.equipment_name LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "ORDER BY et.process_order, e.equipment_code" +
            "</script>")
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "equipment_id", property = "equipmentId"),
            @Result(column = "equipment_code", property = "equipmentCode"),
            @Result(column = "initial_schedule_time", property = "initialScheduleTime"),
            @Result(column = "cycle_end_time", property = "cycleEndTime"),
            @Result(column = "next_week_start_time", property = "nextWeekStartTime"),
            @Result(column = "weekend_rest", property = "weekendRest"),
            @Result(column = "sunday_disabled", property = "sundayDisabled"),
            @Result(column = "enabled", property = "enabled"),
            @Result(column = "min_staff_required", property = "minStaffRequired"),
            @Result(column = "required_skill_level", property = "requiredSkillLevel"),
            @Result(column = "remark", property = "remark"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime"),
            @Result(column = "equipment_name", property = "equipmentName"),
            @Result(column = "equipment_type", property = "equipmentType"),
            @Result(column = "equipment_type_name", property = "equipmentTypeName"),
            @Result(column = "workshop_name", property = "workshopName"),
            @Result(column = "equipment_status", property = "equipmentStatus")
    })
    List<EquipmentScheduleConfig> selectConfigList(@Param("equipmentType") String equipmentType,
                                                   @Param("keyword") String keyword);

    @Select("SELECT * FROM equipment_schedule_config WHERE equipment_id = #{equipmentId} LIMIT 1")
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "equipment_id", property = "equipmentId"),
            @Result(column = "equipment_code", property = "equipmentCode"),
            @Result(column = "initial_schedule_time", property = "initialScheduleTime"),
            @Result(column = "cycle_end_time", property = "cycleEndTime"),
            @Result(column = "next_week_start_time", property = "nextWeekStartTime"),
            @Result(column = "weekend_rest", property = "weekendRest"),
            @Result(column = "sunday_disabled", property = "sundayDisabled"),
            @Result(column = "enabled", property = "enabled"),
            @Result(column = "min_staff_required", property = "minStaffRequired"),
            @Result(column = "required_skill_level", property = "requiredSkillLevel"),
            @Result(column = "remark", property = "remark"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime")
    })
    EquipmentScheduleConfig selectByEquipmentId(@Param("equipmentId") Long equipmentId);

    @Select("SELECT * FROM equipment_schedule_config WHERE equipment_code = #{equipmentCode} LIMIT 1")
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "equipment_id", property = "equipmentId"),
            @Result(column = "equipment_code", property = "equipmentCode"),
            @Result(column = "initial_schedule_time", property = "initialScheduleTime"),
            @Result(column = "cycle_end_time", property = "cycleEndTime"),
            @Result(column = "next_week_start_time", property = "nextWeekStartTime"),
            @Result(column = "weekend_rest", property = "weekendRest"),
            @Result(column = "sunday_disabled", property = "sundayDisabled"),
            @Result(column = "enabled", property = "enabled"),
            @Result(column = "min_staff_required", property = "minStaffRequired"),
            @Result(column = "required_skill_level", property = "requiredSkillLevel"),
            @Result(column = "remark", property = "remark"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime")
    })
    EquipmentScheduleConfig selectByEquipmentCode(@Param("equipmentCode") String equipmentCode);
}
