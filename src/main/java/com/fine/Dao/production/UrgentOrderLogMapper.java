package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fine.model.production.UrgentOrderLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 紧急插单记录Mapper
 */
@Mapper
public interface UrgentOrderLogMapper extends BaseMapper<UrgentOrderLog> {
    
    /**
     * 分页查询紧急插单记录
     */
    IPage<UrgentOrderLog> selectUrgentOrderList(IPage<UrgentOrderLog> page, @Param("params") Map<String, Object> params);
    
    /**
     * 根据排程ID查询
     */
    List<UrgentOrderLog> selectByScheduleId(@Param("scheduleId") Long scheduleId);
}
