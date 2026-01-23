package com.fine.mapper.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fine.model.production.OrderPreprocessing;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 订单预处理Mapper
 */
@Mapper
public interface OrderPreprocessingMapper extends BaseMapper<OrderPreprocessing> {

    /**
     * 分页查询预处理订单
     */
    @Select("<script>" +
            "SELECT * FROM order_preprocessing " +
            "WHERE 1=1 " +
            "<if test='status != null'> AND `status` = #{status}</if> " +
            "<if test='orderNo != null'> AND order_no LIKE CONCAT('%', #{orderNo}, '%')</if> " +
            "<if test='materialCode != null'> AND material_code = #{materialCode}</if> " +
            "ORDER BY created_at DESC" +
            "</script>")
    IPage<OrderPreprocessing> selectPreprocessingPage(Page<OrderPreprocessing> page,
                                                       @Param("status") String status,
                                                       @Param("orderNo") String orderNo,
                                                       @Param("materialCode") String materialCode);

    /**
     * 根据订单ID查询预处理记录
     */
    @Select("SELECT * FROM order_preprocessing WHERE order_id = #{orderId}")
    List<OrderPreprocessing> selectByOrderId(@Param("orderId") Long orderId);

    /**
     * 根据订单明细ID查询预处理记录
     */
    @Select("SELECT * FROM order_preprocessing WHERE order_item_id = #{orderItemId} LIMIT 1")
    OrderPreprocessing selectByOrderItemId(@Param("orderItemId") Long orderItemId);

    /**
     * 更新锁定状态和锁定数量
     */
    @Update("UPDATE order_preprocessing SET lock_status = #{lockStatus}, locked_qty = #{lockedQty}, schedule_type = #{scheduleType} WHERE id = #{id}")
    int updateLockInfo(@Param("id") Long id,
                       @Param("lockStatus") String lockStatus,
                       @Param("lockedQty") java.math.BigDecimal lockedQty,
                       @Param("scheduleType") String scheduleType);

    /**
     * 更新订单状态为已派发
     */
    @Update("UPDATE order_preprocessing SET `status` = #{status}, target_pool = #{targetPool} WHERE id = #{id}")
    int updateStatusAndPool(@Param("id") Long id,
                            @Param("status") String status,
                            @Param("targetPool") String targetPool);
}
