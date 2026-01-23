package com.fine.Dao.rd;

import com.fine.modle.rd.TapeSpec;
import com.fine.modle.rd.DictItem;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 胶带规格Mapper
 */
@Mapper
public interface TapeSpecMapper {

    /**
     * 分页查询规格列表
     */
    @Select("<script>" +
            "SELECT * FROM tape_spec WHERE 1=1" +
            "<if test='materialCode != null and materialCode != \"\"'> AND material_code LIKE CONCAT('%',#{materialCode},'%')</if>" +
            "<if test='productName != null and productName != \"\"'> AND product_name LIKE CONCAT('%',#{productName},'%')</if>" +
            "<if test='colorCode != null and colorCode != \"\"'> AND color_code = #{colorCode}</if>" +
            "<if test='baseMaterial != null and baseMaterial != \"\"'> AND base_material = #{baseMaterial}</if>" +
            "<if test='status != null'> AND status = #{status}</if>" +
            " ORDER BY create_time DESC" +
            " LIMIT #{offset}, #{size}" +
            "</script>")
    @Results(id = "tapeSpecResultMap", value = {
            @Result(property = "id", column = "id"),
            @Result(property = "materialCode", column = "material_code"),
            @Result(property = "productName", column = "product_name"),
            @Result(property = "colorCode", column = "color_code"),
            @Result(property = "colorName", column = "color_name"),
            @Result(property = "baseThickness", column = "base_thickness"),
            @Result(property = "baseMaterial", column = "base_material"),
            @Result(property = "glueMaterial", column = "glue_material"),
            @Result(property = "glueThickness", column = "glue_thickness"),
            @Result(property = "initialTackMin", column = "initial_tack_min"),
            @Result(property = "initialTackMax", column = "initial_tack_max"),
            @Result(property = "initialTackType", column = "initial_tack_type"),
            @Result(property = "totalThickness", column = "total_thickness"),
            @Result(property = "totalThicknessMin", column = "total_thickness_min"),
            @Result(property = "totalThicknessMax", column = "total_thickness_max"),
            @Result(property = "peelStrengthMin", column = "peel_strength_min"),
            @Result(property = "peelStrengthMax", column = "peel_strength_max"),
            @Result(property = "peelStrengthType", column = "peel_strength_type"),
            @Result(property = "unwindForceMin", column = "unwind_force_min"),
            @Result(property = "unwindForceMax", column = "unwind_force_max"),
            @Result(property = "unwindForceType", column = "unwind_force_type"),
            @Result(property = "heatResistance", column = "heat_resistance"),
            @Result(property = "heatResistanceType", column = "heat_resistance_type"),
            @Result(property = "remark", column = "remark"),
            @Result(property = "status", column = "status"),
            @Result(property = "createTime", column = "create_time"),
            @Result(property = "updateTime", column = "update_time"),
            @Result(property = "createBy", column = "create_by"),
            @Result(property = "updateBy", column = "update_by")
    })
    List<TapeSpec> selectList(@Param("materialCode") String materialCode,
                              @Param("productName") String productName,
                              @Param("colorCode") String colorCode,
                              @Param("baseMaterial") String baseMaterial,
                              @Param("status") Integer status,
                              @Param("offset") int offset,
                              @Param("size") int size);

    /**
     * 分页查询规格列表（简化版）
     */
    @Select("<script>" +
            "SELECT * FROM tape_spec WHERE 1=1" +
            "<if test='materialCode != null and materialCode != \"\"'> AND material_code LIKE CONCAT('%',#{materialCode},'%')</if>" +
            "<if test='productName != null and productName != \"\"'> AND product_name LIKE CONCAT('%',#{productName},'%')</if>" +
            "<if test='colorCode != null and colorCode != \"\"'> AND color_code = #{colorCode}</if>" +
            "<if test='baseMaterial != null and baseMaterial != \"\"'> AND base_material = #{baseMaterial}</if>" +
            "<if test='status != null'> AND status = #{status}</if>" +
            " ORDER BY create_time DESC" +
            " LIMIT #{offset}, #{size}" +
            "</script>")
    @ResultMap("tapeSpecResultMap")
    List<TapeSpec> selectListByPage(@Param("offset") int offset, @Param("size") int size, 
                                    @Param("materialCode") String materialCode, 
                                    @Param("productName") String productName,
                                    @Param("colorCode") String colorCode,
                                    @Param("baseMaterial") String baseMaterial,
                                    @Param("status") Integer status);

    /**
     * 统计总数
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM tape_spec WHERE 1=1" +
            "<if test='materialCode != null and materialCode != \"\"'> AND material_code LIKE CONCAT('%',#{materialCode},'%')</if>" +
            "<if test='productName != null and productName != \"\"'> AND product_name LIKE CONCAT('%',#{productName},'%')</if>" +
            "<if test='colorCode != null and colorCode != \"\"'> AND color_code = #{colorCode}</if>" +
            "<if test='baseMaterial != null and baseMaterial != \"\"'> AND base_material = #{baseMaterial}</if>" +
            "<if test='status != null'> AND status = #{status}</if>" +
            "</script>")
    int selectCount(@Param("materialCode") String materialCode,
                    @Param("productName") String productName,
                    @Param("colorCode") String colorCode,
                    @Param("baseMaterial") String baseMaterial,
                    @Param("status") Integer status);

    /**
     * 根据ID查询
     */
    @Select("SELECT * FROM tape_spec WHERE id = #{id}")
    @ResultMap("tapeSpecResultMap")
    TapeSpec selectById(@Param("id") Long id);

    /**
     * 根据料号查询
     */
    @Select("SELECT * FROM tape_spec WHERE material_code = #{materialCode} LIMIT 1")
    @ResultMap("tapeSpecResultMap")
    TapeSpec selectByMaterialCode(@Param("materialCode") String materialCode);

    /**
     * 根据料号列表批量查询
     */
    @Select("<script>" +
            "SELECT * FROM tape_spec WHERE material_code IN " +
            "<foreach item='item' index='index' collection='materialCodes' open='(' separator=',' close=')'>" +
            "#{item}" +
            "</foreach>" +
            "</script>")
    @ResultMap("tapeSpecResultMap")
    List<TapeSpec> selectByMaterialCodes(@Param("materialCodes") List<String> materialCodes);

    /**
     * 新增
     */
    @Insert("INSERT INTO tape_spec (material_code, product_name, color_code, color_name, " +
            "base_thickness, base_material, glue_material, glue_thickness, " +
            "initial_tack_min, initial_tack_max, initial_tack_type, " +
            "total_thickness, total_thickness_min, total_thickness_max, " +
            "peel_strength_min, peel_strength_max, peel_strength_type, " +
            "unwind_force_min, unwind_force_max, unwind_force_type, " +
            "heat_resistance, heat_resistance_type, remark, status, create_by) " +
            "VALUES (#{materialCode}, #{productName}, #{colorCode}, #{colorName}, " +
            "#{baseThickness}, #{baseMaterial}, #{glueMaterial}, #{glueThickness}, " +
            "#{initialTackMin}, #{initialTackMax}, #{initialTackType}, " +
            "#{totalThickness}, #{totalThicknessMin}, #{totalThicknessMax}, " +
            "#{peelStrengthMin}, #{peelStrengthMax}, #{peelStrengthType}, " +
            "#{unwindForceMin}, #{unwindForceMax}, #{unwindForceType}, " +
            "#{heatResistance}, #{heatResistanceType}, #{remark}, #{status}, #{createBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TapeSpec spec);

    /**
     * 更新
     */
    @Update("UPDATE tape_spec SET " +
            "material_code = #{materialCode}, product_name = #{productName}, " +
            "color_code = #{colorCode}, color_name = #{colorName}, " +
            "base_thickness = #{baseThickness}, base_material = #{baseMaterial}, " +
            "glue_material = #{glueMaterial}, glue_thickness = #{glueThickness}, " +
            "initial_tack_min = #{initialTackMin}, initial_tack_max = #{initialTackMax}, initial_tack_type = #{initialTackType}, " +
            "total_thickness = #{totalThickness}, total_thickness_min = #{totalThicknessMin}, total_thickness_max = #{totalThicknessMax}, " +
            "peel_strength_min = #{peelStrengthMin}, peel_strength_max = #{peelStrengthMax}, peel_strength_type = #{peelStrengthType}, " +
            "unwind_force_min = #{unwindForceMin}, unwind_force_max = #{unwindForceMax}, unwind_force_type = #{unwindForceType}, " +
            "heat_resistance = #{heatResistance}, heat_resistance_type = #{heatResistanceType}, " +
            "remark = #{remark}, status = #{status}, update_by = #{updateBy} " +
            "WHERE id = #{id}")
    int update(TapeSpec spec);

    /**
     * 删除
     */
    @Delete("DELETE FROM tape_spec WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    /**
     * 检查料号是否存在
     */
    @Select("SELECT COUNT(*) FROM tape_spec WHERE material_code = #{materialCode} AND id != #{excludeId}")
    int checkMaterialCodeExists(@Param("materialCode") String materialCode, @Param("excludeId") Long excludeId);

    /**
     * 查询所有启用的规格（用于下拉选择）
     */
    @Select("SELECT id, material_code, product_name, color_code, total_thickness FROM tape_spec WHERE status = 1 ORDER BY material_code")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "materialCode", column = "material_code"),
            @Result(property = "productName", column = "product_name"),
            @Result(property = "colorCode", column = "color_code"),
            @Result(property = "totalThickness", column = "total_thickness")
    })
    List<TapeSpec> selectAllEnabled();

    // ========== 字典相关 ==========

    /**
     * 查询颜色字典
     */
    @Select("SELECT id, color_code as code, color_name as name, color_hex as extra FROM tape_color_dict WHERE status = 1 ORDER BY sort_order")
    List<DictItem> selectColorDict();

    /**
     * 查询材质字典
     */
    @Select("SELECT id, material_code as code, material_name as name FROM tape_material_dict WHERE material_type = #{type} AND status = 1 ORDER BY sort_order")
    List<DictItem> selectMaterialDict(@Param("type") String type);
}
