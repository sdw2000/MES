package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.production.ShiftDefinition;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 班次定义Mapper
 */
@Mapper
public interface ShiftDefinitionMapper extends BaseMapper<ShiftDefinition> {

    /**
     * 查询所有启用的班次
     */
    @Select("SELECT * FROM shift_definition WHERE status = 1 ORDER BY shift_code")
    List<ShiftDefinition> selectAllActive();

    /**
     * 根据班次编码查询
     */
    @Select("SELECT * FROM shift_definition WHERE shift_code = #{shiftCode}")
    ShiftDefinition selectByCode(@Param("shiftCode") String shiftCode);
}
