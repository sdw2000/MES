package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.model.production.Equipment;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 设备Mapper接口
 */
@Mapper
public interface EquipmentMapper extends BaseMapper<Equipment> {

    /**
     * 查询设备列表（关联车间和设备类型）
     */
    @Select("<script>" +
            "SELECT e.*, w.workshop_name, et.type_name as equipment_type_name " +
            "FROM equipment e " +
            "LEFT JOIN workshop w ON e.workshop_id = w.id " +
            "LEFT JOIN equipment_type et ON e.equipment_type = et.type_code " +
            "WHERE e.is_deleted = 0 " +
            "<if test='equipmentType != null and equipmentType != \"\"'> AND e.equipment_type = #{equipmentType} </if>" +
            "<if test='workshopId != null'> AND e.workshop_id = #{workshopId} </if>" +
            "<if test='status != null and status != \"\"'> AND e.status = #{status} </if>" +
            "<if test='keyword != null and keyword != \"\"'> AND (e.equipment_code LIKE CONCAT('%',#{keyword},'%') OR e.equipment_name LIKE CONCAT('%',#{keyword},'%')) </if>" +
            "ORDER BY et.process_order, e.equipment_code" +
            "</script>")
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "equipment_code", property = "equipmentCode"),
            @Result(column = "equipment_name", property = "equipmentName"),
            @Result(column = "equipment_type", property = "equipmentType"),
            @Result(column = "workshop_id", property = "workshopId"),
            @Result(column = "brand", property = "brand"),
            @Result(column = "model", property = "model"),
            @Result(column = "max_width", property = "maxWidth"),
            @Result(column = "max_speed", property = "maxSpeed"),
            @Result(column = "daily_capacity", property = "dailyCapacity"),
            @Result(column = "purchase_date", property = "purchaseDate"),
            @Result(column = "status", property = "status"),
            @Result(column = "location", property = "location"),
            @Result(column = "remark", property = "remark"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime"),
            @Result(column = "workshop_name", property = "workshopName"),
            @Result(column = "equipment_type_name", property = "equipmentTypeName")
    })
    List<Equipment> selectEquipmentList(@Param("equipmentType") String equipmentType,
                                        @Param("workshopId") Long workshopId,
                                        @Param("status") String status,
                                        @Param("keyword") String keyword);

    /**
     * 查询设备列表（关联车间和设备类型）
     */
    @Select("<script>" +
            "SELECT e.*, w.workshop_name, et.type_name as equipment_type_name " +
            "FROM equipment e " +
            "LEFT JOIN workshop w ON e.workshop_id = w.id " +
            "LEFT JOIN equipment_type et ON e.equipment_type = et.type_code " +
            "WHERE e.is_deleted = 0 " +
            "<if test='equipmentType != null and equipmentType != \"\"'> AND e.equipment_type = #{equipmentType} </if>" +
            "<if test='workshopId != null'> AND e.workshop_id = #{workshopId} </if>" +
            "<if test='status != null and status != \"\"'> AND e.status = #{status} </if>" +
            "<if test='keyword != null and keyword != \"\"'> AND (e.equipment_code LIKE CONCAT('%',#{keyword},'%') OR e.equipment_name LIKE CONCAT('%',#{keyword},'%')) </if>" +
            "ORDER BY et.process_order, e.equipment_code" +
            "</script>")
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "equipment_code", property = "equipmentCode"),
            @Result(column = "equipment_name", property = "equipmentName"),
            @Result(column = "equipment_type", property = "equipmentType"),
            @Result(column = "workshop_id", property = "workshopId"),
            @Result(column = "brand", property = "brand"),
            @Result(column = "model", property = "model"),
            @Result(column = "max_width", property = "maxWidth"),
            @Result(column = "max_speed", property = "maxSpeed"),
            @Result(column = "daily_capacity", property = "dailyCapacity"),
            @Result(column = "purchase_date", property = "purchaseDate"),
            @Result(column = "status", property = "status"),
            @Result(column = "location", property = "location"),
            @Result(column = "remark", property = "remark"),
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime"),
            @Result(column = "workshop_name", property = "workshopName"),
            @Result(column = "equipment_type_name", property = "equipmentTypeName")
    })
    IPage<Equipment> selectEquipmentPageList(IPage<Equipment> page,
                                        @Param("equipmentType") String equipmentType,
                                        @Param("workshopId") Long workshopId,
                                        @Param("status") String status,
                                        @Param("keyword") String keyword);

    /**
     * 根据设备类型查询可用设备
     */
    @Select("SELECT e.*, w.workshop_name, et.type_name as equipment_type_name " +
            "FROM equipment e " +
            "LEFT JOIN workshop w ON e.workshop_id = w.id " +
            "LEFT JOIN equipment_type et ON e.equipment_type = et.type_code " +
            "WHERE e.is_deleted = 0 AND e.status = 'normal' AND e.equipment_type = #{equipmentType} " +
            "ORDER BY e.equipment_code")
    @Results({
            @Result(column = "id", property = "id"),
            @Result(column = "equipment_code", property = "equipmentCode"),
            @Result(column = "equipment_name", property = "equipmentName"),
            @Result(column = "equipment_type", property = "equipmentType"),
            @Result(column = "workshop_id", property = "workshopId"),
            @Result(column = "max_width", property = "maxWidth"),
            @Result(column = "max_speed", property = "maxSpeed"),
            @Result(column = "daily_capacity", property = "dailyCapacity"),
            @Result(column = "status", property = "status"),
            @Result(column = "workshop_name", property = "workshopName"),
            @Result(column = "equipment_type_name", property = "equipmentTypeName")
    })
    List<Equipment> selectAvailableByType(@Param("equipmentType") String equipmentType);

    /**
     * 检查设备编号是否存在
     */
    @Select("SELECT COUNT(*) FROM equipment WHERE equipment_code = #{equipmentCode} AND is_deleted = 0 AND id != #{excludeId}")
    int checkCodeExists(@Param("equipmentCode") String equipmentCode, @Param("excludeId") Long excludeId);
}
