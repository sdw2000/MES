package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.model.production.ProductionReport;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

/**
 * 生产报工Mapper
 */
@Mapper
public interface ProductionReportMapper {
    
    /**
     * 分页查询报工记录
     */
    @Select("<script>" +
            "SELECT pr.*, e.equipment_name FROM production_report pr " +
            "LEFT JOIN production_equipment e ON pr.equipment_id = e.id " +
            "WHERE 1=1 " +
            "<if test='params.reportNo != null and params.reportNo != \"\"'>" +
            "AND pr.report_no LIKE CONCAT('%', #{params.reportNo}, '%') " +
            "</if>" +
            "<if test='params.taskType != null and params.taskType != \"\"'>" +
            "AND pr.task_type = #{params.taskType} " +
            "</if>" +
            "<if test='params.taskNo != null and params.taskNo != \"\"'>" +
            "AND pr.task_no LIKE CONCAT('%', #{params.taskNo}, '%') " +
            "</if>" +
            "<if test='params.staffId != null'>" +
            "AND pr.staff_id = #{params.staffId} " +
            "</if>" +
            "<if test='params.reportDate != null'>" +
            "AND pr.report_date = #{params.reportDate} " +
            "</if>" +
            "ORDER BY pr.create_time DESC" +
            "</script>")
    IPage<ProductionReport> selectList(IPage<ProductionReport> page, @Param("params") Map<String, Object> params);
    
    /**
     * 根据ID查询
     */
    @Select("SELECT pr.*, e.equipment_name FROM production_report pr " +
            "LEFT JOIN production_equipment e ON pr.equipment_id = e.id " +
            "WHERE pr.id = #{id}")
    ProductionReport selectById(@Param("id") Long id);
    
    /**
     * 根据任务ID和类型查询报工记录
     */
    @Select("SELECT * FROM production_report WHERE task_id = #{taskId} AND task_type = #{taskType}")
    List<ProductionReport> selectByTask(@Param("taskId") Long taskId, @Param("taskType") String taskType);
    
    /**
     * 生成报工单号
     */
    @Select("SELECT CONCAT('PR-', DATE_FORMAT(NOW(), '%Y%m%d'), '-', " +
            "LPAD(IFNULL(MAX(CAST(SUBSTRING(report_no, -3) AS UNSIGNED)), 0) + 1, 3, '0')) " +
            "FROM production_report WHERE report_no LIKE CONCAT('PR-', DATE_FORMAT(NOW(), '%Y%m%d'), '%')")
    String generateReportNo();
    
    /**
     * 插入报工记录
     */
    @Insert("INSERT INTO production_report (report_no, task_type, task_id, task_no, equipment_id, " +
            "staff_id, staff_name, shift_code, report_date, output_qty, output_length, output_sqm, " +
            "defect_qty, defect_reason, start_time, end_time, work_minutes, pause_minutes, " +
            "output_batch_no, remark) VALUES " +
            "(#{reportNo}, #{taskType}, #{taskId}, #{taskNo}, #{equipmentId}, #{staffId}, #{staffName}, " +
            "#{shiftCode}, #{reportDate}, #{outputQty}, #{outputLength}, #{outputSqm}, #{defectQty}, " +
            "#{defectReason}, #{startTime}, #{endTime}, #{workMinutes}, #{pauseMinutes}, " +
            "#{outputBatchNo}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ProductionReport report);
    
    /**
     * 更新报工记录
     */
    @Update("<script>" +
            "UPDATE production_report SET " +
            "<if test='outputQty != null'>output_qty = #{outputQty}, </if>" +
            "<if test='outputLength != null'>output_length = #{outputLength}, </if>" +
            "<if test='outputSqm != null'>output_sqm = #{outputSqm}, </if>" +
            "<if test='defectQty != null'>defect_qty = #{defectQty}, </if>" +
            "<if test='defectReason != null'>defect_reason = #{defectReason}, </if>" +
            "<if test='startTime != null'>start_time = #{startTime}, </if>" +
            "<if test='endTime != null'>end_time = #{endTime}, </if>" +
            "<if test='workMinutes != null'>work_minutes = #{workMinutes}, </if>" +
            "<if test='pauseMinutes != null'>pause_minutes = #{pauseMinutes}, </if>" +
            "<if test='outputBatchNo != null'>output_batch_no = #{outputBatchNo}, </if>" +
            "<if test='remark != null'>remark = #{remark}, </if>" +
            "update_time = NOW() " +
            "WHERE id = #{id}" +
            "</script>")
    int update(ProductionReport report);
    
    /**
     * 删除报工记录
     */
    @Delete("DELETE FROM production_report WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
    
    /**
     * 统计今日产量
     */
    @Select("SELECT task_type, SUM(output_qty) as total_qty, SUM(output_sqm) as total_sqm " +
            "FROM production_report WHERE report_date = CURDATE() GROUP BY task_type")
    List<Map<String, Object>> countTodayOutput();

    /**
     * 按班次统计当月/当年生产报工总平米数
     */
    @Select("<script>" +
            "SELECT " +
            "IFNULL(SUM(CASE WHEN DATE_FORMAT(pr.report_date, '%Y-%m') = DATE_FORMAT(CURDATE(), '%Y-%m') THEN pr.output_sqm ELSE 0 END), 0) AS monthArea, " +
            "IFNULL(SUM(CASE WHEN YEAR(pr.report_date) = YEAR(CURDATE()) THEN pr.output_sqm ELSE 0 END), 0) AS yearArea " +
            "FROM production_report pr " +
            "WHERE 1=1 " +
            "<if test='shiftCode != null and shiftCode != \"\"'>" +
            "AND UPPER(pr.shift_code) = UPPER(#{shiftCode}) " +
            "</if>" +
            "</script>")
    Map<String, Object> selectShiftProductionAreaSummary(@Param("shiftCode") String shiftCode);
    
    /**
     * 统计人员产量
     */
    @Select("SELECT staff_id, staff_name, SUM(output_qty) as total_qty, SUM(work_minutes) as total_minutes " +
            "FROM production_report WHERE report_date BETWEEN #{startDate} AND #{endDate} " +
            "GROUP BY staff_id, staff_name ORDER BY total_qty DESC")
    List<Map<String, Object>> countStaffOutput(@Param("startDate") String startDate, @Param("endDate") String endDate);
}
