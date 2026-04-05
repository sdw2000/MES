package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.model.production.ProcessParams;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 工艺参数Mapper
 */
@Mapper
public interface ProcessParamsMapper extends BaseMapper<ProcessParams> {

    /**
     * 分页查询工艺参数列表 (MyBatis-Plus分页)
     */
    @Select("<script>" +
            "SELECT pp.*, ts.product_name AS material_name, e.equipment_name " +
            "FROM process_params pp " +
            "LEFT JOIN tape_spec ts ON pp.material_code = ts.material_code " +
            "LEFT JOIN equipment e ON pp.equipment_code = e.equipment_code AND e.is_deleted = 0 " +
            "WHERE pp.status = 1 " +
            "<if test='materialCode != null and materialCode != \"\"'>" +
            "  AND pp.material_code LIKE CONCAT('%', #{materialCode}, '%') " +
            "</if>" +
            "<if test='processType != null and processType != \"\"'>" +
            "  AND pp.process_type = #{processType} " +
            "</if>" +
            "<if test='equipmentCode != null and equipmentCode != \"\"'>" +
            "  AND pp.equipment_code = #{equipmentCode} " +
            "</if>" +
            "ORDER BY pp.material_code, pp.process_type " +
            "</script>")
    IPage<ProcessParams> selectProcessParamsPageList(IPage<ProcessParams> page,
                                                      @Param("materialCode") String materialCode,
                                                      @Param("processType") String processType,
                                                      @Param("equipmentCode") String equipmentCode);

    /**
     * 查询工艺参数列表
     */
    @Select("<script>" +
            "SELECT pp.*, ts.product_name AS material_name, e.equipment_name " +
            "FROM process_params pp " +
            "LEFT JOIN tape_spec ts ON pp.material_code = ts.material_code " +
            "LEFT JOIN equipment e ON pp.equipment_code = e.equipment_code AND e.is_deleted = 0 " +
            "WHERE pp.status = 1 " +
            "<if test='materialCode != null and materialCode != \"\"'>" +
            "  AND pp.material_code LIKE CONCAT('%', #{materialCode}, '%') " +
            "</if>" +
            "<if test='processType != null and processType != \"\"'>" +
            "  AND pp.process_type = #{processType} " +
            "</if>" +
            "<if test='equipmentCode != null and equipmentCode != \"\"'>" +
            "  AND pp.equipment_code = #{equipmentCode} " +
            "</if>" +
            "ORDER BY pp.material_code, pp.process_type " +
            "</script>")
    List<ProcessParams> selectParamsList(@Param("materialCode") String materialCode,
                                         @Param("processType") String processType,
                                         @Param("equipmentCode") String equipmentCode);

    /**
     * 根据料号和工序类型查询参数
     */
            @Select("<script>" +
                    "SELECT pp.*, ts.product_name AS material_name, e.equipment_name " +
                    "FROM process_params pp " +
                    "LEFT JOIN tape_spec ts ON pp.material_code = ts.material_code " +
                    "LEFT JOIN equipment e ON pp.equipment_code = e.equipment_code AND e.is_deleted = 0 " +
                    "WHERE pp.material_code = #{materialCode} AND pp.process_type = #{processType} AND pp.status = 1 " +
                    "<if test='equipmentCode != null and equipmentCode != \"\"'>" +
                    "  AND (pp.equipment_code = #{equipmentCode} OR pp.equipment_code IS NULL OR pp.equipment_code = '') " +
                    "  ORDER BY CASE WHEN pp.equipment_code = #{equipmentCode} THEN 0 ELSE 1 END, pp.id DESC LIMIT 1 " +
                    "</if>" +
                    "<if test='equipmentCode == null or equipmentCode == \"\"'>" +
                    "  ORDER BY pp.id DESC LIMIT 1 " +
                    "</if>" +
                    "</script>")
    ProcessParams selectByMaterialAndProcess(@Param("materialCode") String materialCode,
                                                     @Param("processType") String processType,
                                                     @Param("equipmentCode") String equipmentCode);

    /**
     * 根据料号查询所有工序参数
     */
            @Select("SELECT pp.*, ts.product_name AS material_name, e.equipment_name " +
            "FROM process_params pp " +
            "LEFT JOIN tape_spec ts ON pp.material_code = ts.material_code " +
                    "LEFT JOIN equipment e ON pp.equipment_code = e.equipment_code AND e.is_deleted = 0 " +
            "WHERE pp.material_code = #{materialCode} AND pp.status = 1 " +
            "ORDER BY FIELD(pp.process_type, 'COATING', 'REWINDING', 'SLITTING', 'STRIPPING')")
    List<ProcessParams> selectByMaterialCode(@Param("materialCode") String materialCode);

    /**
     * 检查是否已存在
     */
        @Select("SELECT COUNT(1) FROM process_params " +
                        "WHERE material_code = #{materialCode} AND process_type = #{processType} " +
                        "AND IFNULL(equipment_code, '') = IFNULL(#{equipmentCode}, '') " +
                        "AND id != #{excludeId} AND status = 1")
    int checkExists(@Param("materialCode") String materialCode,
                   @Param("processType") String processType,
                                   @Param("equipmentCode") String equipmentCode,
                   @Param("excludeId") Long excludeId);

    /**
     * 按唯一键查询（忽略status），用于避免唯一索引冲突
     */
    @Select("SELECT * FROM process_params " +
            "WHERE material_code = #{materialCode} AND process_type = #{processType} " +
            "AND IFNULL(equipment_code, '') = IFNULL(#{equipmentCode}, '') " +
            "ORDER BY id DESC LIMIT 1")
    ProcessParams selectAnyByUnique(@Param("materialCode") String materialCode,
                                    @Param("processType") String processType,
                                    @Param("equipmentCode") String equipmentCode);
}
