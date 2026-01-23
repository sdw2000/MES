package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.model.production.ScheduleStripping;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

/**
 * 分条计划Mapper
 */
@Mapper
public interface ScheduleStrippingMapper extends BaseMapper<ScheduleStripping> {
      /**
     * 根据排程ID查询分条计划
     */
    @Select("SELECT st.*, e.equipment_name FROM schedule_stripping st " +
            "LEFT JOIN equipment e ON st.equipment_id = e.id " +
            "WHERE st.schedule_id = #{scheduleId} ORDER BY st.plan_start_time ASC")
    List<ScheduleStripping> selectByScheduleId(@Param("scheduleId") Long scheduleId);
      /**
     * 根据ID查询
     */
    @Select("SELECT st.*, e.equipment_name FROM schedule_stripping st " +
            "LEFT JOIN equipment e ON st.equipment_id = e.id " +
            "WHERE st.id = #{id}")
    ScheduleStripping selectById(@Param("id") Long id);
    
    /**
     * 根据任务单号查询
     */
    @Select("SELECT * FROM schedule_stripping WHERE task_no = #{taskNo}")
    ScheduleStripping selectByTaskNo(@Param("taskNo") String taskNo);
    
    /**
     * 生成任务单号
     */
    @Select("SELECT CONCAT('ST-', DATE_FORMAT(NOW(), '%Y%m%d'), '-', " +
            "LPAD(IFNULL(MAX(CAST(SUBSTRING(task_no, -3) AS UNSIGNED)), 0) + 1, 3, '0')) " +
            "FROM schedule_stripping WHERE task_no LIKE CONCAT('ST-', DATE_FORMAT(NOW(), '%Y%m%d'), '%')")
    String generateTaskNo();
    
    /**
     * 插入分条计划
     */
    @Insert("INSERT INTO schedule_stripping (schedule_id, task_no, equipment_id, equipment_code, " +
            "staff_id, staff_name, shift_code, plan_date, source_batch_no, source_stock_id, " +
            "jumbo_width, jumbo_length, material_code, material_name, thickness, target_width, " +
            "target_length, cuts_width, cuts_length, plan_rolls, stripping_speed, " +
            "plan_start_time, plan_end_time, plan_duration, status, remark, create_by) VALUES " +
            "(#{scheduleId}, #{taskNo}, #{equipmentId}, #{equipmentCode}, #{staffId}, #{staffName}, " +
            "#{shiftCode}, #{planDate}, #{sourceBatchNo}, #{sourceStockId}, #{jumboWidth}, #{jumboLength}, " +
            "#{materialCode}, #{materialName}, #{thickness}, #{targetWidth}, #{targetLength}, " +
            "#{cutsWidth}, #{cutsLength}, #{planRolls}, #{strippingSpeed}, #{planStartTime}, " +
            "#{planEndTime}, #{planDuration}, #{status}, #{remark}, #{createBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ScheduleStripping stripping);
    
    /**
     * 更新分条计划
     */
    @Update("<script>" +
            "UPDATE schedule_stripping SET " +
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
    int update(ScheduleStripping stripping);
    
    /**
     * 根据排程ID删除
     */
    @Delete("DELETE FROM schedule_stripping WHERE schedule_id = #{scheduleId}")
    int deleteByScheduleId(@Param("scheduleId") Long scheduleId);
      /**
     * 按日期查询分条任务
     */
    @Select("<script>" +
            "SELECT st.*, e.equipment_name FROM schedule_stripping st " +
            "LEFT JOIN equipment e ON st.equipment_id = e.id " +
            "WHERE 1=1 " +
            "<if test='planDate != null'>AND st.plan_date = #{planDate} </if>" +
            "<if test='status != null and status != \"\"'>AND st.status = #{status} </if>" +
            "<if test='equipmentId != null'>AND st.equipment_id = #{equipmentId} </if>" +
            "ORDER BY st.plan_start_time ASC" +
            "</script>")
    List<ScheduleStripping> selectByCondition(Map<String, Object> params);
      /**
     * MyBatis-Plus分页查询分条计划（标准方式）
     */
    @Select("<script>" +
            "SELECT st.*, e.equipment_name FROM schedule_stripping st " +
            "LEFT JOIN equipment e ON st.equipment_id = e.id " +
            "WHERE 1=1 " +
            "<if test='params.scheduleId != null'>AND st.schedule_id = #{params.scheduleId} </if>" +
            "<if test='params.planDate != null'>AND st.plan_date = #{params.planDate} </if>" +
            "<if test='params.status != null and params.status != \"\"'>AND st.status = #{params.status} </if>" +
            "<if test='params.equipmentId != null'>AND st.equipment_id = #{params.equipmentId} </if>" +
            "<if test='params.materialCode != null and params.materialCode != \"\"'>AND st.material_code LIKE CONCAT('%', #{params.materialCode}, '%') </if>" +
            "ORDER BY st.plan_start_time ASC " +
            "</script>")
    IPage<ScheduleStripping> selectPage(Page<ScheduleStripping> page, @Param("params") Map<String, Object> params);
}
