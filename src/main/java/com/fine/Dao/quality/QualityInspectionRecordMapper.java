package com.fine.Dao.quality;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.model.quality.QualityInspectionRecord;
import com.fine.model.quality.dto.QualityReportDefectStat;
import com.fine.model.quality.dto.QualityReportTypeStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface QualityInspectionRecordMapper extends BaseMapper<QualityInspectionRecord> {
    IPage<QualityInspectionRecord> selectPaged(Page<QualityInspectionRecord> page,
                                              @Param("type") String inspectionType,
                                              @Param("inspectionNo") String inspectionNo,
                                              @Param("batchNo") String batchNo,
                                              @Param("rollCode") String rollCode,
                                              @Param("result") String result,
                                              @Param("startDate") String startDate,
                                              @Param("endDate") String endDate);

    @org.apache.ibatis.annotations.Select("SELECT CONCAT('QC-', DATE_FORMAT(NOW(), '%Y%m%d'), '-', LPAD(IFNULL(MAX(CAST(SUBSTRING(inspection_no, 12) AS UNSIGNED)), 0) + 1, 3, '0')) " +
            "FROM quality_inspection WHERE inspection_no LIKE CONCAT('QC-', DATE_FORMAT(NOW(), '%Y%m%d'), '-%')")
    String generateInspectionNo();

    java.util.List<QualityReportTypeStat> selectTypeStats();

    java.util.List<QualityReportDefectStat> selectDefectTop(@Param("limit") int limit);
}
