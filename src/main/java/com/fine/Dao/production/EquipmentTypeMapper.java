package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.production.EquipmentType;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 设备类型Mapper接口
 */
@Mapper
public interface EquipmentTypeMapper extends BaseMapper<EquipmentType> {

    /**
     * 查询所有启用的设备类型
     */
    @Select("SELECT * FROM equipment_type WHERE status = 1 ORDER BY process_order")
    List<EquipmentType> selectAllEnabled();
}
