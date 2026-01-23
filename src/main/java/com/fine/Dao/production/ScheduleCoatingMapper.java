package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.model.production.ScheduleCoating;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

/**
 * 涂布计划Mapper
 */
@Mapper
public interface ScheduleCoatingMapper extends BaseMapper<ScheduleCoating> {
    
    /**
     * 根据排程ID查询涂布计划
     */
    @Select("SELECT sc.*, e.equipment_name FROM schedule_coating sc " +
            "LEFT JOIN equipment e ON sc.equipment_id = e.id " +
            "WHERE sc.schedule_id = #{scheduleId} ORDER BY sc.plan_start_time ASC")
    List<ScheduleCoating> selectByScheduleId(@Param("scheduleId") Long scheduleId);
    
    /**
     * 根据ID查询（带设备名称）
     */
    @Select("SELECT sc.*, e.equipment_name FROM schedule_coating sc " +
            "LEFT JOIN equipment e ON sc.equipment_id = e.id " +
            "WHERE sc.id = #{id}")
    ScheduleCoating selectByIdWithEquipment(@Param("id") Long id);
    
    /**
     * 根据任务单号查询
     */
    @Select("SELECT * FROM schedule_coating WHERE task_no = #{taskNo}")
    ScheduleCoating selectByTaskNo(@Param("taskNo") String taskNo);
    
    /**
     * 生成任务单号
     */
    @Select("SELECT CONCAT('CT-', DATE_FORMAT(NOW(), '%Y%m%d'), '-', " +
            "LPAD(IFNULL(MAX(CAST(SUBSTRING(task_no, -3) AS UNSIGNED)), 0) + 1, 3, '0')) " +
            "FROM schedule_coating WHERE task_no LIKE CONCAT('CT-', DATE_FORMAT(NOW(), '%Y%m%d'), '%')")
    String generateTaskNo();
    
    /**
     * 插入涂布计划
     */
    @Insert("INSERT INTO schedule_coating (schedule_id, task_no, equipment_id, equipment_code, " +
            "staff_id, staff_name, shift_code, plan_date, material_code, material_name, " +
            "color_code, color_name, thickness, plan_length, plan_sqm, jumbo_width, " +
            "coating_speed, oven_temp, plan_start_time, plan_end_time, plan_duration, " +
            "status, remark, create_by) VALUES " +
            "(#{scheduleId}, #{taskNo}, #{equipmentId}, #{equipmentCode}, #{staffId}, #{staffName}, " +
            "#{shiftCode}, #{planDate}, #{materialCode}, #{materialName}, #{colorCode}, #{colorName}, " +
            "#{thickness}, #{planLength}, #{planSqm}, #{jumboWidth}, #{coatingSpeed}, #{ovenTemp}, " +
            "#{planStartTime}, #{planEndTime}, #{planDuration}, #{status}, #{remark}, #{createBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ScheduleCoating coating);
    
    /**
     * 更新涂布计划
     */
    @Update("<script>" +
            "UPDATE schedule_coating SET " +
            "<if test='colorCode != null'>color_code = #{colorCode}, </if>" +
            "<if test='colorName != null'>color_name = #{colorName}, </if>" +
            "<if test='thickness != null'>thickness = #{thickness}, </if>" +
            "<if test='equipmentId != null'>equipment_id = #{equipmentId}, </if>" +
            "<if test='equipmentCode != null'>equipment_code = #{equipmentCode}, </if>" +
            "<if test='staffId != null'>staff_id = #{staffId}, </if>" +
            "<if test='staffName != null'>staff_name = #{staffName}, </if>" +
            "<if test='shiftCode != null'>shift_code = #{shiftCode}, </if>" +
            "<if test='planDate != null'>plan_date = #{planDate}, </if>" +
            "<if test='planLength != null'>plan_length = #{planLength}, </if>" +
            "<if test='planSqm != null'>plan_sqm = #{planSqm}, </if>" +
            "<if test='jumboWidth != null'>jumbo_width = #{jumboWidth}, </if>" +
            "<if test='actualLength != null'>actual_length = #{actualLength}, </if>" +
            "<if test='actualSqm != null'>actual_sqm = #{actualSqm}, </if>" +
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
    int update(ScheduleCoating coating);
    
    /**
     * 根据排程ID删除
     */
    @Delete("DELETE FROM schedule_coating WHERE schedule_id = #{scheduleId}")
    int deleteByScheduleId(@Param("scheduleId") Long scheduleId);
    
    /**
     * 按日期查询涂布任务（用于看板）
     */
    @Select("<script>" +
            "SELECT sc.*, e.equipment_name FROM schedule_coating sc " +
            "LEFT JOIN equipment e ON sc.equipment_id = e.id " +
            "WHERE 1=1 " +
            "<if test='planDate != null'>AND sc.plan_date = #{planDate} </if>" +
            "<if test='status != null and status != \"\"'>AND sc.status = #{status} </if>" +
            "<if test='equipmentId != null'>AND sc.equipment_id = #{equipmentId} </if>" +
            "ORDER BY sc.plan_start_time ASC" +
            "</script>")
    List<ScheduleCoating> selectByCondition(Map<String, Object> params);
    
    /**
     * 统计设备当天任务
     */
    @Select("SELECT equipment_id, COUNT(*) as task_count, SUM(plan_duration) as total_duration " +
            "FROM schedule_coating WHERE plan_date = #{planDate} AND status != 'cancelled' " +
            "GROUP BY equipment_id")
    List<Map<String, Object>> countEquipmentTasks(@Param("planDate") String planDate);
    
    /**
     * 计算某订单明细已排程的总面积
     */
    @Select("SELECT IFNULL(SUM(plan_sqm), 0) FROM schedule_coating " +
            "WHERE order_item_id = #{orderItemId} AND status != 'cancelled'")
    java.math.BigDecimal sumPlanSqmByOrderItemId(@Param("orderItemId") Long orderItemId);
    
    /**
     * MyBatis-Plus分页查询涂布计划（标准方式）
     */
    @Select("<script>" +
            "SELECT sc.*, e.equipment_name FROM schedule_coating sc " +
            "LEFT JOIN equipment e ON sc.equipment_id = e.id " +
            "WHERE 1=1 " +
            "</script>")
    IPage<ScheduleCoating> selectPage(Page<ScheduleCoating> page, @Param("params") Map<String, Object> params);
    
    /**
     * 清空排程的所有涂布任务的jumboWidth字段
     */
    @Update("UPDATE schedule_coating SET jumbo_width = NULL, update_time = NOW() WHERE schedule_id = #{scheduleId}")
    int clearJumboWidthByScheduleId(@Param("scheduleId") Long scheduleId);
}
