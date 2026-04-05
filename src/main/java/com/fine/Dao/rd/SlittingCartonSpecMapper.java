package com.fine.Dao.rd;

import com.fine.modle.rd.SlittingCartonSpec;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SlittingCartonSpecMapper {

    @Select("<script>" +
            "SELECT * FROM slitting_carton_spec WHERE 1=1 " +
            "<if test='materialCode != null and materialCode != \"\"'> AND material_code LIKE CONCAT('%', #{materialCode}, '%')</if>" +
            "<if test='specName != null and specName != \"\"'> AND spec_name LIKE CONCAT('%', #{specName}, '%')</if>" +
            "<if test='status != null'> AND status = #{status}</if>" +
            " ORDER BY update_time DESC, id DESC " +
            " LIMIT #{offset}, #{size}" +
            "</script>")
    @Results(id = "slittingCartonSpecMap", value = {
            @Result(property = "id", column = "id"),
            @Result(property = "materialCode", column = "material_code"),
            @Result(property = "specName", column = "spec_name"),
            @Result(property = "lengthMm", column = "length_mm"),
            @Result(property = "widthMm", column = "width_mm"),
            @Result(property = "heightMm", column = "height_mm"),
            @Result(property = "status", column = "status"),
            @Result(property = "remark", column = "remark"),
            @Result(property = "createTime", column = "create_time"),
            @Result(property = "updateTime", column = "update_time"),
            @Result(property = "createBy", column = "create_by"),
            @Result(property = "updateBy", column = "update_by")
    })
    List<SlittingCartonSpec> selectList(@Param("materialCode") String materialCode,
                                        @Param("specName") String specName,
                                        @Param("status") Integer status,
                                        @Param("offset") Integer offset,
                                        @Param("size") Integer size);

    @Select("<script>" +
            "SELECT COUNT(*) FROM slitting_carton_spec WHERE 1=1 " +
            "<if test='materialCode != null and materialCode != \"\"'> AND material_code LIKE CONCAT('%', #{materialCode}, '%')</if>" +
            "<if test='specName != null and specName != \"\"'> AND spec_name LIKE CONCAT('%', #{specName}, '%')</if>" +
            "<if test='status != null'> AND status = #{status}</if>" +
            "</script>")
    int selectCount(@Param("materialCode") String materialCode,
                    @Param("specName") String specName,
                    @Param("status") Integer status);

    @Select("SELECT * FROM slitting_carton_spec WHERE id = #{id} LIMIT 1")
    @ResultMap("slittingCartonSpecMap")
    SlittingCartonSpec selectById(@Param("id") Long id);

    @Select("<script>" +
            "SELECT * FROM slitting_carton_spec WHERE material_code = #{materialCode} " +
            "<if test='status != null'> AND status = #{status}</if>" +
            " ORDER BY update_time DESC, id DESC" +
            "</script>")
    @ResultMap("slittingCartonSpecMap")
    List<SlittingCartonSpec> selectByMaterialCode(@Param("materialCode") String materialCode,
                                                  @Param("status") Integer status);

    @Insert("INSERT INTO slitting_carton_spec (material_code, spec_name, length_mm, width_mm, height_mm, status, remark, create_by, update_by) " +
            "VALUES (#{materialCode}, #{specName}, #{lengthMm}, #{widthMm}, #{heightMm}, #{status}, #{remark}, #{createBy}, #{updateBy})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SlittingCartonSpec spec);

    @Update("UPDATE slitting_carton_spec SET material_code=#{materialCode}, spec_name=#{specName}, length_mm=#{lengthMm}, width_mm=#{widthMm}, height_mm=#{heightMm}, status=#{status}, remark=#{remark}, update_by=#{updateBy} WHERE id=#{id}")
    int update(SlittingCartonSpec spec);

    @Delete("DELETE FROM slitting_carton_spec WHERE id=#{id}")
    int deleteById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM slitting_carton_spec WHERE material_code = #{materialCode} AND spec_name = #{specName} AND id != #{excludeId}")
    int checkExists(@Param("materialCode") String materialCode,
                    @Param("specName") String specName,
                    @Param("excludeId") Long excludeId);
}
