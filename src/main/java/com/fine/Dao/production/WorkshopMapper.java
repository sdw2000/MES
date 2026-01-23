package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.production.Workshop;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 车间Mapper接口
 */
@Mapper
public interface WorkshopMapper extends BaseMapper<Workshop> {

    /**
     * 查询所有启用的车间
     */
    @Select("SELECT * FROM workshop WHERE status = 1 AND is_deleted = 0 ORDER BY workshop_code")
    List<Workshop> selectAllEnabled();

    /**
     * 检查车间编号是否存在
     */
    @Select("SELECT COUNT(*) FROM workshop WHERE workshop_code = #{workshopCode} AND is_deleted = 0 AND id != #{excludeId}")
    int checkCodeExists(@Param("workshopCode") String workshopCode, @Param("excludeId") Long excludeId);
}
