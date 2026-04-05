package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.model.production.ScheduleSlitting;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface ScheduleSlittingMapper extends BaseMapper<ScheduleSlitting> {

    @Select("SELECT ss.*, e.equipment_name FROM schedule_slitting ss " +
            "LEFT JOIN equipment e ON ss.equipment_id = e.id " +
            "WHERE ss.schedule_id = #{scheduleId} ORDER BY ss.plan_start_time ASC")
    List<ScheduleSlitting> selectByScheduleId(@Param("scheduleId") Long scheduleId);

    /**
     * 批量查询任务号（按排程ID）
     */
    @Select("<script>" +
            "SELECT schedule_id AS scheduleId, task_no AS taskNo FROM schedule_slitting " +
            "WHERE schedule_id IN " +
            "<foreach collection='scheduleIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach> " +
            "ORDER BY id DESC" +
            "</script>")
    List<Map<String, Object>> selectTaskNoByScheduleIds(@Param("scheduleIds") List<Long> scheduleIds);

    @Select("SELECT ss.*, e.equipment_name FROM schedule_slitting ss " +
            "LEFT JOIN equipment e ON ss.equipment_id = e.id " +
            "WHERE ss.id = #{id}")
    ScheduleSlitting selectById(@Param("id") Long id);

    @Select("SELECT * FROM schedule_slitting WHERE task_no = #{taskNo}")
    ScheduleSlitting selectByTaskNo(@Param("taskNo") String taskNo);

    @Select("SELECT CONCAT('FQ-', DATE_FORMAT(COALESCE(#{planDate}, NOW()), '%y%m%d'), '-', " +
            "LPAD(IFNULL(MAX(CAST(SUBSTRING(task_no, -3) AS UNSIGNED)), 0) + 1, 3, '0')) " +
            "FROM schedule_slitting " +
            "WHERE task_no LIKE CONCAT('FQ-', DATE_FORMAT(COALESCE(#{planDate}, NOW()), '%y%m%d'), '-%')")
    String generateTaskNo(@Param("planDate") java.util.Date planDate);

    @Insert("INSERT INTO schedule_slitting (schedule_id, task_no, equipment_id, equipment_code, " +
            "staff_id, staff_name, shift_code, plan_date, source_batch_no, source_stock_id, " +
            "slit_width, slit_length, material_code, material_name, thickness, target_width, " +
            "cuts_per_slit, plan_rolls, edge_loss, slitting_speed, plan_start_time, plan_end_time, " +
            "plan_duration, status, remark, create_by) VALUES " +
            "(#{scheduleId}, #{taskNo}, #{equipmentId}, #{equipmentCode}, #{staffId}, #{staffName}, " +
            "#{shiftCode}, #{planDate}, #{sourceBatchNo}, #{sourceStockId}, #{slitWidth}, #{slitLength}, " +
            "#{materialCode}, #{materialName}, #{thickness}, #{targetWidth}, #{cutsPerSlit}, " +
            "#{planRolls}, #{edgeLoss}, #{slittingSpeed}, #{planStartTime}, #{planEndTime}, " +
            "#{planDuration}, #{status}, #{remark}, #{createBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ScheduleSlitting slitting);

    @Update("<script>" +
            "UPDATE schedule_slitting SET " +
            "<if test='equipmentId != null'>equipment_id = #{equipmentId}, </if>" +
            "<if test='equipmentCode != null'>equipment_code = #{equipmentCode}, </if>" +
            "<if test='staffId != null'>staff_id = #{staffId}, </if>" +
            "<if test='staffName != null'>staff_name = #{staffName}, </if>" +
            "<if test='shiftCode != null'>shift_code = #{shiftCode}, </if>" +
            "<if test='planDate != null'>plan_date = #{planDate}, </if>" +
            "<if test='planRolls != null'>plan_rolls = #{planRolls}, </if>" +
            "<if test='actualRolls != null'>actual_rolls = #{actualRolls}, </if>" +
            "<if test='planStartTime != null'>plan_start_time = #{planStartTime}, </if>" +
            "<if test='planEndTime != null'>plan_end_time = #{planEndTime}, </if>" +
            "<if test='planDuration != null'>plan_duration = #{planDuration}, </if>" +
            "<if test='actualStartTime != null'>actual_start_time = #{actualStartTime}, </if>" +
            "<if test='actualEndTime != null'>actual_end_time = #{actualEndTime}, </if>" +
            "<if test='actualDuration != null'>actual_duration = #{actualDuration}, </if>" +
            "<if test='status != null'>status = #{status}, </if>" +
            "<if test='outputBatchNo != null'>output_batch_no = #{outputBatchNo}, </if>" +
            "<if test='remark != null'>remark = #{remark}, </if>" +
            "update_by = #{updateBy}, update_time = NOW() " +
            "WHERE id = #{id}" +
            "</script>")
    int update(ScheduleSlitting slitting);

    @Delete("DELETE FROM schedule_slitting WHERE schedule_id = #{scheduleId}")
    int deleteByScheduleId(@Param("scheduleId") Long scheduleId);

    @Select("<script>" +
            "SELECT ss.*, e.equipment_name FROM schedule_slitting ss " +
            "LEFT JOIN equipment e ON ss.equipment_id = e.id " +
            "WHERE 1=1 " +
            "<if test='planDate != null'>AND ss.plan_date = #{planDate} </if>" +
            "<if test='status != null and status != \"\"'>AND ss.status = #{status} </if>" +
            "<if test='equipmentId != null'>AND ss.equipment_id = #{equipmentId} </if>" +
            "ORDER BY ss.plan_start_time ASC" +
            "</script>")
    List<ScheduleSlitting> selectByCondition(Map<String, Object> params);    /**
     * MyBatis-Plus分页查询分切计划（标准方式）
     */
    @Select("<script>" +
            "SELECT ss.*, e.equipment_name FROM schedule_slitting ss " +
            "LEFT JOIN equipment e ON ss.equipment_id = e.id " +
            "WHERE 1=1 " +
            "<if test='params.scheduleId != null'>AND ss.schedule_id = #{params.scheduleId} </if>" +
            "<if test='params.planDate != null'>AND ss.plan_date = #{params.planDate} </if>" +
            "<if test='params.status != null and params.status != \"\"'>AND ss.status = #{params.status} </if>" +
            "<if test='params.equipmentId != null'>AND ss.equipment_id = #{params.equipmentId} </if>" +
            "<if test='params.materialCode != null and params.materialCode != \"\"'>AND ss.material_code LIKE CONCAT('%', #{params.materialCode}, '%') </if>" +
            "ORDER BY ss.plan_start_time ASC " +
            "</script>")
    IPage<ScheduleSlitting> selectPage(Page<ScheduleSlitting> page, @Param("params") Map<String, Object> params);
}
