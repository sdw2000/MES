package com.fine.mapper.production;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fine.model.production.OrderMaterialLock;
import org.apache.ibatis.annotations.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单预处理物料锁定Mapper
 */
@Mapper
public interface PreprocessingMaterialLockMapper extends BaseMapper<OrderMaterialLock> {

    /**
     * 根据预处理ID查询锁定的物料列表
     */
    @Results(id = "orderLockMap", value = {
            @Result(property = "id", column = "id"),
            @Result(property = "orderId", column = "order_id"),
            @Result(property = "orderItemId", column = "order_item_id"),
            @Result(property = "orderNo", column = "order_no"),
            @Result(property = "preprocessingId", column = "preprocessing_id"),
            @Result(property = "customerId", column = "customer_id"),
            @Result(property = "customerName", column = "customer_name"),
            @Result(property = "customerPriorityScore", column = "customer_priority_score"),
            @Result(property = "materialCode", column = "material_code"),
            @Result(property = "materialSpec", column = "material_spec"),
            @Result(property = "qrCode", column = "material_qr_code"),
            @Result(property = "stockType", column = "stock_type"),
            @Result(property = "stockTableName", column = "stock_table_name"),
            @Result(property = "tapeStockId", column = "stock_record_id"),
            @Result(property = "lockQty", column = "locked_quantity"),
            @Result(property = "lockArea", column = "locked_area"),
            @Result(property = "fifoOrder", column = "fifo_order"),
            @Result(property = "lockedBy", column = "locked_by"),
            @Result(property = "sharedOrderCount", column = "shared_order_count"),
            @Result(property = "sharedOrderDetails", column = "shared_order_details"),
            @Result(property = "lockStatus", column = "lock_status"),
            @Result(property = "lockedAt", column = "lock_time"),
            @Result(property = "remark", column = "remark")
    })
    @Select("SELECT * FROM order_material_lock WHERE preprocessing_id = #{preprocessingId} ORDER BY fifo_order ASC")
    List<OrderMaterialLock> selectByPreprocessingId(@Param("preprocessingId") Long preprocessingId);

    /**
     * 根据订单ID查询锁定的物料列表
     */
    @Select("SELECT * FROM order_material_lock WHERE order_id = #{orderId} AND lock_status = 'locked' ORDER BY fifo_order ASC")
    List<OrderMaterialLock> selectByOrderId(@Param("orderId") Long orderId);

    /**
     * 统计胶带库存被锁定的卷数
     */
    @Select("SELECT COUNT(*) FROM order_material_lock WHERE stock_record_id = #{tapeStockId} AND stock_table_name = 'tape_stock' AND lock_status = 'locked'")
    int countLockedByTapeStockId(@Param("tapeStockId") Long tapeStockId);

    /**
     * 统计胶带库存被锁定的卷数量（按 lock_qty 求和）
     */
    @Select("SELECT COALESCE(SUM(locked_quantity), 0) FROM order_material_lock WHERE stock_record_id = #{tapeStockId} AND stock_table_name = 'tape_stock' AND lock_status = 'locked'")
    java.math.BigDecimal sumLockedQtyByTapeStockId(@Param("tapeStockId") Long tapeStockId);

    /**
     * 统计胶带库存被锁定的面积
     */
    @Select("SELECT COALESCE(SUM(locked_area), 0) FROM order_material_lock WHERE stock_record_id = #{tapeStockId} AND stock_table_name = 'tape_stock' AND lock_status = 'locked'")
    BigDecimal sumLockedAreaByTapeStockId(@Param("tapeStockId") Long tapeStockId);

    /**
     * 根据订单明细ID查询锁定的物料列表
     */
    @Select("SELECT * FROM order_material_lock WHERE order_item_id = #{orderItemId} ORDER BY fifo_order ASC")
    @ResultMap("orderLockMap")
    List<OrderMaterialLock> selectByOrderItemId(@Param("orderItemId") Long orderItemId);

    /**
     * 查询预处理记录的总锁定面积
     */
    @Select("SELECT COALESCE(SUM(locked_area), 0) FROM order_material_lock WHERE preprocessing_id = #{preprocessingId} AND lock_status = 'locked'")
    BigDecimal sumLockedAreaByPreprocessingId(@Param("preprocessingId") Long preprocessingId);

    /** 按订单明细ID统计锁定面积（主业务键） */
    @Select("SELECT COALESCE(SUM(locked_area), 0) FROM order_material_lock WHERE order_item_id = #{orderItemId} AND lock_status = 'locked'")
    BigDecimal sumLockedAreaByOrderItemId(@Param("orderItemId") Long orderItemId);

    /** 统计卷级库存的锁定面积（stock_table_name = tape_stock_rolls） */
    @Select("SELECT COALESCE(SUM(locked_area), 0) FROM order_material_lock WHERE stock_record_id = #{rollId} AND stock_table_name = 'tape_stock_rolls' AND lock_status = 'locked'")
    BigDecimal sumLockedAreaByRollId(@Param("rollId") Long rollId);

    /**
     * 批量插入锁定记录
     */
    @Insert("<script>" +
            "INSERT INTO order_material_lock (" +
            "order_id, order_no, order_item_id, customer_id, customer_name, customer_priority_score, " +
            "material_code, material_spec, material_qr_code, stock_type, stock_table_name, stock_record_id, " +
            "locked_quantity, locked_area, shared_order_count, shared_order_details, lock_status, lock_time, remark, preprocessing_id, fifo_order, locked_by" +
            ") VALUES " +
            "<foreach collection='locks' item='lock' separator=','>" +
            "(#{lock.orderId}, #{lock.orderNo}, #{lock.orderItemId}, #{lock.customerId}, #{lock.customerName}, #{lock.customerPriorityScore}, " +
            "#{lock.materialCode}, #{lock.materialSpec}, #{lock.qrCode}, #{lock.stockType}, #{lock.stockTableName}, #{lock.tapeStockId}, " +
            "#{lock.lockQty}, #{lock.lockArea}, #{lock.sharedOrderCount}, #{lock.sharedOrderDetails}, #{lock.lockStatus}, NOW(), #{lock.remark}, #{lock.preprocessingId}, #{lock.fifoOrder}, #{lock.lockedBy})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("locks") List<OrderMaterialLock> locks);
}
