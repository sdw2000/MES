package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.production.ScheduleApprovalLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 排程审批记录Mapper
 */
@Mapper
public interface ScheduleApprovalLogMapper extends BaseMapper<ScheduleApprovalLog> {
    
    /**
     * 根据排程ID查询审批记录
     */
    List<ScheduleApprovalLog> selectByScheduleId(@Param("scheduleId") Long scheduleId);
}
