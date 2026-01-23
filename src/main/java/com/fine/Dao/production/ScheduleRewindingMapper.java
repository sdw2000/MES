package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.model.production.ScheduleRewinding;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface ScheduleRewindingMapper extends BaseMapper<ScheduleRewinding> {

    @Select("SELECT sr.*, e.equipment_name FROM schedule_rewinding sr " +
            "LEFT JOIN equipment e ON sr.equipment_id = e.id " +
            "WHERE sr.schedule_id = #{scheduleId} ORDER BY sr.plan_start_time ASC")
    List<ScheduleRewinding> selectByScheduleId(@Param("scheduleId") Long scheduleId);

    @Select("SELECT sr.*, e.equipment_name FROM schedule_rewinding sr " +
            "LEFT JOIN equipment e ON sr.equipment_id = e.id " +
            "WHERE sr.id = #{id}")
    ScheduleRewinding selectById(@Param("id") Long id);

    @Select("SELECT * FROM schedule_rewinding WHERE task_no = #{taskNo}")
    ScheduleRewinding selectByTaskNo(@Param("taskNo") String taskNo);

    @Select("SELECT CONCAT('RW-', DATE_FORMAT(NOW(), '%Y%m%d'), '-', " +
            "LPAD(IFNULL(MAX(CAST(SUBSTRING(task_no, -3) AS UNSIGNED)), 0) + 1, 3, '0')) " +
            "FROM schedule_rewinding WHERE task_no LIKE CONCAT('RW-', DATE_FORMAT(NOW(), '%Y%m%d'), '%')")
    String generateTaskNo();

    @Insert("INSERT INTO schedule_rewinding (schedule_id, task_no, equipment_id, equipment_code, " +
            "staff_id, staff_name, shift_code, plan_date, source_batch_no, source_stock_id, " +
            "jumbo_width, jumbo_length, material_code, material_name, thickness, slit_length, " +
            "plan_rolls, rewinding_speed, tension, plan_start_time, plan_end_time, plan_duration, " +
            "status, remark, order_nos, create_by) VALUES " +
            "(#{scheduleId}, #{taskNo}, #{equipmentId}, #{equipmentCode}, #{staffId}, #{staffName}, " +
            "#{shiftCode}, #{planDate}, #{sourceBatchNo}, #{sourceStockId}, #{jumboWidth}, #{jumboLength}, " +
            "#{materialCode}, #{materialName}, #{thickness}, #{slitLength}, #{planRolls}, " +
            "#{rewindingSpeed}, #{tension}, #{planStartTime}, #{planEndTime}, #{planDuration}, " +
            "#{status}, #{remark}, #{orderNosText}, #{createBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ScheduleRewinding rewinding);

    @Update("<script>" +
            "UPDATE schedule_rewinding SET " +
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
            "<if test='orderNosText != null'>order_nos = #{orderNosText}, </if>" +
            "update_by = #{updateBy}, update_time = NOW() " +
            "WHERE id = #{id}" +
            "</script>")
    int update(ScheduleRewinding rewinding);

    @Delete("DELETE FROM schedule_rewinding WHERE schedule_id = #{scheduleId}")
    int deleteByScheduleId(@Param("scheduleId") Long scheduleId);    @Select("<script>" +
            "SELECT sr.*, e.equipment_name FROM schedule_rewinding sr " +
            "LEFT JOIN equipment e ON sr.equipment_id = e.id " +
            "WHERE 1=1 " +
            "<if test='planDate != null and planDate != \"\"'>AND (sr.plan_date = #{planDate} OR sr.plan_date IS NULL) </if>" +
            "<if test='status != null and status != \"\"'>AND sr.status = #{status} </if>" +
            "<if test='equipmentId != null'>AND sr.equipment_id = #{equipmentId} </if>" +
            "ORDER BY sr.plan_start_time ASC" +
            "</script>")
    List<ScheduleRewinding> selectByCondition(Map<String, Object> params);
      /**
     * MyBatis-Plus分页查询复卷计划（标准方式）
     */
    @Select("<script>" +
            "SELECT sr.*, e.equipment_name FROM schedule_rewinding sr " +
            "LEFT JOIN equipment e ON sr.equipment_id = e.id " +
            "WHERE 1=1 " +
            "<if test='params.scheduleId != null'>AND sr.schedule_id = #{params.scheduleId} </if>" +
            "<if test='params.planDate != null and params.planDate != \"\"'>AND (sr.plan_date = #{params.planDate} OR sr.plan_date IS NULL) </if>" +
            "<if test='params.status != null and params.status != \"\"'>AND sr.status = #{params.status} </if>" +
            "<if test='params.equipmentId != null'>AND sr.equipment_id = #{params.equipmentId} </if>" +
            "<if test='params.materialCode != null and params.materialCode != \"\"'>AND sr.material_code LIKE CONCAT('%', #{params.materialCode}, '%') </if>" +
            "ORDER BY sr.plan_start_time ASC " +
            "</script>")
    IPage<ScheduleRewinding> selectPage(Page<ScheduleRewinding> page, @Param("params") Map<String, Object> params);

    @Select({
            "<script>",
            "SELECT sr.material_code AS materialCode, sr.material_name AS materialName, sr.slit_length AS length,",
            "SUM(IFNULL(sr.plan_rolls,0)) AS requiredRolls, COUNT(*) AS taskCount, GROUP_CONCAT(DISTINCT sr.order_nos) AS orderNosConcat",
            "FROM schedule_rewinding sr",
            "WHERE 1=1",
            "<if test='status != null and status != \"\"'> AND sr.status = #{status} </if>",
            "GROUP BY sr.material_code, sr.slit_length",
            "ORDER BY sr.material_code, sr.slit_length",
            "</script>"
    })
    List<Map<String, Object>> selectRewindSummary(@Param("status") String status);
}
