package com.fine.Dao.quality;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.model.quality.QualityInspectionRecord;
import com.fine.model.quality.dto.QualityReportDefectStat;
import com.fine.model.quality.dto.QualityReportTypeStat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface QualityInspectionRecordMapper extends BaseMapper<QualityInspectionRecord> {
    IPage<QualityInspectionRecord> selectPaged(Page<QualityInspectionRecord> page,
                                              @Param("type") String inspectionType,
                                              @Param("inspectionNo") String inspectionNo,
                                              @Param("batchNo") String batchNo,
                                              @Param("rollCode") String rollCode,
                                              @Param("materialCode") String materialCode,
                                              @Param("inspectorName") String inspectorName,
                                              @Param("result") String result,
                                              @Param("startDate") String startDate,
                                              @Param("endDate") String endDate);

    /**
     * 生成质检单号：Prefix + 日期(yyMMdd) + 4位序号
     */
    @org.apache.ibatis.annotations.Select("SELECT CONCAT(#{prefix}, DATE_FORMAT(NOW(), '%y%m%d'), " +
            "LPAD(IFNULL(MAX(CAST(RIGHT(inspection_no, 4) AS UNSIGNED)), 0) + 1, 4, '0')) " +
            "FROM quality_inspection " +
            "WHERE inspection_no LIKE CONCAT(#{prefix}, DATE_FORMAT(NOW(), '%y%m%d'), '%')")
    String generateInspectionNo(@Param("prefix") String prefix);

    java.util.List<QualityReportTypeStat> selectTypeStats();

    java.util.List<QualityReportDefectStat> selectDefectTop(@Param("limit") int limit);

    List<String> selectDistinctBatchNos(@Param("type") String inspectionType,
                                        @Param("materialCode") String materialCode,
                                        @Param("keyword") String keyword,
                                        @Param("limit") int limit);

        @Select("<script>" +
            "SELECT cr.roll_code AS rollCode, cr.batch_no AS batchNo, cr.schedule_id AS scheduleId, cr.report_id AS reportId, " +
            "       COALESCE(ms.material_code, '') AS materialCode, COALESCE(ms.material_name, '') AS materialName, " +
            "       COALESCE(soi.thickness, 0) AS thickness, COALESCE(soi.color_code, '') AS colorCode, " +
            "       cr.width_mm AS widthMm, cr.length_m AS lengthM, cr.area AS area, " +
            "       CASE WHEN qi.roll_code IS NULL THEN 0 ELSE 1 END AS inspected " +
            "FROM manual_schedule_coating_roll cr " +
            "LEFT JOIN manual_schedule ms ON ms.id = cr.schedule_id " +
            "LEFT JOIN sales_order_items soi ON soi.id = ms.order_detail_id AND soi.is_deleted = 0 " +
            "LEFT JOIN (" +
            "   SELECT roll_code, MAX(id) AS id " +
            "   FROM quality_inspection " +
            "   WHERE is_deleted = 0 AND inspection_type = 'process' AND process_node = 'coating' " +
            "   GROUP BY roll_code" +
            ") qi ON qi.roll_code = cr.roll_code " +
            "WHERE cr.is_deleted = 0 " +
            "<if test=\"keyword != null and keyword != ''\">" +
            "  AND (cr.roll_code LIKE CONCAT('%', #{keyword}, '%') OR IFNULL(cr.batch_no, '') LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if>" +
            "<if test=\"materialCode != null and materialCode != ''\">" +
            "  AND COALESCE(ms.material_code, '') = #{materialCode} " +
            "</if>" +
            "<if test='onlyPending != null and onlyPending'>" +
            "  AND qi.roll_code IS NULL " +
            "</if>" +
            "ORDER BY cr.created_at DESC, cr.id DESC " +
            "LIMIT #{limit}" +
            "</script>")
        List<Map<String, Object>> selectCoatingRollCandidates(@Param("keyword") String keyword,
                                  @Param("materialCode") String materialCode,
                                  @Param("onlyPending") Boolean onlyPending,
                                  @Param("limit") int limit);

        @Select("SELECT cr.roll_code AS rollCode, cr.batch_no AS batchNo, cr.schedule_id AS scheduleId, cr.report_id AS reportId, " +
            "       COALESCE(ms.material_code, '') AS materialCode, COALESCE(ms.material_name, '') AS materialName, " +
            "       COALESCE(soi.thickness, 0) AS thickness, COALESCE(soi.color_code, '') AS colorCode, " +
            "       cr.width_mm AS widthMm, cr.length_m AS lengthM, cr.area AS area " +
            "FROM manual_schedule_coating_roll cr " +
            "LEFT JOIN manual_schedule ms ON ms.id = cr.schedule_id " +
            "LEFT JOIN sales_order_items soi ON soi.id = ms.order_detail_id AND soi.is_deleted = 0 " +
            "WHERE cr.is_deleted = 0 AND cr.roll_code = #{rollCode} " +
            "ORDER BY cr.id DESC LIMIT 1")
        Map<String, Object> selectCoatingRollByCode(@Param("rollCode") String rollCode);

        @Select("SELECT COUNT(1) FROM quality_inspection " +
            "WHERE is_deleted = 0 AND inspection_type = 'process' AND process_node = 'coating' AND roll_code = #{rollCode}")
        int countCoatingProcessInspectionByRoll(@Param("rollCode") String rollCode);
}
