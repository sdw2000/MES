package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.production.EquipmentDailyStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface EquipmentDailyStatusMapper extends BaseMapper<EquipmentDailyStatus> {

    @Select("<script>" +
            "SELECT ds.id, " +
            "e.id AS equipment_id, " +
            "e.equipment_code, " +
            "ds.plan_date, " +
            "COALESCE(ds.daily_status, 'OPEN') AS daily_status, " +
            "ds.reason, " +
            "COALESCE(ds.min_staff_required, 1) AS min_staff_required, " +
            "ds.required_skill_level, " +
            "ds.create_time, ds.update_time, ds.create_by, ds.update_by, " +
            "e.equipment_name, e.equipment_type, et.type_name AS equipment_type_name, w.workshop_name, e.status AS equipment_status " +
            "FROM equipment e " +
            "LEFT JOIN equipment_daily_status ds ON ds.equipment_id = e.id AND ds.plan_date = #{planDate} " +
            "LEFT JOIN equipment_type et ON e.equipment_type = et.type_code " +
            "LEFT JOIN workshop w ON e.workshop_id = w.id " +
            "WHERE e.is_deleted = 0 " +
            "<if test='equipmentType != null and equipmentType != \"\"'> AND e.equipment_type = #{equipmentType} </if>" +
            "<if test='keyword != null and keyword != \"\"'> AND (e.equipment_code LIKE CONCAT('%', #{keyword}, '%') OR e.equipment_name LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "ORDER BY et.process_order, e.equipment_code " +
            "</script>")
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "equipment_id", property = "equipmentId"),
            @Result(column = "equipment_code", property = "equipmentCode"),
            @Result(column = "plan_date", property = "planDate"),
            @Result(column = "daily_status", property = "dailyStatus"),
            @Result(column = "reason", property = "reason"),
            @Result(column = "min_staff_required", property = "minStaffRequired"),
            @Result(column = "required_skill_level", property = "requiredSkillLevel"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime"),
            @Result(column = "create_by", property = "createBy"),
            @Result(column = "update_by", property = "updateBy"),
            @Result(column = "equipment_name", property = "equipmentName"),
            @Result(column = "equipment_type", property = "equipmentType"),
            @Result(column = "equipment_type_name", property = "equipmentTypeName"),
            @Result(column = "workshop_name", property = "workshopName"),
            @Result(column = "equipment_status", property = "equipmentStatus")
    })
        List<EquipmentDailyStatus> selectDailyStatusList(@Param("planDate") LocalDateTime planDate,
                                                     @Param("equipmentType") String equipmentType,
                                                     @Param("keyword") String keyword);

    @Select("SELECT * FROM equipment_daily_status WHERE equipment_id = #{equipmentId} AND plan_date = #{planDate} LIMIT 1")
        EquipmentDailyStatus selectByDateAndEquipment(@Param("planDate") LocalDateTime planDate,
                                                   @Param("equipmentId") Long equipmentId);

    @Select("SELECT * FROM equipment_daily_status WHERE equipment_code = #{equipmentCode} AND plan_date = #{planDate} LIMIT 1")
        EquipmentDailyStatus selectByDateAndEquipmentCode(@Param("planDate") LocalDateTime planDate,
                                                       @Param("equipmentCode") String equipmentCode);
}
