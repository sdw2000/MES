package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.modle.production.BatchSchedule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 批次排程数据访问层
 */
@Mapper
public interface BatchScheduleMapper extends BaseMapper<BatchSchedule> {
}
