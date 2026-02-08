package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.production.ScheduleBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ScheduleBatchMapper extends BaseMapper<ScheduleBatch> {

    @Select("SELECT * FROM schedule_batch WHERE source_batch_id = #{sourceBatchId} AND process_type = #{processType} LIMIT 1")
    ScheduleBatch selectBySourceBatchId(@Param("sourceBatchId") Long sourceBatchId, @Param("processType") String processType);
}
