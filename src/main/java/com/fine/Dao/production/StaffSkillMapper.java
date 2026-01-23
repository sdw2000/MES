package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.production.StaffSkill;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 人员技能Mapper
 */
@Mapper
public interface StaffSkillMapper extends BaseMapper<StaffSkill> {

    /**
     * 查询人员技能列表（带关联信息）
     */
    @Select("SELECT sk.*, et.type_name as equipment_type_name, s.staff_name " +
            "FROM staff_skill sk " +
            "LEFT JOIN equipment_type et ON sk.equipment_type = et.type_code " +
            "LEFT JOIN production_staff s ON sk.staff_id = s.id " +
            "WHERE sk.staff_id = #{staffId} " +
            "ORDER BY et.process_order")
    List<StaffSkill> selectByStaffId(@Param("staffId") Long staffId);

    /**
     * 删除人员的所有技能
     */
    @Delete("DELETE FROM staff_skill WHERE staff_id = #{staffId}")
    int deleteByStaffId(@Param("staffId") Long staffId);

    /**
     * 检查技能是否已存在
     */
    @Select("SELECT COUNT(1) FROM staff_skill WHERE staff_id = #{staffId} AND equipment_type = #{equipmentType}")
    int checkSkillExists(@Param("staffId") Long staffId, @Param("equipmentType") String equipmentType);

    /**
     * 根据设备类型查询有此技能的人员ID列表
     */
    @Select("SELECT staff_id FROM staff_skill WHERE equipment_type = #{equipmentType}")
    List<Long> selectStaffIdsByEquipmentType(@Param("equipmentType") String equipmentType);

    /**
     * 批量插入技能
     */
    @Insert("<script>" +
            "INSERT INTO staff_skill (staff_id, equipment_type, proficiency, max_machines, certificate, cert_expire_date, create_time) VALUES " +
            "<foreach collection='skills' item='skill' separator=','>" +
            "(#{skill.staffId}, #{skill.equipmentType}, #{skill.proficiency}, #{skill.maxMachines}, #{skill.certificate}, #{skill.certExpireDate}, NOW())" +
            "</foreach>" +
            "</script>")
    int batchInsert(@Param("skills") List<StaffSkill> skills);
}
