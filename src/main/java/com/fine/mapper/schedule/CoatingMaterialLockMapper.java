package com.fine.mapper.schedule;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.schedule.CoatingMaterialLock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 涂布原材料锁定Mapper
 */
@Mapper
public interface CoatingMaterialLockMapper extends BaseMapper<CoatingMaterialLock> {
    
    /**
     * 查询涂布任务的所有锁定原材料
     */
    @Select("SELECT * FROM coating_material_lock WHERE coating_task_id = #{taskId} AND lock_status = 'LOCKED'")
    List<CoatingMaterialLock> selectLockedByTaskId(@Param("taskId") Long taskId);
}
