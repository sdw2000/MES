package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.model.production.RewindingProcessParams;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface RewindingProcessParamsMapper extends BaseMapper<RewindingProcessParams> {

    @Select("<script>" +
            "SELECT rp.*, ts.product_name AS material_name, e.equipment_name " +
            "FROM rewinding_process_params rp " +
            "LEFT JOIN tape_spec ts ON rp.material_code = ts.material_code " +
            "LEFT JOIN equipment e ON rp.equipment_code = e.equipment_code AND e.is_deleted = 0 " +
            "WHERE rp.status = 1 " +
            "<if test='materialCode != null and materialCode != \"\"'>" +
            "  AND rp.material_code LIKE CONCAT('%', #{materialCode}, '%') " +
            "</if>" +
            "<if test='equipmentCode != null and equipmentCode != \"\"'>" +
            "  AND rp.equipment_code = #{equipmentCode} " +
            "</if>" +
            "ORDER BY rp.material_code, rp.id DESC " +
            "</script>")
    IPage<RewindingProcessParams> selectPageList(IPage<RewindingProcessParams> page,
                                                 @Param("materialCode") String materialCode,
                                                 @Param("equipmentCode") String equipmentCode);

    @Select("<script>" +
            "SELECT rp.*, ts.product_name AS material_name, e.equipment_name " +
            "FROM rewinding_process_params rp " +
            "LEFT JOIN tape_spec ts ON rp.material_code = ts.material_code " +
            "LEFT JOIN equipment e ON rp.equipment_code = e.equipment_code AND e.is_deleted = 0 " +
            "WHERE rp.material_code = #{materialCode} AND rp.status = 1 " +
            "<if test='equipmentCode != null and equipmentCode != \"\"'>" +
            "  AND (rp.equipment_code = #{equipmentCode} OR rp.equipment_code IS NULL OR rp.equipment_code = '') " +
            "  ORDER BY CASE WHEN rp.equipment_code = #{equipmentCode} THEN 0 ELSE 1 END, rp.id DESC LIMIT 1 " +
            "</if>" +
            "<if test='equipmentCode == null or equipmentCode == \"\"'>" +
            "  ORDER BY rp.id DESC LIMIT 1 " +
            "</if>" +
            "</script>")
    RewindingProcessParams selectByMaterialAndEquipment(@Param("materialCode") String materialCode,
                                                        @Param("equipmentCode") String equipmentCode);

    @Select("SELECT COUNT(1) FROM rewinding_process_params " +
            "WHERE material_code = #{materialCode} " +
            "AND IFNULL(equipment_code, '') = IFNULL(#{equipmentCode}, '') " +
            "AND id != #{excludeId} AND status = 1")
    int checkExists(@Param("materialCode") String materialCode,
                    @Param("equipmentCode") String equipmentCode,
                    @Param("excludeId") Long excludeId);

    @Select("<script>" +
            "SELECT rp.*, ts.product_name AS material_name, e.equipment_name " +
            "FROM rewinding_process_params rp " +
            "LEFT JOIN tape_spec ts ON rp.material_code = ts.material_code " +
            "LEFT JOIN equipment e ON rp.equipment_code = e.equipment_code AND e.is_deleted = 0 " +
            "WHERE rp.status = 1 " +
            "<if test='materialCode != null and materialCode != \"\"'>" +
            "  AND rp.material_code LIKE CONCAT('%', #{materialCode}, '%') " +
            "</if>" +
            "<if test='equipmentCode != null and equipmentCode != \"\"'>" +
            "  AND rp.equipment_code = #{equipmentCode} " +
            "</if>" +
            "ORDER BY rp.material_code, rp.id DESC " +
            "</script>")
    List<RewindingProcessParams> selectListForExport(@Param("materialCode") String materialCode,
                                                     @Param("equipmentCode") String equipmentCode);
}
