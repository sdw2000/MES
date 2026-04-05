package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.model.production.SlittingProcessParams;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SlittingProcessParamsMapper extends BaseMapper<SlittingProcessParams> {

    @Select("<script>" +
            "SELECT sp.*, ts.product_name AS material_name, e.equipment_name " +
            "FROM slitting_process_params sp " +
            "LEFT JOIN tape_spec ts ON sp.material_code = ts.material_code " +
            "LEFT JOIN equipment e ON sp.equipment_code = e.equipment_code AND e.is_deleted = 0 " +
            "WHERE sp.status = 1 " +
            "<if test='totalThickness != null'>" +
            "  AND sp.total_thickness = #{totalThickness} " +
            "</if>" +
            "<if test='processLength != null'>" +
            "  AND sp.process_length = #{processLength} " +
            "</if>" +
            "<if test='processWidth != null'>" +
            "  AND sp.process_width = #{processWidth} " +
            "</if>" +
            "<if test='equipmentCode != null and equipmentCode != \"\"'>" +
            "  AND sp.equipment_code = #{equipmentCode} " +
            "</if>" +
            "ORDER BY sp.total_thickness, sp.process_length, sp.process_width, sp.id DESC " +
            "</script>")
    IPage<SlittingProcessParams> selectPageList(IPage<SlittingProcessParams> page,
                                                @Param("totalThickness") java.math.BigDecimal totalThickness,
                                                @Param("processLength") java.math.BigDecimal processLength,
                                                @Param("processWidth") java.math.BigDecimal processWidth,
                                                @Param("equipmentCode") String equipmentCode);

    @Select("<script>" +
            "SELECT sp.*, ts.product_name AS material_name, e.equipment_name " +
            "FROM slitting_process_params sp " +
            "LEFT JOIN tape_spec ts ON sp.material_code = ts.material_code " +
            "LEFT JOIN equipment e ON sp.equipment_code = e.equipment_code AND e.is_deleted = 0 " +
            "WHERE sp.status = 1 " +
            "AND sp.total_thickness = #{totalThickness} " +
            "AND sp.process_length = #{processLength} " +
            "AND sp.process_width = #{processWidth} " +
            "<if test='equipmentCode != null and equipmentCode != \"\"'>" +
            "  AND (sp.equipment_code = #{equipmentCode} OR sp.equipment_code IS NULL OR sp.equipment_code = '') " +
            "  ORDER BY CASE WHEN sp.equipment_code = #{equipmentCode} THEN 0 ELSE 1 END, sp.id DESC LIMIT 1 " +
            "</if>" +
            "<if test='equipmentCode == null or equipmentCode == \"\"'>" +
            "  ORDER BY sp.id DESC LIMIT 1 " +
            "</if>" +
            "</script>")
    SlittingProcessParams selectByDimensions(@Param("totalThickness") java.math.BigDecimal totalThickness,
                                             @Param("processLength") java.math.BigDecimal processLength,
                                             @Param("processWidth") java.math.BigDecimal processWidth,
                                             @Param("equipmentCode") String equipmentCode);

    @Select("SELECT COUNT(1) FROM slitting_process_params " +
            "WHERE total_thickness = #{totalThickness} " +
            "AND process_length = #{processLength} " +
            "AND process_width = #{processWidth} " +
            "AND IFNULL(equipment_code, '') = IFNULL(#{equipmentCode}, '') " +
            "AND id != #{excludeId} AND status = 1")
    int checkExists(@Param("totalThickness") java.math.BigDecimal totalThickness,
                    @Param("processLength") java.math.BigDecimal processLength,
                    @Param("processWidth") java.math.BigDecimal processWidth,
                    @Param("equipmentCode") String equipmentCode,
                    @Param("excludeId") Long excludeId);

    @Select("<script>" +
            "SELECT sp.*, ts.product_name AS material_name, e.equipment_name " +
            "FROM slitting_process_params sp " +
            "LEFT JOIN tape_spec ts ON sp.material_code = ts.material_code " +
            "LEFT JOIN equipment e ON sp.equipment_code = e.equipment_code AND e.is_deleted = 0 " +
            "WHERE sp.status = 1 " +
            "<if test='totalThickness != null'>" +
            "  AND sp.total_thickness = #{totalThickness} " +
            "</if>" +
            "<if test='processLength != null'>" +
            "  AND sp.process_length = #{processLength} " +
            "</if>" +
            "<if test='processWidth != null'>" +
            "  AND sp.process_width = #{processWidth} " +
            "</if>" +
            "<if test='equipmentCode != null and equipmentCode != \"\"'>" +
            "  AND sp.equipment_code = #{equipmentCode} " +
            "</if>" +
            "ORDER BY sp.total_thickness, sp.process_length, sp.process_width, sp.id DESC " +
            "</script>")
    List<SlittingProcessParams> selectListForExport(@Param("totalThickness") java.math.BigDecimal totalThickness,
                                                    @Param("processLength") java.math.BigDecimal processLength,
                                                    @Param("processWidth") java.math.BigDecimal processWidth,
                                                    @Param("equipmentCode") String equipmentCode);
}
