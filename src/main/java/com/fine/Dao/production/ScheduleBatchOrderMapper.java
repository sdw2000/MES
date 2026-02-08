package com.fine.Dao.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.production.ScheduleBatchOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ScheduleBatchOrderMapper extends BaseMapper<ScheduleBatchOrder> {

    @Select("SELECT * FROM schedule_batch_order WHERE batch_id = #{batchId} ORDER BY id ASC")
    List<ScheduleBatchOrder> selectByBatchId(@Param("batchId") Long batchId);

    @Select("SELECT DISTINCT batch_id FROM schedule_batch_order WHERE order_item_id = #{orderItemId}")
    List<Long> selectBatchIdsByOrderItemId(@Param("orderItemId") Long orderItemId);
}
