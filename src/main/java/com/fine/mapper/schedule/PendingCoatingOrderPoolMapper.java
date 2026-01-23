package com.fine.mapper.schedule;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.schedule.PendingCoatingOrderPool;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 待涂布订单池Mapper
 */
@Mapper
public interface PendingCoatingOrderPoolMapper extends BaseMapper<PendingCoatingOrderPool> {
    
    /**
     * 查询料号的所有待涂布订单（按优先级降序）
     */
    @Select("SELECT * FROM pending_coating_order_pool " +
            "WHERE material_code = #{materialCode} AND pool_status = 'WAITING' " +
            "ORDER BY customer_priority DESC, added_at ASC")
    List<PendingCoatingOrderPool> selectWaitingByMaterialCode(@Param("materialCode") String materialCode);
    
    /**
     * 查询所有待涂布料号（去重）
     */
    @Select("SELECT DISTINCT material_code, material_name FROM pending_coating_order_pool " +
            "WHERE pool_status = 'WAITING'")
    List<PendingCoatingOrderPool> selectDistinctWaitingMaterials();

        /**
         * 按池记录ID删除，提交排程后用来移除列表数据。
         */
        int deleteById(@Param("id") Long id);
}
