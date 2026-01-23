package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.model.production.ProductionTeam;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 班组Mapper
 */
@Mapper
public interface ProductionTeamMapper extends BaseMapper<ProductionTeam> {

    /**
     * 查询班组列表（带关联信息）
     */
    @Select("<script>" +
            "SELECT t.*, w.workshop_name, s.staff_name as leader_name, sd.shift_name, " +
            "(SELECT COUNT(1) FROM production_staff ps WHERE ps.team_id = t.id AND ps.is_deleted = 0) as member_count " +
            "FROM production_team t " +
            "LEFT JOIN workshop w ON t.workshop_id = w.id " +
            "LEFT JOIN production_staff s ON t.leader_id = s.id " +
            "LEFT JOIN shift_definition sd ON t.shift_code = sd.shift_code " +
            "WHERE 1=1 " +
            "<if test='teamCode != null and teamCode != \"\"'>" +
            "  AND t.team_code LIKE CONCAT('%', #{teamCode}, '%') " +
            "</if>" +
            "<if test='teamName != null and teamName != \"\"'>" +
            "  AND t.team_name LIKE CONCAT('%', #{teamName}, '%') " +
            "</if>" +
            "<if test='workshopId != null'>" +
            "  AND t.workshop_id = #{workshopId} " +
            "</if>" +
            "<if test='status != null'>" +
            "  AND t.status = #{status} " +
            "</if>" +
            "ORDER BY t.team_code " +
            "</script>")
    List<ProductionTeam> selectTeamList(@Param("teamCode") String teamCode,
                                        @Param("teamName") String teamName,
                                        @Param("workshopId") Long workshopId,
                                        @Param("status") Integer status);

    /**
     * 分页查询班组列表（使用MyBatis-Plus IPage）
     */
    @Select("<script>" +
            "SELECT t.*, w.workshop_name, s.staff_name as leader_name, sd.shift_name, " +
            "(SELECT COUNT(1) FROM production_staff ps WHERE ps.team_id = t.id AND ps.is_deleted = 0) as member_count " +
            "FROM production_team t " +
            "LEFT JOIN workshop w ON t.workshop_id = w.id " +
            "LEFT JOIN production_staff s ON t.leader_id = s.id " +
            "LEFT JOIN shift_definition sd ON t.shift_code = sd.shift_code " +
            "WHERE 1=1 " +
            "<if test='teamCode != null and teamCode != \"\"'>" +
            "  AND t.team_code LIKE CONCAT('%', #{teamCode}, '%') " +
            "</if>" +
            "<if test='teamName != null and teamName != \"\"'>" +
            "  AND t.team_name LIKE CONCAT('%', #{teamName}, '%') " +
            "</if>" +
            "<if test='workshopId != null'>" +
            "  AND t.workshop_id = #{workshopId} " +
            "</if>" +
            "<if test='status != null'>" +
            "  AND t.status = #{status} " +
            "</if>" +
            "ORDER BY t.team_code " +
            "</script>")
    IPage<ProductionTeam> selectTeamPageList(IPage<ProductionTeam> page,
                                             @Param("teamCode") String teamCode,
                                             @Param("teamName") String teamName,
                                             @Param("workshopId") Long workshopId,
                                             @Param("status") Integer status);

    /**
     * 根据ID查询详情（带关联信息）
     */
    @Select("SELECT t.*, w.workshop_name, s.staff_name as leader_name, sd.shift_name " +
            "FROM production_team t " +
            "LEFT JOIN workshop w ON t.workshop_id = w.id " +
            "LEFT JOIN production_staff s ON t.leader_id = s.id " +
            "LEFT JOIN shift_definition sd ON t.shift_code = sd.shift_code " +
            "WHERE t.id = #{id}")
    ProductionTeam selectTeamById(@Param("id") Long id);

    /**
     * 检查班组编号是否已存在
     */
    @Select("SELECT COUNT(1) FROM production_team WHERE team_code = #{teamCode} AND id != #{excludeId}")
    int checkTeamCodeExists(@Param("teamCode") String teamCode, @Param("excludeId") Long excludeId);

    /**
     * 根据车间ID查询班组
     */
    @Select("SELECT * FROM production_team WHERE workshop_id = #{workshopId} AND status = 1 ORDER BY team_code")
    List<ProductionTeam> selectByWorkshopId(@Param("workshopId") Long workshopId);

    /**
     * 查询所有启用的班组
     */
    @Select("SELECT t.*, w.workshop_name FROM production_team t " +
            "LEFT JOIN workshop w ON t.workshop_id = w.id " +
            "WHERE t.status = 1 ORDER BY t.team_code")
    List<ProductionTeam> selectAllActive();
}
