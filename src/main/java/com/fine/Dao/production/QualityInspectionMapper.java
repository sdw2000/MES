package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.model.production.QualityInspection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 质检记录Mapper
 */
@Mapper
public interface QualityInspectionMapper extends BaseMapper<QualityInspection> {
    
    /**
     * 分页查询质检记录列表
     */
    IPage<QualityInspection> selectInspectionList(IPage<QualityInspection> page, @Param("params") Map<String, Object> params);
    
    /**
     * 根据任务查询质检记录
     */
    List<QualityInspection> selectByTask(@Param("taskType") String taskType, @Param("taskId") Long taskId);
    
    /**
     * 根据批次号查询质检记录
     */
    List<QualityInspection> selectByBatchNo(@Param("batchNo") String batchNo);
    
    /**
     * 生成质检单号
     */
    @Select("SELECT CONCAT('QC-', DATE_FORMAT(NOW(), '%Y%m%d'), '-', LPAD(IFNULL(MAX(CAST(SUBSTRING(inspection_no, 13) AS UNSIGNED)), 0) + 1, 3, '0')) " +
            "FROM quality_inspection WHERE inspection_no LIKE CONCAT('QC-', DATE_FORMAT(NOW(), '%Y%m%d'), '-%')")
    String generateInspectionNo();
    
    /**
     * 统计今日质检数量
     */
    @Select("SELECT COUNT(*) FROM quality_inspection WHERE DATE(inspection_time) = CURDATE()")
    Integer getTodayInspectionCount();
    
    /**
     * 统计今日合格率
     */
    @Select("SELECT ROUND(SUM(CASE WHEN overall_result = 'pass' THEN 1 ELSE 0 END) * 100.0 / COUNT(*), 2) " +
            "FROM quality_inspection WHERE DATE(inspection_time) = CURDATE()")
    Double getTodayPassRate();
}
