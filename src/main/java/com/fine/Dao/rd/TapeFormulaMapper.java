package com.fine.Dao.rd;

import com.fine.modle.rd.TapeFormula;
import com.fine.modle.rd.TapeFormulaItem;
import com.fine.modle.rd.TapeRawMaterial;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 配胶标准单Mapper
 */
@Mapper
public interface TapeFormulaMapper {

    // =============== 配方主表 ===============

    @Select("<script>" +
            "SELECT * FROM tape_formula WHERE 1=1" +
            "<if test='materialCode != null and materialCode != \"\"'> AND material_code LIKE CONCAT('%',#{materialCode},'%')</if>" +
            "<if test='productName != null and productName != \"\"'> AND product_name LIKE CONCAT('%',#{productName},'%')</if>" +
            "<if test='glueModel != null and glueModel != \"\"'> AND glue_model LIKE CONCAT('%',#{glueModel},'%')</if>" +
            "<if test='status != null'> AND status = #{status}</if>" +
            " ORDER BY create_time DESC" +
            " LIMIT #{offset}, #{size}" +
            "</script>")
    @Results(id = "formulaResultMap", value = {
            @Result(property = "id", column = "id"),
            @Result(property = "materialCode", column = "material_code"),
            @Result(property = "productName", column = "product_name"),
            @Result(property = "formulaNo", column = "formula_no"),
            @Result(property = "version", column = "version"),
            @Result(property = "createDate", column = "create_date"),
            @Result(property = "glueModel", column = "glue_model"),
            @Result(property = "colorCode", column = "color_code"),
            @Result(property = "coatingThickness", column = "coating_thickness"),
            @Result(property = "glueDensity", column = "glue_density"),
            @Result(property = "solidContent", column = "solid_content"),
            @Result(property = "coatingArea", column = "coating_area"),
            @Result(property = "processRemark", column = "process_remark"),
            @Result(property = "totalWeight", column = "total_weight"),
            @Result(property = "preparedBy", column = "prepared_by"),
            @Result(property = "reviewedBy", column = "reviewed_by"),
            @Result(property = "approvedBy", column = "approved_by"),
            @Result(property = "status", column = "status"),
            @Result(property = "remark", column = "remark"),
            @Result(property = "createTime", column = "create_time"),
            @Result(property = "updateTime", column = "update_time"),
            @Result(property = "createBy", column = "create_by"),
            @Result(property = "updateBy", column = "update_by")
    })
    List<TapeFormula> selectList(@Param("materialCode") String materialCode,
                                  @Param("productName") String productName,
                                  @Param("glueModel") String glueModel,
                                  @Param("status") Integer status,
                                  @Param("offset") int offset,
                                  @Param("size") int size);

    @Select("<script>" +
            "SELECT COUNT(*) FROM tape_formula WHERE 1=1" +
            "<if test='materialCode != null and materialCode != \"\"'> AND material_code LIKE CONCAT('%',#{materialCode},'%')</if>" +
            "<if test='productName != null and productName != \"\"'> AND product_name LIKE CONCAT('%',#{productName},'%')</if>" +
            "<if test='glueModel != null and glueModel != \"\"'> AND glue_model LIKE CONCAT('%',#{glueModel},'%')</if>" +
            "<if test='status != null'> AND status = #{status}</if>" +
            "</script>")
    int selectCount(@Param("materialCode") String materialCode,
                    @Param("productName") String productName,
                    @Param("glueModel") String glueModel,
                    @Param("status") Integer status);

    @Select("SELECT * FROM tape_formula WHERE id = #{id}")
    @ResultMap("formulaResultMap")
    TapeFormula selectById(@Param("id") Long id);

    @Select("SELECT * FROM tape_formula WHERE material_code = #{materialCode}")
    @ResultMap("formulaResultMap")
    TapeFormula selectByMaterialCode(@Param("materialCode") String materialCode);

    @Insert("INSERT INTO tape_formula (material_code, product_name, formula_no, version, create_date, " +
            "glue_model, color_code, coating_thickness, glue_density, solid_content, coating_area, " +
            "process_remark, total_weight, prepared_by, reviewed_by, approved_by, status, remark, create_by) " +
            "VALUES (#{materialCode}, #{productName}, #{formulaNo}, #{version}, #{createDate}, " +
            "#{glueModel}, #{colorCode}, #{coatingThickness}, #{glueDensity}, #{solidContent}, #{coatingArea}, " +
            "#{processRemark}, #{totalWeight}, #{preparedBy}, #{reviewedBy}, #{approvedBy}, #{status}, #{remark}, #{createBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TapeFormula formula);

    @Update("UPDATE tape_formula SET " +
            "material_code = #{materialCode}, product_name = #{productName}, formula_no = #{formulaNo}, " +
            "version = #{version}, create_date = #{createDate}, glue_model = #{glueModel}, " +
            "color_code = #{colorCode}, coating_thickness = #{coatingThickness}, glue_density = #{glueDensity}, " +
            "solid_content = #{solidContent}, coating_area = #{coatingArea}, process_remark = #{processRemark}, " +
            "total_weight = #{totalWeight}, prepared_by = #{preparedBy}, reviewed_by = #{reviewedBy}, " +
            "approved_by = #{approvedBy}, status = #{status}, remark = #{remark}, update_by = #{updateBy} " +
            "WHERE id = #{id}")
    int update(TapeFormula formula);

    @Delete("DELETE FROM tape_formula WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM tape_formula WHERE material_code = #{materialCode} AND id != #{excludeId}")
    int checkMaterialCodeExists(@Param("materialCode") String materialCode, @Param("excludeId") Long excludeId);

    // =============== 配方明细 ===============

    @Select("SELECT * FROM tape_formula_item WHERE formula_id = #{formulaId} ORDER BY sort_order")
    @Results(id = "formulaItemResultMap", value = {
            @Result(property = "id", column = "id"),
            @Result(property = "formulaId", column = "formula_id"),
            @Result(property = "materialCode", column = "material_code"),
            @Result(property = "materialName", column = "material_name"),
            @Result(property = "weight", column = "weight"),
            @Result(property = "ratio", column = "ratio"),
            @Result(property = "remark", column = "remark"),
            @Result(property = "sortOrder", column = "sort_order")
    })
    List<TapeFormulaItem> selectItemsByFormulaId(@Param("formulaId") Long formulaId);

    @Insert("INSERT INTO tape_formula_item (formula_id, material_code, material_name, weight, ratio, remark, sort_order) " +
            "VALUES (#{formulaId}, #{materialCode}, #{materialName}, #{weight}, #{ratio}, #{remark}, #{sortOrder})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertItem(TapeFormulaItem item);

    @Update("UPDATE tape_formula_item SET material_code = #{materialCode}, material_name = #{materialName}, " +
            "weight = #{weight}, ratio = #{ratio}, remark = #{remark}, sort_order = #{sortOrder} WHERE id = #{id}")
    int updateItem(TapeFormulaItem item);

    @Delete("DELETE FROM tape_formula_item WHERE id = #{id}")
    int deleteItem(@Param("id") Long id);

    @Delete("DELETE FROM tape_formula_item WHERE formula_id = #{formulaId}")
    int deleteItemsByFormulaId(@Param("formulaId") Long formulaId);

    // =============== 原料字典 ===============

    @Select("SELECT * FROM tape_raw_material WHERE status = 1 ORDER BY sort_order")
    @Results(id = "rawMaterialResultMap", value = {
            @Result(property = "id", column = "id"),
            @Result(property = "materialCode", column = "material_code"),
            @Result(property = "materialName", column = "material_name"),
            @Result(property = "materialType", column = "material_type"),
            @Result(property = "unit", column = "unit"),
            @Result(property = "spec", column = "spec"),
            @Result(property = "sortOrder", column = "sort_order"),
            @Result(property = "status", column = "status")
    })
    List<TapeRawMaterial> selectAllRawMaterials();

    @Select("SELECT * FROM tape_raw_material WHERE material_type = #{type} AND status = 1 ORDER BY sort_order")
    @ResultMap("rawMaterialResultMap")
    List<TapeRawMaterial> selectRawMaterialsByType(@Param("type") String type);

    @Insert("INSERT INTO tape_raw_material (material_code, material_name, material_type, unit, spec, sort_order, status) " +
            "VALUES (#{materialCode}, #{materialName}, #{materialType}, #{unit}, #{spec}, #{sortOrder}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRawMaterial(TapeRawMaterial material);

    @Update("UPDATE tape_raw_material SET material_name = #{materialName}, material_type = #{materialType}, " +
            "unit = #{unit}, spec = #{spec}, sort_order = #{sortOrder}, status = #{status} WHERE id = #{id}")
    int updateRawMaterial(TapeRawMaterial material);

    @Delete("DELETE FROM tape_raw_material WHERE id = #{id}")
    int deleteRawMaterial(@Param("id") Long id);
}
