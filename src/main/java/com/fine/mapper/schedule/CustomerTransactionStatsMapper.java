package com.fine.mapper.schedule;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.schedule.CustomerTransactionStats;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户交易统计Mapper
 */
@Mapper
public interface CustomerTransactionStatsMapper extends BaseMapper<CustomerTransactionStats> {
}
