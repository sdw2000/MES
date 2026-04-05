package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.production.EquipmentStaffAssignment;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface EquipmentStaffAssignmentMapper extends BaseMapper<EquipmentStaffAssignment> {

    @Select("SELECT a.id, a.equipment_id, a.equipment_code, a.plan_date, a.shift_id, a.shift_code, a.shift_name, " +
            "a.staff_id, a.staff_code, a.staff_name, a.role_name, a.on_duty, a.remark, a.create_time, a.update_time, a.create_by, a.update_by, " +
            "e.equipment_type " +
            "FROM equipment_staff_assignment a " +
            "LEFT JOIN equipment e ON a.equipment_id = e.id " +
            "WHERE a.plan_date = #{planDate} AND a.equipment_id = #{equipmentId} " +
            "ORDER BY a.shift_id, a.id")
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "equipment_id", property = "equipmentId"),
            @Result(column = "equipment_code", property = "equipmentCode"),
            @Result(column = "plan_date", property = "planDate"),
            @Result(column = "shift_id", property = "shiftId"),
            @Result(column = "shift_code", property = "shiftCode"),
            @Result(column = "shift_name", property = "shiftName"),
            @Result(column = "staff_id", property = "staffId"),
            @Result(column = "staff_code", property = "staffCode"),
            @Result(column = "staff_name", property = "staffName"),
            @Result(column = "role_name", property = "roleName"),
            @Result(column = "on_duty", property = "onDuty"),
            @Result(column = "remark", property = "remark"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime"),
            @Result(column = "create_by", property = "createBy"),
            @Result(column = "update_by", property = "updateBy"),
            @Result(column = "equipment_type", property = "equipmentType")
    })
        List<EquipmentStaffAssignment> selectByDateAndEquipment(@Param("planDate") LocalDateTime planDate,
                                                            @Param("equipmentId") Long equipmentId);

    @Delete("DELETE FROM equipment_staff_assignment WHERE plan_date = #{planDate} AND equipment_id = #{equipmentId}")
        int deleteByDateAndEquipment(@Param("planDate") LocalDateTime planDate,
                                 @Param("equipmentId") Long equipmentId);

    @Select("SELECT COUNT(1) FROM equipment_staff_assignment WHERE plan_date = #{planDate} AND equipment_id = #{equipmentId} AND on_duty = 1")
        int countOnDutyByDateAndEquipment(@Param("planDate") LocalDateTime planDate,
                                      @Param("equipmentId") Long equipmentId);
}
