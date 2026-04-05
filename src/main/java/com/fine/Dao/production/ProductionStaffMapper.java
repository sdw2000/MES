package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.model.production.ProductionStaff;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 生产人员Mapper
 */
@Mapper
public interface ProductionStaffMapper extends BaseMapper<ProductionStaff> {

    /**
     * 查询人员列表（带关联信息）
     */
    @Select("<script>" +
            "SELECT s.*, t.team_name, w.workshop_name " +
            "FROM production_staff s " +
            "LEFT JOIN production_team t ON s.team_id = t.id " +
            "LEFT JOIN workshop w ON s.workshop_id = w.id " +
            "WHERE s.is_deleted = 0 " +
            "<if test='staffCode != null and staffCode != \"\"'>" +
            "  AND s.staff_code LIKE CONCAT('%', #{staffCode}, '%') " +
            "</if>" +
            "<if test='staffName != null and staffName != \"\"'>" +
            "  AND s.staff_name LIKE CONCAT('%', #{staffName}, '%') " +
            "</if>" +
            "<if test='teamId != null'>" +
            "  AND s.team_id = #{teamId} " +
            "</if>" +
            "<if test='workshopId != null'>" +
            "  AND s.workshop_id = #{workshopId} " +
            "</if>" +
            "<if test='status != null and status != \"\"'>" +
            "  AND s.status = #{status} " +
            "</if>" +
            "<if test='department != null and department != \"\"'>" +
            "  AND s.department LIKE CONCAT('%', #{department}, '%') " +
            "</if>" +
            "<if test='positionName != null and positionName != \"\"'>" +
            "  AND s.position_name LIKE CONCAT('%', #{positionName}, '%') " +
            "</if>" +
            "ORDER BY s.staff_code " +
            "</script>")
    List<ProductionStaff> selectStaffList(@Param("staffCode") String staffCode,
                                          @Param("staffName") String staffName,
                                          @Param("teamId") Long teamId,
                                          @Param("workshopId") Long workshopId,
                                          @Param("status") String status,
                                          @Param("department") String department,
                                          @Param("positionName") String positionName);

    /**
     * 分页查询人员列表（使用MyBatis-Plus IPage）
     */
    @Select("<script>" +
            "SELECT s.*, t.team_name, w.workshop_name " +
            "FROM production_staff s " +
            "LEFT JOIN production_team t ON s.team_id = t.id " +
            "LEFT JOIN workshop w ON s.workshop_id = w.id " +
            "WHERE s.is_deleted = 0 " +
            "<if test='staffCode != null and staffCode != \"\"'>" +
            "  AND s.staff_code LIKE CONCAT('%', #{staffCode}, '%') " +
            "</if>" +
            "<if test='staffName != null and staffName != \"\"'>" +
            "  AND s.staff_name LIKE CONCAT('%', #{staffName}, '%') " +
            "</if>" +
            "<if test='teamId != null'>" +
            "  AND s.team_id = #{teamId} " +
            "</if>" +
            "<if test='workshopId != null'>" +
            "  AND s.workshop_id = #{workshopId} " +
            "</if>" +
            "<if test='status != null and status != \"\"'>" +
            "  AND s.status = #{status} " +
            "</if>" +
            "<if test='department != null and department != \"\"'>" +
            "  AND s.department LIKE CONCAT('%', #{department}, '%') " +
            "</if>" +
            "<if test='positionName != null and positionName != \"\"'>" +
            "  AND s.position_name LIKE CONCAT('%', #{positionName}, '%') " +
            "</if>" +
            "ORDER BY s.staff_code " +
            "</script>")
    IPage<ProductionStaff> selectStaffPageList(IPage<ProductionStaff> page,
                                               @Param("staffCode") String staffCode,
                                               @Param("staffName") String staffName,
                                               @Param("teamId") Long teamId,
                                               @Param("workshopId") Long workshopId,
                                               @Param("status") String status,
                                               @Param("department") String department,
                                               @Param("positionName") String positionName);

    /**
     * 根据ID查询详情（带关联信息）
     */
    @Select("SELECT s.*, t.team_name, w.workshop_name " +
            "FROM production_staff s " +
            "LEFT JOIN production_team t ON s.team_id = t.id " +
            "LEFT JOIN workshop w ON s.workshop_id = w.id " +
            "WHERE s.id = #{id} AND s.is_deleted = 0")
    ProductionStaff selectStaffById(@Param("id") Long id);

    /**
     * 检查工号是否已存在
     */
        @Select("SELECT COUNT(1) FROM production_staff WHERE staff_code = #{staffCode} AND id != #{excludeId}")
    int checkStaffCodeExists(@Param("staffCode") String staffCode, @Param("excludeId") Long excludeId);

    /**
     * 根据班组ID查询组员
     */
    @Select("SELECT * FROM production_staff WHERE team_id = #{teamId} AND is_deleted = 0 ORDER BY staff_code")
    List<ProductionStaff> selectByTeamId(@Param("teamId") Long teamId);

    /**
     * 根据设备类型查询可操作人员
     */
    @Select("SELECT s.*, t.team_name, w.workshop_name " +
            "FROM production_staff s " +
            "INNER JOIN staff_skill sk ON s.id = sk.staff_id " +
            "LEFT JOIN production_team t ON s.team_id = t.id " +
            "LEFT JOIN workshop w ON s.workshop_id = w.id " +
            "WHERE sk.equipment_type = #{equipmentType} AND s.status = 'active' AND s.is_deleted = 0 " +
            "ORDER BY sk.proficiency DESC, sk.max_machines DESC")
    List<ProductionStaff> selectByEquipmentType(@Param("equipmentType") String equipmentType);
}
