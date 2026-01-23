package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.model.production.SchedulePrinting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 印刷计划Mapper
 */
@Mapper
public interface SchedulePrintingMapper extends BaseMapper<SchedulePrinting> {
    
    /**
     * 分页查询印刷任务列表
     */
    IPage<SchedulePrinting> selectPrintingTasks(IPage<SchedulePrinting> page, @Param("params") Map<String, Object> params);
    
    /**
     * 根据排程ID查询印刷任务
     */
    List<SchedulePrinting> selectByScheduleId(@Param("scheduleId") Long scheduleId);
    
    /**
     * 生成任务单号
     */
    @Select("SELECT CONCAT('PR-', DATE_FORMAT(NOW(), '%Y%m%d'), '-', LPAD(IFNULL(MAX(CAST(SUBSTRING(task_no, 13) AS UNSIGNED)), 0) + 1, 3, '0')) " +
            "FROM schedule_printing WHERE task_no LIKE CONCAT('PR-', DATE_FORMAT(NOW(), '%Y%m%d'), '-%')")
    String generateTaskNo();
    
    /**
     * 统计今日印刷产量
     */
    @Select("SELECT IFNULL(SUM(actual_sqm), 0) FROM schedule_printing WHERE DATE(actual_end_time) = CURDATE() AND status = 'completed'")
    Double getTodayOutputSqm();
}
