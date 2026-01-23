package com.fine.mapper.schedule;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.production.CostTracking;
import org.apache.ibatis.annotations.Mapper;

/**
 * 成本追溯 Mapper
 */
@Mapper
public interface CostTrackingMapper extends BaseMapper<CostTracking> {
}
